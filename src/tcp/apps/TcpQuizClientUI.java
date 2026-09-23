package tcp.apps;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;

public class TcpQuizClientUI extends JFrame {

    private JTextField txtIp, txtPort, txtAnswer;
    private JTextArea txtQuestion, txtLog;
    private JButton btnPlay, btnCreate, btnSend;
    private JButton[] btnChoices = new JButton[4]; // 4 nút A, B, C, D

    private Socket socket;
    private DataInputStream dis;
    private DataOutputStream dos;

    public TcpQuizClientUI() {
        setTitle("🎮 HỆ THỐNG TRẮC NGHIỆM TỔNG HỢP");
        setSize(600, 680); // Nới rộng xíu để chứa bộ nút ABCD
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

        // --- TOP: Kết nối và Chọn vai trò ---
        JPanel top = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 5));
        top.setBackground(main.getBackground());

        txtIp = new JTextField("", 12); // Đã xóa localhost để nhập IP LAN
        txtPort = new JTextField("3004", 4);
        
        // Dùng nút Xanh Navy
        btnPlay = createRoundedBtn("VÀO THI", new Color(0, 0, 128));
        btnCreate = createRoundedBtn("TẠO ĐỀ MỚI", new Color(0, 0, 128));

        top.add(new JLabel("Server IP:")); top.add(txtIp);
        top.add(new JLabel("Port:")); top.add(txtPort);
        top.add(btnPlay);
        top.add(btnCreate);
        main.add(top, BorderLayout.NORTH);

        // --- CENTER: Hiển thị ---
        JPanel center = new JPanel(new BorderLayout(10, 10));
        center.setBackground(main.getBackground());

        txtQuestion = new JTextArea("Vui lòng Kết nối để Bắt đầu...");
        txtQuestion.setEditable(false);
        txtQuestion.setFont(new Font("Segoe UI", Font.BOLD, 18));
        txtQuestion.setForeground(new Color(0, 102, 204));
        txtQuestion.setLineWrap(true);
        txtQuestion.setWrapStyleWord(true);
        txtQuestion.setMargin(new Insets(20, 20, 20, 20));
        JScrollPane scrollQuestion = new JScrollPane(txtQuestion);
        scrollQuestion.setBorder(BorderFactory.createTitledBorder("BẢNG CÂU HỎI"));

        txtLog = new JTextArea(5, 20);
        txtLog.setEditable(false);
        txtLog.setBackground(new Color(20, 20, 20));
        txtLog.setForeground(new Color(76, 255, 0));
        txtLog.setFont(new Font("Consolas", Font.PLAIN, 13));
        txtLog.setMargin(new Insets(10, 10, 10, 10));
        JScrollPane scrollLog = new JScrollPane(txtLog);
        scrollLog.setBorder(BorderFactory.createTitledBorder("TRẠNG THÁI SERVER"));

        center.add(scrollQuestion, BorderLayout.CENTER);
        center.add(scrollLog, BorderLayout.SOUTH);
        main.add(center, BorderLayout.CENTER);

        // --- BOTTOM: Khu vực Nhập/Chọn đáp án ---
        JPanel bottom = new JPanel(new BorderLayout(10, 10));
        bottom.setBackground(main.getBackground());
        
        // Hàng 1: Nút trắc nghiệm A, B, C, D
        JPanel pnlMultipleChoice = new JPanel(new GridLayout(1, 4, 10, 10));
        pnlMultipleChoice.setBackground(main.getBackground());
        pnlMultipleChoice.setBorder(BorderFactory.createTitledBorder("CHỌN NHANH (NẾU LÀ TRẮC NGHIỆM)"));
        
        String[] labels = {"A", "B", "C", "D"};
        Color[] btnColors = {new Color(255, 152, 0), new Color(33, 150, 243), new Color(76, 175, 80), new Color(244, 67, 54)};
        
        for (int i = 0; i < 4; i++) {
            btnChoices[i] = createRoundedBtn(labels[i], btnColors[i]);
            btnChoices[i].setFont(new Font("Segoe UI", Font.BOLD, 18));
            btnChoices[i].setEnabled(false);
            
            final String choiceStr = labels[i];
            btnChoices[i].addActionListener(e -> sendQuickAnswer(choiceStr));
            pnlMultipleChoice.add(btnChoices[i]);
        }
        
        // Hàng 2: Textbox nhập đáp án tự luận
        JPanel pnlTextAnswer = new JPanel(new BorderLayout(10, 10));
        pnlTextAnswer.setBackground(main.getBackground());
        pnlTextAnswer.setBorder(BorderFactory.createTitledBorder("GÕ ĐÁP ÁN (NẾU LÀ ĐIỀN TỪ)"));
        
        txtAnswer = new JTextField();
        txtAnswer.setFont(new Font("Consolas", Font.BOLD, 16));
        txtAnswer.setEnabled(false);

        btnSend = createRoundedBtn("GỬI →", new Color(0, 0, 128));
        btnSend.setEnabled(false);
        
        pnlTextAnswer.add(txtAnswer, BorderLayout.CENTER);
        pnlTextAnswer.add(btnSend, BorderLayout.EAST);

        bottom.add(pnlMultipleChoice, BorderLayout.NORTH);
        bottom.add(pnlTextAnswer, BorderLayout.CENTER);
        
        main.add(bottom, BorderLayout.SOUTH);

        // --- SỰ KIỆN ---
        btnPlay.addActionListener(e -> playQuiz());
        btnCreate.addActionListener(e -> createQuiz());
        btnSend.addActionListener(e -> sendTextAnswer());
        txtAnswer.addActionListener(e -> sendTextAnswer());
    }

    // Nút bo tròn Custom
    private JButton createRoundedBtn(String text, Color bg) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (getModel().isEnabled()) {
                    g2.setColor(getBackground());
                } else {
                    g2.setColor(Color.GRAY); // Màu xám khi bị disable
                }
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 15, 15);
                super.paintComponent(g);
                g2.dispose();
            }
        };
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btn.setFocusPainted(false);
        btn.setContentAreaFilled(false);
        btn.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private void log(String msg) {
        SwingUtilities.invokeLater(() -> {
            txtLog.append(msg + "\n");
            txtLog.setCaretPosition(txtLog.getDocument().getLength());
        });
    }

    // Bật/tắt các ô nhập đáp án
    private void setEnableInput(boolean isEnabled) {
        btnSend.setEnabled(isEnabled);
        txtAnswer.setEnabled(isEnabled);
        for(JButton b : btnChoices) b.setEnabled(isEnabled);
    }

    private boolean connect() {
        if (socket != null && !socket.isClosed()) return true;
        String ip = txtIp.getText().trim();
        if (ip.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập IP Server!", "Lỗi", JOptionPane.WARNING_MESSAGE);
            return false;
        }
        
        try {
            socket = new Socket(ip, Integer.parseInt(txtPort.getText().trim()));
            dis = new DataInputStream(socket.getInputStream());
            dos = new DataOutputStream(socket.getOutputStream());
            new Thread(this::receiveLoop).start();
            log("[OK] Đã kết nối tới Server");
            return true;
        } catch (Exception e) {
            log("[LỖI] Không thể kết nối tới Server!");
            return false;
        }
    }

    private void playQuiz() {
        if (!connect()) return;
        try { dos.writeUTF("LIST|"); } 
        catch (Exception e) { log("Lỗi gửi yêu cầu!"); }
    }

    private void createQuiz() {
        if (!connect()) return;
        
        String topicName = JOptionPane.showInputDialog(this, "Nhập tên bộ đề (VD: Trắc nghiệm Địa lý):");
        if (topicName == null || topicName.trim().isEmpty()) return;

        String strCount = JOptionPane.showInputDialog(this, "Bạn muốn tạo bao nhiêu câu hỏi?");
        try {
            int count = Integer.parseInt(strCount);
            StringBuilder payload = new StringBuilder("CREATE|" + topicName);
            
            for (int i = 1; i <= count; i++) {
                String q = JOptionPane.showInputDialog(this, "Nhập CÂU HỎI số " + i + ":\n(Nên nhập thêm A,B,C,D nếu là trắc nghiệm)");
                String a = JOptionPane.showInputDialog(this, "Nhập ĐÁP ÁN ĐÚNG cho câu " + i + ":\n(VD: Gõ 'A' hoặc gõ chữ bình thường)");
                payload.append("|").append(q).append("|").append(a);
            }
            dos.writeUTF(payload.toString());
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Đã hủy hoặc nhập sai số lượng!");
        }
    }

    // Gửi đáp án bằng nút chọn nhanh ABCD
    private void sendQuickAnswer(String choice) {
        try {
            dos.writeUTF("ANSWER|" + choice);
            log("Bạn chọn nhanh: " + choice);
        } catch (Exception e) { log("[LỖI] Mất kết nối"); }
    }

    // Gửi đáp án bằng cách gõ tay
    private void sendTextAnswer() {
        try {
            String ans = txtAnswer.getText().trim();
            if (ans.isEmpty()) return;
            dos.writeUTF("ANSWER|" + ans);
            log("Bạn gõ: " + ans);
            txtAnswer.setText("");
        } catch (Exception e) { log("[LỖI] Mất kết nối"); }
    }

    // Vòng lặp nhận phản hồi
    private void receiveLoop() {
        try {
            while (true) {
                String msg = dis.readUTF();
                SwingUtilities.invokeLater(() -> {
                    String[] parts = msg.split("\\|");

                    if (parts[0].equals("LIST")) {
                        String[] topics = parts[1].split(",");
                        String choice = (String) JOptionPane.showInputDialog(null, "Chọn bộ đề để thi:", 
                                "Danh sách Đề", JOptionPane.QUESTION_MESSAGE, null, topics, topics[0]);
                        if (choice != null) {
                            try {
                                dos.writeUTF("JOIN|" + choice);
                                btnPlay.setEnabled(false);
                                btnCreate.setEnabled(false);
                                setEnableInput(true);
                                txtQuestion.setText("Đang tải đề...");
                            } catch (Exception ex) {}
                        }
                    } 
                    else if (parts[0].equals("QUESTION")) {
                        txtQuestion.setText(parts[1]);
                    } 
                    else if (parts[0].equals("RESULT") || parts[0].equals("MSG")) {
                        log("👉 " + parts[1]);
                    } 
                    else if (parts[0].equals("SCORE")) {
                        txtQuestion.setText("KẾT THÚC!\n" + parts[1]);
                        setEnableInput(false);
                        btnPlay.setEnabled(true);
                        btnCreate.setEnabled(true);
                    }
                });
            }
        } catch (Exception e) {
            log("⚠️ Kết nối đã đóng.");
        }
    }
}