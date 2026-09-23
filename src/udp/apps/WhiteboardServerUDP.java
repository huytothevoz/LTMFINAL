package udp.apps;

import java.util.List;
import javax.swing.*;
import java.awt.*;
import java.net.*;
import java.util.*;

public class WhiteboardServerUDP extends JFrame {
    private JTextArea logArea;
    private DatagramSocket socket;
    private int port;
    
    private HashMap<SocketAddress, String> clients = new HashMap<>();
    private List<String> drawHistory = new ArrayList<>();

    public WhiteboardServerUDP(int port) {
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
        setTitle("Bảng Vẽ - SERVER (Port: " + port + ")");
        setSize(450, 300);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                if (socket != null && !socket.isClosed()) socket.close();
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

                log("✅ Server chạy tại Port " + port);
                log("📌 IP LAN: " + hostIP);
                log("---------------------------------------");

                // ===== BUFFER MAX UDP =====
                byte[] buffer = new byte[65507];

                while (!socket.isClosed()) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);

                    String msg = new String(packet.getData(), 0, packet.getLength(), "UTF-8");
                    SocketAddress clientAddr = packet.getSocketAddress();

                    if (msg.startsWith("JOIN|")) {
                        String username = msg.substring(5);
                        clients.put(clientAddr, username);
                        log("+++ [" + username + "] từ " + clientAddr);

                        // ===== FIX: SYNC CHUẨN =====
                        sendToClient("SYNC|CLEAR", clientAddr);
                        for (String historyCmd : drawHistory) {
                            sendToClient("SYNC|" + historyCmd, clientAddr);
                        }
                    } 
                    else if (msg.startsWith("DRAW|")) {

                        // ===== FIX: TRÁNH DUPLICATE =====
                        if (!drawHistory.contains(msg)) {
                            drawHistory.add(msg);
                        }

                        broadcast(msg, clientAddr);
                    } 
                    else if (msg.startsWith("UNDO|")) {
                        String strokeId = msg.split("\\|")[1];

                        // ===== FIX: XÓA ĐÚNG STROKE =====
                        drawHistory.removeIf(cmd -> cmd.startsWith("DRAW|" + strokeId + "|"));

                        broadcast(msg, clientAddr);
                    }
                    else if (msg.equals("CLEAR")) {
                        drawHistory.clear();
                        broadcast("CLEAR", clientAddr);
                    }
                }

            } catch (SocketException se) {
                log("Server đóng.");
            } catch (Exception e) {
                log("Lỗi: " + e.getMessage());
            }
        }).start();
    }

    private void broadcast(String msg, SocketAddress senderAddr) {
        for (SocketAddress client : clients.keySet()) {
            if (!client.equals(senderAddr)) {
                sendToClient(msg, client);
            }
        }
    }

    private void sendToClient(String msg, SocketAddress address) {
        try {
            byte[] data = msg.getBytes("UTF-8");
            DatagramPacket packet = new DatagramPacket(data, data.length, address);
            socket.send(packet);
        } catch (Exception ignored) {}
    }
}