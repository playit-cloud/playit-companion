package gg.playit;

import gg.playit.proto.rest.AgentTunnel;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.function.Consumer;

public class ForwardingDatagramCompatLayer implements DatagramCompatLayer {
    private final String id;
    private final InetSocketAddress targetAddress;
    private final PlayitAgent agent;
    private Consumer<RoutableDatagramPacket> packetSender;

    private final HashMap<SimpleSocketAddressPair, DatagramChannel> natTable = new HashMap<>();

    public ForwardingDatagramCompatLayer(String id, InetSocketAddress targetAddress, PlayitAgent agent) {
        this.id = id;
        this.targetAddress = targetAddress;
        this.agent = agent;
    }

    @Override
    public void datagramStarted(Consumer<RoutableDatagramPacket> packetSender) {
        this.packetSender = packetSender;
    }

    @Override
    public void receivedPacket(RoutableDatagramPacket packet) {
        var fourTuple = new SimpleSocketAddressPair(SimpleSocketAddress.from(packet.source()), SimpleSocketAddress.from(packet.destination()));
        var conn = natTable.get(fourTuple);
        if (conn != null) {
            var buf = conn.alloc().buffer(packet.contents().length);
            buf.writeBytes(packet.contents());
            conn.writeAndFlush(new DatagramPacket(buf, targetAddress));
        } else {
            var flow = packet.flow();
            var from = packet.destination();
            var to = packet.source();
            var fut = new Bootstrap()
                    .group(agent.group)
                    .channel(agent.epoll ? EpollDatagramChannel.class : NioDatagramChannel.class)
                    .handler(new SimpleChannelInboundHandler<DatagramPacket>() {
                        @Override
                        protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) throws Exception {
                            if (msg.sender().equals(targetAddress)) {
                                var buf = new byte[msg.content().readableBytes()];
                                msg.content().readBytes(buf);
                                var packet = new RoutableDatagramPacket(flow, from, to, buf);
                                packetSender.accept(packet);
                            }
                        }
                    })
                    .connect(targetAddress);
            natTable.put(fourTuple, (DatagramChannel) fut.channel());
        }
    }

    @Override
    public String protocolName() {
        return id;
    }

    @Override
    public void tunnelUpdated(AgentTunnel tunnel) {
    }
}
