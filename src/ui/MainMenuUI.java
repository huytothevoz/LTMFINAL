package ui;

import javax.swing.*;
import java.awt.*;

public class MainMenuUI extends JFrame {

    public MainMenuUI() {
        setTitle("Network Programming App"); // tên cửa sổ
        setSize(600, 420); // kích thước màn hình
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); // thoát app khi đóng
        setLocationRelativeTo(null); // căn giữa màn hình

        // set giao diện chính
        setContentPane(createMainPanel());
    }

    private JPanel createMainPanel() {

        // panel chính có nền gradient
        JPanel panel = new JPanel() {
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);

                Graphics2D g2 = (Graphics2D) g;

                // tạo nền chuyển màu xanh từ trên xuống dưới
                GradientPaint gp = new GradientPaint(
                        0, 0, new Color(30, 144, 255),
                        0, getHeight(), new Color(10, 25, 80)
                );

                g2.setPaint(gp);
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
        };

        panel.setLayout(new BorderLayout());

        // ===== tiêu đề =====
        JLabel lblTitle = new JLabel("NETWORK PROGRAMMING", SwingConstants.CENTER);
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 26));
        lblTitle.setForeground(Color.WHITE);

        JLabel lblSub = new JLabel("TCP / UDP Applications", SwingConstants.CENTER);
        lblSub.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        lblSub.setForeground(new Color(220, 220, 220));

        // gom 2 dòng tiêu đề lại
        JPanel header = new JPanel(new GridLayout(2,1));
        header.setOpaque(false);
        header.add(lblTitle);
        header.add(lblSub);

        panel.add(header, BorderLayout.NORTH);

        // ===== phần nút chọn =====
        JPanel card = new JPanel();
        card.setBackground(Color.WHITE);
        card.setLayout(new GridLayout(2,1,20,20));
        card.setBorder(BorderFactory.createEmptyBorder(30,50,30,50));

        JButton btnTCP = createModernButton("TCP Application");
        btnTCP.setBackground(new Color(0,123,255)); // màu xanh TCP

        JButton btnUDP = createModernButton("UDP Application");
        btnUDP.setBackground(new Color(40,167,69)); // màu xanh UDP

        card.add(btnTCP);
        card.add(btnUDP);

        // căn giữa cái khung nút
        JPanel wrapper = new JPanel(new GridBagLayout());
        wrapper.setOpaque(false);
        wrapper.add(card);

        panel.add(wrapper, BorderLayout.CENTER);

        // footer 
        JPanel infoPanel = new JPanel(new GridLayout(2,1));
        infoPanel.setOpaque(false);

        JLabel lblName = new JLabel("Võ Trịnh Quốc Huy & Đào Tuấn Minh", SwingConstants.CENTER);
        lblName.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblName.setForeground(Color.WHITE);

        JLabel lblMSSV = new JLabel("MSSV: 52400273 - 52400289", SwingConstants.CENTER);
        lblMSSV.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblMSSV.setForeground(new Color(220,220,220));

        infoPanel.add(lblName);
        infoPanel.add(lblMSSV);

        panel.add(infoPanel, BorderLayout.SOUTH);

        // ---- xử lý bấm nút ----

        // bấm TCP -> mở menu TCP, đóng màn hình hiện tại
        btnTCP.addActionListener(e -> {
            new ProtocolMenuUI("TCP Protocol").setVisible(true);
            dispose();
        });

        // bấm UDP -> mở menu UDP, đóng màn hình hiện tại
        btnUDP.addActionListener(e -> {
            new ProtocolMenuUI("UDP Protocol").setVisible(true);
            dispose();
        });

        return panel;
    }

    // tạo nút nhìn cho đẹp (dùng lại nhiều lần)
    private JButton createModernButton(String text) {
        JButton btn = new JButton(text);

        btn.setFocusPainted(false); // bỏ viền khi click
        btn.setFont(new Font("Segoe UI", Font.BOLD, 16));
        btn.setForeground(Color.WHITE);

        btn.setOpaque(true);
        btn.setBorderPainted(false);

        btn.setCursor(new Cursor(Cursor.HAND_CURSOR)); // hover chuột thành tay

        btn.setBorder(BorderFactory.createEmptyBorder(12,20,12,20));

        return btn;
    }
}