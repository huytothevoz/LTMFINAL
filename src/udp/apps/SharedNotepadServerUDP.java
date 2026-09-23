package udp.apps;

import javax.swing.*;
import java.awt.*;
import java.net.*;
import java.util.*;

public class SharedNotepadServerUDP extends JFrame {
    private JTextArea logArea;
    private DatagramSocket socket;
    private int port;
    
    private HashMap<SocketAddress, String> clients = new HashMap<>();
    private String sharedText = "";

    public SharedNotepadServerUDP(int port) {
        this.port = port;
        setupUI();
        startServer();
    }

    // ===== LẤY IP LAN CHUẨN =====
    private String getLANIP() {
        try {
            Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface netint : Collections.list(nets)) {
                if (!netint.isUp() || netint.isLoopback() || netint.isVirtual()) continue;

                for (InetAddress addr : Collections.list(netint.getInetAddresses())) {
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {}
        return "127.0.0.1";
    }

    private void setupUI() {
        setTitle("UDP Shared Notepad - SERVER (Port: " + port + ")");
        setSize(450, 350);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE); 
        setLocationRelativeTo(null);

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                if (socket != null && !socket.isClosed()) {
                    socket.close(); 
                    log("Đã đóng Port " + port);
                }
                dispose(); 
            }
        });

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setBackground(Color.BLACK);
        logArea.setForeground(Color.GREEN);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 14));

        add(new JScrollPane(logArea), BorderLayout.CENTER);
    }

    private void log(String msg) {
        SwingUtilities.invokeLater(() -> logArea.append(msg + "\n"));
    }

    private void startServer() {
        new Thread(() -> {
            try {
                // ===== SOCKET BIND CHUẨN =====
                socket = new DatagramSocket(null);
                socket.setReuseAddress(true);
                socket.bind(new InetSocketAddress(port));

                String hostIP = getLANIP();

                log("✅ Server Notepad chạy tại Port " + port);
                log("📌 IP LAN: " + hostIP);
                log("---------------------------------------");

                byte[] buffer = new byte[65507]; // max UDP

                while (!socket.isClosed()) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);

                    String message = new String(packet.getData(), 0, packet.getLength(), "UTF-8");
                    SocketAddress clientAddress = packet.getSocketAddress();

                    // ===== JOIN =====
                    if (message.startsWith("JOIN|")) {
                        String username = message.substring(5);

                        if (!clients.containsKey(clientAddress)) {
                            clients.put(clientAddress, username);
                            log("+++ [" + username + "] từ " + clientAddress);
                        }

                        // ===== SYNC FULL =====
                        sendToClient("SYNC|" + sharedText, clientAddress);
                    } 

                    // ===== UPDATE =====
                    else if (message.startsWith("UPDATE|")) {

                        // Server giữ trạng thái chuẩn
                        sharedText = message.substring(7);

                        // ===== BROADCAST CHO TẤT CẢ (QUAN TRỌNG) =====
                        for (SocketAddress client : clients.keySet()) {
                            sendToClient("SYNC|" + sharedText, client);
                        }
                    }
                }

            } catch (SocketException se) {
                log("Server đóng.");
            } catch (Exception e) {
                log("Lỗi Server: " + e.getMessage());
            }
        }).start();
    }

    private void sendToClient(String msg, SocketAddress address) {
        try {
            byte[] data = msg.getBytes("UTF-8");
            DatagramPacket packet = new DatagramPacket(data, data.length, address);
            socket.send(packet);
        } catch (Exception ignored) {}
    }
}