package udp.chat;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class UdpChatClientUI extends JFrame {

    private JTextPane txtInput;
    private JScrollPane scrollChat;
    private DefaultListModel<String> sidebarModel;
    private JList<String> sidebarList;
    private JLabel lblCurrentChat;
    private JPanel mainArea;

    // --- THAY ĐỔI SANG UDP ---
    private DatagramSocket socket;
    private InetAddress serverAddress;
    private final int serverPort = 3026;
    // -------------------------

    private String myUsername;
    private boolean isAuthenticated = false; 
    
    private String currentTargetId = "ALL"; 
    private String currentTargetDisplay = "[ALL] Chat Chung";

    private final Map<String, JPanel> chatRooms = new HashMap<>();
    private final Set<String> myJoinedGroups = new HashSet<>();
    
    private Image backgroundImage = null;
    private final Map<String, ImageIcon> emojiCache = new HashMap<>();

    private String lastUsersRaw = "";
    private String lastGroupsRaw = "";
    private String lastFriendsRaw = "";
    
    public UdpChatClientUI() {
        setTitle("MESSENGER UDP (Bạn Bè & Nhóm)");
        setSize(1050, 700);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}

        loadEmojiCache();
        initUI();
        connectToServer();
    }

    private void loadEmojiCache() {
        String[][] emojiData = {
            {":-)", "/icons/happy.png"}, {":-D", "/icons/lol.png"}, {":-(", "/icons/sad.png"},
            {":-P", "/icons/tongue-out.png"}, {"<3", "/icons/heart.png"}, {"(y)", "/icons/like.png"},
            {"T_T", "/icons/cry.png"}, {"8-)", "/icons/smiling-with-sunglasses.png"},
            {">_<", "/icons/pouting-face.png"}, {"^_^", "/icons/smiling.png"}
        };

        for (String[] data : emojiData) {
            try {
                java.net.URL imgURL = getClass().getResource(data[1]);
                if (imgURL != null) {
                    Image img = javax.imageio.ImageIO.read(imgURL);
                    Image scaledImg = img.getScaledInstance(24, 24, Image.SCALE_SMOOTH);
                    ImageIcon scaledIcon = new ImageIcon(scaledImg);
                    scaledIcon.setDescription(data[0]); 
                    emojiCache.put(data[0], scaledIcon);
                }
            } catch (Exception e) {
                System.out.println("Lỗi đọc ảnh: " + data[0] + " | " + e.getMessage());
            }
        }
    }

    private void initUI() {
        setLayout(new BorderLayout());

        // --- SIDEBAR ---
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setPreferredSize(new Dimension(280, 0));
        sidebar.setBackground(new Color(245, 246, 250));
        sidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Color.LIGHT_GRAY));

        JPanel headerSidebar = new JPanel(new BorderLayout(5, 5));
        headerSidebar.setBorder(new EmptyBorder(15, 15, 15, 15));
        headerSidebar.setBackground(new Color(245, 246, 250));
        JLabel lblTitle = new JLabel("Danh bạ");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 18));
        
        JButton btnCreateGroup = new JButton("+ TẠO NHÓM");
        btnCreateGroup.setBackground(new Color(52, 152, 219)); 
        btnCreateGroup.setForeground(Color.WHITE);
        btnCreateGroup.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnCreateGroup.setFocusPainted(false);
        btnCreateGroup.setOpaque(true);          
        btnCreateGroup.setBorderPainted(false);  
        btnCreateGroup.setCursor(new Cursor(Cursor.HAND_CURSOR));
        
        headerSidebar.add(lblTitle, BorderLayout.WEST);
        headerSidebar.add(btnCreateGroup, BorderLayout.EAST);
        
        JLabel lblHint = new JLabel("(Chuột phải vào Nhóm/Khách để Tương tác)");
        lblHint.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        lblHint.setForeground(Color.GRAY);
        JPanel pnlNorth = new JPanel(new BorderLayout());
        pnlNorth.setBackground(new Color(245, 246, 250));
        pnlNorth.add(headerSidebar, BorderLayout.NORTH);
        pnlNorth.add(lblHint, BorderLayout.CENTER);
        lblHint.setBorder(new EmptyBorder(0, 15, 10, 0));
        sidebar.add(pnlNorth, BorderLayout.NORTH);

        sidebarModel = new DefaultListModel<>();
        sidebarList = new JList<>(sidebarModel);
        sidebarList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        sidebarList.setFixedCellHeight(35);
        sidebarList.setFont(new Font("Segoe UI", Font.BOLD, 14));
        sidebarList.setBackground(new Color(245, 246, 250));
        
        sidebar.add(new JScrollPane(sidebarList), BorderLayout.CENTER);
        add(sidebar, BorderLayout.WEST);

        // --- KHU VỰC CHAT ---
        mainArea = new JPanel(new BorderLayout());
        mainArea.setBackground(Color.WHITE);

        JPanel chatHeader = new JPanel(new BorderLayout());
        chatHeader.setBackground(Color.WHITE);
        chatHeader.setBorder(new EmptyBorder(10, 15, 10, 15));
        
        lblCurrentChat = new JLabel(currentTargetDisplay);
        lblCurrentChat.setFont(new Font("Segoe UI", Font.BOLD, 18));
        chatHeader.add(lblCurrentChat, BorderLayout.WEST);
        
        JButton btnChangeBg = new JButton("ĐỔI HÌNH NỀN");
        btnChangeBg.setBackground(new Color(241, 196, 15));
        btnChangeBg.setFocusPainted(false);
        btnChangeBg.setBorderPainted(false);
        btnChangeBg.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnChangeBg.addActionListener(e -> changeBackground());
        chatHeader.add(btnChangeBg, BorderLayout.EAST);
        
        JPanel headerBorderPanel = new JPanel(new BorderLayout());
        headerBorderPanel.add(chatHeader, BorderLayout.CENTER);
        headerBorderPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(220, 220, 220)));
        mainArea.add(headerBorderPanel, BorderLayout.NORTH);

        scrollChat = new JScrollPane();
        scrollChat.setBorder(null);
        scrollChat.getVerticalScrollBar().setUnitIncrement(20);
        scrollChat.setOpaque(false);
        scrollChat.getViewport().setOpaque(false);
        
        switchToRoom("ALL"); 
        mainArea.add(scrollChat, BorderLayout.CENTER);

        // --- KHUNG NHẬP LIỆU ---
        JPanel bottomPanel = new JPanel(new BorderLayout(10, 10));
        bottomPanel.setBackground(Color.WHITE);
        bottomPanel.setBorder(new EmptyBorder(15, 15, 15, 15));

        txtInput = new JTextPane();
        txtInput.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        txtInput.setBorder(new EmptyBorder(5, 10, 5, 10));
        
        JScrollPane scrollInput = new JScrollPane(txtInput);
        scrollInput.setPreferredSize(new Dimension(0, 55));

        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actionPanel.setBackground(Color.WHITE);
        
        JButton btnEmoji = new JButton(":-)");
        btnEmoji.setFont(new Font("Segoe UI", Font.BOLD, 16));
        btnEmoji.setContentAreaFilled(false);
        btnEmoji.setBorderPainted(false);
        btnEmoji.setCursor(new Cursor(Cursor.HAND_CURSOR));

        JButton btnSendFile = new JButton("ĐÍNH KÈM");
        btnSendFile.setBackground(new Color(108, 117, 125)); 
        btnSendFile.setForeground(Color.WHITE);
        btnSendFile.setFocusPainted(false);
        btnSendFile.setOpaque(true);          
        btnSendFile.setBorderPainted(false);  

        JButton btnSend = new JButton("GỬI (ENTER)");
        btnSend.setBackground(new Color(0, 123, 255)); 
        btnSend.setForeground(Color.WHITE);
        btnSend.setFocusPainted(false);
        btnSend.setOpaque(true);          
        btnSend.setBorderPainted(false);  

        actionPanel.add(btnEmoji);
        actionPanel.add(btnSendFile);
        actionPanel.add(btnSend);

        bottomPanel.add(scrollInput, BorderLayout.CENTER);
        bottomPanel.add(actionPanel, BorderLayout.EAST);
        mainArea.add(bottomPanel, BorderLayout.SOUTH);

        add(mainArea, BorderLayout.CENTER);

        // --- CÁC SỰ KIỆN NÚT BẤM ---
        btnCreateGroup.addActionListener(e -> {
            String grpName = JOptionPane.showInputDialog(this, "Nhập tên nhóm muốn tạo:");
            if (grpName != null && !grpName.trim().isEmpty()) {
                myJoinedGroups.add(grpName.trim()); 
                sendToServer("[CREATE_GROUP]" + grpName.trim());
            }
        });

        sidebarList.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) { handleContextMenu(e); }
            public void mouseReleased(MouseEvent e) { handleContextMenu(e); }
            
            private void handleContextMenu(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    int row = sidebarList.locationToIndex(e.getPoint());
                    sidebarList.setSelectedIndex(row);
                    String val = sidebarList.getSelectedValue();
                    
                    JPopupMenu popup = new JPopupMenu();
                    boolean showPopup = false;

                    if (val != null) {
                        if (val.startsWith("    [Khách] ")) {
                            String target = val.substring(12);
                            JMenuItem itemAdd = new JMenuItem("Gửi lời mời kết bạn tới " + target);
                            itemAdd.addActionListener(ev -> sendToServer("[REQ_FRIEND]" + target));
                            popup.add(itemAdd);
                            showPopup = true;
                        } 
                        else if (val.startsWith("    [Khám phá] ")) {
                            String target = val.substring(15);
                            JMenuItem itemJoin = new JMenuItem("Tham gia nhóm: " + target);
                            itemJoin.addActionListener(ev -> {
                                myJoinedGroups.add(target);
                                updateSidebar(null, null, null); // Cần logic đồng bộ từ server, giả lập ở đây
                                addSystemBubble("ALL", "Bạn đã tham gia nhóm: " + target);
                            });
                            popup.add(itemJoin);
                            showPopup = true;
                        } 
                        else if (val.startsWith("    [Nhóm] ")) {
                            String target = val.substring(11);
                            JMenuItem itemLeave = new JMenuItem("Rời khỏi nhóm: " + target);
                            itemLeave.addActionListener(ev -> {
                                myJoinedGroups.remove(target);
                                if (currentTargetId.equals("GROUP_" + target)) {
                                    currentTargetId = "ALL";
                                    currentTargetDisplay = "[ALL] Chat Chung";
                                    lblCurrentChat.setText(currentTargetDisplay);
                                    switchToRoom("ALL");
                                }
                                updateSidebar(null, null, null);
                                addSystemBubble("ALL", "Bạn đã rời khỏi nhóm: " + target);
                            });
                            popup.add(itemLeave);
                            showPopup = true;
                        }
                    }
                    if (showPopup) popup.show(e.getComponent(), e.getX(), e.getY());
                }
            }

            public void mouseClicked(MouseEvent evt) {
                if (SwingUtilities.isLeftMouseButton(evt) && evt.getClickCount() == 1) {
                    String selected = sidebarList.getSelectedValue();
                    if (selected == null || selected.startsWith("---")) return; 
                    
                    if (selected.contains("Chat Chung")) {
                        currentTargetId = "ALL";
                        currentTargetDisplay = "[ALL] Chat Chung";
                    } 
                    else if (selected.startsWith("    [Khám phá] ")) {
                        JOptionPane.showMessageDialog(UdpChatClientUI.this, "Bạn cần chuột phải và chọn 'Tham gia nhóm' trước!");
                        return;
                    }
                    else if (selected.startsWith("    [Nhóm] ")) {
                        currentTargetId = "GROUP_" + selected.substring(11);
                        currentTargetDisplay = "[Nhóm] " + selected.substring(11);
                    } else if (selected.startsWith("    [Bạn] ")) {
                        currentTargetId = "PM_" + selected.substring(10);
                        currentTargetDisplay = "[Bạn Bè] " + selected.substring(10);
                    } else if (selected.startsWith("    [Khách] ")) {
                        currentTargetId = "PM_" + selected.substring(12);
                        currentTargetDisplay = "[Khách] " + selected.substring(12);
                    }
                    lblCurrentChat.setText(currentTargetDisplay);
                    switchToRoom(currentTargetId);
                }
            }
        });

        btnEmoji.addActionListener(e -> {
            JPopupMenu dynamicPopup = createEmojiMenu();
            dynamicPopup.show(btnEmoji, 0, -dynamicPopup.getPreferredSize().height);
        });

        btnSend.addActionListener(e -> sendMessage());
        btnSendFile.addActionListener(e -> sendFileBase64()); 
        
        txtInput.addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    if (e.isShiftDown()) {
                        try { txtInput.getDocument().insertString(txtInput.getCaretPosition(), "\n", null); } catch (Exception ex) {}
                    } else { 
                        e.consume(); 
                        sendMessage(); 
                    }
                }
            }
        });
    }

    // --- HÀM GỬI DỮ LIỆU SANG UDP ---
    private void sendToServer(String msg) {
        try {
            if (socket != null && serverAddress != null) {
                byte[] sendData = msg.getBytes(StandardCharsets.UTF_8);
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, serverPort);
                socket.send(sendPacket);
            }
        } catch (IOException e) {
            System.err.println("Lỗi gửi dữ liệu: " + e.getMessage());
        }
    }
    // --------------------------------

    private String getMixedInputText() {
        StringBuilder sb = new StringBuilder();
        javax.swing.text.StyledDocument doc = txtInput.getStyledDocument();
        for (int i = 0; i < doc.getLength(); i++) {
            javax.swing.text.Element el = doc.getCharacterElement(i);
            javax.swing.text.AttributeSet attrs = el.getAttributes();
            
            if (javax.swing.text.StyleConstants.getIcon(attrs) != null) {
                Icon icon = javax.swing.text.StyleConstants.getIcon(attrs);
                if (icon instanceof ImageIcon && ((ImageIcon) icon).getDescription() != null) {
                    sb.append(((ImageIcon) icon).getDescription()); 
                }
            } else {
                try { sb.append(doc.getText(i, 1)); } catch (Exception ignored) {}
            }
        }
        return sb.toString();
    }

    private void changeBackground() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new FileNameExtensionFilter("Image Files", "jpg", "png", "jpeg"));
        int res = fileChooser.showOpenDialog(this);
        if (res == JFileChooser.APPROVE_OPTION) {
            try {
                backgroundImage = new ImageIcon(fileChooser.getSelectedFile().getAbsolutePath()).getImage();
                for (JPanel p : chatRooms.values()) p.repaint();
                scrollChat.repaint();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Không thể tải ảnh!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    class RoomPanel extends JPanel {
        public RoomPanel() { setOpaque(false); }
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (backgroundImage != null) {
                g.drawImage(backgroundImage, 0, 0, getWidth(), getHeight(), this);
            } else {
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        }
    }

    private JPanel getRoomPanel(String roomId) {
        if (!chatRooms.containsKey(roomId)) {
            RoomPanel p = new RoomPanel();
            p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
            p.setBorder(new EmptyBorder(10, 10, 10, 10));
            chatRooms.put(roomId, p);
        }
        return chatRooms.get(roomId);
    }

    private void switchToRoom(String roomId) {
        JPanel room = getRoomPanel(roomId);
        scrollChat.setViewportView(room);
        scrollToBottom();
    }
    
    private void scrollToBottom() {
        SwingUtilities.invokeLater(() -> {
            JScrollBar v = scrollChat.getVerticalScrollBar();
            v.setValue(v.getMaximum());
        });
    }

    private JPopupMenu createEmojiMenu() {
        JPopupMenu menu = new JPopupMenu();
        JPanel panel = new JPanel(new GridLayout(2, 5, 5, 5));
        panel.setBackground(Color.WHITE);
        String[] emojis = {":-)", ":-D", ":-(", ":-P", "<3", "(y)", "T_T", "8-)", ">_<", "^_^"};
        
        for (String em : emojis) {
            JButton b = new JButton();
            ImageIcon cachedIcon = emojiCache.get(em);
            if(cachedIcon != null) b.setIcon(cachedIcon);
            else b.setText(em);
            
            b.setFont(new Font("Segoe UI", Font.BOLD, 14));
            b.setBorderPainted(false);
            b.setContentAreaFilled(false);
            b.setCursor(new Cursor(Cursor.HAND_CURSOR));
            
            b.addActionListener(e -> { 
                ImageIcon iconToInsert = emojiCache.get(em);
                if (iconToInsert != null) txtInput.insertIcon(iconToInsert);
                else {
                    try { txtInput.getDocument().insertString(txtInput.getCaretPosition(), em, null); } catch (Exception ex) {}
                }
                menu.setVisible(false); 
                txtInput.requestFocus(); 
            });
            panel.add(b);
        }
        menu.add(panel);
        return menu;
    }

    private void connectToServer() {
        try {
            JPanel panelIP = new JPanel(new BorderLayout(0, 10));
            JLabel lblInstruction = new JLabel("<html><b style='font-size:14px; font-family: Segoe UI;'>Nhập IP Server:</b></html>");
            JTextField txtIP = new JTextField("");
            txtIP.setFont(new Font("Segoe UI", Font.BOLD, 18));
            txtIP.setHorizontalAlignment(JTextField.CENTER);
            
            panelIP.add(lblInstruction, BorderLayout.NORTH);
            panelIP.add(txtIP, BorderLayout.CENTER);

            int result = JOptionPane.showConfirmDialog(this, panelIP, "Kết nối (UDP)", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (result != JOptionPane.OK_OPTION || txtIP.getText().trim().isEmpty()) System.exit(0); 
            
            String serverIP = txtIP.getText().trim();
            
            // Khởi tạo UDP Socket
            serverAddress = InetAddress.getByName(serverIP);
            socket = new DatagramSocket(); // Bind port tự động cho Client

            showAuthDialog();

            if (!isAuthenticated) System.exit(0);

            setTitle("VOZ (UDP) - Đang đăng nhập dưới tên: " + myUsername);
            addSystemBubble("ALL", "Đã kết nối UDP. Chào mừng " + myUsername + "!");

            new Thread(this::receiveMessages).start();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Không thể kết nối đến Máy chủ Chat (UDP)!");
            System.exit(0);
        }
    }

    private void showAuthDialog() {
        JDialog authDialog = new JDialog(this, "Hệ thống Đăng nhập", true);
        authDialog.setSize(420, 260); 
        authDialog.setLocationRelativeTo(this);
        authDialog.setLayout(new BorderLayout());

        JPanel pnlCenter = new JPanel(new GridLayout(2, 1, 10, 15));
        pnlCenter.setBorder(new EmptyBorder(25, 30, 15, 30)); 
        
        JPanel pnlUser = new JPanel(new BorderLayout(15, 0));
        pnlUser.add(new JLabel("Tài khoản:"), BorderLayout.WEST);
        JTextField txtUser = new JTextField();
        pnlUser.add(txtUser, BorderLayout.CENTER);

        JPanel pnlPass = new JPanel(new BorderLayout(15, 0));
        pnlPass.add(new JLabel("Mật khẩu:"), BorderLayout.WEST);
        JPasswordField txtPass = new JPasswordField();
        pnlPass.add(txtPass, BorderLayout.CENTER);

        pnlCenter.add(pnlUser);
        pnlCenter.add(pnlPass);

        JPanel pnlBottom = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 15));
        JButton btnLogin = new JButton("ĐĂNG NHẬP");
        JButton btnReg = new JButton("ĐĂNG KÝ");
        pnlBottom.add(btnLogin);
        pnlBottom.add(btnReg);

        authDialog.add(new JLabel("VUI LÒNG ĐĂNG NHẬP / ĐĂNG KÝ (UDP)", SwingConstants.CENTER), BorderLayout.NORTH);
        authDialog.add(pnlCenter, BorderLayout.CENTER);
        authDialog.add(pnlBottom, BorderLayout.SOUTH);

        // --- Logic đợi phản hồi UDP đồng bộ cho form Đăng nhập ---
        ActionListener authAction = e -> {
            boolean isLogin = e.getSource() == btnLogin;
            String u = txtUser.getText().trim();
            String p = new String(txtPass.getPassword()).trim();
            if (u.isEmpty() || p.isEmpty()) return;
            
            String prefix = isLogin ? "[LOGIN]" : "[REGISTER]";
            sendToServer(prefix + u + ":" + p);
            
            try {
                // Đợi nhận gói tin xác thực trả về
                socket.setSoTimeout(3000); // Timeout 3s tránh treo
                byte[] receiveData = new byte[1024];
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                socket.receive(receivePacket);
                socket.setSoTimeout(0); // Trả lại block vô hạn cho chat
                
                String res = new String(receivePacket.getData(), 0, receivePacket.getLength(), StandardCharsets.UTF_8);
                
                if (isLogin) {
                    if (res.startsWith("[LOG_SUCCESS]")) {
                        myUsername = u;
                        isAuthenticated = true;
                        authDialog.dispose();
                    } else {
                        JOptionPane.showMessageDialog(authDialog, res.substring(10), "Lỗi", JOptionPane.ERROR_MESSAGE);
                    }
                } else {
                    if (res.startsWith("[REG_SUCCESS]")) {
                        JOptionPane.showMessageDialog(authDialog, "Đăng ký thành công!", "Thành công", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(authDialog, res.substring(10), "Lỗi", JOptionPane.ERROR_MESSAGE);
                    }
                }
            } catch (SocketTimeoutException ex) {
                JOptionPane.showMessageDialog(authDialog, "Máy chủ không phản hồi (Timeout).", "Lỗi mạng", JOptionPane.ERROR_MESSAGE);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        };

        btnLogin.addActionListener(authAction);
        btnReg.addActionListener(authAction);

        authDialog.setVisible(true);
    }

    private void sendMessage() {
        String text = getMixedInputText().trim();
        if (text.isEmpty()) return;

        if (currentTargetId.equals("ALL")) {
            sendToServer("[ALL]" + text);
            addChatBubble("ALL", "Bạn (Chung)", text, true);
        } 
        else if (currentTargetId.startsWith("GROUP_")) {
            String grpName = currentTargetId.substring(6);
            sendToServer("[GROUP]" + grpName + ":" + text);
        }
        else if (currentTargetId.startsWith("PM_")) {
            String targetUser = currentTargetId.substring(3);
            sendToServer("[PM]" + targetUser + ":" + text);
        }
        
        txtInput.setText("");
        txtInput.requestFocus();
    }

    private void sendFileBase64() {
        JFileChooser fileChooser = new JFileChooser();
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            
            // --- UDP CONSTRAINT: Giới hạn file xuống ~45KB ---
            // MTU của Datagram thông thường giới hạn dưới 64KB để không bị mất gói (fragmentation loss).
            // Nếu gửi 5MB bằng DatagramPacket, nó sẽ báo lỗi Message Too Long.
            if (selectedFile.length() > 45 * 1024) {
                JOptionPane.showMessageDialog(this, "UDP không hỗ trợ file > 45KB trong 1 gói tin!\nHãy thu nhỏ file hoặc dùng TCP.", "Lỗi file", JOptionPane.ERROR_MESSAGE);
                return;
            }

            try {
                byte[] fileContent = Files.readAllBytes(selectedFile.toPath());
                String encodedString = Base64.getEncoder().encodeToString(fileContent);
                String fileName = selectedFile.getName();

                sendToServer("[FILE]" + currentTargetId + ":" + fileName + ":" + encodedString);
                addSystemBubble(currentTargetId, "Bạn đã gửi tệp: " + fileName);

            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Lỗi khi đọc file!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // --- VÒNG LẶP NHẬN MESSAGE UDP ---
    private void receiveMessages() {
        byte[] receiveData = new byte[65535]; // Max buffer an toàn cho DatagramPacket
        try {
            while (true) {
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                socket.receive(receivePacket);
                String msg = new String(receivePacket.getData(), 0, receivePacket.getLength(), StandardCharsets.UTF_8);
                
                SwingUtilities.invokeLater(() -> processMessage(msg));
            }
        } catch (Exception e) {
            if(isAuthenticated) addSystemBubble("ALL", "Mất kết nối với Máy chủ Chat UDP.");
        }
    }

    private void processMessage(String msg) {
        if (msg.startsWith("[USERS]")) {
            updateSidebar(msg.substring(7), null, null); 
        } else if (msg.startsWith("[GROUPS]")) {
            updateSidebar(null, msg.substring(8), null);
        } else if (msg.startsWith("[FRIENDS]")) {
            // Server gửi danh sách bạn bè: [FRIENDS]userA,userB
            updateSidebar(null, null, msg.substring(9));
        } else if (msg.startsWith("[REQ_FRIEND]")) {
            // Có người gửi lời mời kết bạn
            String sender = msg.substring(12).trim();
            int opt = JOptionPane.showConfirmDialog(this, 
                sender + " đã gửi lời mời kết bạn!\nBạn có muốn đồng ý không?", 
                "Yêu cầu kết bạn", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
                
            if (opt == JOptionPane.YES_OPTION) {
                sendToServer("[ACC_FRIEND]" + sender); // Gửi phản hồi đồng ý lên Server
                addSystemBubble("ALL", "Bạn và " + sender + " đã trở thành bạn bè!");
            }
        } else if (msg.startsWith("[ALL]")) {
            String[] parts = msg.substring(5).split(": ", 2);
            if (!parts[0].equals(myUsername)) addChatBubble("ALL", parts[0], parts[1], false);
        } 
        else if (msg.startsWith("[GROUP]")) {
            String[] parts = msg.substring(7).split(":", 3);
            String grpName = parts[0];
            if (!myJoinedGroups.contains(grpName)) return; 
            
            String roomId = "GROUP_" + grpName;
            boolean isMe = parts[1].equals(myUsername);
            addChatBubble(roomId, isMe ? "Bạn" : parts[1], parts[2], isMe);
        } 
        else if (msg.startsWith("[PM]")) {
            String[] parts = msg.substring(4).split(": ", 2);
            addChatBubble("PM_" + parts[0], parts[0], parts[1], false);
        } else if (msg.startsWith("[PM_ECHO]")) {
            String[] parts = msg.substring(9).split(": ", 2);
            addChatBubble("PM_" + parts[0], "Bạn", parts[1], true);
        } else if (msg.startsWith("[SYS]")) {
            addSystemBubble(currentTargetId, msg.substring(5)); 
        } 
        else if (msg.startsWith("[FILE]")) {
            String[] parts = msg.substring(6).split(":", 4);
            if(parts.length == 4) {
                String roomId = parts[0];
                String sender = parts[1];
                String fileName = parts[2];
                String fileData = parts[3];

                if (!sender.equals(myUsername)) {
                    int opt = JOptionPane.showConfirmDialog(this, 
                        sender + " đã gửi tệp: " + fileName + "\nBạn có muốn tải xuống không?", 
                        "Nhận Tệp Đính Kèm", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);
                        
                    if (opt == JOptionPane.YES_OPTION) {
                        saveReceivedFile(fileName, fileData);
                        addSystemBubble(roomId, "Bạn đã nhận tệp: " + fileName + " từ " + sender);
                    }
                }
            }
        }
    }

    // --- CÁC HÀM BỊ CẮT NGANG TỪ CODE CŨ (Đã hoàn thiện) ---
    private void saveReceivedFile(String fileName, String base64Data) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setSelectedFile(new File(fileName));
        int userSelection = fileChooser.showSaveDialog(this);
        
        if (userSelection == JFileChooser.APPROVE_OPTION) {
            try {
                File fileToSave = fileChooser.getSelectedFile();
                byte[] decodedBytes = Base64.getDecoder().decode(base64Data);
                Files.write(fileToSave.toPath(), decodedBytes);
                JOptionPane.showMessageDialog(this, "Lưu file thành công!");
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "Lỗi khi lưu tệp!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    private void addChatBubble(String roomId, String sender, String message, boolean isMe) {
        JPanel room = getRoomPanel(roomId);
        JLabel lblMsg = new JLabel("<html><b>" + sender + "</b>: " + message + "</html>");
        lblMsg.setOpaque(true);
        lblMsg.setBackground(isMe ? new Color(173, 216, 230) : new Color(240, 240, 240));
        lblMsg.setBorder(new EmptyBorder(5, 10, 5, 10));
        
        JPanel row = new JPanel(new FlowLayout(isMe ? FlowLayout.RIGHT : FlowLayout.LEFT));
        row.setOpaque(false);
        row.add(lblMsg);
        
        room.add(row);
        room.revalidate();
        scrollToBottom();
    }
    
    private void addSystemBubble(String roomId, String message) {
        JPanel room = getRoomPanel(roomId);
        JLabel lblMsg = new JLabel("<html><i>" + message + "</i></html>");
        lblMsg.setForeground(Color.GRAY);
        
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER));
        row.setOpaque(false);
        row.add(lblMsg);
        
        room.add(row);
        room.revalidate();
        scrollToBottom();
    }
    
    private void updateSidebar(String usersRaw, String groupsRaw, String friendsRaw) {
        // Cập nhật lại chuỗi trạng thái nếu có dữ liệu mới
        if (usersRaw != null) lastUsersRaw = usersRaw;
        if (groupsRaw != null) lastGroupsRaw = groupsRaw;
        if (friendsRaw != null) lastFriendsRaw = friendsRaw;

        sidebarModel.clear();
        sidebarModel.addElement("--- CHUNG ---");
        sidebarModel.addElement("    [ALL] Chat Chung");
        
        // --- NHÓM ---
        sidebarModel.addElement("--- NHÓM ---");
        if (!lastGroupsRaw.isEmpty()) {
            for (String g : lastGroupsRaw.split(",")) {
                if (g.trim().isEmpty()) continue;
                if (myJoinedGroups.contains(g.trim())) {
                    sidebarModel.addElement("    [Nhóm] " + g.trim());
                } else {
                    sidebarModel.addElement("    [Khám phá] " + g.trim());
                }
            }
        }

        // --- BẠN BÈ ---
        sidebarModel.addElement("--- BẠN BÈ ---");
        Set<String> friendSet = new HashSet<>();
        if (!lastFriendsRaw.isEmpty()) {
            for (String f : lastFriendsRaw.split(",")) {
                if (f.trim().isEmpty()) continue;
                friendSet.add(f.trim());
                sidebarModel.addElement("    [Bạn] " + f.trim());
            }
        }

        // --- KHÁCH ---
        sidebarModel.addElement("--- KHÁCH (ONLINE) ---");
        if (!lastUsersRaw.isEmpty()) {
            for (String u : lastUsersRaw.split(",")) {
                u = u.trim();
                // Không hiển thị chính mình và những người đã là bạn bè trong mục Khách
                if (u.isEmpty() || u.equals(myUsername) || friendSet.contains(u)) continue;
                sidebarModel.addElement("    [Khách] " + u);
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new UdpChatClientUI().setVisible(true));
    }
}