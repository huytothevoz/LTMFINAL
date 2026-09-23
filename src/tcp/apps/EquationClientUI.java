package tcp.apps;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;

public class EquationClientUI extends JFrame {

    private JTextField txtIp, txtPort;
    private JComboBox<String> cbMode;
    private JTextField txtA, txtB, txtC, txtD, txtE;
    private JLabel lblC, lblD, lblE;
    private JTextArea txtResult;
    private JButton btnSolve;

    public EquationClientUI(String defaultIp, String defaultPort) {
        setTitle("MÁY TÍNH GIẢI PHƯƠNG TRÌNH (TCP CLIENT)");
        setSize(480, 580);
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

        // --- KHU VỰC 1: CẤU HÌNH MẠNG ---
        JPanel top = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        top.setBackground(main.getBackground());
        
        txtIp = new JTextField(defaultIp, 12);
        txtIp.setToolTipText("Nhập IP hiển thị bên phần mềm Server");
        txtPort = new JTextField(defaultPort, 5);
        
        top.add(new JLabel("Server IP:")); top.add(txtIp);
        top.add(new JLabel("Port:")); top.add(txtPort);
        main.add(top, BorderLayout.NORTH);

        // --- KHU VỰC 2: NHẬP LIỆU TOÁN HỌC ---
        JPanel center = new JPanel(new BorderLayout(10, 10));
        center.setBackground(main.getBackground());

        cbMode = new JComboBox<>(new String[]{
                "Phương trình Bậc 1 (ax + b = 0)",
                "Phương trình Bậc 2 (ax² + bx + c = 0)",
                "Phương trình Bậc 3 (ax³ + bx² + cx + d = 0)",
                "Phương trình Bậc 4 (ax⁴ + bx³ + cx² + dx + e = 0)"
        });
        cbMode.setFont(new Font("Segoe UI", Font.BOLD, 14));
        cbMode.setBackground(Color.WHITE);
        center.add(cbMode, BorderLayout.NORTH);

        // Bảng nhập hệ số
        JPanel pnlInputs = new JPanel(new GridBagLayout());
        pnlInputs.setBackground(main.getBackground());
        pnlInputs.setBorder(BorderFactory.createTitledBorder(null, "Nhập hệ số", TitledBorder.LEFT, TitledBorder.TOP, new Font("Segoe UI", Font.BOLD, 12)));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 10, 5, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        txtA = new JTextField(); txtB = new JTextField(); 
        txtC = new JTextField(); txtD = new JTextField(); txtE = new JTextField();
        lblC = new JLabel("Hệ số c:"); lblD = new JLabel("Hệ số d:"); lblE = new JLabel("Hệ số e:");

        Font fontInput = new Font("Consolas", Font.BOLD, 16);
        txtA.setFont(fontInput); txtB.setFont(fontInput); txtC.setFont(fontInput); txtD.setFont(fontInput); txtE.setFont(fontInput);
        txtA.setHorizontalAlignment(JTextField.CENTER); txtB.setHorizontalAlignment(JTextField.CENTER);
        txtC.setHorizontalAlignment(JTextField.CENTER); txtD.setHorizontalAlignment(JTextField.CENTER); txtE.setHorizontalAlignment(JTextField.CENTER);

        addInputRow(pnlInputs, gbc, 0, new JLabel("Hệ số a:"), txtA);
        addInputRow(pnlInputs, gbc, 1, new JLabel("Hệ số b:"), txtB);
        addInputRow(pnlInputs, gbc, 2, lblC, txtC);
        addInputRow(pnlInputs, gbc, 3, lblD, txtD);
        addInputRow(pnlInputs, gbc, 4, lblE, txtE);
        
        center.add(pnlInputs, BorderLayout.CENTER);

        // Khung hiển thị kết quả
        JPanel pnlRes = new JPanel(new BorderLayout(5, 5));
        pnlRes.setBackground(main.getBackground());
        JLabel lblRes = new JLabel("Kết quả:");
        lblRes.setFont(new Font("Segoe UI", Font.BOLD, 14));
        pnlRes.add(lblRes, BorderLayout.NORTH);

        txtResult = new JTextArea(4, 20);
        txtResult.setFont(new Font("Consolas", Font.BOLD, 15));
        txtResult.setForeground(new Color(220, 53, 69)); 
        txtResult.setEditable(false);
        txtResult.setBackground(Color.WHITE);
        txtResult.setMargin(new Insets(10, 10, 10, 10));
        pnlRes.add(new JScrollPane(txtResult), BorderLayout.CENTER);
        
        center.add(pnlRes, BorderLayout.SOUTH);
        main.add(center, BorderLayout.CENTER);

        // --- KHU VỰC 3: NÚT GỬI ---
        btnSolve = new JButton("GỬI YÊU CẦU GIẢI TOÁN");
        btnSolve.setBackground(new Color(0, 123, 255));
        btnSolve.setForeground(Color.WHITE);
        btnSolve.setFont(new Font("Segoe UI", Font.BOLD, 16));
        btnSolve.setFocusPainted(false);
        btnSolve.setOpaque(true);
        btnSolve.setBorderPainted(false);
        btnSolve.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnSolve.setPreferredSize(new Dimension(100, 45));

        main.add(btnSolve, BorderLayout.SOUTH);

        // --- Sự kiện ---
        cbMode.addActionListener(e -> updateUIState());
        btnSolve.addActionListener(e -> requestSolve());
        
        updateUIState();
    }

    private void addInputRow(JPanel pnl, GridBagConstraints gbc, int y, JLabel lbl, JTextField txt) {
        gbc.gridy = y;
        gbc.gridx = 0; gbc.weightx = 0.2; pnl.add(lbl, gbc);
        gbc.gridx = 1; gbc.weightx = 0.8; pnl.add(txt, gbc);
    }

    private void updateUIState() {
        int mode = cbMode.getSelectedIndex();
        lblC.setVisible(mode >= 1); txtC.setVisible(mode >= 1);
        lblD.setVisible(mode >= 2); txtD.setVisible(mode >= 2);
        lblE.setVisible(mode >= 3); txtE.setVisible(mode >= 3);
        revalidate(); repaint();
    }

    private void requestSolve() {
        String ip = txtIp.getText().trim();
        String portStr = txtPort.getText().trim();

        if (ip.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Bro chưa nhập IP kìa! Nhìn sang máy Server copy IP chép vào đây nhé.", "Thiếu IP Server", JOptionPane.WARNING_MESSAGE);
            txtIp.requestFocus();
            return;
        }

        try {
            int port = Integer.parseInt(portStr);

            new Thread(() -> {
                try (Socket socket = new Socket(ip, port);
                     DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                     DataInputStream dis = new DataInputStream(socket.getInputStream())) {

                    int mode = cbMode.getSelectedIndex() + 1;
                    String data = "B" + mode + "|" + txtA.getText().trim() + "|" + txtB.getText().trim();
                    if (mode >= 2) data += "|" + txtC.getText().trim();
                    if (mode >= 3) data += "|" + txtD.getText().trim();
                    if (mode >= 4) data += "|" + txtE.getText().trim();

                    txtResult.setForeground(Color.BLUE);
                    txtResult.setText("Đang gửi yêu cầu tới " + ip + "...");

                    dos.writeUTF(data);
                    String res = dis.readUTF();

                    SwingUtilities.invokeLater(() -> {
                        txtResult.setForeground(new Color(220, 53, 69));
                        txtResult.setText(res.replace("RESULT|", ""));
                    });

                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        txtResult.setForeground(Color.RED);
                        txtResult.setText("LỖI KẾT NỐI!\n1. Kiểm tra lại IP xem gõ đúng chưa.\n2. Chắc chắn máy chủ (Server) đã tắt Tường Lửa (Firewall).");
                    });
                }
            }).start();

        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Port phải là chữ số hợp lệ!", "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }
}