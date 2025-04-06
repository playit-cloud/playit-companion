package gg.playit;

import com.google.gson.Gson;
import gg.playit.proto.control.AgentSessionId;
import gg.playit.proto.control.c2s.AgentKeepAliveControlRequest;
import gg.playit.proto.control.c2s.ControlRpcRequest;
import gg.playit.proto.control.c2s.PingControlRequest;
import gg.playit.proto.control.s2c.*;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import io.netty.util.Timer;

import java.io.Closeable;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

public class PlayitAgent implements Closeable {
    public enum ClaimStep {
        NotDone,
        Rejected,
        Accepted
    }

    private record ClaimSetupRequest(String code, String agent_type, String version) {}
    private record ClaimSetupResponse(String status, String data) {}
    private record ClaimExchangeRequest(String code) {}
    private record ClaimExchangeResponse(String status, ClaimExchangeResponseInner data) {}
    private record ClaimExchangeResponseInner(String secret_key) {}
    private record AgentRoutingGetResponse(String status, AgentRoutingGetResponseInner data) {}
    private record AgentRoutingGetResponseInner(String agent_id, List<String> targets4, List<String> targets6, boolean disable_ip6) {}
    private record ProtoRegisterRequest(PlayitAgentVersion agent_version, String client_addr, String tunnel_addr) {}
    private record PlayitAgentVersion(AgentVersion version, boolean official, String details_website) {}
    private record AgentVersion(String platform, String version, boolean has_expired) {}
    private record ProtoRegisterResponse(String status, ProtoRegisterResponseInner data) {}
    private record ProtoRegisterResponseInner(String key) {}

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final NioEventLoopGroup group = new NioEventLoopGroup();
    private final Gson gson = new Gson();

    private final Timer timer = new HashedWheelTimer();

    private String claimCode;
    private String secretKey;

    public PlayitAgent() {
        // TODO: load secret from file
        claimCode = makeClaimCode();
    }

    public PlayitAgent(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getClaimCode() {
        return claimCode;
    }

    /// Progresses the claim procedure. Repeatedly call with a delay until ClaimStep.Accepted or ClaimStep.Rejected is returned
    public ClaimStep claimStep() throws IOException, InterruptedException {
        if (claimCode == null)
            return secretKey == null ? ClaimStep.Rejected : ClaimStep.Accepted;
        var claimSetupReq = HttpRequest.newBuilder(URI.create("https://api.playit.gg/claim/setup"))
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(new ClaimSetupRequest(claimCode, "self-managed", "playit-companion 0.1.0")))) // TODO: gradlery to replace version
                .setHeader("Content-Type", "application/json")
                .build();
        var claimSetupResp = httpClient.send(claimSetupReq, HttpResponse.BodyHandlers.ofString());
        var resp = gson.fromJson(claimSetupResp.body(), ClaimSetupResponse.class);
        switch (resp.data) {
            case "WaitingForUserVisit":
            case "WaitingForUser":
                return ClaimStep.NotDone;
            case "UserRejected":
                claimCode = null;
                return ClaimStep.Rejected;
            case "UserAccepted":
                break;
            default:
                throw new IOException("Bad step type?");
        }
        var claimExchangeReq = HttpRequest.newBuilder(URI.create("https://api.playit.gg/claim/exchange"))
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(new ClaimExchangeRequest(claimCode))))
                .setHeader("Content-Type", "application/json")
                .build();
        var claimExchangeResp = httpClient.send(claimExchangeReq, HttpResponse.BodyHandlers.ofString());
        var exchangeResp = gson.fromJson(claimExchangeResp.body(), ClaimExchangeResponse.class);
        // TODO: save secret to file
        secretKey = exchangeResp.data.secret_key;
        System.out.println(secretKey);
        claimCode = null;
        return ClaimStep.Accepted;
    }

    public void run(BiConsumer<InetSocketAddress, NioSocketChannel> connector) throws IOException, InterruptedException {
        var agentRoutingGetReq = HttpRequest.newBuilder(URI.create("https://api.playit.gg/agents/routing/get"))
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .setHeader("Content-Type", "application/json")
                .setHeader("Authorization", "Agent-Key " + secretKey)
                .build();
        var agentRoutingGetResp = httpClient.send(agentRoutingGetReq, HttpResponse.BodyHandlers.ofString());
        var resp = gson.fromJson(agentRoutingGetResp.body(), AgentRoutingGetResponse.class);
        System.out.println(resp);
        List<InetSocketAddress> addrsToTry = new ArrayList<>();
        if (!resp.data.disable_ip6) {
            for (String addr : resp.data.targets6) {
                addrsToTry.add(new InetSocketAddress(Inet6Address.getByName(addr), 5525));
            }
        }
        for (String addr : resp.data.targets4) {
            addrsToTry.add(new InetSocketAddress(Inet4Address.getByName(addr), 5525));
        }
        Bootstrap b = new Bootstrap();
        b.group(group);
        b.channel(NioDatagramChannel.class);
        b.handler(new ControlChannelHandler(connector, addrsToTry));
        b.bind(0);
    }

    private static final String ALPHABET = "0123456789abcdefghjkmnpqrstvwxyz";

    private static String makeClaimCode() {
        var rng = new SecureRandom();
        var builder = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            builder.append(ALPHABET.charAt(rng.nextInt(32)));
        }
        return builder.toString();
    }

    @Override
    public void close() throws IOException {
//        httpClient.close();
        try {
            group.shutdownGracefully().await();
        } catch (InterruptedException ignored) {}
    }

    private class ControlChannelHandler extends SimpleChannelInboundHandler<DatagramPacket> {
        private final BiConsumer<InetSocketAddress, NioSocketChannel> connector;
        private Set<Long> pendingRequests = new HashSet<>();
        private byte[] authKey;

        private final List<InetSocketAddress> addresses;
        private int currentTargetAddress = 0;
        private int currentTargetAddressTries = 0;

        private int rtt = -1;

        private long expiry = 0;
        private AgentSessionId id;

        private Timeout establishReattemptTimeout;
        private Timeout keepaliveTimeout;
        private Timeout pingTimeout;

        public ControlChannelHandler(BiConsumer<InetSocketAddress, NioSocketChannel> connector, List<InetSocketAddress> addresses) {
            this.connector = connector;
            this.addresses = addresses;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) throws Exception {
            if (!msg.sender().equals(addresses.get(currentTargetAddress))) {
                System.out.println("Bad sender address, discarding unread");
                return;
            }
            var buffer = msg.content();
            var parsed = WireReadable.from(buffer);
            if (parsed instanceof ControlRpcResponse rpcResponse) {
                if (!pendingRequests.remove(rpcResponse.request_id())) {
                    throw new IOException("bad response request id");
                }
                if (rpcResponse.content() instanceof PongControlResponse pong) {
                    if (pong.session_expire_at().isPresent()) {
                        expiry = (pong.session_expire_at().getAsLong() - pong.server_now()) + pong.request_now(); // wrong to about RTT, which is Close Enough
                    }
                    if (expiry < System.currentTimeMillis()) {
                        rtt = (int) (System.currentTimeMillis() - pong.request_now());
                        establishReattemptTimeout.cancel();
                        var protoRegisterReq = HttpRequest.newBuilder(URI.create("https://api.playit.gg/proto/register"))
                                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(new ProtoRegisterRequest(
                                        new PlayitAgentVersion(
                                                new AgentVersion("minecraft-plugin", "0.1.0", false),
                                                true,
                                                null),
                                        pong.client_addr().address().toString().split("/")[1],
                                        pong.tunnel_addr().address().toString().split("/")[1]
                                ))))
                                .setHeader("Content-Type", "application/json")
                                .setHeader("Authorization", "Agent-Key " + secretKey)
                                .build();
                        var protoRegisterResp = httpClient.send(protoRegisterReq, HttpResponse.BodyHandlers.ofString());
                        var registerResp = gson.fromJson(protoRegisterResp.body(), ProtoRegisterResponse.class);
                        System.out.println(registerResp.data.key);
                        authKey = HexFormat.of().parseHex(registerResp.data.key);
                        var ch = ctx.channel();
                        var keyBuffer = ch.alloc().buffer(8 + authKey.length);
                        var lastRequestId = System.currentTimeMillis();
                        keyBuffer.writeLong(lastRequestId);
                        pendingRequests.add(lastRequestId);
                        keyBuffer.writeBytes(authKey);
                        ch.writeAndFlush(new DatagramPacket(keyBuffer, addresses.get(currentTargetAddress)));
                    }
                } else if (rpcResponse.content() instanceof RequestQueuedControlResponse) {
                    if (authKey == null) {
                        throw new IOException("what");
                    }
                    timer.newTimeout(timeout -> {
                        var ch = ctx.channel();
                        var keyBuffer = ch.alloc().buffer(8 + authKey.length);
                        var lastRequestId = System.currentTimeMillis();
                        keyBuffer.writeLong(lastRequestId);
                        pendingRequests.add(lastRequestId);
                        keyBuffer.writeBytes(authKey);
                        ch.writeAndFlush(new DatagramPacket(keyBuffer, addresses.get(currentTargetAddress)));
                    }, 200, TimeUnit.MILLISECONDS);
                } else if (rpcResponse.content() instanceof AgentRegisteredControlResponse agentRegistered) {
                    expiry = agentRegistered.expires_at();
                    id = agentRegistered.id();
                    if (pingTimeout == null) {
                        System.out.println(agentRegistered);
                        pingTimeout = timer.newTimeout(timeout -> {
                            var ch = ctx.channel();
                            var keepAliveBuffer = ch.alloc().buffer(22);
                            var lastRequestId = System.currentTimeMillis();
                            var keepAliveMsg = new ControlRpcRequest(lastRequestId, new PingControlRequest(lastRequestId, OptionalInt.of(rtt), Optional.of(id)));
                            pendingRequests.add(lastRequestId);
                            keepAliveMsg.writeTo(keepAliveBuffer);
                            ch.writeAndFlush(new DatagramPacket(keepAliveBuffer, addresses.get(currentTargetAddress)));
                            pingTimeout = timer.newTimeout(timeout.task(), 1000, TimeUnit.MILLISECONDS);
                        }, 1000, TimeUnit.MILLISECONDS);
                    }
                    if (keepaliveTimeout == null) {
                        keepaliveTimeout = timer.newTimeout(timeout -> {
                            var ch = ctx.channel();
                            var keepAliveBuffer = ch.alloc().buffer(22);
                            var lastRequestId = System.currentTimeMillis();
                            var keepAliveMsg = new ControlRpcRequest(lastRequestId, new AgentKeepAliveControlRequest(id));
                            pendingRequests.add(lastRequestId);
                            keepAliveMsg.writeTo(keepAliveBuffer);
                            ch.writeAndFlush(new DatagramPacket(keepAliveBuffer, addresses.get(currentTargetAddress)));
                            keepaliveTimeout = timer.newTimeout(timeout.task(), 10, TimeUnit.SECONDS);
                        }, 10, TimeUnit.SECONDS);
                    }
                } else {
                    throw new IOException("unhandled response type");
                }
            } else if (parsed instanceof NewClient newClient) {
                System.out.println(newClient);
                Bootstrap b = new Bootstrap();
                b.group(group);
                b.channel(NioSocketChannel.class);
                b.handler(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelActive(ChannelHandlerContext ctx) throws Exception {
                        var token = newClient.claim_instructions().token();
                        System.out.println(Arrays.toString(token));
                        var buf = ctx.alloc().buffer(token.length);
                        buf.writeBytes(token);
                        ctx.channel().writeAndFlush(buf);
                        connector.accept(newClient.peer_addr().address(), (NioSocketChannel) ctx.channel());
                        super.channelActive(ctx);
                    }

                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                        ((ByteBuf) msg).skipBytes(8);
                        ctx.pipeline().remove(this);
                        super.channelRead(ctx, msg);
                    }
                });
                b.connect(newClient.claim_instructions().address().address());
            }
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            super.channelActive(ctx);
            var ch = ctx.channel();
            var buffer = ch.alloc().buffer(22);
            var msg = new ControlRpcRequest(1, new PingControlRequest(System.currentTimeMillis(), OptionalInt.empty(), Optional.empty()));
            pendingRequests.add(1L);
            msg.writeTo(buffer);
            ch.writeAndFlush(new DatagramPacket(buffer, addresses.get(currentTargetAddress)));
            establishReattemptTimeout = timer.newTimeout(timeout -> {
                currentTargetAddressTries += 1;
                var isv6 = addresses.get(currentTargetAddress).getAddress() instanceof Inet6Address;
                var maxAttempts = isv6 ? 3 : 5;
                if (currentTargetAddressTries >= maxAttempts) {
                    currentTargetAddress += 1;
                    currentTargetAddress %= addresses.size();
                    System.out.println(addresses.get(currentTargetAddress));
                }
                var chRetry = ctx.channel();
                var bufferRetry = chRetry.alloc().buffer(22);
                var msgRetry = new ControlRpcRequest(1, new PingControlRequest(System.currentTimeMillis(), OptionalInt.empty(), Optional.empty()));
                pendingRequests.add(1L);
                msgRetry.writeTo(bufferRetry);
                ch.writeAndFlush(new DatagramPacket(bufferRetry, addresses.get(currentTargetAddress)));
                establishReattemptTimeout = timer.newTimeout(timeout.task(), 500, TimeUnit.MILLISECONDS);
            }, 500, TimeUnit.MILLISECONDS);
        }
    }
}
