package tcp.apps;

import javax.swing.*;

public class TcpQuizLauncher {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            String[] options = {"Mở Server (Máy Chủ Lưu Trữ)", "Mở Client (Người Chơi / Người Tạo Đề)"};
            int choice = JOptionPane.showOptionDialog(null,
                    "Bạn muốn khởi động thành phần nào?",
                    "HỆ THỐNG TRẮC NGHIỆM TRỰC TUYẾN",
                    JOptionPane.DEFAULT_OPTION,
                    JOptionPane.QUESTION_MESSAGE,
                    null, options, options[0]);

            if (choice == 0) {
                new TcpQuizServer().setVisible(true);
            } else if (choice == 1) {
                new TcpQuizClientUI().setVisible(true);
            }
        });
    }
}