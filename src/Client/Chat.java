package Client;

import javax.swing.*;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;

public class Chat extends Thread {
    private boolean isRunning = true;

    private JTextArea chatArea;
    private MulticastSocket socket;
    private InetAddress inetAddress;

    public Chat(String username, JTextArea chatArea, MulticastSocket socket, InetAddress inetAddress) {
        this.chatArea = chatArea;
        this.socket = socket;
        this.inetAddress = inetAddress;
    }

    public void run() {
        byte[] buffer = new byte[1024];
        DatagramPacket packet;
        try {
            socket.joinGroup(inetAddress);
            while(isRunning) {
                packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String messaggio = new String(packet.getData(), packet.getOffset(), packet.getLength());
                chatArea.append(messaggio + '\n');
            }
        } catch (IOException e1) { e1.printStackTrace(); }
    }
    public void closeChat() {
        isRunning = false;
    }
}