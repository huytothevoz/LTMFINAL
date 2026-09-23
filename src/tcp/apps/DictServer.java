package tcp.apps;

import javax.swing.*;
import java.awt.*;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

public class DictServer extends JFrame {
    private int port;
    private Map<String, String> dictionary;
    
    // Các component cho giao diện
    private JTextArea txtLog;
    private JLabel lblServerInfo;
    private ServerSocket serverSocket;
    private boolean isRunning = false;

    public DictServer(int port) {
        this.port = port;
        initDictionary();

        // --- CÀI ĐẶT CỬA SỔ ---
        setTitle("MÁY CHỦ TỪ ĐIỂN (SERVER)");
        setSize(500, 420);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        setLayout(new BorderLayout(10, 10));
        JPanel main = new JPanel(new BorderLayout(10, 10));
        main.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        main.setBackground(new Color(240, 242, 245));
        setContentPane(main);

        // --- KHU VỰC PHÍA TRÊN: NÚT BẤM VÀ THÔNG TIN IP ---
        JPanel pnlTop = new JPanel(new GridLayout(2, 1, 10, 15));
        pnlTop.setBackground(main.getBackground());
        pnlTop.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));

        // Nút bấm Custom Bo Tròn - Xanh Navy
        JButton btnToggle = new JButton("Khởi động Server Từ Điển (Port " + port + ")") {
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
        
        btnToggle.setBackground(new Color(0, 0, 128)); 
        btnToggle.setForeground(Color.WHITE);
        btnToggle.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btnToggle.setFocusPainted(false);
        btnToggle.setContentAreaFilled(false);
        btnToggle.setBorder(BorderFactory.createEmptyBorder(12, 20, 12, 20)); 
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

        // Bấm nút để bật Server
        btnToggle.addActionListener(e -> {
            if (!isRunning) startServer(btnToggle);
        });
    }

    // Kho từ vựng
    private void initDictionary() {
        dictionary = new HashMap<>();
        
        dictionary.put("network", "Mạng máy tính");
        dictionary.put("server", "Máy chủ (Cung cấp tài nguyên/dịch vụ)");
        dictionary.put("client", "Máy khách (Gửi yêu cầu đến máy chủ)");
        dictionary.put("protocol", "Giao thức mạng (Ví dụ: TCP, UDP, HTTP)");
        dictionary.put("socket", "Ổ cắm mạng (Giao diện lập trình để gửi/nhận dữ liệu)");
        dictionary.put("router", "Bộ định tuyến (Điều phối lưu lượng mạng)");
        dictionary.put("switch", "Bộ chuyển mạch mạng");
        dictionary.put("firewall", "Tường lửa (Bảo vệ an ninh mạng)");
        dictionary.put("bandwidth", "Băng thông (Tốc độ truyền tải dữ liệu)");
        dictionary.put("latency", "Độ trễ mạng");
        dictionary.put("database", "Cơ sở dữ liệu");
        dictionary.put("software", "Phần mềm");
        dictionary.put("hardware", "Phần cứng");
        dictionary.put("encryption", "Mã hóa dữ liệu");
        dictionary.put("decryption", "Giải mã dữ liệu");
        dictionary.put("bug", "Lỗi phần mềm");
        dictionary.put("developer", "Lập trình viên / Nhà phát triển");

        dictionary.put("hello", "Xin chào");
        dictionary.put("world", "Thế giới");
        dictionary.put("apple", "Quả táo");
        dictionary.put("banana", "Quả chuối");
        dictionary.put("computer", "Máy vi tính");
        dictionary.put("student", "Học sinh / Sinh viên");
        dictionary.put("teacher", "Giáo viên");
        dictionary.put("school", "Trường học");
        dictionary.put("university", "Trường đại học");
    }

    // Lấy IP LAN
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

    // Ghi log ra màn hình JTextArea
    private void log(String message) {
        SwingUtilities.invokeLater(() -> {
            txtLog.append(message + "\n");
            // Tự động cuộn xuống dòng mới nhất
            txtLog.setCaretPosition(txtLog.getDocument().getLength());
        });
    }

    private void startServer(JButton btn) {
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port);
                isRunning = true;
                String myIP = getLocalIpAddress(); 

                SwingUtilities.invokeLater(() -> {
                    btn.setText("Server Từ Điển Đang Hoạt Động!");
                    btn.setEnabled(false);
                    
                    lblServerInfo.setText("👉 Báo cho Client: IP = " + myIP + " | Port = " + port + " 👈");
                    lblServerInfo.setForeground(new Color(0, 123, 255));
                });
                
                log("✅ TỪ ĐIỂN SERVER ĐÃ BẬT TẠI PORT: " + port);
                log("📌 ĐỊA CHỈ IP CỦA BẠN LÀ: " + myIP);
                log("--------------------------------------------------");

                while (isRunning) {
                    Socket client = serverSocket.accept();
                    String clientIP = client.getInetAddress().getHostAddress();
                    log("📥 [Client " + clientIP + "] vừa kết nối!");
                    
                    new Thread(() -> handleClient(client, clientIP)).start();
                }
            } catch (Exception ex) {
                log("❌ Lỗi Server: " + ex.getMessage());
            }
        }).start();
    }

    private void handleClient(Socket client, String clientIP) {
        try (DataInputStream dis = new DataInputStream(client.getInputStream());
             DataOutputStream dos = new DataOutputStream(client.getOutputStream())) {
            
            String req = dis.readUTF();
            if (req.startsWith("DICT|")) {
                String word = req.substring(5).trim().toLowerCase();
                log("   🔍 Client [" + clientIP + "] đang tra từ: '" + word + "'");
                
                String meaning = dictionary.getOrDefault(word, "Không tìm thấy nghĩa của từ này trong hệ thống!");
                
                dos.writeUTF(meaning);
                log("   📤 Đã trả kết quả cho [" + clientIP + "]");
            }
        } catch (Exception e) {
            // Client ngắt kết nối
        } finally {
            try { client.close(); } catch (Exception ignored) {}
        }
    }
}