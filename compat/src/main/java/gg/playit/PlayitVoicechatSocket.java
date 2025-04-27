package gg.playit;

import de.maxhenkel.voicechat.api.RawUdpPacket;
import de.maxhenkel.voicechat.api.VoicechatSocket;
import de.maxhenkel.voicechat.api.events.VoiceHostEvent;
import gg.playit.proto.rest.AgentTunnel;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

public class PlayitVoicechatSocket implements VoicechatSocket, DatagramCompatLayer {
    private Consumer<RoutableDatagramPacket> packetSender;
    private final BlockingQueue<PlayitDatagramPacket> queue = new LinkedBlockingQueue<>();
    private final HashMap<SimpleSocketAddress, InetSocketAddress> reverseNat = new HashMap<>();
    private String connectionAddress;
    private int fakedLocalPort;

    @Override
    public void open(int port, String bindAddress) throws Exception {
    }

    @Override
    public RawUdpPacket read() throws Exception {
        return queue.take();
    }

    @Override
    public void send(byte[] data, SocketAddress address) throws Exception {
        packetSender.accept(new RoutableDatagramPacket(reverseNat.get(SimpleSocketAddress.from(address)), (InetSocketAddress) address, data));
    }

    @Override
    public int getLocalPort() {
        return fakedLocalPort;
    }

    @Override
    public void close() {
    }

    @Override
    public boolean isClosed() {
        return false;
    }

    @Override
    public String protocolName() {
        return "simple-voice-chat";
    }

    @Override
    public void tunnelAssigned(Consumer<RoutableDatagramPacket> packetSender, AgentTunnel tunnel) {
        this.packetSender = packetSender;
        connectionAddress = tunnel.assigned_domain + ":" + tunnel.port.from;
        fakedLocalPort = tunnel.port.from;
    }

    @Override
    public void receivedPacket(RoutableDatagramPacket packet) {
        reverseNat.put(SimpleSocketAddress.from(packet.source()), packet.destination());
        queue.add(new PlayitDatagramPacket(packet.contents(), System.currentTimeMillis(), packet.source()));
    }

    public void setVoiceHost(VoiceHostEvent ev) {
        ev.setVoiceHost(connectionAddress);
    }
}