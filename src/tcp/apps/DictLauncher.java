package tcp.apps;


import javax.swing.*;

public class DictLauncher {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            String[] options = {"Tạo Host (Server)", "Tham gia (Client)"};
            int choice = JOptionPane.showOptionDialog(null,
                    "Chọn chế độ chạy Từ Điển:",
                    "Khởi động Từ Điển TCP",
                    JOptionPane.DEFAULT_OPTION,
                    JOptionPane.QUESTION_MESSAGE,
                    null, options, options[0]);

            if (choice == 0) {
                // Chạy Server
                String portStr = JOptionPane.showInputDialog(null, "Nhập Port để mở Server:", "3001");
                if (portStr != null && !portStr.trim().isEmpty()) {
                    try {
                        int port = Integer.parseInt(portStr.trim());
                        new DictServer(port).setVisible(true); // Gọi bộ não Server
                        JOptionPane.showMessageDialog(null, "Đã mở Server Từ Điển tại port " + port + "\n(Bao gồm kho từ vựng IT và cơ bản)");
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(null, "Port không hợp lệ!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                    }
                }
            } else if (choice == 1) {
                // Chạy Client
                new DictClientUI("", "3001").setVisible(true);
            }
        });
    }
}