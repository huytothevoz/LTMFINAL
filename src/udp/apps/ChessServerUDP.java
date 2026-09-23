package udp.apps;

import javax.swing.*;
import java.awt.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;

public class ChessServerUDP extends JFrame {
    private JTextArea logArea;
    private DatagramSocket socket;
    private int port;
    private List<SocketAddress> players = new ArrayList<>();

    public ChessServerUDP(int port) {
        this.port = port;
        setupUI();
        startServer();
    }

    private void setupUI() {
        setTitle("Chess Server (Port: " + port + ")");
        setSize(400, 250);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent e) {
                if (socket != null && !socket.isClosed()) socket.close();
                dispose();
            }
        });

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setBackground(Color.BLACK);
        logArea.setForeground(Color.GREEN);
        logArea.setFont(new Font("Consolas", Font.BOLD, 14));
        add(new JScrollPane(logArea), BorderLayout.CENTER);
    }

    private void log(String msg) {
        SwingUtilities.invokeLater(() -> logArea.append(msg + "\n"));
    }

    private String getLocalIPv4() {
        try {
            java.util.Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
            while (nets.hasMoreElements()) {
                NetworkInterface netint = nets.nextElement();

                if (!netint.isUp() || netint.isLoopback()) continue;

                java.util.Enumeration<InetAddress> addrs = netint.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();

                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            log("Lỗi lấy IP: " + e.getMessage());
        }
        return "Không tìm thấy IP";
    }

    private void startServer() {
        new Thread(() -> {
            try {
                socket = new DatagramSocket(port, InetAddress.getByName("0.0.0.0"));

                String hostIP = getLocalIPv4();

                log("✅ Server Cờ Vua đang chạy...");
                log("🌐 IP LAN của bạn: " + hostIP);
                log("📡 Port: " + port);
                log("👉 Client nhập IP này để kết nối!");
                log("---------------------------------------");

                byte[] buffer = new byte[2048];

                while (!socket.isClosed()) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);

                    String msg = new String(packet.getData(), 0, packet.getLength(), "UTF-8");
                    SocketAddress clientAddr = packet.getSocketAddress();

                    if (msg.startsWith("JOIN|")) {
                        if (!players.contains(clientAddr)) {
                            players.add(clientAddr);
                            log("⚡ Player joined: " + clientAddr);

                            sendTo("CONNECTED", clientAddr);
                        }
                    }

                    // ===== RELAY MOVE =====
                    else if (msg.startsWith("MOVE|")) {
                        for (SocketAddress p : players) {
                            if (!p.equals(clientAddr)) {
                                sendTo(msg, p);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log("❌ Lỗi Server: " + e.getMessage());
            }
        }).start();
    }

    private void sendTo(String msg, SocketAddress addr) {
        try {
            byte[] data = msg.getBytes("UTF-8");
            DatagramPacket packet = new DatagramPacket(data, data.length, addr);
            socket.send(packet);
        } catch (Exception ignored) {}
    }
}