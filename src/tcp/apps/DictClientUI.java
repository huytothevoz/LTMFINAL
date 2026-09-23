package tcp.apps;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;

public class DictClientUI extends JFrame {

    private JTextField txtIp, txtPort;
    private JTextField txtWord;
    private JTextArea txtResult;
    private JButton btnTranslate;

    // Đã thêm tham số IP và Port
    public DictClientUI(String defaultIp, String defaultPort) {
        setTitle("TỪ ĐIỂN ANH - VIỆT (TCP CLIENT)");
        setSize(400, 320);
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

        // nhập IP + Port
        JPanel top = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        top.setBackground(main.getBackground());
        
        txtIp = new JTextField(defaultIp, 10);
        txtPort = new JTextField(defaultPort, 5);
        
        top.add(new JLabel("Server IP:"));
        top.add(txtIp);
        top.add(new JLabel("Port:"));
        top.add(txtPort);

        main.add(top, BorderLayout.NORTH);

        // khu nhập từ và hiển thị nghĩa
        JPanel center = new JPanel(new BorderLayout(5, 5));
        center.setBackground(main.getBackground());

        JPanel inputPanel = new JPanel(new GridLayout(3, 1, 2, 2));
        inputPanel.setBackground(main.getBackground());
        
        JLabel lblGuide = new JLabel("Nhập từ tiếng Anh:");
        lblGuide.setFont(new Font("Segoe UI", Font.BOLD, 14));
        
        txtWord = new JTextField();
        txtWord.setFont(new Font("Consolas", Font.BOLD, 18));
        txtWord.setHorizontalAlignment(JTextField.CENTER);
        
        JLabel lblHint = new JLabel("Ví dụ: hello, network, server, firewall...");
        lblHint.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        lblHint.setForeground(Color.GRAY);
        
        inputPanel.add(lblGuide);
        inputPanel.add(txtWord);
        inputPanel.add(lblHint);
        
        center.add(inputPanel, BorderLayout.NORTH);

        JPanel resPanel = new JPanel(new BorderLayout(2, 2));
        resPanel.setBackground(main.getBackground());
        
        JLabel lblRes = new JLabel("Nghĩa:");
        lblRes.setFont(new Font("Segoe UI", Font.BOLD, 14));
        
        txtResult = new JTextArea();
        txtResult.setFont(new Font("Segoe UI", Font.BOLD, 16));
        txtResult.setForeground(new Color(0, 102, 204));
        txtResult.setEditable(false);
        txtResult.setLineWrap(true);
        txtResult.setWrapStyleWord(true);
        txtResult.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.LIGHT_GRAY),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));

        resPanel.add(lblRes, BorderLayout.NORTH);
        resPanel.add(new JScrollPane(txtResult), BorderLayout.CENTER);
        
        center.add(resPanel, BorderLayout.CENTER);
        main.add(center, BorderLayout.CENTER);

        // --- NÚT TRA CỨU CUSTOM BO TRÒN, XANH NAVY ---
        btnTranslate = new JButton("TRA CỨU") {
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

        btnTranslate.setBackground(new Color(0, 0, 128)); // Màu xanh Navy
        btnTranslate.setForeground(Color.WHITE); // Chữ trắng
        btnTranslate.setFont(new Font("Segoe UI", Font.BOLD, 16));
        
        // Chống lẹm chữ và lỗi viền vuông
        btnTranslate.setFocusPainted(false);
        btnTranslate.setContentAreaFilled(false);
        btnTranslate.setBorder(BorderFactory.createEmptyBorder(12, 20, 12, 20)); // Padding đệm
        btnTranslate.setCursor(new Cursor(Cursor.HAND_CURSOR));

        main.add(btnTranslate, BorderLayout.SOUTH);

        btnTranslate.addActionListener(e -> sendRequest());
        txtWord.addActionListener(e -> sendRequest()); 
    }

    private void sendRequest() {
        String ip = txtIp.getText().trim();
        String portStr = txtPort.getText().trim();
        String word = txtWord.getText().trim();

        if (ip.isEmpty() || portStr.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Nhập IP và Port!", "Lỗi", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (word.isEmpty()) {
            txtResult.setText("Chưa nhập từ!");
            txtResult.setForeground(Color.RED);
            return;
        }

        try {
            int port = Integer.parseInt(portStr);

            try (Socket socket = new Socket(ip, port);
                 DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                 DataInputStream dis = new DataInputStream(socket.getInputStream())) {

                txtResult.setForeground(Color.GRAY);
                txtResult.setText("Đang tra cứu...");

                dos.writeUTF("DICT|" + word);

                String response = dis.readUTF();
                txtResult.setText(response);
                
                if (response.contains("Không tìm thấy")) {
                    txtResult.setForeground(Color.RED);
                } else {
                    txtResult.setForeground(new Color(0, 102, 204));
                }

            } catch (Exception ex) {
                txtResult.setText("Không kết nối được server! (Sai IP, sai Port hoặc Server chưa bật)");
                txtResult.setForeground(Color.RED);
            }

        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Port phải là số!", "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }
}