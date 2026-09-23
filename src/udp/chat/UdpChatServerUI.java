package udp.chat;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UdpChatServerUI extends JFrame {

    private JTextArea txtLog;
    private JTextField txtPort;
    private JTextField txtServerMessage;
    
    private DefaultListModel<String> modelUsers;
    private JList<String> listUsers;
    
    private DefaultListModel<String> modelGroups;
    private JList<String> listGroups;
    
    // Đổi ServerSocket sang DatagramSocket cho UDP
    private DatagramSocket serverSocket;
    private boolean isRunning = false;
    
    // Giới hạn tối đa 20 luồng xử lý cùng lúc để chống sập Server
    private final ExecutorService workerPool = Executors.newFixedThreadPool(20);
    
    // --- THAY ĐỔI KIẾN TRÚC QUẢN LÝ CLIENT ---
    // UDP không có luồng riêng, ta map Username với địa chỉ (IP + Port) của họ để gửi ngược lại
    private final Map<String, SocketAddress> activeClients = new ConcurrentHashMap<>();
    private final Map<SocketAddress, String> addressToUser = new ConcurrentHashMap<>();
    
    private final Set<String> chatGroups = new ConcurrentSkipListSet<>();
    private final Map<String, Set<String>> friendsMap = new ConcurrentHashMap<>();

    private final Map<String, String> registeredAccounts = new ConcurrentHashMap<>();
    private static final String ACCOUNT_FILE = "accounts.txt";

    public UdpChatServerUI() {
        setTitle("Hệ Thống Quản Trị Máy Chủ UDP");
        setSize(900, 650); 
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        
        setupUI();
        loadAccounts();

        // Dọn dẹp tài nguyên khi tắt Server
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent windowEvent) {
                isRunning = false;
                if (serverSocket != null && !serverSocket.isClosed()) {
                    serverSocket.close();
                }
                workerPool.shutdownNow(); // Tắt thread pool
            }
        });
    }

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

    private void setupUI() {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}
        setLayout(new BorderLayout(10, 10));
        ((JPanel)getContentPane()).setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 5));
        
        topPanel.add(new JLabel("IP Server (Gửi cho Client):"));
        JTextField txtServerIP = new JTextField(getLocalIP(), 12);
        txtServerIP.setEditable(false);
        txtServerIP.setFont(new Font("Segoe UI", Font.BOLD, 14));
        txtServerIP.setForeground(new Color(220, 53, 69)); 
        topPanel.add(txtServerIP);

        topPanel.add(new JLabel("Cổng kết nối UDP:"));
        txtPort = new JTextField("3026", 6);
        txtPort.setFont(new Font("Segoe UI", Font.BOLD, 14));
        
        JButton btnStart = createFlatButton("KHỞI ĐỘNG UDP SERVER", new Color(40, 167, 69));
        JButton btnRefresh = createFlatButton("LÀM MỚI DANH SÁCH", new Color(23, 162, 184));
        
        topPanel.add(txtPort);
        topPanel.add(btnStart);
        topPanel.add(btnRefresh);
        add(topPanel, BorderLayout.NORTH);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(550); 
        splitPane.setDividerSize(5);

        txtLog = new JTextArea();
        txtLog.setEditable(false);
        txtLog.setFont(new Font("Consolas", Font.PLAIN, 14));
        txtLog.setBackground(new Color(250, 250, 250));
        JScrollPane scrollLog = new JScrollPane(txtLog);
        scrollLog.setBorder(BorderFactory.createTitledBorder(null, "Nhật ký hệ thống UDP", TitledBorder.LEFT, TitledBorder.TOP, new Font("Segoe UI", Font.BOLD, 12)));
        splitPane.setLeftComponent(scrollLog);

        JPanel rightPanel = new JPanel(new GridLayout(2, 1, 0, 10)); 

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

        JPanel bottomPanel = new JPanel(new BorderLayout(10, 0));
        bottomPanel.setBorder(new EmptyBorder(10, 0, 0, 0));
        
        txtServerMessage = new JTextField();
        txtServerMessage.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        
        JButton btnSendMsg = createFlatButton("GỬI TIN NHẮN", new Color(0, 123, 255));
        
        bottomPanel.add(txtServerMessage, BorderLayout.CENTER);
        bottomPanel.add(btnSendMsg, BorderLayout.EAST);
        add(bottomPanel, BorderLayout.SOUTH);

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

    // --- LOGIC CHÍNH CỦA UDP SERVER ---
    private void startServer(JButton btn) {
        new Thread(() -> {
            try {
                int port = Integer.parseInt(txtPort.getText());
                serverSocket = new DatagramSocket(port);
                isRunning = true;
                log("[HỆ THỐNG] Máy chủ UDP bắt đầu chạy tại cổng: " + port);
                
                SwingUtilities.invokeLater(() -> {
                    btn.setEnabled(false);
                    btn.setText("MÁY CHỦ ĐANG CHẠY...");
                    btn.setBackground(Color.GRAY);
                });

                // Luồng duy nhất để lắng nghe mọi gói tin đến
                byte[] buffer = new byte[65507]; // Kích thước tối đa của gói UDP
                while (isRunning) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    serverSocket.receive(packet);
                    
                    String message = new String(packet.getData(), 0, packet.getLength(), "UTF-8");
                    SocketAddress senderAddress = packet.getSocketAddress();
                    
                    // Sử dụng Thread Pool thay vì new Thread()
                    workerPool.execute(() -> handleIncomingMessage(message, senderAddress));
                }
            } catch (Exception e) {
                if (isRunning) {
                    log("[LỖI] Không thể khởi động máy chủ: " + e.getMessage());
                }
            }
        }).start();
    }

    // Hàm trung tâm xử lý logic (thay thế cho vòng lặp trong ClientHandler của TCP)
    private void handleIncomingMessage(String msg, SocketAddress senderAddr) {
        String username = addressToUser.get(senderAddr);

        try {
            // 1. GIAI ĐOẠN XÁC THỰC (Chưa cần login)
            if (msg.startsWith("[REGISTER]")) {
                String[] parts = msg.substring(10).split(":", 2);
                if (parts.length == 2) {
                    String user = parts[0];
                    String pass = parts[1];
                    if (registeredAccounts.containsKey(user)) {
                        sendToAddress(senderAddr, "[REG_FAIL]Tên tài khoản đã tồn tại!");
                    } else {
                        saveAccount(user, pass);
                        sendToAddress(senderAddr, "[REG_SUCCESS]Đăng ký thành công!");
                        log("[HỆ THỐNG] Tài khoản mới: " + user);
                    }
                }
                return;
            } 
            else if (msg.startsWith("[LOGIN]")) {
                String[] parts = msg.substring(7).split(":", 2);
                if (parts.length == 2) {
                    String user = parts[0];
                    String pass = parts[1];
                    
                    if (!registeredAccounts.containsKey(user) || !registeredAccounts.get(user).equals(pass)) {
                        sendToAddress(senderAddr, "[LOG_FAIL]Sai tài khoản hoặc mật khẩu!");
                    } else {
                        // SỬA LỖI: Xử lý ghi đè nếu Client bị crash và đăng nhập lại bằng cổng UDP mới
                        if (activeClients.containsKey(user)) {
                            SocketAddress oldAddr = activeClients.get(user);
                            addressToUser.remove(oldAddr); // Xóa rác địa chỉ cũ
                            log("[HỆ THỐNG] Cập nhật phiên kết nối mới cho: " + user);
                        }
                        
                        // Đăng nhập thành công -> Map User với IP/Port
                        activeClients.put(user, senderAddr);
                        addressToUser.put(senderAddr, user);
                        friendsMap.putIfAbsent(user, new ConcurrentSkipListSet<>());
                        
                        sendToAddress(senderAddr, "[LOG_SUCCESS]" + user);
                        log("[KẾT NỐI] Người dùng '" + user + "' đã tham gia (UDP).");
                        
                        broadcastStates();
                        sendFriendList(user);
                        broadcast("[SYS] " + user + " đã tham gia phòng trò chuyện.");
                    }
                }
                return;
            }

            // 2. CÁC LỆNH YÊU CẦU ĐÃ ĐĂNG NHẬP
            if (username == null) return; // Bỏ qua nếu gói tin gửi từ IP chưa đăng nhập

            if (msg.startsWith("[LOGOUT]")) {
                // Client chủ động báo thoát do UDP không tự phát hiện ngắt kết nối
                disconnectUser(username);
            }
            else if (msg.startsWith("[ALL]")) {
                broadcast("[ALL]" + username + ": " + msg.substring(5));
            } 
            else if (msg.startsWith("[PM]")) {
                String[] parts = msg.substring(4).split(":", 2);
                if (parts.length == 2) {
                    SocketAddress targetAddr = activeClients.get(parts[0]);
                    if (targetAddr != null) {
                        sendToAddress(targetAddr, "[PM]" + username + ": " + parts[1]);
                        sendToAddress(senderAddr, "[PM_ECHO]" + parts[0] + ": " + parts[1]);
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
                String[] parts = msg.substring(7).split(":", 2);
                if (parts.length == 2) {
                    broadcast("[GROUP]" + parts[0] + ":" + username + ":" + parts[1]);
                }
            }
            else if (msg.startsWith("[REQ_FRIEND]")) {
                String target = msg.substring(12);
                SocketAddress tAddr = activeClients.get(target);
                if (tAddr != null) {
                    sendToAddress(tAddr, "[REQ_FRIEND]" + username); 
                    log("[YÊU CẦU KẾT BẠN] Từ " + username + " tới " + target);
                }
            }
            else if (msg.startsWith("[ACC_FRIEND]")) {
                String target = msg.substring(12);
                friendsMap.get(username).add(target);
                if(friendsMap.containsKey(target)) friendsMap.get(target).add(username);
                
                sendFriendList(username);
                sendFriendList(target);
                
                SocketAddress tAddr = activeClients.get(target);
                if(tAddr != null) sendToAddress(tAddr, "[SYS] Bạn và " + username + " đã trở thành bạn bè!");
                sendToAddress(senderAddr, "[SYS] Bạn và " + target + " đã trở thành bạn bè!");
                log("[KẾT BẠN THÀNH CÔNG] " + username + " và " + target);
            }
            else if (msg.startsWith("[FILE]")) {
                // LƯU Ý UDP: Base64 làm file to ra. Nếu gói tin > 64KB, Datagram sẽ bị cắt gọt/lỗi.
                String[] parts = msg.substring(6).split(":", 3);
                if (parts.length == 3) {
                    String targetId = parts[0]; 
                    String fileName = parts[1];
                    String fileData = parts[2];
                    String forwardMsg = "[FILE]" + targetId + ":" + username + ":" + fileName + ":" + fileData;

                    if (targetId.equals("ALL")) {
                        broadcast(forwardMsg); 
                        log("[FILE] " + username + " gửi tệp " + fileName + " (Chung).");
                    } 
                    else if (targetId.startsWith("GROUP_")) {
                        broadcast(forwardMsg); 
                        log("[FILE] " + username + " gửi tệp " + fileName + " vào Nhóm " + targetId.substring(6) + ".");
                    } 
                    else if (targetId.startsWith("PM_")) {
                        String targetUser = targetId.substring(3);
                        SocketAddress tAddr = activeClients.get(targetUser);
                        if (tAddr != null) {
                            sendToAddress(tAddr, forwardMsg); 
                            log("[FILE] " + username + " gửi tệp " + fileName + " cho " + targetUser + ".");
                        }
                    }
                }
            }
        } catch (Exception e) {
            log("[LỖI GÓI TIN] Không thể xử lý yêu cầu từ " + (username != null ? username : senderAddr));
        }
    }

    // --- HÀM GỬI DỮ LIỆU UDP TỚI 1 ĐỊA CHỈ ---
    private void sendToAddress(SocketAddress address, String msg) {
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                byte[] data = msg.getBytes("UTF-8");
                DatagramPacket packet = new DatagramPacket(data, data.length, address);
                serverSocket.send(packet);
            } catch (Exception e) {
                // Bỏ qua lỗi gửi tin để tránh rác log
            }
        }
    }

    private void broadcast(String msg) {
        for (SocketAddress addr : activeClients.values()) {
            sendToAddress(addr, msg);
        }
    }

    private void log(String msg) {
        SwingUtilities.invokeLater(() -> {
            txtLog.append(msg + "\n");
            txtLog.setCaretPosition(txtLog.getDocument().getLength());
        });
    }

    private void refreshUI() {
        SwingUtilities.invokeLater(() -> {
            modelUsers.clear();
            for (String u : activeClients.keySet()) modelUsers.addElement(u);
            modelGroups.clear();
            for (String g : chatGroups) modelGroups.addElement(g);
        });
    }

    private void broadcastStates() {
        StringBuilder sbUsers = new StringBuilder("[USERS]");
        for (String u : activeClients.keySet()) sbUsers.append(u).append(",");
        StringBuilder sbGroups = new StringBuilder("[GROUPS]");
        for (String g : chatGroups) sbGroups.append(g).append(",");

        String usersMsg = sbUsers.toString();
        String groupsMsg = sbGroups.toString();
        
        broadcast(usersMsg);
        broadcast(groupsMsg);
        refreshUI(); 
    }

    private void sendFriendList(String username) {
        SocketAddress addr = activeClients.get(username);
        if (addr != null) {
            Set<String> friends = friendsMap.getOrDefault(username, new HashSet<>());
            StringBuilder sb = new StringBuilder("[FRIENDS]");
            for (String f : friends) sb.append(f).append(",");
            sendToAddress(addr, sb.toString());
        }
    }

    private void kickUser(String username) {
        if (username != null) {
            SocketAddress targetAddr = activeClients.get(username);
            if (targetAddr != null) {
                sendToAddress(targetAddr, "[SYS] BẠN ĐÃ BỊ QUẢN TRỊ VIÊN ĐUỔI KHỎI MÁY CHỦ!");
                log("[TRỤC XUẤT] Đã kích người dùng: " + username);
                disconnectUser(username);
            }
        }
    }

    // Hàm xử lý ngắt kết nối thống nhất cho UDP
    private void disconnectUser(String username) {
        SocketAddress addr = activeClients.remove(username);
        if (addr != null) {
            addressToUser.remove(addr);
            broadcastStates();
            broadcast("[SYS] " + username + " đã rời mạng.");
        }
    }

    private void sendServerMessage() {
        String msg = txtServerMessage.getText().trim();
        if (msg.isEmpty()) return;

        String selectedUser = listUsers.getSelectedValue();

        if (selectedUser == null) {
            broadcast("[ADMIN_ALL]" + msg);
            log("Server gửi thông báo chung: " + msg);
        } else {
            SocketAddress targetAddr = activeClients.get(selectedUser);
            if (targetAddr != null) {
                sendToAddress(targetAddr, "[ADMIN_PM]" + msg);
                log("Server gửi tin nhắn riêng tới " + selectedUser + ": " + msg);
            }
        }
        txtServerMessage.setText(""); 
        listUsers.clearSelection(); 
    }

    private String getLocalIP() {
        try {
            try (final DatagramSocket socket = new DatagramSocket()) {
                socket.connect(InetAddress.getByName("8.8.8.8"), 10002);
                return socket.getLocalAddress().getHostAddress();
            }
        } catch (Exception e) {
            try {
                return InetAddress.getLocalHost().getHostAddress();
            } catch (Exception ex) {
                return "127.0.0.1"; 
            }
        }
    }
}