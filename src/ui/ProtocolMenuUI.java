package ui;

// import giao diện TCP
import tcp.chat.TcpChatClientUI;
import tcp.chat.TcpChatServerUI;
import tcp.file.TcpFileClientUI; 
import tcp.file.TcpFileServerUI; 
import tcp.apps.*; 

// import giao diện UDP
import udp.chat.UdpChatClientUI;
import udp.chat.UdpChatServerUI;
import udp.file.UdpFileClientUI;
import udp.file.UdpFileServerUI;
import udp.apps.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

public class ProtocolMenuUI extends JFrame {

    private String protocolName;

    public ProtocolMenuUI(String protocolName) {
        this.protocolName = protocolName;

        setTitle(protocolName + " Dashboard");
        setSize(700, 450);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        setContentPane(createUI());
    }

    private JPanel createUI() {

        // panel chính có nền gradient
        JPanel panel = new JPanel() {
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);

                Graphics2D g2 = (Graphics2D) g;

                GradientPaint gp = new GradientPaint(
                        0, 0, new Color(20, 20, 60),
                        0, getHeight(), new Color(0, 120, 215)
                );

                g2.setPaint(gp);
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
        };

        panel.setLayout(new BorderLayout());

        // tiêu đề phía trên
        JLabel lblTitle = new JLabel(protocolName + " Network Suite", SwingConstants.CENTER);
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 24));
        lblTitle.setForeground(Color.WHITE);
        panel.add(lblTitle, BorderLayout.NORTH);

        // khu vực chức năng chính
        JPanel grid = new JPanel(new GridLayout(2, 2, 20, 20));
        grid.setBorder(BorderFactory.createEmptyBorder(30, 50, 30, 50));
        grid.setOpaque(false);

        JButton btnChat = createCardButton("Chat Application");
        JButton btnFile = createCardButton("File Transfer");
        JButton btnApps = createCardButton("Mini Apps");
        JLabel empty = new JLabel("");

        grid.add(btnChat);
        grid.add(btnFile);
        grid.add(btnApps);
        grid.add(empty);

        panel.add(grid, BorderLayout.CENTER);

        // nút quay lại
        JButton btnBack = new JButton("← Back");
        btnBack.setFocusPainted(false);
        btnBack.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btnBack.setCursor(new Cursor(Cursor.HAND_CURSOR));

        JPanel bottom = new JPanel();
        bottom.setOpaque(false);
        bottom.add(btnBack);

        panel.add(bottom, BorderLayout.SOUTH);

        // sự kiện nút
        btnChat.addActionListener(e -> showSubMenu(btnChat, "Chat"));
        btnFile.addActionListener(e -> showSubMenu(btnFile, "File"));
        btnApps.addActionListener(e -> showAppsSubMenu(btnApps));

        btnBack.addActionListener(e -> {
            new MainMenuUI().setVisible(true);
            dispose();
        });

        return panel;
    }

    private void showSubMenu(JButton parentButton, String appType) {

        JPopupMenu popup = new JPopupMenu();

        JMenuItem itemServer = new JMenuItem("Tạo Server (Admin)");
        JMenuItem itemClient = new JMenuItem("Kết nối Client (User)");

        Font menuFont = new Font("Segoe UI", Font.BOLD, 14);
        itemServer.setFont(menuFont);
        itemClient.setFont(menuFont);

        itemServer.addActionListener(e -> launchApp(appType, "Server"));
        itemClient.addActionListener(e -> launchApp(appType, "Client"));

        popup.add(itemServer);
        popup.add(itemClient);

        popup.show(parentButton, 0, parentButton.getHeight());
    }

    private void showAppsSubMenu(JButton parentButton) {

        JPopupMenu popup = new JPopupMenu();
        Font menuFont = new Font("Segoe UI", Font.BOLD, 14);

        if (protocolName.contains("TCP")) {

            JMenuItem itemCalc = new JMenuItem("Máy Tính Bỏ Túi");
            JMenuItem itemEq = new JMenuItem("Giải Phương Trình");
            JMenuItem itemDict = new JMenuItem("Từ Điển");
            JMenuItem itemSys = new JMenuItem("System Info");
            JMenuItem itemQuiz = new JMenuItem("Quiz");

            JMenuItem[] items = {itemCalc, itemEq, itemDict, itemSys, itemQuiz};

            for (JMenuItem item : items) {
                item.setFont(menuFont);
                popup.add(item);
            }

            itemCalc.addActionListener(e -> CalcLauncher.main(null));
            itemEq.addActionListener(e -> EquationLauncher.main(null));
            itemDict.addActionListener(e -> DictLauncher.main(null));
            itemSys.addActionListener(e -> SysInfoLauncher.main(null));
            itemQuiz.addActionListener(e -> TcpQuizLauncher.main(null));
        }

        else if (protocolName.contains("UDP")) {

            JMenuItem itemChess = new JMenuItem("Cờ Vua P2P");
            JMenuItem itemBoard = new JMenuItem("Whiteboard");
            JMenuItem itemNote = new JMenuItem("Notepad");

            JMenuItem[] items = {itemChess, itemBoard, itemNote};

            for (JMenuItem item : items) {
                item.setFont(menuFont);
                popup.add(item);
            }

            itemChess.addActionListener(e -> ChessUDP.main(null));
            itemBoard.addActionListener(e -> WhiteboardUDP.main(null));
            itemNote.addActionListener(e -> SharedNotepadUDP.main(null));
        }

        popup.show(parentButton, 0, parentButton.getHeight());
    }

    private void launchApp(String appType, String role) {

        if (protocolName.contains("TCP")) {

            if (appType.equals("Chat")) {
                if (role.equals("Server")) new TcpChatServerUI().setVisible(true);
                else new TcpChatClientUI().setVisible(true);
            }

            else if (appType.equals("File")) {
                if (role.equals("Server")) new TcpFileServerUI().setVisible(true);
                else new TcpFileClientUI().setVisible(true);
            }
        }

        else if (protocolName.contains("UDP")) {

            if (appType.equals("Chat")) {
                if (role.equals("Server")) new UdpChatServerUI().setVisible(true);
                else new UdpChatClientUI().setVisible(true);
            }

            else if (appType.equals("File")) {
                if (role.equals("Server")) new UdpFileServerUI().setVisible(true);
                else new UdpFileClientUI().setVisible(true);
            }
        }
    }

    private JButton createCardButton(String text) {

        JButton btn = new JButton(text);

        btn.setFont(new Font("Segoe UI", Font.BOLD, 18));
        btn.setFocusPainted(false);
        btn.setBackground(Color.WHITE);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));

        // hover đổi màu nhẹ
        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                btn.setBackground(new Color(220, 220, 220));
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                btn.setBackground(Color.WHITE);
            }
        });

        return btn;
    }
}