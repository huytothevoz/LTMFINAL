package udp.apps;

import javax.swing.*;
import java.awt.*;

public class ChessUDP {
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        // 1. Chỉnh Font chữ to cho menu đầu tiên
        Font mainFont = new Font("Arial", Font.PLAIN, 18);
        Font boldFont = new Font("Arial", Font.BOLD, 18);

        JLabel titleLabel = new JLabel("Chọn vai trò của bạn trong Game Cờ Vua:");
        titleLabel.setFont(boldFont);

        String[] options = {"Tạo phòng (Cầm Trắng)", "Tham gia (Cầm Đen)"};
        int choice = JOptionPane.showOptionDialog(null, 
                titleLabel, 
                "Chess UDP Multiplayer",
                JOptionPane.DEFAULT_OPTION, 
                JOptionPane.INFORMATION_MESSAGE, 
                null, options, options[0]);

        if (choice == 0) {
            // GIAO DIỆN TẠO PHÒNG (HOST)
            JPanel hostPanel = new JPanel(new BorderLayout(0, 10)); // Khoảng cách dòng 10px
            hostPanel.setPreferredSize(new Dimension(350, 80)); // Ép khung to ra: Rộng 350, Cao 80

            JLabel lblPort = new JLabel("Nhập Cổng (Port) để mở phòng:");
            lblPort.setFont(mainFont);
            
            JTextField portField = new JTextField("5002");
            portField.setFont(mainFont);
            portField.setHorizontalAlignment(JTextField.CENTER); // Căn giữa chữ cho đẹp
            
            hostPanel.add(lblPort, BorderLayout.NORTH);
            hostPanel.add(portField, BorderLayout.CENTER);

            int result = JOptionPane.showConfirmDialog(null, hostPanel, "Tạo phòng Server", 
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            
            if (result == JOptionPane.OK_OPTION) {
                int port = Integer.parseInt(portField.getText().trim());
                SwingUtilities.invokeLater(() -> {
                    ChessServerUDP server = new ChessServerUDP(port);
                    server.setVisible(true);
                    server.setLocation(20, 20); // Góc màn hình
                });
                // Host tự kết nối vào server của chính mình 
                try {
                    String hostIP = java.net.InetAddress.getLocalHost().getHostAddress();

                    // Nếu lỡ dính localhost thì fallback về IP LAN chuẩn
                    if (hostIP.startsWith("127.")) {
                        java.util.Enumeration<java.net.NetworkInterface> nets = java.net.NetworkInterface.getNetworkInterfaces();
                        while (nets.hasMoreElements()) {
                            java.net.NetworkInterface netint = nets.nextElement();
                            java.util.Enumeration<java.net.InetAddress> addrs = netint.getInetAddresses();
                            while (addrs.hasMoreElements()) {
                                java.net.InetAddress addr = addrs.nextElement();
                                if (!addr.isLoopbackAddress() && addr instanceof java.net.Inet4Address) {
                                    hostIP = addr.getHostAddress();
                                    break;
                                }
                            }
                        }
                    }

                    String finalIP = hostIP;
                    SwingUtilities.invokeLater(() -> new ChessClientUDP(finalIP, port, true).setVisible(true));

                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> new ChessClientUDP("127.0.0.1", port, true).setVisible(true));
                }
            }

        } else if (choice == 1) {
            // CLIENT
            // GridLayout 2 hàng 2 cột, khoảng cách ngang 10px, dọc 15px
            JPanel clientPanel = new JPanel(new GridLayout(2, 2, 10, 15)); 
            clientPanel.setPreferredSize(new Dimension(400, 100)); // Ép khung to ra: Rộng 400, Cao 100
            
            JLabel lblIP = new JLabel("IP Máy chủ:");
            lblIP.setFont(mainFont);
            // SỬA Ở ĐÂY: Để trống để người chơi phải nhập IP LAN của đối thủ
            JTextField ipField = new JTextField("");
            ipField.setFont(mainFont);
            ipField.setHorizontalAlignment(JTextField.CENTER);
            
            JLabel lblPort = new JLabel("Cổng (Port):");
            lblPort.setFont(mainFont);
            JTextField portField = new JTextField("5002");
            portField.setFont(mainFont);
            portField.setHorizontalAlignment(JTextField.CENTER);
            
            clientPanel.add(lblIP); 
            clientPanel.add(ipField);
            clientPanel.add(lblPort); 
            clientPanel.add(portField);

            int result = JOptionPane.showConfirmDialog(null, clientPanel, "Kết nối đến đối thủ", 
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            
            if (result == JOptionPane.OK_OPTION) {
                int port = Integer.parseInt(portField.getText().trim());
                String ip = ipField.getText().trim();
                if(ip.isEmpty()) {
                    JOptionPane.showMessageDialog(null, "Bạn chưa nhập IP Máy chủ!");
                    return;
                }
                SwingUtilities.invokeLater(() -> new ChessClientUDP(ip, port, false).setVisible(true));
            }
        } else {
            // Người dùng bấm dấu X tắt menu
            System.exit(0);
        }
    }
}