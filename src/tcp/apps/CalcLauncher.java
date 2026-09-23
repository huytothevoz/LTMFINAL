package tcp.apps;

import javax.swing.*;
import java.awt.*;

public class CalcLauncher {
    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception e) {}

        SwingUtilities.invokeLater(() -> {
            Font mainFont = new Font("Arial", Font.BOLD, 16);
            JLabel titleLabel = new JLabel("HỆ THỐNG MÁY TÍNH PHÂN TÁN (TCP)", SwingConstants.CENTER);
            titleLabel.setFont(mainFont);

            String[] options = {"Mở Máy Chủ Tính Toán (Server)", "Mở Máy Tính (Client)"};
            int choice = JOptionPane.showOptionDialog(null, 
                    titleLabel, 
                    "TCP Calculator",
                    JOptionPane.DEFAULT_OPTION, 
                    JOptionPane.INFORMATION_MESSAGE, 
                    null, options, options[0]);

            if (choice == 0) {
                // Mở Server Độc Lập
                new CalcServer().setVisible(true);
            } else if (choice == 1) {
                // Mở Client, để người dùng nhập IP
                new CalcClientUI("", "3000").setVisible(true);
            } else {
                System.exit(0);
            }
        });
    }
}