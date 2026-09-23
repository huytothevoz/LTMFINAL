package tcp.chat;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

public class TcpChatServerUI extends JFrame {

    private JTextArea txtLog;
    private JTextField txtPort;
    private JTextField txtServerMessage;
    
    private DefaultListModel<String> modelUsers;
    private JList<String> listUsers;
    
    private DefaultListModel<String> modelGroups;
    private JList<String> listGroups;
    
    private ServerSocket serverSocket;
    
    // Sử dụng ConcurrentHashMap để quản lý danh sách Client an toàn trong môi trường đa luồng
    private final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private final Set<String> chatGroups = new ConcurrentSkipListSet<>();
    private final Map<String, Set<String>> friendsMap = new ConcurrentHashMap<>();

    // --- THÊM: BỘ NHỚ LƯU TÀI KHOẢN ---
    private final Map<String, String> registeredAccounts = new ConcurrentHashMap<>();
    private static final String ACCOUNT_FILE = "accounts.txt";

    public TcpChatServerUI() {
        setTitle("Hệ Thống Quản Trị Máy Chủ TCP");
        setSize(900, 650); 
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        
        setupUI();
        loadAccounts(); // Tải danh sách tài khoản ngay khi mở App
    }

    // --- THÊM: HÀM ĐỌC/GHI FILE TÀI KHOẢN ---
    private void loadAccounts() {
        try {
            File file = new File(ACCOUNT_FILE);
            if (!file.exists()) file.createNewFile();
            BufferedReader br = new BufferedReader(new FileReader(file));
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(":", 2);
                if (parts.length == 2) registeredAccounts.put(parts[0], parts[1]);
            }
            br.close();
            log("[HỆ THỐNG] Đã tải " + registeredAccounts.size() + " tài khoản.");
        } catch (IOException e) {
            log("[LỖI] Lỗi đọc file tài khoản!");
        }
    }

    private synchronized void saveAccount(String username, String password) {
        registeredAccounts.put(username, password);
        try (PrintWriter pw = new PrintWriter(new FileWriter(ACCOUNT_FILE, true))) {
            pw.println(username + ":" + password);
        } catch (IOException e) {
            log("[LỖI] Lỗi lưu tài khoản!");
        }
    }
    // ----------------------------------------

    private void setupUI() {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}
        setLayout(new BorderLayout(10, 10));
        ((JPanel)getContentPane()).setBorder(new EmptyBorder(10, 10, 10, 10));

        // Thiết lập bảng điều khiển phía trên: Cấu hình cổng và trạng thái Server
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 5));
        
        // --- HIỂN THỊ IP SERVER ---
        topPanel.add(new JLabel("IP Server (Gửi cho Client):"));
        JTextField txtServerIP = new JTextField(getLocalIP(), 12);
        txtServerIP.setEditable(false); // Chỉ cho copy, không cho sửa
        txtServerIP.setFont(new Font("Segoe UI", Font.BOLD, 14));
        txtServerIP.setForeground(new Color(220, 53, 69)); 
        topPanel.add(txtServerIP);
        // -----------------------------------------

        topPanel.add(new JLabel("Cổng kết nối:"));
        txtPort = new JTextField("2026", 6);
        txtPort.setFont(new Font("Segoe UI", Font.BOLD, 14));
        
        JButton btnStart = createFlatButton("KHỞI ĐỘNG MÁY CHỦ", new Color(40, 167, 69));
        JButton btnRefresh = createFlatButton("LÀM MỚI DANH SÁCH", new Color(23, 162, 184));
        
        topPanel.add(txtPort);
        topPanel.add(btnStart);
        topPanel.add(btnRefresh);
        add(topPanel, BorderLayout.NORTH);

        // Thiết lập khu vực hiển thị trung tâm: Nhật ký hệ thống và Quản lý người dùng
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(550); 
        splitPane.setDividerSize(5);

        // Khu vực hiển thị Log: Theo dõi mọi hoạt động gửi/nhận tin nhắn và kết nối
        txtLog = new JTextArea();
        txtLog.setEditable(false);
        txtLog.setFont(new Font("Consolas", Font.PLAIN, 14));
        txtLog.setBackground(new Color(250, 250, 250));
        JScrollPane scrollLog = new JScrollPane(txtLog);
        scrollLog.setBorder(BorderFactory.createTitledBorder(null, "Nhật ký hệ thống", TitledBorder.LEFT, TitledBorder.TOP, new Font("Segoe UI", Font.BOLD, 12)));
        splitPane.setLeftComponent(scrollLog);

        // Cấu trúc cột bên phải: Chia làm 2 phần để quản lý User Online và các Nhóm Chat hiện có
        JPanel rightPanel = new JPanel(new GridLayout(2, 1, 0, 10)); 

        // Quản lý người dùng trực tuyến
        JPanel panelUsers = new JPanel(new BorderLayout(0, 5));
        modelUsers = new DefaultListModel<>();
        listUsers = new JList<>(modelUsers);
        listUsers.setFont(new Font("Segoe UI", Font.BOLD, 14));
        listUsers.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane scrollUsers = new JScrollPane(listUsers);
        scrollUsers.setBorder(BorderFactory.createTitledBorder(null, "Người dùng đang trực tuyến", TitledBorder.LEFT, TitledBorder.TOP, new Font("Segoe UI", Font.BOLD, 12)));
        
        JButton btnKick = createFlatButton("ĐUỔI KHỎI PHÒNG", new Color(220, 53, 69)); 
        panelUsers.add(scrollUsers, BorderLayout.CENTER);
        panelUsers.add(btnKick, BorderLayout.SOUTH);

        // Quản lý danh sách các nhóm đã tạo
        JPanel panelGroups = new JPanel(new BorderLayout());
        modelGroups = new DefaultListModel<>();
        listGroups = new JList<>(modelGroups);
        listGroups.setFont(new Font("Segoe UI", Font.BOLD, 14));
        listGroups.setForeground(new Color(41, 128, 185)); 
        JScrollPane scrollGroups = new JScrollPane(listGroups);
        scrollGroups.setBorder(BorderFactory.createTitledBorder(null, "Nhóm trò chuyện", TitledBorder.LEFT, TitledBorder.TOP, new Font("Segoe UI", Font.BOLD, 12)));
        panelGroups.add(scrollGroups, BorderLayout.CENTER);

        rightPanel.add(panelUsers);
        rightPanel.add(panelGroups);
        splitPane.setRightComponent(rightPanel);
        add(splitPane, BorderLayout.CENTER);

        // Thiết lập khu vực tương tác của Admin: Cho phép Server gửi thông báo hoặc nhắn tin riêng
        JPanel bottomPanel = new JPanel(new BorderLayout(10, 0));
        bottomPanel.setBorder(new EmptyBorder(10, 0, 0, 0));
        
        txtServerMessage = new JTextField();
        txtServerMessage.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        txtServerMessage.setToolTipText("Nhập tin nhắn (Chọn 1 người để nhắn riêng hoặc không chọn để gửi tất cả)");
        
        JButton btnSendMsg = createFlatButton("GỬI TIN NHẮN", new Color(0, 123, 255));
        
        bottomPanel.add(txtServerMessage, BorderLayout.CENTER);
        bottomPanel.add(btnSendMsg, BorderLayout.EAST);
        add(bottomPanel, BorderLayout.SOUTH);

        // Gán các sự kiện điều khiển
        btnStart.addActionListener(e -> startServer(btnStart));
        btnRefresh.addActionListener(e -> refreshUI());
        btnKick.addActionListener(e -> kickUser(listUsers.getSelectedValue()));
        btnSendMsg.addActionListener(e -> sendServerMessage());
        txtServerMessage.addActionListener(e -> sendServerMessage());
        
        
    }

    private JButton createFlatButton(String text, Color bgColor) {
        JButton btn = new JButton(text);
        btn.setBackground(bgColor);
        btn.setForeground(Color.WHITE);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btn.setFocusPainted(false);
        btn.setOpaque(true);
        btn.setBorderPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }

    // Logic khởi chạy Socket Server trên luồng phụ để tránh treo giao diện
    private void startServer(JButton btn) {
        new Thread(() -> {
            try {
                int port = Integer.parseInt(txtPort.getText());
                serverSocket = new ServerSocket(port);
                log("[HỆ THỐNG] Máy chủ bắt đầu chạy tại cổng: " + port);
                
                SwingUtilities.invokeLater(() -> {
                    btn.setEnabled(false);
                    btn.setText("MÁY CHỦ ĐANG CHẠY...");
                    btn.setBackground(Color.GRAY);
                });

                while (true) {
                    Socket socket = serverSocket.accept();
                    // Mỗi kết nối mới được bàn giao cho một luồng ClientHandler riêng biệt
                    new ClientHandler(socket).start();
                }
            } catch (Exception e) {
                log("[LỖI] Không thể khởi động máy chủ: " + e.getMessage());
            }
        }).start();
    }

    private void log(String msg) {
        SwingUtilities.invokeLater(() -> {
            txtLog.append(msg + "\n");
            txtLog.setCaretPosition(txtLog.getDocument().getLength());
        });
    }

    private void broadcast(String msg) {
        for (ClientHandler c : clients.values()) c.sendMessage(msg);
    }

    private void refreshUI() {
        SwingUtilities.invokeLater(() -> {
            modelUsers.clear();
            for (String u : clients.keySet()) modelUsers.addElement(u);
            modelGroups.clear();
            for (String g : chatGroups) modelGroups.addElement(g);
        });
    }

    // Gửi cập nhật danh sách User và Group mới nhất cho tất cả Client khi có thay đổi
    private void broadcastStates() {
        StringBuilder sbUsers = new StringBuilder("[USERS]");
        for (String u : clients.keySet()) sbUsers.append(u).append(",");
        StringBuilder sbGroups = new StringBuilder("[GROUPS]");
        for (String g : chatGroups) sbGroups.append(g).append(",");

        String usersMsg = sbUsers.toString();
        String groupsMsg = sbGroups.toString();
        
        for (ClientHandler c : clients.values()) {
            c.sendMessage(usersMsg);
            c.sendMessage(groupsMsg);
        }
        refreshUI(); 
    }

    private void sendFriendList(String username) {
        ClientHandler c = clients.get(username);
        if (c != null) {
            Set<String> friends = friendsMap.getOrDefault(username, new HashSet<>());
            StringBuilder sb = new StringBuilder("[FRIENDS]");
            for (String f : friends) sb.append(f).append(",");
            c.sendMessage(sb.toString());
        }
    }

    private void kickUser(String username) {
        if (username != null) {
            ClientHandler target = clients.get(username);
            if (target != null) {
                target.sendMessage("[SYS] BẠN ĐÃ BỊ QUẢN TRỊ VIÊN ĐUỔI KHỎI MÁY CHỦ!");
                log("[TRỤC XUẤT] Đã ngắt kết nối người dùng: " + username);
                target.disconnect();
            }
        }
    }

    // Logic xử lý tin nhắn từ Admin Server xuống Client (hỗ trợ cả Chat All và Chat riêng)
    private void sendServerMessage() {
        String msg = txtServerMessage.getText().trim();
        if (msg.isEmpty()) return;

        String selectedUser = listUsers.getSelectedValue();

        if (selectedUser == null) {
            // Trường hợp không chọn user: Gửi thông báo Popup cho toàn máy chủ
            broadcast("[ADMIN_ALL]" + msg);
            log("Server gửi thông báo chung: " + msg);
        } else {
            // Trường hợp có chọn user: Gửi Popup tin nhắn riêng từ Server
            ClientHandler target = clients.get(selectedUser);
            if (target != null) {
                target.sendMessage("[ADMIN_PM]" + msg);
                log("Server gửi tin nhắn riêng tới " + selectedUser + ": " + msg);
            }
        }
        
        txtServerMessage.setText(""); 
        listUsers.clearSelection(); 
    }

    // Lớp nội tại xử lý giao tiếp song song với từng Client
    class ClientHandler extends Thread {
        private Socket socket;
        private BufferedReader in;
        private PrintWriter out;
        private String username;
        private boolean isRunning = true;

        public ClientHandler(Socket socket) { this.socket = socket; }

        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
                out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);

                // --- SỬA LOGIC: GIAI ĐOẠN XÁC THỰC (ĐĂNG NHẬP / ĐĂNG KÝ) ---
                boolean isAuthenticated = false;
                while (!isAuthenticated && isRunning) {
                    String authMsg = in.readLine();
                    if (authMsg == null) return; // Disconnect

                    if (authMsg.startsWith("[REGISTER]")) {
                        String[] parts = authMsg.substring(10).split(":", 2);
                        if (parts.length == 2) {
                            String user = parts[0];
                            String pass = parts[1];
                            if (registeredAccounts.containsKey(user)) {
                                out.println("[REG_FAIL]Tên tài khoản đã tồn tại!");
                            } else {
                                saveAccount(user, pass);
                                out.println("[REG_SUCCESS]Đăng ký thành công!");
                                log("[HỆ THỐNG] Tài khoản mới được đăng ký: " + user);
                            }
                        }
                    } 
                    else if (authMsg.startsWith("[LOGIN]")) {
                        String[] parts = authMsg.substring(7).split(":", 2);
                        if (parts.length == 2) {
                            String user = parts[0];
                            String pass = parts[1];
                            
                            if (!registeredAccounts.containsKey(user) || !registeredAccounts.get(user).equals(pass)) {
                                out.println("[LOG_FAIL]Sai tài khoản hoặc mật khẩu!");
                            } else if (clients.containsKey(user)) {
                                out.println("[LOG_FAIL]Tài khoản đang được đăng nhập ở nơi khác!");
                            } else {
                                isAuthenticated = true;
                                this.username = user;
                                out.println("[LOG_SUCCESS]" + user);
                            }
                        }
                    }
                }

                if (!isRunning) return; // Nếu bị ngắt kết nối giữa chừng

                // --- GIAI ĐOẠN ĐÃ VÀO CHAT ---
                clients.put(username, this);
                friendsMap.putIfAbsent(username, new ConcurrentSkipListSet<>());
                
                log("[KẾT NỐI] Người dùng '" + username + "' đã tham gia.");
                broadcastStates();
                sendFriendList(username); 
                broadcast("[SYS] " + username + " đã tham gia phòng trò chuyện.");

                // Vòng lặp lắng nghe các yêu cầu/tin nhắn từ Client
                String msg;
                while (isRunning && (msg = in.readLine()) != null) {
                    if (msg.startsWith("[ALL]")) {
                        broadcast("[ALL]" + username + ": " + msg.substring(5));
                    } 
                    else if (msg.startsWith("[PM]")) {
                        // Cú pháp: [PM]TargetUser:Nội dung
                        String[] parts = msg.substring(4).split(":", 2);
                        if (parts.length == 2) {
                            ClientHandler target = clients.get(parts[0]);
                            if (target != null) {
                                target.sendMessage("[PM]" + username + ": " + parts[1]);
                                this.sendMessage("[PM_ECHO]" + parts[0] + ": " + parts[1]);
                            }
                        }
                    }
                    else if (msg.startsWith("[CREATE_GROUP]")) {
                        String grpName = msg.substring(14).trim();
                        if (!grpName.isEmpty() && chatGroups.add(grpName)) {
                            broadcastStates();
                            broadcast("[SYS] Nhóm mới '" + grpName + "' đã được khởi tạo.");
                        }
                    }
                    else if (msg.startsWith("[GROUP]")) {
                        // Cú pháp: [GROUP]GroupName:Nội dung
                        String[] parts = msg.substring(7).split(":", 2);
                        if (parts.length == 2) {
                            broadcast("[GROUP]" + parts[0] + ":" + username + ":" + parts[1]);
                        }
                    }
                    else if (msg.startsWith("[REQ_FRIEND]")) {
                        String target = msg.substring(12);
                        ClientHandler t = clients.get(target);
                        if (t != null) {
                            t.sendMessage("[REQ_FRIEND]" + username); 
                            log("[YÊU CẦU KẾT BẠN] Từ " + username + " tới " + target);
                        }
                    }
                    else if (msg.startsWith("[ACC_FRIEND]")) {
                        String target = msg.substring(12);
                        friendsMap.get(username).add(target);
                        if(friendsMap.containsKey(target)) friendsMap.get(target).add(username);
                        
                        sendFriendList(username);
                        sendFriendList(target);
                        
                        ClientHandler t = clients.get(target);
                        if(t != null) t.sendMessage("[SYS] Bạn và " + username + " đã trở thành bạn bè!");
                        this.sendMessage("[SYS] Bạn và " + target + " đã trở thành bạn bè!");
                        log("[KẾT BẠN THÀNH CÔNG] " + username + " và " + target);
                    }
                    // Logic xử lý file truyền từ Client lên Server để phân phối
                    else if (msg.startsWith("[FILE]")) {
                        // Cú pháp từ Client gửi lên: [FILE]TargetId:FileName:Base64Data
                        String[] parts = msg.substring(6).split(":", 3);
                        if (parts.length == 3) {
                            String targetId = parts[0]; 
                            String fileName = parts[1];
                            String fileData = parts[2];

                            // Đóng gói lại thành: [FILE]RoomId:SenderName:FileName:Base64Data
                            String forwardMsg = "[FILE]" + targetId + ":" + username + ":" + fileName + ":" + fileData;

                            if (targetId.equals("ALL")) {
                                broadcast(forwardMsg); // Gửi file cho tất cả mọi người
                                log("[FILE] " + username + " đã gửi tệp " + fileName + " vào Chat Chung.");
                            } 
                            else if (targetId.startsWith("GROUP_")) {
                                broadcast(forwardMsg); // Gửi file vào nhóm (Hiện tại nhóm phát Broadcast)
                                log("[FILE] " + username + " đã gửi tệp " + fileName + " vào Nhóm " + targetId.substring(6) + ".");
                            } 
                            else if (targetId.startsWith("PM_")) {
                                String targetUser = targetId.substring(3);
                                ClientHandler t = clients.get(targetUser);
                                if (t != null) {
                                    t.sendMessage(forwardMsg); // Gửi file riêng lẻ
                                    log("[FILE] " + username + " đã gửi tệp " + fileName + " cho " + targetUser + ".");
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                if (isRunning && username != null) log("[NGẮT KẾT NỐI] " + username + " đã rời khỏi máy chủ.");
            } finally {
                disconnect();
            }
        }

        void sendMessage(String msg) { if (out != null) out.println(msg); }
        
        void disconnect() {
            isRunning = false;
            if (username != null && clients.containsKey(username)) {
                clients.remove(username);
                broadcastStates();
                broadcast("[SYS] " + username + " đã rời mạng.");
                username = null;
            }
            try { if (socket != null) socket.close(); } catch (Exception e) {}
        }
    }
 // --- LẤY IP TỰ ĐỘNG ---
    private String getLocalIP() {
        try {
            // Mẹo dùng DatagramSocket trỏ ra Internet để lấy đúng IP LAN đang dùng
            try (final DatagramSocket socket = new DatagramSocket()) {
                socket.connect(InetAddress.getByName("8.8.8.8"), 10002);
                return socket.getLocalAddress().getHostAddress();
            }
        } catch (Exception e) {
            try {
                // Phương án dự phòng
                return InetAddress.getLocalHost().getHostAddress();
            } catch (Exception ex) {
                return "127.0.0.1"; // Nếu mất mạng hoàn toàn
            }
        }
    }
}