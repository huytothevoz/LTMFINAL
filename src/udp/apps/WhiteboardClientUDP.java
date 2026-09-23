package udp.apps;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.UUID;

public class WhiteboardClientUDP extends JFrame {
    private JPanel canvasPanel;
    private BufferedImage canvasImage;
    private Graphics2D g2d;
    
    private Color currentColor = Color.BLACK;
    private int currentStrokeThick = 5;
    private int prevX = -1, prevY = -1;

    // Phục vụ tính năng Undo
    private String currentStrokeId = ""; 
    private Stack<String> myStrokeIds = new Stack<>(); // Lưu các ID nét vẽ của mình
    private List<String> allDrawCommands = new ArrayList<>(); // Lưu toàn bộ lệnh vẽ trên bảng

    private DatagramSocket socket;
    private InetAddress serverIP;
    private int serverPort;
    private String username;

    public WhiteboardClientUDP(String ip, int port, String username) {
        this.serverPort = port;
        this.username = username;
        setupUI();
        connectToServer(ip);
    }

    private void setupUI() {
        setTitle("Bảng vẽ chung - " + username);
        setSize(1000, 700);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                if (socket != null && !socket.isClosed()) socket.close();
                dispose();
            }
        });

        // --- TOOLBAR ---
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 10));
        toolbar.setBackground(new Color(44, 62, 80));
        
        Color[] colors = {Color.BLACK, Color.RED, new Color(40, 167, 69), new Color(0, 123, 255), Color.ORANGE, Color.WHITE};
        String[] colorNames = {"Đen", "Đỏ", "Xanh lá", "Xanh dương", "Cam", "Cục Tẩy"};
        
        for (int i = 0; i < colors.length; i++) {
            JButton btnColor = new JButton();
            btnColor.setPreferredSize(new Dimension(35, 35));
            btnColor.setBackground(colors[i]);
            btnColor.setOpaque(true); // FIX LỖI KHÔNG HIỆN MÀU BẢNG PALETTE
            btnColor.setBorderPainted(false);
            btnColor.setToolTipText(colorNames[i]);
            btnColor.setCursor(new Cursor(Cursor.HAND_CURSOR));
            
            Color selectedColor = colors[i];
            btnColor.addActionListener(e -> currentColor = selectedColor);
            toolbar.add(btnColor);
        }

        JLabel lblStroke = new JLabel("Độ dày:");
        lblStroke.setForeground(Color.WHITE);
        toolbar.add(lblStroke);
        
        JSlider strokeSlider = new JSlider(1, 30, 5);
        strokeSlider.setBackground(new Color(44, 62, 80));
        strokeSlider.addChangeListener(e -> currentStrokeThick = strokeSlider.getValue());
        toolbar.add(strokeSlider);

        // Khởi tạo màu Pastel
        Color pastelYellow = new Color(255, 235, 155); // Vàng Pastel
        Color pastelRed = new Color(255, 153, 153);    // Đỏ/Hồng Pastel

        // Nút UNDO
        JButton btnUndo = new RoundedButton("↩ QUAY LẠI (UNDO)", pastelYellow);
        btnUndo.setForeground(Color.DARK_GRAY); // Chữ màu xám đậm để dễ đọc trên nền vàng sáng
        btnUndo.addActionListener(e -> triggerUndo()); 
        toolbar.add(btnUndo);

        // Nút XÓA 
        JButton btnClear = new RoundedButton("XÓA BẢNG", pastelRed); 
        btnClear.setForeground(Color.WHITE); 
        btnClear.addActionListener(e -> { 
            allDrawCommands.clear(); 
            clearCanvas(); 
            sendUDPMessage("CLEAR"); 
        }); 
        toolbar.add(btnClear); 

        add(toolbar, BorderLayout.NORTH);
        
        // --- CANVAS ---
        canvasImage = new BufferedImage(1920, 1080, BufferedImage.TYPE_INT_ARGB);
        g2d = canvasImage.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        
        // TẠO PANEL TRƯỚC RỒI MỚI GỌI CLEAR CANVAS ĐỂ KHÔNG BỊ LỖI KHI REPAINT
        canvasPanel = new JPanel() {
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.drawImage(canvasImage, 0, 0, null);
            }
        };
        canvasPanel.setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
        
        clearCanvas(); // ĐÃ FIX: Chuyển lệnh này xuống dưới!

        canvasPanel.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                prevX = e.getX();
                prevY = e.getY();
                // Bắt đầu một nét vẽ mới, tạo ID độc nhất
                currentStrokeId = UUID.randomUUID().toString();
                myStrokeIds.push(currentStrokeId);
            }
        });

        canvasPanel.addMouseMotionListener(new MouseMotionAdapter() {
            public void mouseDragged(MouseEvent e) {
                int currX = e.getX();
                int currY = e.getY();
                if (prevX != -1 && prevY != -1) {
                    String cmd = String.format("DRAW|%s|%d|%d|%d|%d|%d|%d", 
                            currentStrokeId, prevX, prevY, currX, currY, currentColor.getRGB(), currentStrokeThick);
                    
                    allDrawCommands.add(cmd); // Lưu bộ nhớ cục bộ
                    drawLineOnCanvas(prevX, prevY, currX, currY, currentColor.getRGB(), currentStrokeThick);
                    sendUDPMessage(cmd);
                }
                prevX = currX;
                prevY = currY;
            }
        });

        add(new JScrollPane(canvasPanel), BorderLayout.CENTER);
    }

    private void drawLineOnCanvas(int x1, int y1, int x2, int y2, int colorRGB, int stroke) {
        if (g2d == null || canvasPanel == null) return;
        g2d.setPaint(new Color(colorRGB));
        g2d.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2d.drawLine(x1, y1, x2, y2);
        canvasPanel.repaint();
    }

    private void clearCanvas() {
        if (g2d == null || canvasPanel == null) return; // Thêm check an toàn tuyệt đối
        g2d.setPaint(Color.WHITE);
        g2d.fillRect(0, 0, canvasImage.getWidth(), canvasImage.getHeight());
        canvasPanel.repaint();
    }

    // Hàm thực hiện Undo
    private void triggerUndo() {
        if (!myStrokeIds.isEmpty()) {
            String lastStrokeId = myStrokeIds.pop();
            sendUDPMessage("UNDO|" + lastStrokeId);
            processUndo(lastStrokeId);
        }
    }

    // Xóa dữ liệu nét vẽ khỏi danh sách và vẽ lại toàn bộ
    private void processUndo(String strokeId) {
        allDrawCommands.removeIf(cmd -> cmd.startsWith("DRAW|" + strokeId));
        redrawAll();
    }

    private void redrawAll() {
        clearCanvas();
        for (String cmd : allDrawCommands) {
            parseAndDraw(cmd);
        }
    }

    private void parseAndDraw(String cmd) {
        try {
            String[] p = cmd.split("\\|");
            int x1 = Integer.parseInt(p[2]);
            int y1 = Integer.parseInt(p[3]);
            int x2 = Integer.parseInt(p[4]);
            int y2 = Integer.parseInt(p[5]);
            int col = Integer.parseInt(p[6]);
            int strk = Integer.parseInt(p[7]);
            drawLineOnCanvas(x1, y1, x2, y2, col, strk);
        } catch (Exception ignored) {}
    }

    private void connectToServer(String ip) {
        try {
            // Bind socket với port random nhưng rõ ràng
            socket = new DatagramSocket(null);
            socket.setReuseAddress(true);
            socket.bind(new java.net.InetSocketAddress(0));

            // Ép dùng IPv4
            serverIP = InetAddress.getByName(ip);

            if (!(serverIP instanceof java.net.Inet4Address)) {
                JOptionPane.showMessageDialog(this, "Chỉ hỗ trợ IPv4!");
                return;
            }

            sendUDPMessage("JOIN|" + username);
            startListening();

        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Lỗi kết nối: " + e.getMessage());
        }
    }

    private void sendUDPMessage(String msg) {
        try {
            if (socket != null && !socket.isClosed()) {
                byte[] data = msg.getBytes("UTF-8");
                socket.send(new DatagramPacket(data, data.length, serverIP, serverPort));
            }
        } catch (Exception ignored) {}
    }

    private void startListening() {
        new Thread(() -> {
            try {
                byte[] buffer = new byte[65507];
                while (!socket.isClosed()) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    String msg = new String(packet.getData(), 0, packet.getLength(), "UTF-8");

                    SwingUtilities.invokeLater(() -> {
                        if (msg.startsWith("DRAW|")) {
                            allDrawCommands.add(msg);
                            parseAndDraw(msg);
                        } 
                        else if (msg.startsWith("SYNC|DRAW|")) {
                            String realCmd = msg.substring(5); 
                            allDrawCommands.add(realCmd);
                            parseAndDraw(realCmd);
                        }
                        else if (msg.startsWith("UNDO|")) {
                            String strokeId = msg.split("\\|")[1];
                            processUndo(strokeId);
                        }
                        else if (msg.equals("CLEAR") || msg.equals("SYNC|CLEAR")) {
                            allDrawCommands.clear();
                            clearCanvas();
                        }
                    });
                }
            } catch (Exception ignored) {}
        }).start();
    }
}
class RoundedButton extends JButton {
    private Color bgColor;

    public RoundedButton(String text, Color bgColor) {
        super(text);
        this.bgColor = bgColor;
        
        setContentAreaFilled(false); 
        setFocusPainted(false);      
        setBorderPainted(false);    
        setOpaque(false);            
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        if (getModel().isArmed()) {
            g2.setColor(bgColor.darker());
        } else {
            g2.setColor(bgColor);
        }
        
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
        g2.dispose();
        
        super.paintComponent(g);
    }
}