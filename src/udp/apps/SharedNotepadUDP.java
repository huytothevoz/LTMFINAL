package udp.apps;

import javax.swing.*;
import java.awt.*;
import java.net.InetAddress;

public class SharedNotepadUDP {

    public static void main(String[] args) {
        // Cài đặt giao diện Look & Feel cho đẹp giống hệ điều hành
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        String[] options = {"Tạo phòng (Vừa Host vừa gõ)", "Tham gia phòng (Chỉ gõ)"};
        int choice = JOptionPane.showOptionDialog(null, 
                "Chọn chế độ Bảng ghi chú chung:", 
                "Shared Notepad UDP",
                JOptionPane.DEFAULT_OPTION, 
                JOptionPane.INFORMATION_MESSAGE, 
                null, 
                options, 
                options[0]);

        if (choice == 0) {
            // ================= CHẾ ĐỘ HOST =================
            
            // Tạo Panel cho Host tự nhập Port và Tên
            JPanel panel = new JPanel(new GridLayout(4, 1, 5, 5));
            panel.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));

            Font inputFont = new Font("Arial", Font.PLAIN, 18);
            Font labelFont = new Font("Arial", Font.BOLD, 14);

            JTextField portField = new JTextField("5000");
            portField.setFont(inputFont);

            JTextField userField = new JTextField("HostAdmin");
            userField.setFont(inputFont);

            JLabel lblPort = new JLabel("Mở phòng tại Cổng (Port):");
            lblPort.setFont(labelFont);
            panel.add(lblPort);
            panel.add(portField);

            JLabel lblUser = new JLabel("Tên hiển thị của bạn:");
            lblUser.setFont(labelFont);
            panel.add(lblUser);
            panel.add(userField);

            int result = JOptionPane.showConfirmDialog(null, panel, 
                    "Cấu hình Máy chủ (Host)", 
                    JOptionPane.OK_CANCEL_OPTION, 
                    JOptionPane.PLAIN_MESSAGE);

            if (result == JOptionPane.OK_OPTION) {
                String portStr = portField.getText().trim();
                String username = userField.getText().trim();

                if (!portStr.isEmpty() && !username.isEmpty()) {
                    try {
                        int port = Integer.parseInt(portStr);
                        
                        // 1. Kích hoạt Server chạy ngầm với Port đã nhập
                        SwingUtilities.invokeLater(() -> {
                            SharedNotepadServerUDP server = new SharedNotepadServerUDP(port);
                            server.setVisible(true);
                            server.setLocation(50, 50); 
                        });

                        // 2. Kích hoạt giao diện Client cho Host kết nối (dùng IP LAN)
                        SwingUtilities.invokeLater(() -> {
                            try {
                                String hostIP = InetAddress.getLocalHost().getHostAddress();
                                SharedNotepadClientUDP client = new SharedNotepadClientUDP(hostIP, port, username);
                                client.setVisible(true);
                            } catch (Exception e) {
                                // Fallback nếu lỗi không lấy được LAN IP thì dùng localhost
                                SharedNotepadClientUDP client = new SharedNotepadClientUDP("127.0.0.1", port, username);
                                client.setVisible(true);
                            }
                        });
                        
                    } catch (NumberFormatException e) {
                        JOptionPane.showMessageDialog(null, "Port phải là một số hợp lệ!", "Lỗi nhập liệu", JOptionPane.ERROR_MESSAGE);
                    }
                } else {
                    JOptionPane.showMessageDialog(null, "Vui lòng nhập đầy đủ thông tin!", "Lỗi", JOptionPane.WARNING_MESSAGE);
                }
            }

        } else if (choice == 1) {
            // ================= CHẾ ĐỘ CLIENT =================
            
            JPanel panel = new JPanel(new GridLayout(6, 1, 5, 5));
            panel.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));

            Font inputFont = new Font("Arial", Font.PLAIN, 18);
            Font labelFont = new Font("Arial", Font.BOLD, 14);

            // SỬA Ở ĐÂY: Xóa "127.0.0.1" mặc định đi
            JTextField ipField = new JTextField("");
            ipField.setFont(inputFont);

            JTextField portField = new JTextField("5000");
            portField.setFont(inputFont);

            JTextField userField = new JTextField("User" + (int)(Math.random() * 1000));
            userField.setFont(inputFont);

            JLabel lblIp = new JLabel("Địa chỉ IP Server:");
            lblIp.setFont(labelFont);
            panel.add(lblIp);
            panel.add(ipField);

            JLabel lblPort = new JLabel("Cổng (Port):");
            lblPort.setFont(labelFont);
            panel.add(lblPort);
            panel.add(portField);

            JLabel lblUser = new JLabel("Tên người dùng:");
            lblUser.setFont(labelFont);
            panel.add(lblUser);
            panel.add(userField);

            int result = JOptionPane.showConfirmDialog(null, panel, 
                    "Nhập thông tin kết nối", 
                    JOptionPane.OK_CANCEL_OPTION, 
                    JOptionPane.PLAIN_MESSAGE);

            if (result == JOptionPane.OK_OPTION) {
                String ip = ipField.getText().trim();
                String portStr = portField.getText().trim();
                String username = userField.getText().trim();

                if (!ip.isEmpty() && !portStr.isEmpty() && !username.isEmpty()) {
                    try {
                        int port = Integer.parseInt(portStr);
                        
                        SwingUtilities.invokeLater(() -> {
                            SharedNotepadClientUDP client = new SharedNotepadClientUDP(ip, port, username);
                            client.setVisible(true);
                        });
                    } catch (NumberFormatException e) {
                        JOptionPane.showMessageDialog(null, "Port phải là một số hợp lệ!", "Lỗi nhập liệu", JOptionPane.ERROR_MESSAGE);
                    }
                } else {
                    JOptionPane.showMessageDialog(null, "Vui lòng nhập đầy đủ thông tin!", "Lỗi", JOptionPane.WARNING_MESSAGE);
                }
            }
        }
    }
}