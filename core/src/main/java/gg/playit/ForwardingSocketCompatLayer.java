package gg.playit;

import gg.playit.proto.rest.AgentTunnel;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.net.InetSocketAddress;

public class ForwardingSocketCompatLayer implements SocketCompatLayer {
    private final String id;
    private final InetSocketAddress targetAddress;
    private final PlayitAgent agent;

    public ForwardingSocketCompatLayer(String id, InetSocketAddress targetAddress, PlayitAgent agent) {
        this.id = id;
        this.targetAddress = targetAddress;
        this.agent = agent;
    }

    @Override
    public void receivedConnection(InetSocketAddress localAddress, InetSocketAddress remoteAddress, SocketChannel channel) {
        channel.pipeline().addLast(new PlayitBoundHandler());
    }

    @Override
    public String protocolName() {
        return id;
    }

    @Override
    public void tunnelUpdated(AgentTunnel tunnel) {
    }

    private class LocalBoundHandler extends ChannelInboundHandlerAdapter {
        SocketChannel toPlayit;
        public LocalBoundHandler(SocketChannel channel) {
            super();
            toPlayit = channel;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            ctx.read();
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            toPlayit.writeAndFlush(msg).addListener(it -> {
                if (it.isSuccess()) {
                    ctx.channel().read();
                } else {
                    agent.logger.error("Error forwarding with LocalBoundHandler", it.cause());
                    toPlayit.close();
                }
            });
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            if (toPlayit.isActive()) {
                toPlayit.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            super.exceptionCaught(ctx, cause);
            agent.logger.error("Error on LocalBoundHandler", cause);
            this.channelInactive(ctx);
        }
    }

    private class PlayitBoundHandler extends ChannelInboundHandlerAdapter {
        SocketChannel toLocal;

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            var fut = new Bootstrap()
                    .group(agent.group)
                    .channel(agent.epoll ? EpollSocketChannel.class : NioSocketChannel.class)
                    .handler(new LocalBoundHandler((SocketChannel) ctx.channel()))
                    .option(ChannelOption.AUTO_READ, false)
                    .connect(targetAddress);
            toLocal = (SocketChannel) fut.channel();
            fut.addListener(it -> {
                if (it.isSuccess()) {
                    ctx.channel().read();
                } else {
                    agent.logger.error("Error opening localbound channel", it.cause());
                    ctx.channel().close();
                }
            });
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (toLocal.isActive()) {
                toLocal.writeAndFlush(msg).addListener(it -> {
                    if (it.isSuccess()) {
                        ctx.channel().read();
                    } else {
                        agent.logger.error("Error forwarding with PlayitBoundHandler", it.cause());
                        ((ChannelFuture) it).channel().close();
                    }
                });
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            if (toLocal.isActive()) {
                toLocal.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            super.exceptionCaught(ctx, cause);
            agent.logger.error("Error on PlayitBoundHandler", cause);
            this.channelInactive(ctx);
        }
    }
}