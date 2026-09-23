package udp.file;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public class UdpFileServerUI extends JFrame {

    private JTextArea txtLog;
    private JTextField txtPort;
    private JTextField txtIpAddress;
    private JButton btnStart, btnStop;

    private DefaultTableModel tableModel;
    private JTable tableFiles;

    private JLabel lblTotalFiles;
    private JLabel lblTotalSize;

    private DatagramSocket serverSocket;
    private volatile boolean isRunning = false;

    private final String SAVE_DIR = "server_data";

    private AtomicLong totalSize = new AtomicLong(0);
    private int totalFiles = 0;

    private static final int PACKET_SIZE = 8192;

    public UdpFileServerUI() {
        setTitle("MÁY CHỦ QUẢN LÝ FILE (UDP FTP SERVER)");
        setSize(1100, 650);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        new File(SAVE_DIR).mkdirs();
        setupUI();
    }

    // ================= LẤY IP LAN CHUẨN =================
    private String getLANIP() {
        try {
            Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface netint : Collections.list(nets)) {

                if (!netint.isUp() || netint.isLoopback() || netint.isVirtual()) continue;

                for (InetAddress addr : Collections.list(netint.getInetAddresses())) {
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "127.0.0.1"; // fallback nếu lỗi
    }

    private void setupUI() {
        setLayout(new BorderLayout(10, 10));
        JPanel main = new JPanel(new BorderLayout(10, 10));
        main.setBorder(new EmptyBorder(15, 15, 15, 15));
        main.setBackground(new Color(240, 242, 245));
        setContentPane(main);

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        top.setBackground(main.getBackground());

        txtIpAddress = new JTextField(12);
        txtIpAddress.setFont(new Font("Consolas", Font.BOLD, 14));
        txtIpAddress.setEditable(false);
        txtIpAddress.setBackground(Color.WHITE);
        txtIpAddress.setForeground(Color.BLUE);


        txtIpAddress.setText(getLANIP());

        txtPort = new JTextField("3027", 5);
        txtPort.setFont(new Font("Consolas", Font.BOLD, 14));

        btnStart = new RoundedButton("MỞ CỔNG", 20);
        btnStop = new RoundedButton("ĐÓNG CỔNG", 20);
        JButton btnOpenFolder = new RoundedButton("MỞ THƯ MỤC LƯU TRỮ", 20);

        // size cho đẹp
        btnStart.setPreferredSize(new Dimension(120, 35));
        btnStop.setPreferredSize(new Dimension(120, 35));
        btnOpenFolder.setPreferredSize(new Dimension(200, 35));

        // màu cố định
        btnStart.setBackground(new Color(40, 167, 69));     // xanh lá
        btnStop.setBackground(new Color(220, 53, 69));      // đỏ
        btnOpenFolder.setBackground(new Color(23, 162, 184)); // xanh biển

        btnStop.setEnabled(false);

        top.add(new JLabel("IP Server (Đưa cho Client):"));
        top.add(txtIpAddress);
        top.add(new JLabel("Port:"));
        top.add(txtPort);
        top.add(btnStart);
        top.add(btnStop);
        top.add(btnOpenFolder);

        main.add(top, BorderLayout.NORTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        split.setDividerLocation(450);
        split.setBorder(null);

        txtLog = new JTextArea();
        txtLog.setFont(new Font("Consolas", Font.PLAIN, 13));
        txtLog.setEditable(false);
        txtLog.setMargin(new Insets(5, 5, 5, 5));
        JScrollPane logPane = new JScrollPane(txtLog);
        logPane.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.GRAY), "NHẬT KÝ HỆ THỐNG"));

        String[] columns = {"Tên File", "Kích Thước", "Thời Gian"};
        tableModel = new DefaultTableModel(columns, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };

        tableFiles = new JTable(tableModel);
        JScrollPane filePane = new JScrollPane(tableFiles);

        split.setLeftComponent(logPane);
        split.setRightComponent(filePane);
        main.add(split, BorderLayout.CENTER);

        JPanel stats = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        lblTotalFiles = new JLabel("Tổng file: 0");
        lblTotalSize = new JLabel("Dung lượng: 0 B");
        stats.add(lblTotalFiles);
        stats.add(lblTotalSize);
        main.add(stats, BorderLayout.SOUTH);

        btnStart.addActionListener(e -> startServer());
        btnStop.addActionListener(e -> stopServer());

        loadOldFiles();
    }

    private JButton createBtn(String text, Color color) {
        JButton btn = new JButton(text);
        btn.setBackground(color);
        btn.setForeground(Color.WHITE);
        return btn;
    }

    private void log(String msg) {
        SwingUtilities.invokeLater(() -> txtLog.append(msg + "\n"));
    }

    private void startServer() {
        try {
            int port = Integer.parseInt(txtPort.getText().trim());
            serverSocket = new DatagramSocket(port);
            isRunning = true;

            log("[OK] Server chạy tại " + txtIpAddress.getText() + ":" + port);

            btnStart.setEnabled(false);
            btnStop.setEnabled(true);

            new Thread(() -> {
                while (isRunning) {
                    try {
                        byte[] buffer = new byte[1024];
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        serverSocket.receive(packet);

                        String msg = new String(packet.getData(), 0, packet.getLength(), "UTF-8");
                        processCommand(msg, packet.getAddress(), packet.getPort());

                    } catch (Exception e) {
                        if (isRunning) log("Lỗi: " + e.getMessage());
                    }
                }
            }).start();

        } catch (Exception e) {
            log("Không mở được port!");
        }
    }

    private void stopServer() {
        isRunning = false;
        if (serverSocket != null) serverSocket.close();
        log("Đã dừng server.");
        btnStart.setEnabled(true);
        btnStop.setEnabled(false);
    }

    private void processCommand(String msg, InetAddress addr, int port) {
        try {
            String[] parts = msg.split("\\|");
            String cmd = parts[0];

            if (cmd.equals("LIST")) {
                File[] files = new File(SAVE_DIR).listFiles();
                StringBuilder res = new StringBuilder("LIST_RES");

                if (files != null) {
                    for (File f : files) {
                        if (f.isFile()) res.append("|").append(f.getName());
                    }
                }
                sendUdp(res.toString(), addr, port);
            }

            // ================= UPLOAD =================
            else if (cmd.equals("UPLOAD")) {
                String clientId = parts[1];
                String fileName = parts[2];
                long fileSize = Long.parseLong(parts[3]);

                int dataPort = new Random().nextInt(10000) + 20000;

                sendUdp("UPLOAD_ACCEPT|" + dataPort, addr, port);

                new Thread(() -> receiveFile(fileName, fileSize, dataPort)).start();
            }

            // ================= DOWNLOAD =================
            else if (cmd.equals("DOWNLOAD")) {
                String clientId = parts[1];
                String fileName = parts[2];

                File file = new File(SAVE_DIR, fileName);

                if (!file.exists()) {
                    sendUdp("ERROR|File not found", addr, port);
                    return;
                }

                int dataPort = new Random().nextInt(10000) + 20000;

                sendUdp("DOWNLOAD_ACCEPT|" + dataPort + "|" + file.length(), addr, port);

                new Thread(() -> sendFile(file, addr, dataPort)).start();
            }

        } catch (Exception e) {
            log("Lỗi xử lý lệnh: " + e.getMessage());
        }
    }

    private void sendUdp(String msg, InetAddress addr, int port) {
        try {
            byte[] data = msg.getBytes("UTF-8");
            serverSocket.send(new DatagramPacket(data, data.length, addr, port));
        } catch (Exception e) {
            log("Lỗi gửi UDP");
        }
    }

    private void loadOldFiles() {
        File[] files = new File(SAVE_DIR).listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isFile()) {
                    tableModel.addRow(new Object[]{f.getName(), f.length()});
                }
            }
        }
    }
    
    private void receiveFile(String fileName, long fileSize, int port) {
        try (DatagramSocket socket = new DatagramSocket(port);
             FileOutputStream fos = new FileOutputStream(new File(SAVE_DIR, fileName))) {

            byte[] buffer = new byte[PACKET_SIZE];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

            long total = 0;

            while (total < fileSize) {
                socket.receive(packet);
                fos.write(packet.getData(), 0, packet.getLength());
                total += packet.getLength();

                // ACK
                byte[] ack = "ACK".getBytes();
                socket.send(new DatagramPacket(ack, ack.length, packet.getAddress(), packet.getPort()));
            }

            log("[OK] Nhận file: " + fileName);

        } catch (Exception e) {
            log("Lỗi nhận file: " + e.getMessage());
        }
    }
    private void sendFile(File file, InetAddress addr, int port) {
        try (DatagramSocket socket = new DatagramSocket();
             FileInputStream fis = new FileInputStream(file)) {

            byte[] buffer = new byte[PACKET_SIZE];
            int read;

            while ((read = fis.read(buffer)) != -1) {
                DatagramPacket packet = new DatagramPacket(buffer, read, addr, port);

                boolean ack = false;
                int retry = 0;

                while (!ack && retry < 3) {
                    socket.send(packet);

                    try {
                        byte[] ackBuf = new byte[10];
                        DatagramPacket ackPacket = new DatagramPacket(ackBuf, ackBuf.length);
                        socket.setSoTimeout(10000);
                        socket.receive(ackPacket);
                        ack = true;
                    } catch (SocketTimeoutException e) {
                        retry++;
                    }
                }

                if (!ack) {
                    log("Mất kết nối khi gửi file!");
                    return;
                }
            }

            log("[OK] Gửi file xong: " + file.getName());

        } catch (Exception e) {
            log("Lỗi gửi file: " + e.getMessage());
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
        setFont(new Font("Segoe UI", Font.BOLD, 12));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // nền
        g2.setColor(getBackground());
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);

        super.paintComponent(g);
        g2.dispose();
    }

    @Override
    protected void paintBorder(Graphics g) {
        // bỏ border
    }
}