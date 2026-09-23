package tcp.apps;

import javax.swing.*;
import java.awt.*;

public class EquationLauncher {
    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception e) {}

        SwingUtilities.invokeLater(() -> {
            Font mainFont = new Font("Arial", Font.BOLD, 16);
            JLabel titleLabel = new JLabel("HỆ THỐNG GIẢI TOÁN PHÂN TÁN (TCP)", SwingConstants.CENTER);
            titleLabel.setFont(mainFont);

            String[] options = {"Mở Máy Chủ Giải Toán (Server)", "Mở Máy Tính (Client)"};
            int choice = JOptionPane.showOptionDialog(null, 
                    titleLabel, 
                    "TCP Equation Solver",
                    JOptionPane.DEFAULT_OPTION, 
                    JOptionPane.INFORMATION_MESSAGE, 
                    null, options, options[0]);

            if (choice == 0) {
                // Mở Server
                new EquationServer().setVisible(true);
            } else if (choice == 1) {
                // Mở Client (Để trống IP bắt người dùng tự gõ cái IP lấy từ Server)
                new EquationClientUI("", "3002").setVisible(true);
            } else {
                System.exit(0);
            }
        });
    }
}