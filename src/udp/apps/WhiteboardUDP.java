package udp.apps;

import javax.swing.*;
import java.awt.*;
import java.net.*;
import java.util.*;

public class WhiteboardUDP {
    
    // ====== LẤY IP LAN CHUẨN (KHÔNG LẤY 127) ======
    private static String getLANIP() {
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

        return "127.0.0.1"; // fallback
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        String[] options = {"Tạo phòng Vẽ (Host)", "Tham gia phòng (Client)"};
        int choice = JOptionPane.showOptionDialog(null, 
                "Chọn chế độ Bảng Vẽ Chung:", 
                "Shared Whiteboard UDP",
                JOptionPane.DEFAULT_OPTION, 
                JOptionPane.INFORMATION_MESSAGE, 
                null, options, options[0]);

        if (choice == 0) {
            // ================= HOST =================
            JPanel panel = new JPanel(new GridLayout(4, 1, 8, 8));
            panel.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
            Font font = new Font("Arial", Font.PLAIN, 16);

            JTextField portField = new JTextField("5001"); 
            portField.setFont(font);

            JTextField userField = new JTextField("Host"); 
            userField.setFont(font);

            panel.add(new JLabel("Mở phòng tại Cổng (Port):")); 
            panel.add(portField);
            panel.add(new JLabel("Tên của bạn:")); 
            panel.add(userField);

            if (JOptionPane.showConfirmDialog(null, panel, "Cấu hình Máy chủ", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
                try {
                    int port = Integer.parseInt(portField.getText().trim());
                    String username = userField.getText().trim();

                    if (port < 1024 || port > 65535) {
                        JOptionPane.showMessageDialog(null, "Port phải từ 1024 - 65535");
                        return;
                    }

                    String hostIP = getLANIP();

                    JOptionPane.showMessageDialog(null, 
                        "📡 Server đang chạy\nIP: " + hostIP + "\nPort: " + port);

                    // ===== START SERVER =====
                    SwingUtilities.invokeLater(() -> {
                        WhiteboardServerUDP server = new WhiteboardServerUDP(port);
                        server.setVisible(true);
                        server.setLocation(50, 50);
                    });

                    // ===== HOST JOIN LUÔN =====
                    SwingUtilities.invokeLater(() -> {
                        WhiteboardClientUDP client = new WhiteboardClientUDP(hostIP, port, username);
                        client.setVisible(true);
                    });

                } catch (NumberFormatException e) {
                    JOptionPane.showMessageDialog(null, "Port không hợp lệ!");
                }
            }

        } else if (choice == 1) {
            // ================= CLIENT =================
            JPanel panel = new JPanel(new GridLayout(6, 1, 8, 8));
            panel.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
            Font font = new Font("Arial", Font.PLAIN, 16);

            JTextField ipField = new JTextField(""); 
            ipField.setFont(font);

            JTextField portField = new JTextField("5001"); 
            portField.setFont(font);

            JTextField userField = new JTextField("User" + (int)(Math.random()*1000)); 
            userField.setFont(font);

            panel.add(new JLabel("IP Server:")); 
            panel.add(ipField);
            panel.add(new JLabel("Cổng (Port):")); 
            panel.add(portField);
            panel.add(new JLabel("Tên hiển thị:")); 
            panel.add(userField);

            if (JOptionPane.showConfirmDialog(null, panel, "Tham gia phòng", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
                try {
                    int port = Integer.parseInt(portField.getText().trim());
                    String username = userField.getText().trim();
                    String ip = ipField.getText().trim();

                    // ===== VALIDATE =====
                    if (ip.isEmpty()) {
                        JOptionPane.showMessageDialog(null, "IP không được để trống!");
                        return;
                    }

                    if (!ip.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
                        JOptionPane.showMessageDialog(null, "IP không hợp lệ!");
                        return;
                    }

                    if (port < 1024 || port > 65535) {
                        JOptionPane.showMessageDialog(null, "Port không hợp lệ!");
                        return;
                    }

                    SwingUtilities.invokeLater(() -> {
                        WhiteboardClientUDP client = new WhiteboardClientUDP(ip, port, username);
                        client.setVisible(true);
                    });

                } catch (Exception e) {
                    JOptionPane.showMessageDialog(null, "Thông tin không hợp lệ!");
                }
            }
        }
    }
}