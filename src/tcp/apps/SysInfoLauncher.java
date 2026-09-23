package tcp.apps;

import javax.swing.*;

public class SysInfoLauncher {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            String[] options = {"Mở Server (Máy bị quét thông tin)", "Mở Client (Terminal đi quét)"};
            int choice = JOptionPane.showOptionDialog(null,
                    "Bạn muốn mở thành phần nào của SYS_INFO?",
                    "HỆ THỐNG TRUY XUẤT THÔNG TIN",
                    JOptionPane.DEFAULT_OPTION,
                    JOptionPane.QUESTION_MESSAGE,
                    null, options, options[0]);

            if (choice == 0) {
                new SysInfoServer().setVisible(true);
            } else if (choice == 1) {
                new SysInfoClientUI().setVisible(true);
            }
        });
    }
}