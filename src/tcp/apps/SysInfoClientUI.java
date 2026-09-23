package tcp.apps;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;

public class SysInfoClientUI extends JFrame {

    private JTextField txtIp, txtPort;
    private JTextArea txtResult;
    private JButton btnGetInfo;

    public SysInfoClientUI() {
        setTitle("THÔNG TIN HỆ THỐNG (TCP CLIENT)");
        setSize(460, 380);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        
        setupUI();
    }

    private void setupUI() {
        setLayout(new BorderLayout(10, 10));

        JPanel main = new JPanel(new BorderLayout(10, 10));
        main.setBorder(new EmptyBorder(15, 15, 15, 15));
        main.setBackground(new Color(240, 242, 245));
        setContentPane(main);

        // --- Khu nhập IP và Port ---
        JPanel top = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        top.setBackground(main.getBackground());
        
        // Để trống IP mặc định để người dùng tự nhập IP LAN của Host
        txtIp = new JTextField("", 12);
        txtPort = new JTextField("3003", 5);
        
        top.add(new JLabel("Server IP:"));
        top.add(txtIp);
        top.add(new JLabel("Port:"));
        top.add(txtPort);

        main.add(top, BorderLayout.NORTH);

        // --- Khu hiển thị kết quả giống terminal (Hacker mode) ---
        JPanel center = new JPanel(new BorderLayout());
        center.setBackground(main.getBackground());
        
        JLabel lblGuide = new JLabel("Thông số từ Máy chủ:");
        lblGuide.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblGuide.setBorder(new EmptyBorder(0, 0, 5, 0));
        
        txtResult = new JTextArea("Sẵn sàng kết nối...\nBấm 'TRUY XUẤT' để lấy thông tin.");
        txtResult.setFont(new Font("Consolas", Font.BOLD, 14));
        txtResult.setBackground(new Color(20, 20, 20)); // nền đen
        txtResult.setForeground(new Color(76, 255, 0)); // chữ xanh lá hacker
        txtResult.setEditable(false);
        txtResult.setMargin(new Insets(15, 15, 15, 15));
        
        JScrollPane scrollPane = new JScrollPane(txtResult);
        scrollPane.setBorder(BorderFactory.createLineBorder(Color.GRAY, 2));

        center.add(lblGuide, BorderLayout.NORTH);
        center.add(scrollPane, BorderLayout.CENTER);
        
        main.add(center, BorderLayout.CENTER);

        // --- Nút truy xuất thông tin (Đồng bộ nút Xanh Navy, bo tròn 20) ---
        btnGetInfo = new JButton("TRUY XUẤT THÔNG TIN MÁY CHỦ") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getBackground());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                super.paintComponent(g);
                g2.dispose();
            }
        };

        btnGetInfo.setBackground(new Color(0, 0, 128)); // Màu xanh Navy
        btnGetInfo.setForeground(Color.WHITE);
        btnGetInfo.setFont(new Font("Segoe UI", Font.BOLD, 16));
        
        // Chống lẹm chữ và lỗi viền vuông
        btnGetInfo.setFocusPainted(false);
        btnGetInfo.setContentAreaFilled(false);
        btnGetInfo.setBorder(BorderFactory.createEmptyBorder(12, 20, 12, 20)); 
        btnGetInfo.setCursor(new Cursor(Cursor.HAND_CURSOR));

        main.add(btnGetInfo, BorderLayout.SOUTH);

        // Sự kiện nút bấm
        btnGetInfo.addActionListener(e -> sendRequest());
    }

    // Gửi yêu cầu SYS lên server và nhận lại thông tin
    private void sendRequest() {
        String ip = txtIp.getText().trim();
        String portStr = txtPort.getText().trim();

        if (ip.isEmpty() || portStr.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập IP và Port do Server cung cấp!", "Lỗi", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            int port = Integer.parseInt(portStr);

            try (Socket socket = new Socket(ip, port);
                 DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                 DataInputStream dis = new DataInputStream(socket.getInputStream())) {

                txtResult.setForeground(new Color(76, 255, 0)); // Trả lại màu xanh
                txtResult.setText("Đang gửi yêu cầu quét hệ thống (SYS|)...\n");

                // Gửi lệnh SYS cho server
                dos.writeUTF("SYS|");

                // Nhận dữ liệu trả về
                String response = dis.readUTF();
                
                String[] infoLines = response.split("\\|");
                StringBuilder formattedRes = new StringBuilder();
                formattedRes.append("=== TRẠNG THÁI MÁY CHỦ (HOST) ===\n\n");
                
                for (String line : infoLines) {
                    formattedRes.append(" > ").append(line.trim()).append("\n");
                }
                
                txtResult.setText(formattedRes.toString());

            } catch (Exception ex) {
                txtResult.setForeground(Color.RED);
                txtResult.setText("Không thể kết nối đến server!\n\nNguyên nhân có thể:\n1. Sai địa chỉ IP LAN\n2. Sai Port (Mặc định 3003)\n3. Máy chủ (Host) chưa mở Server.");
            }

        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Port phải là số!", "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }
}