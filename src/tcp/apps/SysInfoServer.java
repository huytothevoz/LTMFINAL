package tcp.apps;

import javax.swing.*;
import java.awt.*;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class SysInfoServer extends JFrame {
    private JTextArea txtLog;
    private JLabel lblServerInfo;
    private ServerSocket serverSocket;
    private boolean isRunning = false;

    public SysInfoServer() {
        setTitle("MÁY CHỦ THÔNG TIN HỆ THỐNG (HOST)");
        setSize(480, 400); // Nới rộng ra một chút cho thoáng
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
        JButton btnToggle = new JButton("Khởi động Server (Port 3003)") {
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

        // Nơi hiển thị IP LAN cho bạn bè nhập
        lblServerInfo = new JLabel("Vui lòng khởi động Server để xem IP kết nối!", SwingConstants.CENTER);
        lblServerInfo.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblServerInfo.setForeground(new Color(100, 100, 100));
        pnlTop.add(lblServerInfo);

        main.add(pnlTop, BorderLayout.NORTH);

        // --- Nhật ký truy cập ---
        txtLog = new JTextArea("Sẵn sàng khởi động...\n");
        txtLog.setEditable(false);
        txtLog.setFont(new Font("Consolas", Font.PLAIN, 13));
        txtLog.setMargin(new Insets(10, 10, 10, 10));
        JScrollPane scroll = new JScrollPane(txtLog);
        scroll.setBorder(BorderFactory.createTitledBorder("Nhật ký truy cập"));
        main.add(scroll, BorderLayout.CENTER);

        // --- Sự kiện ---
        btnToggle.addActionListener(e -> {
            if (!isRunning) {
                startServer(3003, btnToggle);
            }
        });
    }

    // Hàm lấy IP mạng LAN thực tế
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
                String myIP = getLocalIpAddress(); // Quét IP LAN ngay lúc khởi động

                SwingUtilities.invokeLater(() -> {
                    btn.setText("Đang hoạt động trên Port " + port);
                    btn.setEnabled(false); // Disable nút đi nhưng vẫn giữ màu nền Xanh Navy
                    
                    // Hiện IP lên màn hình
                    lblServerInfo.setText("👉 Báo cho Client: IP = " + myIP + " | Port = " + port + " 👈");
                    lblServerInfo.setForeground(new Color(0, 123, 255));
                });
                
                log("✅ Đã mở cổng cung cấp thông tin tại Port: " + port);
                log("📌 ĐỊA CHỈ IP LAN CỦA BẠN LÀ: " + myIP);
                log("--------------------------------------------------");

                while (true) {
                    Socket client = serverSocket.accept();
                    String clientIP = client.getInetAddress().getHostAddress();
                    log("⚡ Client [" + clientIP + "] đang kết nối...");
                    new Thread(() -> handleClient(client, clientIP)).start();
                }
            } catch (Exception ex) {
                log("❌ Lỗi Server: " + ex.getMessage());
            }
        }).start();
    }

    private void handleClient(Socket client, String ip) {
        try (DataInputStream dis = new DataInputStream(client.getInputStream());
             DataOutputStream dos = new DataOutputStream(client.getOutputStream())) {
            
            String req = dis.readUTF();
            
            if (req.startsWith("SYS|")) {
                log("📥 Nhận yêu cầu quét cấu hình từ [" + ip + "]");
                String systemData = gatherSystemInfo();
                dos.writeUTF(systemData);
                log("📤 Đã gửi cấu hình thành công cho [" + ip + "]");
            }
        } catch (Exception e) {
            log("⚠️ Client [" + ip + "] ngắt kết nối.");
        }
    }

    // Hàm lấy cấu hình máy, nối với nhau bằng dấu |
    private String gatherSystemInfo() {
        Runtime runtime = Runtime.getRuntime();
        StringBuilder sb = new StringBuilder();

        String osName = System.getProperty("os.name");
        String osArch = System.getProperty("os.arch");
        sb.append("He dieu hanh: ").append(osName).append(" (").append(osArch).append(")|");

        int cores = runtime.availableProcessors();
        sb.append("Vi xu ly (CPU): ").append(cores).append(" luong (Threads)|");

        long totalMem = runtime.totalMemory() / (1024 * 1024);
        long freeMem = runtime.freeMemory() / (1024 * 1024);
        sb.append("Bo nho RAM ung dung: ").append(totalMem - freeMem).append(" MB / ").append(totalMem).append(" MB|");

        File[] roots = File.listRoots();
        if (roots != null && roots.length > 0) {
            File root = roots[0];
            long totalSpace = root.getTotalSpace() / (1024 * 1024 * 1024);
            long freeSpace = root.getFreeSpace() / (1024 * 1024 * 1024);
            sb.append("O cung (").append(root.getAbsolutePath()).append("): Trong ").append(freeSpace).append(" GB / Tong ").append(totalSpace).append(" GB|");
        }

        sb.append("Moi truong Java: Phien ban ").append(System.getProperty("java.version"));
        return sb.toString();
    }

    private void log(String message) {
        SwingUtilities.invokeLater(() -> {
            txtLog.append(message + "\n");
            txtLog.setCaretPosition(txtLog.getDocument().getLength());
        });
    }
}