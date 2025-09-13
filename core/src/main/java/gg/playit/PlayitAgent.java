package gg.playit;

import gg.playit.proto.control.AgentSessionId;
import gg.playit.proto.control.c2s.AgentKeepAliveControlRequest;
import gg.playit.proto.control.c2s.ControlRpcRequest;
import gg.playit.proto.control.c2s.PingControlRequest;
import gg.playit.proto.control.c2s.SetupUdpChannelControlRequest;
import gg.playit.proto.control.s2c.*;
import gg.playit.proto.rest.*;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import io.netty.util.Timer;
import org.slf4j.Logger;

import java.io.Closeable;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.InvalidParameterException;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class PlayitAgent implements Closeable {
    public enum ClaimStep {
        NotDone,
        Rejected,
        Accepted
    }

    private static final HashMap<String, CompatLayer> compatLayers = new HashMap<>();

    public static void registerCompatLayer(CompatLayer compatLayer) {
        compatLayers.put(compatLayer.protocolName(), compatLayer);
    }

    public final Platform platform;
    public final ApiClient apiClient = new ApiClient();
    public final EventLoopGroup group;
    public final boolean epoll;
    public final Logger logger;

    private Channel controlChannel;
    private DatagramChannel datagramChannel;

    private final Timer timer = new HashedWheelTimer();

    private String claimCode;
    private String secretKey;

    private AgentRundataResponse cachedRundata;

    private final HashMap<String, CompatLayer> compatLayerOverlay = new HashMap<>();
    private String lastDomain;

    public PlayitAgent(Platform platform) {
        this.platform = platform;
        epoll = platform.shouldUseEpoll();
        if (epoll) {
            group = new EpollEventLoopGroup();
        } else {
            group = new NioEventLoopGroup();
        }
        logger = platform.getLogger();
        try {
            var storedKey = platform.getAgentKey();
            if (storedKey != null) {
                apiClient.setAgentKey(storedKey);
                var rundataResp = apiClient.agentsRundata();
                if (!(rundataResp instanceof AgentRundataResponse)) {
                    throw new IOException("Error getting agent data: " + rundataResp.toString());
                }
                if (!((AgentRundataResponse) rundataResp).agent_type.equals("self-managed")) {
                    throw new IOException("Invalid agent type: " + ((AgentRundataResponse) rundataResp).agent_type);
                }
                if (!((AgentRundataResponse) rundataResp).account_status.equals("ready")) {
                    throw new IOException("Bad account status: " + ((AgentRundataResponse) rundataResp).account_status);
                }
                cachedRundata = (AgentRundataResponse) rundataResp;
                secretKey = storedKey;
            }
        } catch (Exception e) {
            logger.warn("Failed to read agent key from file", e);
        }
        try {
            var path = platform.getCustomTunnelsConfigPath();
            if (Files.exists(path)) {
                var config = QDCSS.load(path);
                var configMap = config.flatten();
                for (var key: configMap.keySet()) {
                    if (key.endsWith(".enabled")) {
                        var tunnel = key.substring(0, key.length() - 8);
                        try {
                            if (config.getBoolean(key).orElse(false)) {
                                var ip = config.get(tunnel + ".ip");
                                if (ip.isEmpty()) {
                                    logger.error("Tunnel lacks ip but is enabled: {}", tunnel);
                                    continue;
                                }
                                var port = config.getInt(tunnel + ".port");
                                if (port.isEmpty()) {
                                    logger.error("Tunnel lacks port but is enabled: {}", tunnel);
                                    continue;
                                }
                                var address = new InetSocketAddress(InetAddress.getByName(ip.get()), port.get());
                                var protocol = config.get(tunnel + ".protocol");
                                if (protocol.isEmpty()) {
                                    logger.error("Tunnel lacks protocol but is enabled: {}", tunnel);
                                    continue;
                                }
                                switch (protocol.get()) {
                                    case "tcp":
                                        var tcpLayer = new ForwardingSocketCompatLayer(tunnel, address, this);
                                        compatLayerOverlay.put(tunnel, tcpLayer);
                                        break;
                                    case "udp":
                                        var udpLayer = new ForwardingDatagramCompatLayer(tunnel, address, this);
                                        compatLayerOverlay.put(tunnel, udpLayer);
                                        break;
                                    default:
                                        logger.error("Invalid protocol for tunnel {}: {}", tunnel, protocol);
                                }
                            } else {
                                compatLayerOverlay.put(tunnel, new DisabledCompatLayer());
                            }
                        } catch (QDCSS.BadValueException | UnknownHostException e) {
                            logger.error("Error parsing config for tunnel {}", tunnel, e);
                        }
                    }
                }
            } else {
                var template = PlayitAgent.class.getResource("/config-template.css");
                try (var stream = template.openStream()) {
                    Files.writeString(path, new String(stream.readAllBytes(), StandardCharsets.UTF_8), StandardCharsets.UTF_8);
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to read custom tunnels configuration", e);
        }
        if (secretKey == null) {
            claimCode = makeClaimCode();
        }
    }

    private SocketCompatLayer getSocketLayer(AgentTunnel tunnel) {
        if (compatLayerOverlay.get(tunnel.tunnel_type) instanceof SocketCompatLayer socketLayer) {
            return socketLayer;
        }
        if (compatLayers.get(tunnel.tunnel_type) instanceof SocketCompatLayer socketLayer && !(compatLayerOverlay.containsKey(tunnel.tunnel_type))) {
            return socketLayer;
        }
        if (compatLayerOverlay.get(tunnel.name) instanceof SocketCompatLayer socketLayer) {
            return socketLayer;
        }
        if (compatLayers.get(tunnel.name) instanceof SocketCompatLayer socketLayer && !(compatLayerOverlay.containsKey(tunnel.name))) {
            return socketLayer;
        }
        return null;
    }

    private DatagramCompatLayer getDatagramLayer(AgentTunnel tunnel) {
        if (compatLayerOverlay.get(tunnel.tunnel_type) instanceof DatagramCompatLayer datagramLayer) {
            return datagramLayer;
        }
        if (compatLayers.get(tunnel.tunnel_type) instanceof DatagramCompatLayer datagramLayer && !(compatLayerOverlay.containsKey(tunnel.tunnel_type))) {
            return datagramLayer;
        }
        if (compatLayerOverlay.get(tunnel.name) instanceof DatagramCompatLayer datagramLayer) {
            return datagramLayer;
        }
        if (compatLayers.get(tunnel.name) instanceof DatagramCompatLayer datagramLayer && !(compatLayerOverlay.containsKey(tunnel.name))) {
            return datagramLayer;
        }
        return null;
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
        secretKey = ((ClaimExchangeResponse) exchangeResp).secret_key;
        apiClient.setAgentKey(secretKey);
        try {
            platform.writeAgentKey(secretKey);
        } catch (IOException e) {
            logger.warn("Failed to write agent key to file", e);
        }
        claimCode = null;
        return ClaimStep.Accepted;
    }

    private boolean anyMatch(List<AgentTunnel> tunnels, CompatLayer compatLayer) {
        String tunnelType;
        if (compatLayer instanceof SocketCompatLayer) {
            tunnelType = "tcp";
        } else {
            tunnelType = "udp";
        }
        for (AgentTunnel tunnel : tunnels) {
            if (!(tunnel.proto.equals(tunnelType) || tunnel.proto.equals("both"))) {
                continue;
            }
            if (tunnel.tunnel_type.equals(compatLayer.protocolName()) || tunnel.name.equals(compatLayer.protocolName())) {
                return true;
            }
        }
        return false;
    }

    private void setupTunnels() throws IOException, InterruptedException {
        var rundataResp = apiClient.agentsRundata();
        if (!(rundataResp instanceof AgentRundataResponse))
            throw new IOException("rundata error: " + rundataResp.toString());
        var id = ((AgentRundataResponse) rundataResp).agent_id;
        var tunnels = ((AgentRundataResponse) rundataResp).tunnels;
        if (tunnels.stream().noneMatch(e -> e.tunnel_type.equals("minecraft-java"))) {
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
        }
        for (CompatLayer compatLayer : compatLayers.values()) {
            if (compatLayerOverlay.containsKey(compatLayer.protocolName())) {
                continue;
            }
            if (anyMatch(tunnels, compatLayer)) {
                continue;
            }
            String tunnelType;
            if (compatLayer instanceof SocketCompatLayer) {
                tunnelType = "tcp";
            } else {
                tunnelType = "udp";
            }
            var tunnelLayerResp = apiClient.tunnelsCreate(new TunnelsCreateRequest(
                    compatLayer.protocolName(),
                    null,
                    tunnelType,
                    1,
                    new TunnelOriginCreate("managed", new TunnelOriginCreateManaged(id)),
                    true,
                    new TunnelCreateUseAllocation("region", new TunnelCreateUseAllocationRegion("global"))
            ));
            if (!(tunnelLayerResp instanceof TunnelsCreateResponse))
                throw new IOException("tunnel creation error for compat layer " + compatLayer.protocolName() + ": " + tunnelLayerResp.toString());
        }
        for (CompatLayer compatLayer : compatLayerOverlay.values()) {
            if (anyMatch(tunnels, compatLayer)) {
                continue;
            }
            String tunnelType;
            if (compatLayer instanceof SocketCompatLayer) {
                tunnelType = "tcp";
            } else if (compatLayer instanceof DatagramCompatLayer) {
                tunnelType = "udp";
            } else {
                continue;
            }
            var tunnelLayerResp = apiClient.tunnelsCreate(new TunnelsCreateRequest(
                    compatLayer.protocolName(),
                    null,
                    tunnelType,
                    1,
                    new TunnelOriginCreate("managed", new TunnelOriginCreateManaged(id)),
                    true,
                    new TunnelCreateUseAllocation("region", new TunnelCreateUseAllocationRegion("global"))
            ));
            if (!(tunnelLayerResp instanceof TunnelsCreateResponse))
                throw new IOException("tunnel creation error for configured compat layer " + compatLayer.protocolName() + ": " + tunnelLayerResp.toString());
            var newRundataResp = apiClient.agentsRundata();
            if (!(newRundataResp instanceof AgentRundataResponse))
                throw new IOException("rundata error: " + newRundataResp.toString());
            cachedRundata = (AgentRundataResponse) newRundataResp;
        }
    }

    public void run() throws IOException, InterruptedException {
        var rundataResp = apiClient.agentsRundata();
        if (!(rundataResp instanceof AgentRundataResponse)) {
            throw new IOException("Error getting agent data: " + rundataResp.toString());
        }
        cachedRundata = (AgentRundataResponse) rundataResp;
        var resp = apiClient.agentsRoutingGet();
        if (!(resp instanceof AgentRoutingGetResponse))
            throw new IOException("agent routing error: " + resp.toString());
        List<InetSocketAddress> addrsToTry = new ArrayList<>();
        if (!((AgentRoutingGetResponse) resp).disable_ip6) {
            for (String addr : ((AgentRoutingGetResponse) resp).targets6) {
                addrsToTry.add(new InetSocketAddress(Inet6Address.getByName(addr), 5525));
            }
        }
        for (String addr : ((AgentRoutingGetResponse) resp).targets4) {
            addrsToTry.add(new InetSocketAddress(Inet4Address.getByName(addr), 5525));
        }
        Bootstrap b = new Bootstrap();
        b.group(group);
        b.channel(epoll ? EpollDatagramChannel.class : NioDatagramChannel.class);
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
        if (datagramChannel != null) {
            try {
                datagramChannel.close().await();
            } catch (InterruptedException ignored) {}
        }
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
        private final Set<Long> pendingRequests = new HashSet<>();
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

        private DatagramChannelHandler datagramChannelHandler;

        public ControlChannelHandler(List<InetSocketAddress> addresses) {
            this.addresses = addresses;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) throws Exception {
            if (!msg.sender().equals(addresses.get(currentTargetAddress))) {
                logger.warn("Bad sender tunnel_addr, discarding unread");
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
                                        "fe4b4acd-19a7-45a8-9f31-c88cb9969855",
                                        0,
                                        1,
                                        0
                                ),
                                2,
                                "minecraft-plugin",
                                pong.client_addr().toString(),
                                pong.tunnel_addr().toString()
                        ));
                        if (!(registerResp instanceof ProtoRegisterResponse)) {
                            logger.error("Failed to register control channel: {}", registerResp);
                            return;
                        }
                        authKey = HexFormat.of().parseHex(((ProtoRegisterResponse) registerResp).key);
                        var ch = ctx.channel();
                        var keyBuffer = ch.alloc().buffer(8 + authKey.length);
                        var lastRequestId = System.currentTimeMillis();
                        keyBuffer.writeLong(lastRequestId);
                        pendingRequests.add(lastRequestId);
                        keyBuffer.writeBytes(authKey);
                        ch.writeAndFlush(new DatagramPacket(keyBuffer, addresses.get(currentTargetAddress)));
                        setupTunnels();
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
                    if (datagramChannelHandler == null) {
                        datagramChannelHandler = new DatagramChannelHandler();
                        sendPacket(ctx.channel(), new ControlRpcRequest(System.currentTimeMillis(), new SetupUdpChannelControlRequest(id)));
                    }
                } else if (rpcResponse.content() instanceof UdpChannelDetailsControlResponse udpDetails) {
                    datagramChannelHandler.authKey = udpDetails.token();
                    datagramChannelHandler.dialAddress = udpDetails.tunnel_addr().address();
                    logger.info("dialing {} for UDP", udpDetails.tunnel_addr().address());
                    Bootstrap b = new Bootstrap();
                    b.group(group);
                    b.channel(epoll ? EpollDatagramChannel.class : NioDatagramChannel.class);
                    b.handler(datagramChannelHandler);
                    datagramChannel = (DatagramChannel) b.bind(0).channel();
                } else {
                    logger.error("Unhandled response type!");
                }
            } else if (parsed instanceof AbstractNewClient newClient) {
                Bootstrap b = new Bootstrap();
                b.group(group);
                b.channel(epoll ? EpollSocketChannel.class : NioSocketChannel.class);
                b.handler(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelActive(ChannelHandlerContext ctx) throws Exception {
                        var token = newClient.claim_instructions().token();
                        var buf = ctx.alloc().buffer(token.length);
                        buf.writeBytes(token);
                        ctx.channel().writeAndFlush(buf);
                        for (AgentTunnel tunnel : cachedRundata.tunnels) {
                            if (newClient.matches(tunnel)) {
                                var socketLayer = getSocketLayer(tunnel);
                                if (socketLayer != null) {
                                    socketLayer.receivedConnection(newClient.connect_addr().address(), newClient.peer_addr().address(), (SocketChannel) ctx.channel());
                                } else if ("minecraft-java".equals(tunnel.tunnel_type)) {
                                    platform.newMinecraftConnection(newClient.peer_addr().address(), (SocketChannel) ctx.channel());
                                } else {
                                    logger.error("No protocol registered for tunnel ID {}! Closing connection!", tunnel.id);
                                    ctx.channel().close();
                                }
                                super.channelActive(ctx);
                                return;
                            }
                        }
                        logger.error("Could not match TCP connection to a tunnel! Closing connection!");
                        super.channelActive(ctx);
                        ctx.channel().close();
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
                    logger.info("trying next tunnel_addr: {}", addresses.get(currentTargetAddress));
                }
                sendPacket(ctx.channel(), new ControlRpcRequest(1, new PingControlRequest(System.currentTimeMillis(), OptionalInt.empty(), Optional.empty())));
                establishReattemptTimeout = timer.newTimeout(timeout.task(), 500, TimeUnit.MILLISECONDS);
            }, 500, TimeUnit.MILLISECONDS);

            for (AgentTunnel tunnel : cachedRundata.tunnels) {
                if (tunnel.proto.equals("udp"))
                    continue;
                if ("minecraft-java".equals(tunnel.tunnel_type)) {
                    var domain = tunnel.custom_domain;
                    if (domain == null)
                        domain = tunnel.assigned_domain;
                    if (lastDomain == null) {
                        platform.tunnelAddressInformation(domain);
                        lastDomain = domain;
                    }
                }
                var socketLayer = getSocketLayer(tunnel);
                if (socketLayer != null) {
                    socketLayer.tunnelUpdated(tunnel);
                }
            }

            timer.newTimeout(timeout -> {
                timer.newTimeout(timeout.task(), 10, TimeUnit.SECONDS);
                var rundataResp = apiClient.agentsRundata();
                if (!(rundataResp instanceof AgentRundataResponse))
                    throw new IOException("rundata error: " + rundataResp.toString());
                if (!rundataResp.equals(cachedRundata)) {
                    for (AgentTunnel tunnel : ((AgentRundataResponse) rundataResp).tunnels) {
                        if ("minecraft-java".equals(tunnel.tunnel_type)) {
                            var domain = tunnel.custom_domain;
                            if (domain == null)
                                domain = tunnel.assigned_domain;
                            if (lastDomain == null) {
                                platform.tunnelAddressInformation(domain);
                                lastDomain = domain;
                            }
                        }
                        if (!tunnel.proto.equals("tcp")) {
                            var datagramLayer = getDatagramLayer(tunnel);
                            if (datagramLayer != null) {
                                datagramLayer.tunnelUpdated(tunnel);
                            }
                        }
                        if (!tunnel.proto.equals("udp")) {
                            var socketLayer = getSocketLayer(tunnel);
                            if (socketLayer != null) {
                                socketLayer.tunnelUpdated(tunnel);
                            }
                        }
                    }
                }
                cachedRundata = (AgentRundataResponse) rundataResp;
            }, 10, TimeUnit.SECONDS);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            logger.error("Exception from handler!", cause);
            platform.notifyError();
        }

        private class DatagramChannelHandler extends SimpleChannelInboundHandler<DatagramPacket> {
            private InetSocketAddress dialAddress;
            private byte[] authKey;
            private long lastEstablish = -1;
            private Timeout datagramKeepaliveTimeout;

            @Override
            protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) throws Exception {
                if (!msg.sender().equals(dialAddress)) {
                    logger.warn("Bad sender address for UDP, discarding unread");
                    return;
                }
                var buf = msg.content();
                var allExceptFooter = buf.readBytes(buf.readableBytes() - 8);
                var footer = buf.readLong();
                RoutableDatagramPacket packet;
                if (footer == 0xd01fe6830ddce781L) {
                    lastEstablish = System.nanoTime();
                    return;
                } else if (footer == 0x5cb867cf78817399L) {
                    var allExceptFooterAndPacketId = allExceptFooter.readBytes(allExceptFooter.readableBytes() - 2);
                    var packetId = allExceptFooterAndPacketId.readUnsignedShort();
                    byte[] contents;
                    if (packetId == 0) {
                        contents = new byte[allExceptFooterAndPacketId.readableBytes() - 30];
                    } else {
                        contents = new byte[allExceptFooterAndPacketId.readableBytes() - 33];
                    }
                    allExceptFooterAndPacketId.readBytes(contents);
                    var srcIp = new byte[4];
                    var dstIp = new byte[4];
                    allExceptFooterAndPacketId.readBytes(srcIp);
                    allExceptFooterAndPacketId.readBytes(dstIp);
                    var srcPort = allExceptFooterAndPacketId.readUnsignedShort();
                    var dstPort = allExceptFooterAndPacketId.readUnsignedShort();
                    var src = new InetSocketAddress(Inet4Address.getByAddress(srcIp), srcPort);
                    var dst = new InetSocketAddress(Inet4Address.getByAddress(dstIp), dstPort);

                    var clientServerId = allExceptFooterAndPacketId.readLong();
                    var tunnelId = allExceptFooterAndPacketId.readLong();
                    var portOffset = allExceptFooterAndPacketId.readUnsignedShort();
                    var flow = new UdpFlowExtension(clientServerId, tunnelId, portOffset);

                    if (packetId != 0) {
                        var hasMore = allExceptFooterAndPacketId.readUnsignedByte() != 0;
                        var fragOffset = allExceptFooterAndPacketId.readUnsignedShort();
                    }

                    packet = new RoutableDatagramPacket(flow, src, dst, contents);
                } else if (footer == 0x6cb667cf78817369L) {
                    var contents = new byte[allExceptFooter.readableBytes() - 54];
                    allExceptFooter.readBytes(contents);
                    var srcIp = new byte[16];
                    var dstIp = new byte[16];
                    allExceptFooter.readBytes(srcIp);
                    allExceptFooter.readBytes(dstIp);
                    var srcPort = allExceptFooter.readUnsignedShort();
                    var dstPort = allExceptFooter.readUnsignedShort();
                    var src = new InetSocketAddress(Inet6Address.getByAddress(srcIp), srcPort);
                    var dst = new InetSocketAddress(Inet6Address.getByAddress(dstIp), dstPort);

                    var clientServerId = allExceptFooter.readLong();
                    var tunnelId = allExceptFooter.readLong();
                    var portOffset = allExceptFooter.readUnsignedShort();
                    var flow = new UdpFlowExtension(clientServerId, tunnelId, portOffset);

                    packet = new RoutableDatagramPacket(flow, src, dst, contents);
                } else if (footer == 0x5cb867cf788173b2L) {
                    var contents = new byte[allExceptFooter.readableBytes() - 12];
                    allExceptFooter.readBytes(contents);
                    var srcIp = new byte[4];
                    var dstIp = new byte[4];
                    allExceptFooter.readBytes(srcIp);
                    allExceptFooter.readBytes(dstIp);
                    var srcPort = allExceptFooter.readUnsignedShort();
                    var dstPort = allExceptFooter.readUnsignedShort();
                    var src = new InetSocketAddress(Inet4Address.getByAddress(srcIp), srcPort);
                    var dst = new InetSocketAddress(Inet4Address.getByAddress(dstIp), dstPort);
                    packet = new RoutableDatagramPacket(null, src, dst, contents);
                } else if (footer == 0x6668676f68616366L) {
                    var contents = new byte[allExceptFooter.readableBytes() - 40];
                    allExceptFooter.readBytes(contents);
                    var srcIp = new byte[16];
                    var dstIp = new byte[16];
                    allExceptFooter.readBytes(srcIp);
                    allExceptFooter.readBytes(dstIp);
                    var srcPort = allExceptFooter.readUnsignedShort();
                    var dstPort = allExceptFooter.readUnsignedShort();
                    var src = new InetSocketAddress(Inet6Address.getByAddress(srcIp), srcPort);
                    var dst = new InetSocketAddress(Inet6Address.getByAddress(dstIp), dstPort);
                    packet = new RoutableDatagramPacket(null, src, dst, contents);
                } else {
                    logger.warn("Bad UDP footer, discarding!");
                    return;
                }
                for (AgentTunnel tunnel : cachedRundata.tunnels) {
                    if (packet.matches(tunnel)) {
                        var datagramLayer = getDatagramLayer(tunnel);
                        if (datagramLayer != null) {
                            datagramLayer.receivedPacket(packet);
                        }
                        return;
                    }
                }
                logger.error("Could not match UDP packet to a tunnel!");
            }

            private void sendKey(Channel ch) throws IOException {
                var buffer = ch.alloc().buffer(authKey.length);
                buffer.writeBytes(authKey);
                ch.writeAndFlush(new DatagramPacket(buffer, dialAddress));
            }

            @Override
            public void channelActive(ChannelHandlerContext ctx) throws Exception {
                super.channelActive(ctx);
                Consumer<RoutableDatagramPacket> sendPacket = packet -> {
                    var ch = ctx.channel();
                    var buffer = ch.alloc().buffer(packet.contents().length + 48);
                    buffer.writeBytes(packet.contents());
                    if (packet.source().getAddress() instanceof Inet4Address sourceAddress && packet.destination().getAddress() instanceof Inet4Address destinationAddress) {
                        if (packet.flow() == null) {
                            buffer.writeBytes(sourceAddress.getAddress());
                            buffer.writeBytes(destinationAddress.getAddress());
                            buffer.writeShort(packet.source().getPort());
                            buffer.writeShort(packet.destination().getPort());
                            buffer.writeLong(0x5cb867cf788173b2L);
                        } else {
                            buffer.writeBytes(sourceAddress.getAddress());
                            buffer.writeBytes(destinationAddress.getAddress());
                            buffer.writeShort(packet.source().getPort());
                            buffer.writeShort(packet.destination().getPort());
                            buffer.writeLong(packet.flow().client_server_id());
                            buffer.writeLong(packet.flow().tunnel_id());
                            buffer.writeShort(packet.flow().port_offset());
                            buffer.writeShort(0);
                            buffer.writeLong(0x5cb867cf78817399L);
                        }
                    } else if (packet.source().getAddress() instanceof Inet6Address sourceAddress && packet.destination().getAddress() instanceof Inet6Address destinationAddress) {
                        if (packet.flow() == null) {
                            buffer.writeBytes(sourceAddress.getAddress());
                            buffer.writeBytes(destinationAddress.getAddress());
                            buffer.writeShort(packet.source().getPort());
                            buffer.writeShort(packet.destination().getPort());
                            buffer.writeInt(0);
                            buffer.writeLong(0x6668676f68616366L);
                        } else {
                            buffer.writeBytes(sourceAddress.getAddress());
                            buffer.writeBytes(destinationAddress.getAddress());
                            buffer.writeShort(packet.source().getPort());
                            buffer.writeShort(packet.destination().getPort());
                            buffer.writeLong(packet.flow().client_server_id());
                            buffer.writeLong(packet.flow().tunnel_id());
                            buffer.writeShort(packet.flow().port_offset());
                            buffer.writeLong(0x6cb667cf78817369L);
                        }
                    } else {
                        throw new InvalidParameterException("Bad address family for routing!");
                    }
                    ch.writeAndFlush(new DatagramPacket(buffer, dialAddress));
                };
                for (CompatLayer compatLayer : compatLayers.values()) {
                    if (compatLayer instanceof DatagramCompatLayer datagramLayer && !compatLayerOverlay.containsKey(compatLayer.protocolName())) {
                         datagramLayer.datagramStarted(sendPacket);
                    }
                }
                for (CompatLayer compatLayer : compatLayerOverlay.values()) {
                    if (compatLayer instanceof DatagramCompatLayer datagramLayer) {
                        datagramLayer.datagramStarted(sendPacket);
                    }
                }
                for (AgentTunnel tunnel : cachedRundata.tunnels) {
                    if (tunnel.proto.equals("tcp"))
                        continue;
                    var datagramLayer = getDatagramLayer(tunnel);
                    if (datagramLayer != null) {
                        datagramLayer.tunnelUpdated(tunnel);
                    }
                }
                sendKey(ctx.channel());
                datagramKeepaliveTimeout = timer.newTimeout(timeout -> {
                    sendKey(ctx.channel());
                    datagramKeepaliveTimeout = timer.newTimeout(timeout.task(), lastEstablish == -1 ? 500 : 1000, TimeUnit.MILLISECONDS);
                }, 500, TimeUnit.MILLISECONDS);
            }
        }
    }
}
