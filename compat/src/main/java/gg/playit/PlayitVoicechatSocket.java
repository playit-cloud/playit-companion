package gg.playit;

import de.maxhenkel.voicechat.api.RawUdpPacket;
import de.maxhenkel.voicechat.api.VoicechatSocket;
import de.maxhenkel.voicechat.api.events.VoiceHostEvent;
import gg.playit.proto.rest.AgentTunnel;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

public class PlayitVoicechatSocket extends SimpleChannelInboundHandler<DatagramPacket> implements VoicechatSocket, DatagramCompatLayer {
    private Consumer<RoutableDatagramPacket> packetSender;
    private final BlockingQueue<PlayitDatagramPacket> queue = new LinkedBlockingQueue<>();
    private final HashMap<SimpleSocketAddress, InetSocketAddressWithUdpFlowExtension> reverseNat = new HashMap<>();
    private String connectionAddress;

    private NioEventLoopGroup group;
    private NioDatagramChannel datagramChannel;

    @Override
    public void open(int port, String bindAddress) throws Exception {
        group = new NioEventLoopGroup();
        Bootstrap b = new Bootstrap();
        b.group(group);
        b.channel(NioDatagramChannel.class);
        b.handler(this);
        datagramChannel = (NioDatagramChannel) b.bind(bindAddress, port).sync().channel();
    }

    @Override
    public RawUdpPacket read() throws Exception {
        return queue.take();
    }

    @Override
    public void send(byte[] data, SocketAddress address) throws Exception {
        var sourceAddr = reverseNat.get(SimpleSocketAddress.from(address));
        if (sourceAddr != null) {
            packetSender.accept(new RoutableDatagramPacket(sourceAddr.flow(), sourceAddr.address(), (InetSocketAddress) address, data));
        } else {
            var buf = datagramChannel.alloc().buffer(data.length);
            buf.writeBytes(data);
            datagramChannel.writeAndFlush(new DatagramPacket(buf, (InetSocketAddress) address));
        }
    }

    @Override
    public int getLocalPort() {
        return datagramChannel.localAddress().getPort();
    }

    @Override
    public void close() {
        datagramChannel.close().syncUninterruptibly();
        group.shutdownGracefully().syncUninterruptibly();
        datagramChannel = null;
        group = null;
    }

    @Override
    public boolean isClosed() {
        return datagramChannel == null;
    }

    @Override
    public String protocolName() {
        return "simple-voice-chat";
    }

    @Override
    public void datagramStarted(Consumer<RoutableDatagramPacket> packetSender) {
        this.packetSender = packetSender;
    }

    @Override
    public void tunnelUpdated(AgentTunnel tunnel) {
        connectionAddress = tunnel.assigned_domain + ":" + tunnel.port.from;
    }

    @Override
    public void receivedPacket(RoutableDatagramPacket packet) {
        reverseNat.put(SimpleSocketAddress.from(packet.source()), new InetSocketAddressWithUdpFlowExtension(packet.destination(), packet.flow()));
        queue.add(new PlayitDatagramPacket(packet.contents(), System.currentTimeMillis(), packet.source()));
    }

    public void setVoiceHost(VoiceHostEvent ev) {
        if (connectionAddress != null) {
            ev.setVoiceHost(connectionAddress);
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) throws Exception {
        var buf = new byte[msg.content().readableBytes()];
        msg.content().readBytes(buf);
        queue.add(new PlayitDatagramPacket(buf, System.currentTimeMillis(), msg.sender()));
    }
}