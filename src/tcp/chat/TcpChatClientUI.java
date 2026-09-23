package tcp.chat;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class TcpChatClientUI extends JFrame {
	// Các thành phần giao diện
    private JTextPane txtInput;
    private JScrollPane scrollChat;
    private DefaultListModel<String> sidebarModel;
    private JList<String> sidebarList;
    private JLabel lblCurrentChat;
    private JPanel mainArea;
    
    // Biến mạng và luồng dữ liệu
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private String myUsername;
    private boolean isAuthenticated = false; 
    
    // Quản lý trạng thái chat
    private String currentTargetId = "ALL"; 
    private String currentTargetDisplay = "[ALL] Chat Chung";

    // Lưu trữ các phòng chat để chuyển qua lại không bị mất tin nhắn
    private final Map<String, JPanel> chatRooms = new HashMap<>();
    private final Set<String> myJoinedGroups = new HashSet<>();
    
    // --- Biến lưu trữ hình nền & Emoji ---
    private Image backgroundImage = null;
    private final Map<String, ImageIcon> emojiCache = new HashMap<>();

    // Hàm khởi tạo
    public TcpChatClientUI() {
        setTitle("MESSENGER (Bạn Bè & Nhóm)");
        setSize(1050, 700);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}

        loadEmojiCache(); // Tải sẵn Emoji từ URL vào RAM để không bị lag
        initUI();
        connectToServer();
    }

    // --- HÀM TẢI TRƯỚC EMOJI TỪ URL ---
    private void loadEmojiCache() {
        String[][] emojiData = {
            {":-)", "/icons/happy.png"},
            {":-D", "/icons/lol.png"},
            {":-(", "/icons/sad.png"},
            {":-P", "/icons/tongue-out.png"},
            {"<3", "/icons/heart.png"}, 
            {"(y)", "/icons/like.png"},
            {"T_T", "/icons/cry.png"},
            {"8-)", "/icons/smiling-with-sunglasses.png"},
            {">_<", "/icons/pouting-face.png"},
            {"^_^", "/icons/smiling.png"}
        };

        for (String[] data : emojiData) {
            try {
                // Đọc file từ package 'icons' trong thư mục 'src'
                java.net.URL imgURL = getClass().getResource(data[1]);
                
                if (imgURL != null) { // Nếu tìm thấy file ảnh
                    Image img = javax.imageio.ImageIO.read(imgURL);
                    Image scaledImg = img.getScaledInstance(24, 24, Image.SCALE_SMOOTH);
                    ImageIcon scaledIcon = new ImageIcon(scaledImg);
                    scaledIcon.setDescription(data[0]); 
                    emojiCache.put(data[0], scaledIcon);
                } else {
                    System.out.println("Lỗi: Không tìm thấy file " + data[1]);
                }
            } catch (Exception e) {
                System.out.println("Lỗi đọc ảnh: " + data[0] + " | " + e.getMessage());
            }
        }
    }
    
    /**
     * HÀM KHỞI TẠO VÀ SẮP XẾP GIAO DIỆN CHÍNH
     * Phân chia màn hình làm 2 phần: Sidebar (Trái) và Khu vực Chat (Phải/Giữa).
     */
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
        btnChangeBg.setForeground(Color.BLACK);
        btnChangeBg.setFont(new Font("Segoe UI", Font.BOLD, 12));
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
        scrollInput.setBorder(new RoundedBorder(15)); 

        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actionPanel.setBackground(Color.WHITE);
        
        JButton btnEmoji = new JButton(":-)");
        btnEmoji.setFont(new Font("Segoe UI", Font.BOLD, 16));
        btnEmoji.setForeground(new Color(100, 100, 100));
        btnEmoji.setFocusPainted(false);
        btnEmoji.setContentAreaFilled(false);
        btnEmoji.setBorderPainted(false);
        btnEmoji.setCursor(new Cursor(Cursor.HAND_CURSOR));

        JButton btnSendFile = new JButton("ĐÍNH KÈM");
        btnSendFile.setBackground(new Color(108, 117, 125)); 
        btnSendFile.setForeground(Color.WHITE);
        btnSendFile.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btnSendFile.setFocusPainted(false);
        btnSendFile.setOpaque(true);          
        btnSendFile.setBorderPainted(false);  
        btnSendFile.setCursor(new Cursor(Cursor.HAND_CURSOR));

        JButton btnSend = new JButton("GỬI (ENTER)");
        btnSend.setBackground(new Color(0, 123, 255)); 
        btnSend.setForeground(Color.WHITE);
        btnSend.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btnSend.setFocusPainted(false);
        btnSend.setOpaque(true);          
        btnSend.setBorderPainted(false);  
        btnSend.setCursor(new Cursor(Cursor.HAND_CURSOR));

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
                out.println("[CREATE_GROUP]" + grpName.trim());
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
                            itemAdd.addActionListener(ev -> out.println("[REQ_FRIEND]" + target));
                            popup.add(itemAdd);
                            showPopup = true;
                        } 
                        else if (val.startsWith("    [Khám phá] ")) {
                            String target = val.substring(15);
                            JMenuItem itemJoin = new JMenuItem("Tham gia nhóm: " + target);
                            itemJoin.addActionListener(ev -> {
                                myJoinedGroups.add(target);
                                updateSidebar(null, null, null);
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
                        JOptionPane.showMessageDialog(TcpChatClientUI.this, "Bạn cần chuột phải và chọn 'Tham gia nhóm' trước khi xem/nhắn tin!");
                        return;
                    }
                    else if (selected.startsWith("    [Nhóm] ")) {
                        String grpName = selected.substring(11);
                        currentTargetId = "GROUP_" + grpName;
                        currentTargetDisplay = "[Nhóm] " + grpName;
                    } else if (selected.startsWith("    [Bạn] ")) {
                        String usrName = selected.substring(10);
                        currentTargetId = "PM_" + usrName;
                        currentTargetDisplay = "[Bạn Bè] " + usrName;
                    } else if (selected.startsWith("    [Khách] ")) {
                        String usrName = selected.substring(12);
                        currentTargetId = "PM_" + usrName;
                        currentTargetDisplay = "[Khách] " + usrName;
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
                try {
                    sb.append(doc.getText(i, 1));
                } catch (Exception ignored) {}
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
                for (JPanel p : chatRooms.values()) {
                    p.repaint();
                }
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
            if(cachedIcon != null) {
                b.setIcon(cachedIcon);
            } else {
                b.setText(em);
            }
            
            b.setFont(new Font("Segoe UI", Font.BOLD, 14));
            b.setBorderPainted(false);
            b.setContentAreaFilled(false);
            b.setCursor(new Cursor(Cursor.HAND_CURSOR));
            
            b.addActionListener(e -> { 
                ImageIcon iconToInsert = emojiCache.get(em);
                if (iconToInsert != null) {
                    txtInput.insertIcon(iconToInsert);
                } else {
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
            // --- GIAO DIỆN NHẬP IP ---
            JPanel panelIP = new JPanel(new BorderLayout(0, 10));
            JLabel lblInstruction = new JLabel("<html><b style='font-size:14px; font-family: Segoe UI;'>Nhập IP Server:</b><br/><i style='font-size:11px; color: gray;'>(Hỏi người tạo Server để lấy số này)</i></html>");
            
            JTextField txtIP = new JTextField("");
            txtIP.setFont(new Font("Segoe UI", Font.BOLD, 18));
            txtIP.setHorizontalAlignment(JTextField.CENTER);
            txtIP.setPreferredSize(new Dimension(280, 45)); // Kéo to ô nhập liệu
            
            panelIP.add(lblInstruction, BorderLayout.NORTH);
            panelIP.add(txtIP, BorderLayout.CENTER);

            int result = JOptionPane.showConfirmDialog(this, panelIP, "Kết nối đến Máy chủ", 
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            
            if (result != JOptionPane.OK_OPTION || txtIP.getText().trim().isEmpty()) {
                System.exit(0); 
            }
            
            String serverIP = txtIP.getText().trim();
            // ----------------------------------------
            
            socket = new Socket(serverIP, 2026);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);

            showAuthDialog();

            if (!isAuthenticated) {
                System.exit(0);
            }

            setTitle("VOZ - Đang đăng nhập dưới tên: " + myUsername);
            addSystemBubble("ALL", "Đã kết nối. Chào mừng " + myUsername + "!");

            new Thread(this::receiveMessages).start();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Không thể kết nối đến Máy chủ Chat! Hãy kiểm tra lại IP hoặc xem Server đã bật chưa.");
            System.exit(0);
        }
    }

    private void showAuthDialog() {
        JDialog authDialog = new JDialog(this, "Hệ thống Đăng nhập", true);
        authDialog.setSize(420, 260); // Tăng form to hơn
        authDialog.setLocationRelativeTo(this);
        authDialog.setLayout(new BorderLayout());
        authDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JPanel pnlCenter = new JPanel(new GridLayout(2, 1, 10, 15));
        pnlCenter.setBorder(new EmptyBorder(25, 30, 15, 30)); // Căn lề rộng rãi hơn
        
        Font bigFont = new Font("Segoe UI", Font.PLAIN, 16); // Font to hơn

        JPanel pnlUser = new JPanel(new BorderLayout(15, 0));
        JLabel lblUser = new JLabel("Tài khoản:"); 
        lblUser.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblUser.setPreferredSize(new Dimension(80, 0)); // Cố định độ rộng label để cân đối 2 dòng
        pnlUser.add(lblUser, BorderLayout.WEST);
        
        JTextField txtUser = new JTextField();
        txtUser.setFont(bigFont);
        pnlUser.add(txtUser, BorderLayout.CENTER);

        JPanel pnlPass = new JPanel(new BorderLayout(15, 0));
        JLabel lblPass = new JLabel("Mật khẩu:"); 
        lblPass.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblPass.setPreferredSize(new Dimension(80, 0));
        pnlPass.add(lblPass, BorderLayout.WEST);
        
        JPasswordField txtPass = new JPasswordField();
        txtPass.setFont(bigFont);
        pnlPass.add(txtPass, BorderLayout.CENTER);

        pnlCenter.add(pnlUser);
        pnlCenter.add(pnlPass);

        JPanel pnlBottom = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 15));

	     // dùng button custom
	     RoundedButton btnLogin = new RoundedButton("ĐĂNG NHẬP", 20);
	     RoundedButton btnReg = new RoundedButton("ĐĂNG KÝ", 20);
	
	     Font btnFont = new Font("Segoe UI", Font.BOLD, 13);
	     btnLogin.setFont(btnFont);
	     btnReg.setFont(btnFont);
	
	     btnLogin.setPreferredSize(new Dimension(130, 40));
	     btnReg.setPreferredSize(new Dimension(130, 40));
	
	     // set màu cố định
	     btnLogin.setBackground(new Color(0, 123, 255));
	     btnReg.setBackground(new Color(40, 167, 69));
	
	     pnlBottom.add(btnLogin);
	     pnlBottom.add(btnReg);

        JLabel lblHeader = new JLabel("VUI LÒNG ĐĂNG NHẬP HOẶC ĐĂNG KÝ", SwingConstants.CENTER);
        lblHeader.setFont(new Font("Segoe UI", Font.BOLD, 15));
        lblHeader.setBorder(new EmptyBorder(15, 0, 0, 0));
        
        authDialog.add(lblHeader, BorderLayout.NORTH);
        authDialog.add(pnlCenter, BorderLayout.CENTER);
        authDialog.add(pnlBottom, BorderLayout.SOUTH);

        btnLogin.addActionListener(e -> {
            String u = txtUser.getText().trim();
            String p = new String(txtPass.getPassword()).trim();
            if (u.isEmpty() || p.isEmpty()) return;
            
            out.println("[LOGIN]" + u + ":" + p);
            try {
                String res = in.readLine();
                if (res.startsWith("[LOG_SUCCESS]")) {
                    myUsername = u;
                    isAuthenticated = true;
                    authDialog.dispose();
                } else {
                    JOptionPane.showMessageDialog(authDialog, res.substring(10), "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
            } catch (Exception ex) {}
        });

        btnReg.addActionListener(e -> {
            String u = txtUser.getText().trim();
            String p = new String(txtPass.getPassword()).trim();
            if (u.isEmpty() || p.isEmpty()) return;
            
            out.println("[REGISTER]" + u + ":" + p);
            try {
                String res = in.readLine();
                if (res.startsWith("[REG_SUCCESS]")) {
                    JOptionPane.showMessageDialog(authDialog, "Đăng ký thành công! Hãy bấm Đăng nhập.", "Thành công", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(authDialog, res.substring(10), "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
            } catch (Exception ex) {}
        });

        authDialog.setVisible(true);
    }

    private void sendMessage() {
        String text = getMixedInputText().trim();
        if (text.isEmpty()) return;

        if (currentTargetId.equals("ALL")) {
            out.println("[ALL]" + text);
            addChatBubble("ALL", "Bạn (Chung)", text, true);
        } 
        else if (currentTargetId.startsWith("GROUP_")) {
            String grpName = currentTargetId.substring(6);
            out.println("[GROUP]" + grpName + ":" + text);
        }
        else if (currentTargetId.startsWith("PM_")) {
            String targetUser = currentTargetId.substring(3);
            out.println("[PM]" + targetUser + ":" + text);
        }
        
        txtInput.setText("");
        txtInput.requestFocus();
    }

    private void sendFileBase64() {
        JFileChooser fileChooser = new JFileChooser();
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            
            if (selectedFile.length() > 5 * 1024 * 1024) {
                JOptionPane.showMessageDialog(this, "Dung lượng file vượt quá giới hạn (Tối đa 5MB)!", "Lỗi file", JOptionPane.ERROR_MESSAGE);
                return;
            }

            try {
                byte[] fileContent = Files.readAllBytes(selectedFile.toPath());
                String encodedString = Base64.getEncoder().encodeToString(fileContent);
                String fileName = selectedFile.getName();

                out.println("[FILE]" + currentTargetId + ":" + fileName + ":" + encodedString);
                addSystemBubble(currentTargetId, "Bạn đã gửi tệp: " + fileName);

            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Lỗi khi đọc file!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void receiveMessages() {
        try {
            String msg;
            while ((msg = in.readLine()) != null) {
                final String finalMsg = msg;
                SwingUtilities.invokeLater(() -> processMessage(finalMsg));
            }
        } catch (Exception e) {
            if(isAuthenticated) addSystemBubble("ALL", "Mất kết nối với Máy chủ Chat.");
        }
    }

    private void processMessage(String msg) {
        if (msg.startsWith("[USERS]")) {
            updateSidebar(msg.substring(7), null, null);
        } else if (msg.startsWith("[GROUPS]")) {
            updateSidebar(null, msg.substring(8), null);
        } else if (msg.startsWith("[FRIENDS]")) {
            updateSidebar(null, null, msg.substring(9));
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
        else if (msg.startsWith("[ADMIN_ALL]")) {
            String content = msg.substring(11);
            JOptionPane.showMessageDialog(this, content, "THÔNG BÁO TỪ QUẢN TRỊ VIÊN", JOptionPane.WARNING_MESSAGE);
            addSystemBubble("ALL", "Quản trị viên: " + content);
        }
        else if (msg.startsWith("[ADMIN_PM]")) {
            String content = msg.substring(10);
            JOptionPane.showMessageDialog(this, content, "TIN NHẮN MẬT TỪ QUẢN TRỊ VIÊN", JOptionPane.INFORMATION_MESSAGE);
            addSystemBubble(currentTargetId, "Quản trị viên nhắn riêng: " + content);
        }
        else if (msg.startsWith("[FILE]")) {
            String[] parts = msg.substring(6).split(":", 4);
            if(parts.length == 4) {
                String roomId = parts[0];
                String sender = parts[1];
                String fileName = parts[2];
                String fileData = parts[3];

                if (roomId.startsWith("GROUP_")) {
                    String grpName = roomId.substring(6);
                    if (!myJoinedGroups.contains(grpName)) return; 
                }

                if (!sender.equals(myUsername)) {
                    int opt = JOptionPane.showConfirmDialog(this, 
                        sender + " đã gửi tệp: " + fileName + "\nBạn có muốn tải xuống không?", 
                        "Nhận Tệp Đính Kèm", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);
                        
                    if (opt == JOptionPane.YES_OPTION) {
                        saveReceivedFile(fileName, fileData);
                        addSystemBubble(roomId, "Bạn đã nhận tệp: " + fileName + " từ " + sender);
                    } else {
                        addSystemBubble(roomId, "Bạn đã từ chối nhận tệp: " + fileName);
                    }
                }
            }
        }
        else if (msg.startsWith("[REQ_FRIEND]")) {
            String sender = msg.substring(12);
            int opt = JOptionPane.showConfirmDialog(this, 
                sender + " muốn kết bạn với bạn!\nBạn có đồng ý không?", 
                "Lời mời kết bạn", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);
            if (opt == JOptionPane.YES_OPTION) {
                out.println("[ACC_FRIEND]" + sender);
            }
        }
    }

    private void saveReceivedFile(String fileName, String base64Data) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setSelectedFile(new File(fileName));
        int userSelection = fileChooser.showSaveDialog(this);
        
        if (userSelection == JFileChooser.APPROVE_OPTION) {
            File fileToSave = fileChooser.getSelectedFile();
            try {
                byte[] decodedBytes = Base64.getDecoder().decode(base64Data);
                Files.write(fileToSave.toPath(), decodedBytes);
                JOptionPane.showMessageDialog(this, "Lưu file thành công!", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Lỗi khi lưu file!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // --- CÁC HÀM XỬ LÝ CHAT BUBBLE & RENDER EMOJI ---

    private void addSystemBubble(String roomId, String text) {
        JPanel room = getRoomPanel(roomId);
        
        // Dùng FlowLayout.CENTER để bọc JLabel, ép nó luôn ở giữa màn hình
        JPanel wrapper = new JPanel(new FlowLayout(FlowLayout.CENTER));
        wrapper.setOpaque(false);
        
        // Tăng font chữ và làm nổi bật bằng background màu xám nhạt bo góc (dùng HTML)
        String htmlText = "<html><div style='text-align: center; color: #555555; font-family: Segoe UI; font-size: 13px; "
                        + "font-style: italic; background-color: #EAEAEA; padding: 5px 15px; border-radius: 10px;'>" 
                        + text + "</div></html>";
                        
        JLabel lblSys = new JLabel(htmlText);
        wrapper.add(lblSys);
        
        room.add(wrapper);
        room.add(Box.createRigidArea(new Dimension(0, 10))); // Cách dòng một chút
        
        room.revalidate();
        room.repaint();
        scrollToBottom();
    }

    private void addChatBubble(String roomId, String sender, String message, boolean isMe) {
        JPanel room = getRoomPanel(roomId);
        
        JPanel bubbleWrapper = new JPanel(new FlowLayout(isMe ? FlowLayout.RIGHT : FlowLayout.LEFT, 0, 0));
        bubbleWrapper.setOpaque(false);
        
        // Dùng JTextPane cho bong bóng chat để nhét được ảnh vào giữa chữ
        JTextPane msgPane = new JTextPane();
        msgPane.setEditable(false);
        msgPane.setOpaque(false);
        msgPane.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        msgPane.setBorder(new EmptyBorder(5, 10, 5, 10));
        
        // Gọi hàm siêu năng lực: Cứ chỗ nào có mã Emoji là biến thành hình
        insertTextWithEmojis(msgPane, sender + ": " + message);
        
        JPanel bubbleBg = new JPanel(new BorderLayout());
        bubbleBg.setBackground(isMe ? new Color(220, 248, 198) : Color.WHITE);
        bubbleBg.setBorder(new RoundedBorder(10));
        bubbleBg.add(msgPane, BorderLayout.CENTER);

        bubbleWrapper.add(bubbleBg);
        room.add(bubbleWrapper);
        room.add(Box.createRigidArea(new Dimension(0, 5)));
        
        room.revalidate();
        room.repaint();
        scrollToBottom();
    }

    // --- HÀM BIẾN MÃ TEXT THÀNH EMOJI ---
    private void insertTextWithEmojis(JTextPane pane, String text) {
        try {
            javax.swing.text.StyledDocument doc = pane.getStyledDocument();
            int i = 0;
            while (i < text.length()) {
                boolean match = false;
                // Quét xem vị trí hiện tại có khớp với mã Emoji nào không (ví dụ :-) )
                for (String em : emojiCache.keySet()) {
                    if (text.startsWith(em, i)) {
                        pane.setCaretPosition(doc.getLength());
                        pane.insertIcon(emojiCache.get(em)); // Chèn ảnh
                        i += em.length(); // Bỏ qua đoạn text mã đó
                        match = true;
                        break;
                    }
                }
                // Nếu không phải emoji thì in chữ bình thường
                if (!match) {
                    doc.insertString(doc.getLength(), String.valueOf(text.charAt(i)), null);
                    i++;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // --- HÀM CẬP NHẬT DANH SÁCH BÊN TRÁI (SIDEBAR) ---
    private String lastUsers = "", lastGroups = "", lastFriends = "";

    private void updateSidebar(String users, String groups, String friends) {
        if (users != null) lastUsers = users;
        if (groups != null) lastGroups = groups;
        if (friends != null) lastFriends = friends;

        SwingUtilities.invokeLater(() -> {
            sidebarModel.clear();
            
            sidebarModel.addElement("--- Chat Chung ---");
            sidebarModel.addElement("    [ALL] Chat Chung");

            sidebarModel.addElement("--- Nhóm Của Bạn ---");
            for (String g : myJoinedGroups) {
                sidebarModel.addElement("    [Nhóm] " + g);
            }

            sidebarModel.addElement("--- Bạn Bè ---");
            if (!lastFriends.isEmpty()) {
                for (String f : lastFriends.split(",")) {
                    if (!f.trim().isEmpty()) sidebarModel.addElement("    [Bạn] " + f);
                }
            }

            sidebarModel.addElement("--- Khách Online ---");
            if (!lastUsers.isEmpty()) {
                for (String u : lastUsers.split(",")) {
                    if (!u.trim().isEmpty() && !u.equals(myUsername)) {
                        sidebarModel.addElement("    [Khách] " + u);
                    }
                }
            }

            sidebarModel.addElement("--- Nhóm Khám Phá ---");
            if (!lastGroups.isEmpty()) {
                for (String g : lastGroups.split(",")) {
                    if (!g.trim().isEmpty() && !myJoinedGroups.contains(g)) {
                        sidebarModel.addElement("    [Khám phá] " + g);
                    }
                }
            }
        });
    }

    // --- CLASS TẠO VIỀN BO TRÒN CHO GIAO DIỆN ---
    class RoundedBorder implements Border {
        private int radius;

        RoundedBorder(int radius) {
            this.radius = radius;
        }

        public Insets getBorderInsets(Component c) {
            return new Insets(this.radius + 1, this.radius + 1, this.radius + 2, this.radius);
        }

        public boolean isBorderOpaque() {
            return true;
        }

        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setColor(Color.LIGHT_GRAY);
            g2d.drawRoundRect(x, y, width - 1, height - 1, radius, radius);
            g2d.dispose();
        }
    }
} 
class RoundedButton extends JButton {
    private int radius;

    public RoundedButton(String text, int radius) {
        super(text);
        this.radius = radius;
        setContentAreaFilled(false);
        setFocusPainted(false);
        setBorderPainted(false);
        setForeground(Color.WHITE);
        setCursor(new Cursor(Cursor.HAND_CURSOR));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Màu nền luôn cố định
        g2.setColor(getBackground());
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);

        super.paintComponent(g);
        g2.dispose();
    }

    @Override
    protected void paintBorder(Graphics g) {

    }
}