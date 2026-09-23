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
import java.util.concurrent.ConcurrentHashMap;

public class TcpQuizServer extends JFrame {
    private JTextArea txtLog;
    private JLabel lblServerInfo;
    private ServerSocket serverSocket;
    
    // Kho dữ liệu: Tên bộ đề -> Danh sách [Câu hỏi, Đáp án]
    private static ConcurrentHashMap<String, List<String[]>> quizDB = new ConcurrentHashMap<>();

    public TcpQuizServer() {
        setTitle("MÁY CHỦ LƯU TRỮ TRẮC NGHIỆM");
        setSize(500, 400);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        // Nạp sẵn 1 bộ đề mặc định cho vui
        List<String[]> defaultQuiz = new ArrayList<>();
        defaultQuiz.add(new String[]{"Thủ đô của Việt Nam là gì?\nA. Hà Nội\nB. Huế\nC. Đà Nẵng\nD. TP.HCM", "A"});
        defaultQuiz.add(new String[]{"1 + 1 bằng mấy?", "2"});
        quizDB.put("Đề Mặc Định", defaultQuiz);

        setLayout(new BorderLayout(10, 10));
        JPanel main = new JPanel(new BorderLayout(10, 10));
        main.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        main.setBackground(new Color(240, 242, 245));
        setContentPane(main);

        // --- KHU VỰC TRÊN (NÚT BẤM + IP) ---
        JPanel pnlTop = new JPanel(new GridLayout(2, 1, 10, 15));
        pnlTop.setBackground(main.getBackground());
        pnlTop.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));

        JButton btnStart = new JButton("Khởi động Server Trắc Nghiệm (Port 3004)") {
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
        btnStart.setBackground(new Color(0, 0, 128)); 
        btnStart.setForeground(Color.WHITE);
        btnStart.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btnStart.setFocusPainted(false);
        btnStart.setContentAreaFilled(false);
        btnStart.setBorder(BorderFactory.createEmptyBorder(12, 20, 12, 20)); 
        btnStart.setCursor(new Cursor(Cursor.HAND_CURSOR));

        lblServerInfo = new JLabel("Vui lòng khởi động Server để xem IP kết nối!", SwingConstants.CENTER);
        lblServerInfo.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblServerInfo.setForeground(new Color(100, 100, 100));

        pnlTop.add(btnStart);
        pnlTop.add(lblServerInfo);
        main.add(pnlTop, BorderLayout.NORTH);

        // --- KHU VỰC LOG ---
        txtLog = new JTextArea("Đang chờ khởi động...\n");
        txtLog.setEditable(false);
        txtLog.setBackground(new Color(20, 20, 20));
        txtLog.setForeground(new Color(76, 255, 0));
        txtLog.setFont(new Font("Consolas", Font.PLAIN, 13));
        txtLog.setMargin(new Insets(10, 10, 10, 10));
        main.add(new JScrollPane(txtLog), BorderLayout.CENTER);

        btnStart.addActionListener(e -> startServer(3004, btnStart));
    }

    // Lấy IP LAN
    private String getLocalIpAddress() {
        try (final DatagramSocket socket = new DatagramSocket()) {
            socket.connect(InetAddress.getByName("8.8.8.8"), 10002);
            return socket.getLocalAddress().getHostAddress();
        } catch (Exception e) {
            try { return InetAddress.getLocalHost().getHostAddress(); } 
            catch (Exception ex) { return "127.0.0.1"; }
        }
    }

    private void startServer(int port, JButton btn) {
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port);
                String myIP = getLocalIpAddress();

                SwingUtilities.invokeLater(() -> {
                    btn.setEnabled(false);
                    btn.setText("MÁY CHỦ ĐANG HOẠT ĐỘNG");
                    lblServerInfo.setText("👉 Báo cho Client: IP = " + myIP + " | Port = " + port + " 👈");
                    lblServerInfo.setForeground(new Color(0, 123, 255));
                });

                log("✅ Máy chủ đã mở tại Port " + port);
                log("📌 ĐỊA CHỈ IP LAN CỦA BẠN LÀ: " + myIP);
                log("--------------------------------------------------");

                while (true) {
                    Socket client = serverSocket.accept();
                    log("⚡ Có Client kết nối: " + client.getInetAddress().getHostAddress());
                    new Thread(new ClientHandler(client)).start();
                }
            } catch (Exception e) {
                log("❌ Lỗi: " + e.getMessage());
            }
        }).start();
    }

    private void log(String msg) {
        SwingUtilities.invokeLater(() -> {
            txtLog.append(msg + "\n");
            txtLog.setCaretPosition(txtLog.getDocument().getLength());
        });
    }

    // --- LOGIC XỬ LÝ CHO TỪNG CLIENT (GIỮ NGUYÊN) ---
    private class ClientHandler implements Runnable {
        private Socket socket;
        private List<String[]> currentQuiz;
        private int currentQIndex = 0;
        private int score = 0;

        public ClientHandler(Socket socket) { this.socket = socket; }

        @Override
        public void run() {
            try (DataInputStream dis = new DataInputStream(socket.getInputStream());
                 DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {
                
                while (true) {
                    String req = dis.readUTF();
                    String[] parts = req.split("\\|");

                    switch (parts[0]) {
                        case "CREATE": 
                            String topicName = parts[1];
                            List<String[]> newQuiz = new ArrayList<>();
                            for (int i = 2; i < parts.length; i += 2) {
                                newQuiz.add(new String[]{parts[i], parts[i+1]});
                            }
                            quizDB.put(topicName, newQuiz);
                            log("📥 Đã lưu bộ đề mới: " + topicName);
                            dos.writeUTF("MSG|✅ Tạo bộ đề thành công!");
                            break;

                        case "LIST": 
                            String topics = String.join(",", quizDB.keySet());
                            dos.writeUTF("LIST|" + (topics.isEmpty() ? "Trống" : topics));
                            break;

                        case "JOIN": 
                            currentQuiz = quizDB.get(parts[1]);
                            currentQIndex = 0;
                            score = 0;
                            sendQuestion(dos);
                            break;

                        case "ANSWER": 
                            String ans = parts[1];
                            String correctAns = currentQuiz.get(currentQIndex)[1];
                            
                            if (ans.equalsIgnoreCase(correctAns.trim())) {
                                score++;
                                dos.writeUTF("RESULT|Chính xác! 🎉");
                            } else {
                                dos.writeUTF("RESULT|Sai rồi! Đáp án đúng là: " + correctAns);
                            }
                            
                            currentQIndex++;
                            sendQuestion(dos);
                            break;
                    }
                }
            } catch (Exception e) {
                log("⚠️ Một Client đã ngắt kết nối.");
            }
        }

        private void sendQuestion(DataOutputStream dos) throws Exception {
            if (currentQIndex < currentQuiz.size()) {
                dos.writeUTF("QUESTION|Câu " + (currentQIndex + 1) + ":\n" + currentQuiz.get(currentQIndex)[0]);
            } else {
                dos.writeUTF("SCORE|Bạn đã hoàn thành bài thi!\nTổng điểm: " + score + "/" + currentQuiz.size());
            }
        }
    }
}