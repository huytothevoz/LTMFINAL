package tcp.apps;

import javax.swing.*;
import java.awt.*;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class EquationServer extends JFrame {
    private JTextArea txtLog;
    private JLabel lblServerInfo;
    private ServerSocket serverSocket;
    private boolean isRunning = false;

    public EquationServer() {
        setTitle("MÁY CHỦ GIẢI PHƯƠNG TRÌNH (SERVER)");
        setSize(480, 400);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        setLayout(new BorderLayout(10, 10));
        JPanel main = new JPanel(new BorderLayout(10, 10));
        main.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        main.setBackground(new Color(240, 242, 245));
        setContentPane(main);

        // --- KHU VỰC PHÍA TRÊN: NÚT BẤM VÀ THÔNG TIN IP ---
        JPanel pnlTop = new JPanel(new GridLayout(2, 1, 10, 15)); // Tăng khoảng cách cho thoáng
        pnlTop.setBackground(main.getBackground());
        pnlTop.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0)); // Đệm dưới

        // Nút bấm Custom Bo Tròn
        JButton btnToggle = new JButton("Khởi động Server Giải Toán (Port 3002)") {
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
        
        btnToggle.setBackground(new Color(0, 0, 128)); // Màu Xanh Navy cố định
        btnToggle.setForeground(Color.WHITE);
        btnToggle.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btnToggle.setFocusPainted(false);
        btnToggle.setContentAreaFilled(false);
        btnToggle.setBorder(BorderFactory.createEmptyBorder(12, 20, 12, 20)); // Padding cho chữ
        btnToggle.setCursor(new Cursor(Cursor.HAND_CURSOR));

        pnlTop.add(btnToggle);

        lblServerInfo = new JLabel("Vui lòng khởi động Server để xem IP kết nối!", SwingConstants.CENTER);
        lblServerInfo.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblServerInfo.setForeground(new Color(100, 100, 100));
        pnlTop.add(lblServerInfo);

        main.add(pnlTop, BorderLayout.NORTH);

        // --- KHU VỰC LOG ---
        txtLog = new JTextArea("Đang chờ khởi động...\n");
        txtLog.setEditable(false);
        txtLog.setFont(new Font("Consolas", Font.PLAIN, 13));
        txtLog.setMargin(new Insets(10, 10, 10, 10));
        main.add(new JScrollPane(txtLog), BorderLayout.CENTER);

        btnToggle.addActionListener(e -> {
            if (!isRunning) startServer(3002, btnToggle);
        });
    }

    // Hàm xịn để dò đúng IP LAN (Tránh lấy nhầm IP máy ảo)
    private String getLocalIpAddress() {
        try (final DatagramSocket socket = new DatagramSocket()) {
            socket.connect(InetAddress.getByName("8.8.8.8"), 10002);
            return socket.getLocalAddress().getHostAddress();
        } catch (Exception e) {
            try {
                return InetAddress.getLocalHost().getHostAddress();
            } catch (Exception ex) {
                return "127.0.0.1";
            }
        }
    }

    private void startServer(int port, JButton btn) {
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port);
                isRunning = true;
                String myIP = getLocalIpAddress(); // Lấy IP ngay khi chạy

                SwingUtilities.invokeLater(() -> {
                    btn.setText("Server Đang Hoạt Động!");
                    // Đã bỏ dòng đổi màu nền đi để giữ Xanh Navy cố định
                    btn.setEnabled(false);
                    
                    // Show IP cho bro dễ copy gửi bạn bè
                    lblServerInfo.setText("👉 Báo cho Client: IP = " + myIP + " | Port = " + port + " 👈");
                    lblServerInfo.setForeground(new Color(0, 123, 255));
                });
                
                log("✅ Đã mở cổng nhận bài tập tại Port: " + port);
                log("📌 ĐỊA CHỈ IP CỦA BẠN LÀ: " + myIP);
                log("--------------------------------------------------");

                while (isRunning) {
                    Socket client = serverSocket.accept();
                    String clientIP = client.getInetAddress().getHostAddress();
                    new Thread(() -> handleClient(client, clientIP)).start();
                }
            } catch (Exception ex) {
                log("❌ Lỗi Server: " + ex.getMessage());
            }
        }).start();
    }

    private void handleClient(Socket socket, String ip) {
        try (DataInputStream dis = new DataInputStream(socket.getInputStream());
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {

            String req = dis.readUTF();
            log("📥 Client [" + ip + "] gửi data: " + req);

            String result = "";
            String[] parts = req.split("\\|");
            String mode = parts[0];

            try {
                double a = Double.parseDouble(parts[1]);
                double b = Double.parseDouble(parts[2]);

                if (mode.equals("B1")) {
                    result = (a == 0) ? (b == 0 ? "Vô số nghiệm" : "Vô nghiệm") : "x = " + String.format("%.4f", -b / a);
                } else if (mode.equals("B2")) {
                    double c = Double.parseDouble(parts[3]);
                    result = solveB2(a, b, c);
                } else if (mode.equals("B3")) {
                    double c = Double.parseDouble(parts[3]);
                    double d = Double.parseDouble(parts[4]);
                    result = solveNumeric(a, b, c, d, 0, 3);
                } else if (mode.equals("B4")) {
                    double c = Double.parseDouble(parts[3]);
                    double d = Double.parseDouble(parts[4]);
                    double e = Double.parseDouble(parts[5]);
                    result = solveNumeric(a, b, c, d, e, 4);
                }
            } catch (Exception e) {
                result = "Lỗi dữ liệu đầu vào: " + e.getMessage();
            }

            dos.writeUTF("RESULT|" + result);
            log("📤 Đã trả kết quả cho [" + ip + "]");

        } catch (Exception ignored) {
        } finally {
            try { socket.close(); } catch (Exception ignored) {}
        }
    }

    private String solveB2(double a, double b, double c) {
        if (a == 0) return (b == 0) ? (c == 0 ? "Vô số nghiệm" : "Vô nghiệm") : "x = " + String.format("%.4f", -c / b);
        double delta = b * b - 4 * a * c;
        if (delta < 0) return "Vô nghiệm thực";
        if (delta == 0) return "Nghiệm kép x = " + String.format("%.4f", -b / (2 * a));
        return String.format("x1 = %.4f, x2 = %.4f", (-b + Math.sqrt(delta)) / (2 * a), (-b - Math.sqrt(delta)) / (2 * a));
    }

    private String solveNumeric(double a, double b, double c, double d, double e, int degree) {
        if (a == 0) return "Hệ số 'a' phải khác 0!";
        List<Double> roots = new ArrayList<>();
        double step = 0.05;
        
        for (double x = -2000; x <= 2000; x += step) {
            double y1 = evalPoly(a, b, c, d, e, x, degree);
            double y2 = evalPoly(a, b, c, d, e, x + step, degree);
            
            if (Math.abs(y1) < 1e-7) {
                if(!isDuplicate(roots, x)) roots.add(x);
            } else if (y1 * y2 < 0) {
                double left = x, right = x + step;
                for (int i = 0; i < 50; i++) {
                    double mid = (left + right) / 2;
                    if (evalPoly(a, b, c, d, e, mid, degree) * y1 > 0) left = mid;
                    else right = mid;
                }
                if(!isDuplicate(roots, left)) roots.add(left);
            }
        }
        
        if (roots.isEmpty()) return "Vô nghiệm thực.";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < roots.size(); i++) {
            sb.append(String.format("x%d = %.4f  ", i + 1, roots.get(i)));
        }
        return sb.toString().trim();
    }

    private boolean isDuplicate(List<Double> roots, double val) {
        for(double r : roots) if(Math.abs(r - val) < 1e-3) return true;
        return false;
    }

    private double evalPoly(double a, double b, double c, double d, double e, double x, int degree) {
        if (degree == 3) return a*x*x*x + b*x*x + c*x + d;
        return a*x*x*x*x + b*x*x*x + c*x*x + d*x + e;
    }

    private void log(String message) {
        SwingUtilities.invokeLater(() -> {
            txtLog.append(message + "\n");
            txtLog.setCaretPosition(txtLog.getDocument().getLength());
        });
    }
}