package tcp.apps;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;

public class CalcClientUI extends JFrame {

    private JTextField txtIp, txtPort;
    private JTextField txtExpression;
    private JTextField txtResult;
    private JButton btnCalculate;
    
    // Lưu ý: Lúc gọi file này từ Launcher, bro nhớ truyền defaultIp là "" (chuỗi rỗng) nhé!
    public CalcClientUI(String defaultIp, String defaultPort) {
        setTitle("MÁY TÍNH TỪ XA (TCP CLIENT)");
        setSize(450, 320);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        
        setupUI(defaultIp, defaultPort);
    }
    
    private void setupUI(String defaultIp, String defaultPort) {
        setLayout(new BorderLayout(10, 10));

        JPanel main = new JPanel(new BorderLayout(10, 10));
        main.setBorder(new EmptyBorder(15, 15, 15, 15));
        main.setBackground(new Color(240, 242, 245));
        setContentPane(main);

        // --- Cấu hình Mạng ---
        JPanel top = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        top.setBackground(main.getBackground());
        
        txtIp = new JTextField(defaultIp, 12);
        txtIp.setToolTipText("Nhập IP hiển thị bên phần mềm Server (Giống nhập IP Minecraft vậy đó)");
        txtPort = new JTextField(defaultPort, 5);
        
        top.add(new JLabel("Server IP:")); top.add(txtIp);
        top.add(new JLabel("Port:")); top.add(txtPort);
        main.add(top, BorderLayout.NORTH);

        // --- Khu vực nhập biểu thức ---
        JPanel center = new JPanel(new GridLayout(5, 1, 5, 5));
        center.setBackground(main.getBackground());

        JLabel lblGuide = new JLabel("Nhập biểu thức toán học:");
        lblGuide.setFont(new Font("Segoe UI", Font.BOLD, 14));
        
        JLabel lblHint = new JLabel("Hỗ trợ: + - * / ^ sqrt sin cos");
        lblHint.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        lblHint.setForeground(Color.GRAY);
        
        txtExpression = new JTextField();
        txtExpression.setFont(new Font("Consolas", Font.BOLD, 18));
        txtExpression.setHorizontalAlignment(JTextField.CENTER);

        JLabel lblRes = new JLabel("Kết quả:");
        lblRes.setFont(new Font("Segoe UI", Font.BOLD, 14));

        txtResult = new JTextField();
        txtResult.setFont(new Font("Consolas", Font.BOLD, 18));
        txtResult.setHorizontalAlignment(JTextField.CENTER);
        txtResult.setForeground(new Color(220, 53, 69));
        txtResult.setEditable(false);
        txtResult.setBackground(Color.WHITE);

        center.add(lblGuide);
        center.add(lblHint);
        center.add(txtExpression);
        center.add(lblRes);
        center.add(txtResult);
        
        main.add(center, BorderLayout.CENTER);

        // --- Nút Tính Toán ---
        btnCalculate = new JButton("GỬI TÍNH TOÁN");
        btnCalculate.setBackground(new Color(0, 123, 255));
        btnCalculate.setForeground(Color.WHITE);
        btnCalculate.setFont(new Font("Segoe UI", Font.BOLD, 16));
        btnCalculate.setFocusPainted(false);
        btnCalculate.setOpaque(true);
        btnCalculate.setBorderPainted(false);
        btnCalculate.setCursor(new Cursor(Cursor.HAND_CURSOR));

        main.add(btnCalculate, BorderLayout.SOUTH);

        // Sự kiện
        btnCalculate.addActionListener(e -> sendRequest());
        txtExpression.addActionListener(e -> sendRequest()); 
    }

    private void sendRequest() {
        String ip = txtIp.getText().trim();
        String portStr = txtPort.getText().trim();
        String expression = txtExpression.getText().trim();

        // Check cực mạnh nếu lười không nhập IP
        if (ip.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Bro chưa nhập IP kìa! Nhìn sang máy Server copy cái IP dán vào đây (Giống vô server Minecraft ấy).", "Thiếu IP Server", JOptionPane.WARNING_MESSAGE);
            txtIp.requestFocus();
            return;
        }

        if (portStr.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Cổng (Port) đang bị trống!", "Lỗi", JOptionPane.WARNING_MESSAGE);
            txtPort.requestFocus();
            return;
        }

        if (expression.isEmpty()) {
            txtResult.setText("Chưa nhập biểu thức!");
            return;
        }

        try {
            int port = Integer.parseInt(portStr);

            new Thread(() -> {
                try (Socket socket = new Socket(ip, port);
                     DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                     DataInputStream dis = new DataInputStream(socket.getInputStream())) {

                    SwingUtilities.invokeLater(() -> {
                        txtResult.setForeground(Color.BLUE);
                        txtResult.setText("Đang tính...");
                    });

                    dos.writeUTF("CALC|" + expression);
                    String response = dis.readUTF();

                    SwingUtilities.invokeLater(() -> {
                        txtResult.setForeground(new Color(220, 53, 69));
                        txtResult.setText(response);
                    });

                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        txtResult.setForeground(Color.RED);
                        txtResult.setText("Lỗi: Không thể kết nối tới " + ip);
                        JOptionPane.showMessageDialog(this, "Không thể kết nối!\n1. Kiểm tra lại IP xem gõ đúng chưa.\n2. Chắc chắn máy chủ (Server) đang chạy và đã tắt Tường Lửa (Firewall).", "Lỗi kết nối", JOptionPane.ERROR_MESSAGE);
                    });
                }
            }).start();

        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Port phải là chữ số hợp lệ!", "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }
}