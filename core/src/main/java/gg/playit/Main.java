package gg.playit;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import java.nio.charset.StandardCharsets;

public class Main {
    public static void main(String[] args) throws Exception {
        System.out.println("Hello, World!");
        var agent = new PlayitAgent("meow");
//        System.out.println(agent.getClaimCode());
//        while (agent.claimStep() == PlayitAgent.ClaimStep.NotDone) {
//            Thread.sleep(500);
//        }
        agent.run(((address, channel) -> {
            channel.pipeline().addLast(new SimpleChannelInboundHandler<ByteBuf>() {
                @Override
                protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
                    var buf = new byte[msg.readableBytes()];
                    msg.readBytes(buf);
                    System.out.println(new String(buf, StandardCharsets.UTF_8));
                }
            });
        }));
        System.out.println("Hello, World!");
    }
}