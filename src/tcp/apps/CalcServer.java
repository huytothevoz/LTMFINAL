package tcp.apps;

import javax.swing.*;
import java.awt.*;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class CalcServer extends JFrame {
    private JTextArea txtLog;
    private JLabel lblServerInfo;
    private ServerSocket serverSocket;
    private boolean isRunning = false;

    public CalcServer() {
        setTitle("MÁY CHỦ TÍNH TOÁN (CALC SERVER)");
        setSize(480, 400); // Tăng chút chiều cao để hiển thị đẹp phần IP
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        setLayout(new BorderLayout(10, 10));
        JPanel main = new JPanel(new BorderLayout(10, 10));
        main.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        main.setBackground(new Color(240, 242, 245));
        setContentPane(main);

        // --- KHU VỰC PHÍA TRÊN: NÚT BẤM VÀ THÔNG TIN IP ---
        // Tăng khoảng cách dọc giữa nút và dòng chữ (từ 5 lên 15) cho thoáng
        JPanel pnlTop = new JPanel(new GridLayout(2, 1, 10, 15)); 
        pnlTop.setBackground(main.getBackground());
        // Thêm một chút đệm phía dưới pnlTop để tách biệt với khu vực log
        pnlTop.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));

        JButton btnToggle = new JButton("Khởi động Server Máy Tính (Port 3000)") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getBackground());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20); 
                super.paintComponent(g);
                g2.dispose();
            }
            // Bỏ hàm paintBorder đi để chúng ta dùng EmptyBorder bên dưới
        };

        btnToggle.setBackground(new Color(0, 0, 128)); 
        btnToggle.setForeground(Color.WHITE); 
        btnToggle.setFont(new Font("Segoe UI", Font.BOLD, 14));
        
        btnToggle.setFocusPainted(false); 
        btnToggle.setContentAreaFilled(false); 
        
        // CỰC KỲ QUAN TRỌNG: Thêm đệm (Padding) vào bên trong nút
        // Thứ tự: Trên, Trái, Dưới, Phải. Nó giúp nút tự phình to ra, không bị lẹm chữ
        btnToggle.setBorder(BorderFactory.createEmptyBorder(12, 20, 12, 20));

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
            if (!isRunning) startServer(3000, btnToggle);
        });
    }

    // Hàm lấy chính xác IP LAN (Bỏ qua các IP ảo của VMware/VirtualBox)
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
                String myIP = getLocalIpAddress(); // Lấy IP ngay khi vừa mở port

                SwingUtilities.invokeLater(() -> {
                    btn.setText("Server Đang Hoạt Động!");
                    btn.setBackground(new Color(220, 53, 69));
                    btn.setEnabled(false);

                    // Show IP cho bro dễ copy gửi cho máy Client
                    lblServerInfo.setText("👉 Báo cho Client: IP = " + myIP + " | Port = " + port + " 👈");
                    lblServerInfo.setForeground(new Color(0, 123, 255));
                });

                log("✅ Đã mở cổng tính toán tại Port: " + port);
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

            String request = dis.readUTF();
            String[] parts = request.split("\\|", 2);
            
            if (parts[0].equals("CALC") && parts.length > 1) {
                log("📥 Client [" + ip + "] yêu cầu tính: " + parts[1]);
                String result = calculate(parts[1]);
                dos.writeUTF(result);
                log("📤 Đã trả kết quả [" + result + "] cho [" + ip + "]");
            } else {
                dos.writeUTF("Lỗi cú pháp!");
            }
        } catch (Exception ignored) {
        } finally {
            try { socket.close(); } catch (Exception ignored) {}
        }
    }

    // Logic toán học giữ nguyên
    private String calculate(String data) {
        try {
            final String str = data.replace(" ", "");
            if (str.isEmpty()) return "Lỗi: Không có dữ liệu!";

            double result = new Object() {
                int pos = -1, ch;
                void nextChar() { ch = (++pos < str.length()) ? str.charAt(pos) : -1; }
                boolean eat(int charToEat) {
                    while (ch == ' ') nextChar();
                    if (ch == charToEat) { nextChar(); return true; }
                    return false;
                }
                double parse() {
                    nextChar();
                    double x = parseExpression();
                    if (pos < str.length()) throw new RuntimeException("Ký tự lạ: " + (char)ch);
                    return x;
                }
                double parseExpression() {
                    double x = parseTerm();
                    for (;;) {
                        if      (eat('+')) x += parseTerm();
                        else if (eat('-')) x -= parseTerm();
                        else return x;
                    }
                }
                double parseTerm() {
                    double x = parseFactor();
                    for (;;) {
                        if      (eat('*')) x *= parseFactor();
                        else if (eat('/')) x /= parseFactor();
                        else return x;
                    }
                }
                double parseFactor() {
                    if (eat('+')) return parseFactor();
                    if (eat('-')) return -parseFactor();
                    double x;
                    int startPos = this.pos;
                    if (eat('(')) { 
                        x = parseExpression();
                        eat(')');
                    } else if ((ch >= '0' && ch <= '9') || ch == '.') { 
                        while ((ch >= '0' && ch <= '9') || ch == '.') nextChar();
                        x = Double.parseDouble(str.substring(startPos, this.pos));
                    } else if (ch >= 'a' && ch <= 'z') {
                        while (ch >= 'a' && ch <= 'z') nextChar();
                        String func = str.substring(startPos, this.pos);
                        x = parseFactor();
                        if (func.equals("sqrt")) x = Math.sqrt(x);
                        else if (func.equals("sin")) x = Math.sin(Math.toRadians(x));
                        else if (func.equals("cos")) x = Math.cos(Math.toRadians(x));
                        else throw new RuntimeException("Hàm không hỗ trợ: " + func);
                    } else {
                        throw new RuntimeException("Ký tự không hợp lệ");
                    }
                    if (eat('^')) x = Math.pow(x, parseFactor()); 
                    return x;
                }
            }.parse();

            if (result == (long) result) return String.format("%d", (long) result);
            else return String.format("%s", result);
        } catch (Exception e) {
            return "Lỗi: Biểu thức không hợp lệ!";
        }
    }

    private void log(String message) {
        SwingUtilities.invokeLater(() -> {
            txtLog.append(message + "\n");
            txtLog.setCaretPosition(txtLog.getDocument().getLength());
        });
    }
}