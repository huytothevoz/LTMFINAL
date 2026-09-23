package tcp.file;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicLong;

public class TcpFileServerUI extends JFrame {

    private JTextArea txtLog;
    private JTextField txtPort;
    private JTextField txtIpAddress;
    private JButton btnStart, btnStop;

    private DefaultTableModel tableModel;
    private JTable tableFiles;

    private JLabel lblTotalFiles;
    private JLabel lblTotalSize;

    private ServerSocket serverSocket;
    private volatile boolean isRunning = false;

    private final String SAVE_DIR = "server_data";

    private AtomicLong totalSize = new AtomicLong(0);
    private int totalFiles = 0;

    public TcpFileServerUI() {
        setTitle("MÁY CHỦ QUẢN LÝ FILE (TCP FTP SERVER)");
        setSize(1100, 650);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        new File(SAVE_DIR).mkdirs();
        setupUI();
    }

    private void setupUI() {
        setLayout(new BorderLayout(10, 10));
        JPanel main = new JPanel(new BorderLayout(10, 10));
        main.setBorder(new EmptyBorder(15, 15, 15, 15));
        main.setBackground(new Color(240, 242, 245));
        setContentPane(main);
        
        // TOP 
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        top.setBackground(main.getBackground());

        txtIpAddress = new JTextField(12);
        txtIpAddress.setFont(new Font("Consolas", Font.BOLD, 14));
        txtIpAddress.setEditable(false);
        txtIpAddress.setBackground(Color.WHITE);
        txtIpAddress.setForeground(Color.BLUE);
        try {
        	txtIpAddress.setText(getLocalIPv4());
        } catch (Exception e) {
            txtIpAddress.setText("127.0.0.1");
        }

        txtPort = new JTextField("2027", 5);
        txtPort.setFont(new Font("Consolas", Font.BOLD, 14));

        btnStart = createBtn("MỞ CỔNG", new Color(40, 167, 69));
        btnStop = createBtn("ĐÓNG CỔNG", new Color(220, 53, 69));
        btnStop.setEnabled(false);
        JButton btnOpenFolder = createBtn("MỞ THƯ MỤC LƯU TRỮ", new Color(23, 162, 184));

        top.add(new JLabel("IP Server (Đưa cho Client):"));
        top.add(txtIpAddress);
        top.add(new JLabel("Port:"));
        top.add(txtPort);
        top.add(btnStart);
        top.add(btnStop);
        top.add(btnOpenFolder);

        main.add(top, BorderLayout.NORTH);

        // CENTER 
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        split.setDividerLocation(450);
        split.setBorder(null);

        txtLog = new JTextArea();
        txtLog.setFont(new Font("Consolas", Font.PLAIN, 13));
        txtLog.setEditable(false);
        txtLog.setMargin(new Insets(5, 5, 5, 5));
        JScrollPane logPane = new JScrollPane(txtLog);
        logPane.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.GRAY), "NHẬT KÝ HỆ THỐNG", TitledBorder.LEFT, TitledBorder.TOP, new Font("Segoe UI", Font.BOLD, 12)));

        String[] columns = {"Tên File", "Kích Thước", "Thời Gian Cập Nhật"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        tableFiles = new JTable(tableModel);
        tableFiles.setRowHeight(25);
        tableFiles.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        tableFiles.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));

        JScrollPane filePane = new JScrollPane(tableFiles);
        filePane.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.GRAY), "DANH SÁCH FILE ĐÃ LƯU TRÊN SERVER", TitledBorder.LEFT, TitledBorder.TOP, new Font("Segoe UI", Font.BOLD, 12)));

        split.setLeftComponent(logPane);
        split.setRightComponent(filePane);
        main.add(split, BorderLayout.CENTER);

        // BOTTOM 
        JPanel stats = new JPanel(new FlowLayout(FlowLayout.RIGHT, 20, 5));
        stats.setBackground(main.getBackground());
        lblTotalFiles = new JLabel("Tổng số file: 0");
        lblTotalFiles.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblTotalSize = new JLabel("Tổng dung lượng: 0 B");
        lblTotalSize.setFont(new Font("Segoe UI", Font.BOLD, 13));

        stats.add(lblTotalFiles);
        stats.add(lblTotalSize);
        main.add(stats, BorderLayout.SOUTH);

        // EVENTS
        btnStart.addActionListener(e -> startServer());
        btnStop.addActionListener(e -> stopServer());
        btnOpenFolder.addActionListener(e -> openSaveDirectory());

        tableFiles.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                if (evt.getClickCount() == 2 && tableFiles.getSelectedRow() != -1) {
                    openFile(tableModel.getValueAt(tableFiles.getSelectedRow(), 0).toString());
                }
            }
        });

        loadOldFiles();
    }

    private JButton createBtn(String text, Color color) {
        JButton btn = new JButton(text);
        btn.setBackground(color);
        btn.setForeground(Color.WHITE);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private void log(String msg) {
        String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
        SwingUtilities.invokeLater(() -> {
            txtLog.append("[" + time + "] " + msg + "\n");
            txtLog.setCaretPosition(txtLog.getDocument().getLength());
        });
    }

    private void updateStats(long size) {
        totalFiles++;
        totalSize.addAndGet(size);
        SwingUtilities.invokeLater(() -> {
            lblTotalFiles.setText("Tổng số file: " + totalFiles);
            lblTotalSize.setText("Tổng dung lượng: " + formatFileSize(totalSize.get()));
        });
    }

    private void startServer() {
        try {
            int port = Integer.parseInt(txtPort.getText().trim());
            if (port <= 0 || port > 65535) throw new NumberFormatException();

            serverSocket = new ServerSocket(port);
            isRunning = true;
            log("[OK] Server khởi động tại IP: " + txtIpAddress.getText() + " | Port: " + port);

            btnStart.setEnabled(false);
            txtPort.setEditable(false);
            btnStop.setEnabled(true);

            new Thread(() -> {
                while (isRunning) {
                    try {
                        Socket socket = serverSocket.accept();
                        new FileHandler(socket).start();
                    } catch (Exception ex) {
                        if (isRunning) log("[ERROR] Lỗi kết nối: " + ex.getMessage());
                    }
                }
            }).start();
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Cổng (Port) phải từ 1 - 65535!", "Lỗi Validate", JOptionPane.ERROR_MESSAGE);
        } catch (Exception ex) {
            log("[ERROR] Không thể mở cổng: " + ex.getMessage());
        }
    }

    private void stopServer() {
        try {
            isRunning = false;
            if (serverSocket != null) serverSocket.close();
            log("[INFO] Đã đóng Server.");
            btnStart.setEnabled(true);
            txtPort.setEditable(true);
            btnStop.setEnabled(false);
        } catch (Exception ex) {
            log("[ERROR] Lỗi khi đóng Server: " + ex.getMessage());
        }
    }

    private void openSaveDirectory() {
        try {
            Desktop.getDesktop().open(new File(SAVE_DIR));
        } catch (Exception e) {
            log("[ERROR] Không thể mở thư mục lưu trữ!");
        }
    }

    private void openFile(String fileName) {
        try {
            Desktop.getDesktop().open(new File(SAVE_DIR, fileName));
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Không thể mở file này!", "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadOldFiles() {
        File[] files = new File(SAVE_DIR).listFiles();
        if (files != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm");
            for (File f : files) {
                if (f.isFile()) {
                    tableModel.addRow(new Object[]{f.getName(), formatFileSize(f.length()), sdf.format(new Date(f.lastModified()))});
                    updateStats(f.length());
                }
            }
        }
    }

    // LUỒNG XỬ LÝ DỮ LIỆU CHÍNH 
    class FileHandler extends Thread {
        Socket socket;

        FileHandler(Socket s) {
            socket = s;
        }

        public void run() {
            String clientIp = socket.getInetAddress().getHostAddress();
            try (DataInputStream dis = new DataInputStream(socket.getInputStream());
                 DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {

                // 1. Đọc Lệnh
                String command = dis.readUTF();
                // 2. Đọc Định danh Client
                String clientId = dis.readUTF(); 

                // 3. Xử lý theo từng Lệnh
                if (command.equals("LIST")) {
                    handleListFiles(dos, clientId, clientIp);
                } else if (command.equals("UPLOAD")) {
                    handleUpload(dis, clientId, clientIp);
                } else if (command.equals("DOWNLOAD")) {
                    handleDownload(dis, dos, clientId, clientIp);
                } else {
                    log("[WARNING] Nhận lệnh không hợp lệ từ " + clientId);
                }

            } catch (Exception e) {
                log("[ERROR] Lỗi kết nối với Client [" + clientIp + "].");
            } finally {
                try { socket.close(); } catch (Exception ignored) {}
            }
        }

        // --- XỬ LÝ LỆNH LIST ---
        private void handleListFiles(DataOutputStream dos, String clientId, String clientIp) throws IOException {
            log("[INFO] Client [" + clientId + " - " + clientIp + "] yêu cầu danh sách file.");
            File folder = new File(SAVE_DIR);
            File[] listOfFiles = folder.listFiles();
            StringBuilder fileList = new StringBuilder();

            if (listOfFiles != null) {
                for (File file : listOfFiles) {
                    if (file.isFile()) {
                        if (fileList.length() > 0) fileList.append("|");
                        fileList.append(file.getName());
                    }
                }
            }
            dos.writeUTF(fileList.toString()); // Trả danh sách về Client
        }

        // --- XỬ LÝ LỆNH UPLOAD ---
        private void handleUpload(DataInputStream dis, String clientId, String clientIp) throws IOException {
            String originalName = dis.readUTF();
            long expectedSize = dis.readLong();
            String finalName = getSmartUniqueName(originalName);
            File targetFile = new File(SAVE_DIR, finalName);
            boolean isSuccess = false;

            log("[UPLOAD] Nhận file '" + finalName + "' từ [" + clientId + " - " + clientIp + "].");

            try (FileOutputStream fos = new FileOutputStream(targetFile)) {
                byte[] buffer = new byte[8192];
                int read;
                long totalRead = 0;

                while (totalRead < expectedSize && (read = dis.read(buffer)) != -1) {
                    fos.write(buffer, 0, read);
                    totalRead += read;
                }

                if (totalRead == expectedSize) {
                    isSuccess = true;
                    log("[OK] Tải lên hoàn tất: " + finalName + " (bởi " + clientId + ")");
                    String time = new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date());

                    SwingUtilities.invokeLater(() -> {
                        tableModel.addRow(new Object[]{finalName, formatFileSize(expectedSize), time});
                    });
                    updateStats(expectedSize);
                }
            } finally {
                if (!isSuccess && targetFile.exists()) {
                    targetFile.delete();
                    log("[CLEANUP] Xóa file rác '" + targetFile.getName() + "' do tải lên bị lỗi.");
                }
            }
        }

        // --- XỬ LÝ LỆNH DOWNLOAD ---
        private void handleDownload(DataInputStream dis, DataOutputStream dos, String clientId, String clientIp) throws IOException {
            String requestedFile = dis.readUTF();
            File file = new File(SAVE_DIR, requestedFile);

            log("[DOWNLOAD] Client [" + clientId + " - " + clientIp + "] yêu cầu tải file '" + requestedFile + "'.");

            if (!file.exists() || !file.isFile()) {
                dos.writeLong(-1); // Báo lỗi cho Client
                log("[WARNING] File '" + requestedFile + "' không tồn tại!");
                return;
            }

            dos.writeLong(file.length()); // Gửi kích thước trước

            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = fis.read(buffer)) != -1) {
                    dos.write(buffer, 0, read);
                }
            }
            log("[OK] Hoàn tất gửi file '" + requestedFile + "' cho [" + clientId + "].");
        }
    }

    private String getSmartUniqueName(String originalName) {
        File f = new File(SAVE_DIR, originalName);
        if (!f.exists()) return originalName;

        String name = originalName;
        String ext = "";
        int dotIdx = originalName.lastIndexOf('.');
        if (dotIdx > 0 && dotIdx < originalName.length() - 1) {
            name = originalName.substring(0, dotIdx);
            ext = originalName.substring(dotIdx);
        }

        int count = 1;
        while (true) {
            String newName = name + " (" + count + ")" + ext;
            if (!new File(SAVE_DIR, newName).exists()) return newName;
            count++;
        }
    }

    private String formatFileSize(long size) {
        String[] units = {"B", "KB", "MB", "GB"};
        double s = size;
        int i = 0;
        while (s >= 1024 && i < units.length - 1) {
            s /= 1024;
            i++;
        }
        return String.format("%.2f %s", s, units[i]);
    }
    private String getLocalIPv4() {
        try {
            for (java.util.Enumeration<java.net.NetworkInterface> en = java.net.NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                java.net.NetworkInterface intf = en.nextElement();
                for (java.util.Enumeration<java.net.InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    java.net.InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof java.net.Inet4Address) {
                        return inetAddress.getHostAddress();
                    }
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return "127.0.0.1";//fallback
    }
}
