package gg.playit;

import gg.playit.proto.control.AgentSessionId;
import gg.playit.proto.control.c2s.AgentKeepAliveControlRequest;
import gg.playit.proto.control.c2s.ControlRpcRequest;
import gg.playit.proto.control.c2s.PingControlRequest;
import gg.playit.proto.control.s2c.*;
import gg.playit.proto.rest.*;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
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
import org.slf4j.Logger;

import java.io.Closeable;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class PlayitAgent implements Closeable {
    public enum ClaimStep {
        NotDone,
        Rejected,
        Accepted
    }

    private final Platform platform;
    private final ApiClient apiClient = new ApiClient();
    private final NioEventLoopGroup group = new NioEventLoopGroup();
    private final Logger logger;

    private Channel controlChannel;

    private final Timer timer = new HashedWheelTimer();

    private String claimCode;
    private String secretKey;

    public PlayitAgent(Platform platform) {
        this.platform = platform;
        logger = platform.getLogger();
        // TODO: load secret from file
        try {
            var storedKey = platform.getAgentKey();
            if (storedKey != null) {
                secretKey = storedKey;
                apiClient.setAgentKey(storedKey);
            }
        } catch (IOException e) {
            logger.warn("Failed to read agent key from file", e);
        }
        if (secretKey == null) {
            claimCode = makeClaimCode();
        }
    }

    public String getClaimCode() {
        return claimCode;
    }

    /// Progresses the claim procedure. Repeatedly call with a delay until ClaimStep.Accepted or ClaimStep.Rejected is returned
    public ClaimStep claimStep() throws IOException, InterruptedException {
        if (claimCode == null)
            return secretKey == null ? ClaimStep.Rejected : ClaimStep.Accepted;
        var resp = apiClient.claimSetup(claimCode, "self-managed", "playit-companion " + platform.getVersion());
        if (!(resp instanceof ClaimSetupResponse))
            throw new IOException("claim setup error: " + resp.toString());
        switch (((ClaimSetupResponse) resp)) {
            case WaitingForUserVisit:
            case WaitingForUser:
                return ClaimStep.NotDone;
            case UserRejected:
                claimCode = null;
                return ClaimStep.Rejected;
            case UserAccepted:
                break;
        }
        var exchangeResp = apiClient.claimExchange(claimCode);
        if (!(exchangeResp instanceof ClaimExchangeResponse))
            throw new IOException("claim exchange error: " + exchangeResp.toString());
        secretKey = ((ClaimExchangeResponse) exchangeResp).secret_key();
        logger.debug("got secret key {}", secretKey);
        apiClient.setAgentKey(secretKey);
        try {
            platform.writeAgentKey(secretKey);
        } catch (IOException e) {
            logger.warn("Failed to write agent key to file", e);
        }
        var rundataResp = apiClient.agentsRundata();
        if (!(rundataResp instanceof AgentRundataResponse))
            throw new IOException("rundata error: " + rundataResp.toString());
        var id = ((AgentRundataResponse) rundataResp).agent_id();
        var tunnelResp = apiClient.tunnelsCreate(new TunnelsCreateRequest(
                "Minecraft Java",
                "minecraft-java",
                "tcp",
                1,
                new TunnelOriginCreate("managed", new TunnelOriginCreateManaged(id)),
                true,
                new TunnelCreateUseAllocation("region", new TunnelCreateUseAllocationRegion("global"))
        ));
        if (!(tunnelResp instanceof TunnelsCreateResponse))
            throw new IOException("tunnel creation error: " + tunnelResp.toString());
        claimCode = null;
        return ClaimStep.Accepted;
    }

    public void run() throws IOException, InterruptedException {
        var resp = apiClient.agentsRoutingGet();
        if (!(resp instanceof AgentRoutingGetResponse))
            throw new IOException("agent routing error: " + resp.toString());
        logger.debug("got routing resp {}", resp);
        List<InetSocketAddress> addrsToTry = new ArrayList<>();
        if (!((AgentRoutingGetResponse) resp).disable_ip6()) {
            for (String addr : ((AgentRoutingGetResponse) resp).targets6()) {
                addrsToTry.add(new InetSocketAddress(Inet6Address.getByName(addr), 5525));
            }
        }
        for (String addr : ((AgentRoutingGetResponse) resp).targets4()) {
            addrsToTry.add(new InetSocketAddress(Inet4Address.getByName(addr), 5525));
        }
        Bootstrap b = new Bootstrap();
        b.group(group);
        b.channel(NioDatagramChannel.class);
        b.handler(new ControlChannelHandler(addrsToTry));
        controlChannel = b.bind(0).channel();
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
        timer.stop();
        if (controlChannel != null) {
            try {
                controlChannel.close().await();
            } catch (InterruptedException ignored) {}
        }
        try {
            group.shutdownGracefully().await();
        } catch (InterruptedException ignored) {}
    }

    private class ControlChannelHandler extends SimpleChannelInboundHandler<DatagramPacket> {
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

        public ControlChannelHandler(List<InetSocketAddress> addresses) {
            this.addresses = addresses;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) throws Exception {
            if (!msg.sender().equals(addresses.get(currentTargetAddress))) {
                logger.warn("Bad sender address, discarding unread");
                return;
            }
            var buffer = msg.content();
            var parsed = WireReadable.from(buffer);
            if (parsed instanceof ControlRpcResponse rpcResponse) {
                if (!pendingRequests.remove(rpcResponse.request_id())) {
                    logger.error("Got a response to a nonexistent request?");
                    return;
                }
                if (rpcResponse.content() instanceof PongControlResponse pong) {
                    if (pong.session_expire_at().isPresent()) {
                        expiry = (pong.session_expire_at().getAsLong() - pong.server_now()) + pong.request_now(); // wrong to about RTT, which is Close Enough
                    }
                    if (expiry < System.currentTimeMillis()) {
                        rtt = (int) (System.currentTimeMillis() - pong.request_now());
                        establishReattemptTimeout.cancel();
                        var registerResp = apiClient.protoRegister(new ProtoRegisterRequest(
                                new PlayitAgentVersion(
                                        new AgentVersion("minecraft-plugin", platform.getVersion(), false),
                                        true,
                                        null),
                                pong.client_addr().toString(),
                                pong.tunnel_addr().toString()
                        ));
                        if (!(registerResp instanceof ProtoRegisterResponse)) {
                            logger.error("Failed to register control channel: {}", registerResp);
                            return;
                        }
                        logger.debug("got register key {}", ((ProtoRegisterResponse) registerResp).key());
                        authKey = HexFormat.of().parseHex(((ProtoRegisterResponse) registerResp).key());
                        var ch = ctx.channel();
                        var keyBuffer = ch.alloc().buffer(8 + authKey.length);
                        var lastRequestId = System.currentTimeMillis();
                        keyBuffer.writeLong(lastRequestId);
                        pendingRequests.add(lastRequestId);
                        keyBuffer.writeBytes(authKey);
                        ch.writeAndFlush(new DatagramPacket(keyBuffer, addresses.get(currentTargetAddress)));
                    }
                } else if (rpcResponse.content() instanceof RequestQueuedControlResponse) {
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
                        logger.debug("registered agent! response: {}", agentRegistered);
                        pingTimeout = timer.newTimeout(timeout -> {
                            var ch = ctx.channel();
                            sendPacket(ch, new ControlRpcRequest(200, new PingControlRequest(System.currentTimeMillis(), OptionalInt.of(rtt), Optional.of(id))));
                            pingTimeout = timer.newTimeout(timeout.task(), 1000, TimeUnit.MILLISECONDS);
                        }, 1000, TimeUnit.MILLISECONDS);
                    }
                    if (keepaliveTimeout == null) {
                        keepaliveTimeout = timer.newTimeout(timeout -> {
                            var ch = ctx.channel();
                            sendPacket(ch, new ControlRpcRequest(100, new AgentKeepAliveControlRequest(id)));
                            keepaliveTimeout = timer.newTimeout(timeout.task(), 10, TimeUnit.SECONDS);
                        }, 10, TimeUnit.SECONDS);
                    }
                } else {
                    logger.error("Unhandled response type!");
                }
            } else if (parsed instanceof NewClient newClient) {
                logger.debug("new client: {}", newClient);
                Bootstrap b = new Bootstrap();
                b.group(group);
                b.channel(NioSocketChannel.class);
                b.handler(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelActive(ChannelHandlerContext ctx) throws Exception {
                        var token = newClient.claim_instructions().token();
                        var buf = ctx.alloc().buffer(token.length);
                        buf.writeBytes(token);
                        ctx.channel().writeAndFlush(buf);
                        platform.newMinecraftConnection(newClient.peer_addr().address(), (NioSocketChannel) ctx.channel());
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

        private void sendPacket(Channel ch, ControlRpcRequest packet) throws IOException {
            var buffer = ch.alloc().buffer();
            pendingRequests.add(packet.request_id());
            packet.writeTo(buffer);
            ch.writeAndFlush(new DatagramPacket(buffer, addresses.get(currentTargetAddress)));
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            super.channelActive(ctx);
            sendPacket(ctx.channel(), new ControlRpcRequest(1, new PingControlRequest(System.currentTimeMillis(), OptionalInt.empty(), Optional.empty())));
            establishReattemptTimeout = timer.newTimeout(timeout -> {
                currentTargetAddressTries += 1;
                var isv6 = addresses.get(currentTargetAddress).getAddress() instanceof Inet6Address;
                var maxAttempts = isv6 ? 3 : 5;
                if (currentTargetAddressTries >= maxAttempts) {
                    currentTargetAddress += 1;
                    currentTargetAddress %= addresses.size();
                    logger.info("trying next address: {}", addresses.get(currentTargetAddress));
                }
                sendPacket(ctx.channel(), new ControlRpcRequest(1, new PingControlRequest(System.currentTimeMillis(), OptionalInt.empty(), Optional.empty())));
                establishReattemptTimeout = timer.newTimeout(timeout.task(), 500, TimeUnit.MILLISECONDS);
            }, 500, TimeUnit.MILLISECONDS);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            logger.error("Exception from handler!", cause);
        }
    }
}
