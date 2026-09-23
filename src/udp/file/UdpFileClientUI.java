package udp.file;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.*;
import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class UdpFileClientUI extends JFrame {

    private JTextField txtIp, txtPort, txtClientId;
    private JTextArea txtLog;
    private JProgressBar progressBar;
    private DefaultListModel<String> serverFileModel;
    private JList<String> listServerFiles;

    private JButton btnCancel, btnChoose, btnRefresh, btnDownload;
    private volatile boolean isCancelled = false;
    private volatile boolean isTransferring = false;

    // Kích thước chuẩn gói tin UDP
    private static final int PACKET_SIZE = 8192;

    public UdpFileClientUI() {
        setTitle("FTP CLIENT (UDP UPLOAD/DOWNLOAD)");
        setSize(950, 600);
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

        // ===== TOP: KẾT NỐI VÀ ĐỊNH DANH =====
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        top.setBackground(main.getBackground());

        txtClientId = new JTextField("User-01", 10);
        txtClientId.setFont(new Font("Consolas", Font.BOLD, 14));
        txtClientId.setForeground(new Color(0, 102, 204));
        
        txtIp = new JTextField("", 10);
        txtIp.setFont(new Font("Consolas", Font.BOLD, 14));
        
        txtPort = new JTextField("3027", 5);
        txtPort.setFont(new Font("Consolas", Font.BOLD, 14));

        btnRefresh = createBtn("LÀM MỚI", new Color(23, 162, 184));
        btnChoose = createBtn("TẢI LÊN", new Color(0, 123, 255));
        btnCancel = createBtn("HỦY", new Color(220, 53, 69));
        btnCancel.setEnabled(false);

        top.add(new JLabel("Client ID:"));
        top.add(txtClientId);
        top.add(new JLabel("IP Server:"));
        top.add(txtIp);
        top.add(new JLabel("Port:"));
        top.add(txtPort);
        top.add(btnRefresh);
        top.add(btnChoose);
        top.add(btnCancel);

        main.add(top, BorderLayout.NORTH);

        // ===== CENTER: CHIA ĐÔI GIAO DIỆN =====
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(350);

        // Nửa trái: Danh sách File trên Server
        serverFileModel = new DefaultListModel<>();
        listServerFiles = new JList<>(serverFileModel);
        listServerFiles.setFont(new Font("Consolas", Font.PLAIN, 14));
        listServerFiles.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane serverFilesPane = new JScrollPane(listServerFiles);
        
        JPanel leftPanel = new JPanel(new BorderLayout(5, 5));
        leftPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.GRAY), 
                "📁 DANH SÁCH FILE SERVER", 
                TitledBorder.LEFT, TitledBorder.TOP, new Font("Segoe UI", Font.BOLD, 12)));
        leftPanel.add(serverFilesPane, BorderLayout.CENTER);
        
        btnDownload = createBtn("TẢI FILE ĐANG CHỌN", new Color(40, 167, 69));
        leftPanel.add(btnDownload, BorderLayout.SOUTH);

        // Nửa phải: Log & Drag Drop Upload
        txtLog = new JTextArea();
        txtLog.setEditable(false);
        txtLog.setFont(new Font("Consolas", Font.PLAIN, 13));
        txtLog.setMargin(new Insets(10, 10, 10, 10));
        
        JScrollPane logPane = new JScrollPane(txtLog);
        logPane.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.GRAY), 
                "📝 NHẬT KÝ (KÉO THẢ FILE VÀO ĐÂY ĐỂ UPLOAD)", 
                TitledBorder.LEFT, TitledBorder.TOP, new Font("Segoe UI", Font.BOLD, 12)));

        splitPane.setLeftComponent(leftPanel);
        splitPane.setRightComponent(logPane);
        
        main.add(splitPane, BorderLayout.CENTER);

        // ===== BOTTOM: TIẾN ĐỘ =====
        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setFont(new Font("Consolas", Font.BOLD, 14));
        progressBar.setPreferredSize(new Dimension(100, 30));
        progressBar.setString("Sẵn sàng...");
        progressBar.setForeground(new Color(40, 167, 69));

        main.add(progressBar, BorderLayout.SOUTH);

        // --- SỰ KIỆN ---
        btnChoose.addActionListener(e -> chooseFilesAndUpload());
        btnCancel.addActionListener(e -> cancelTransfer());
        btnRefresh.addActionListener(e -> fetchServerFiles());
        btnDownload.addActionListener(e -> downloadSelectedFile());

        enableDragAndDrop(txtLog);
    }

    private JButton createBtn(String text, Color c) {
        JButton btn = new JButton(text);
        btn.setBackground(c);
        btn.setForeground(Color.WHITE);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setBorderPainted(false);
        return btn;
    }

    private void log(String msg) {
        String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
        SwingUtilities.invokeLater(() -> {
            txtLog.append("[" + time + "] " + msg + "\n");
            txtLog.setCaretPosition(txtLog.getDocument().getLength());
        });
    }

    // ================= TIỆN ÍCH MẠNG UDP =================
    private void sendUdpMessage(DatagramSocket socket, String msg, InetAddress addr, int port) throws Exception {
        byte[] data = msg.getBytes("UTF-8");
        DatagramPacket packet = new DatagramPacket(data, data.length, addr, port);
        socket.send(packet);
    }

    // ================= CHỨC NĂNG LẤY DANH SÁCH FILE =================
    private void fetchServerFiles() {
        new Thread(() -> {
            try (DatagramSocket socket = new DatagramSocket()) {
                socket.setSoTimeout(3000); // Chờ phản hồi tối đa 3 giây
                InetAddress serverIp = InetAddress.getByName(txtIp.getText().trim());
                int serverPort = Integer.parseInt(txtPort.getText().trim());
                String clientId = txtClientId.getText().trim();

                // Gửi lệnh LIST
                sendUdpMessage(socket, "LIST|" + clientId, serverIp, serverPort);

                // Nhận phản hồi
                byte[] buffer = new byte[65507];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String response = new String(packet.getData(), 0, packet.getLength(), "UTF-8");
                if (response.startsWith("LIST_RES")) {
                    SwingUtilities.invokeLater(() -> {
                        serverFileModel.clear();
                        String[] parts = response.split("\\|");
                        for (int i = 1; i < parts.length; i++) {
                            serverFileModel.addElement(parts[i]);
                        }
                        log("[INFO] Đã cập nhật danh sách file từ Server.");
                    });
                }
            } catch (SocketTimeoutException e) {
                log("[ERROR] Server không phản hồi. Vui lòng kiểm tra IP/Port.");
            } catch (Exception e) {
                log("[ERROR] Không thể lấy danh sách file: " + e.getMessage());
            }
        }).start();
    }

    // ================= CHỨC NĂNG DOWNLOAD =================
    private void downloadSelectedFile() {
        String selectedFile = listServerFiles.getSelectedValue();
        if (selectedFile == null) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn 1 file bên trái để tải về!", "Cảnh báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (isTransferring) {
            JOptionPane.showMessageDialog(this, "Hệ thống đang bận truyền file!", "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File(selectedFile));
        fc.setDialogTitle("Lưu file tải về ở đâu?");
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File saveFile = fc.getSelectedFile();
            startDownloadProcess(selectedFile, saveFile);
        }
    }

    private void startDownloadProcess(String fileName, File saveFile) {
        new Thread(() -> {
            isTransferring = true;
            isCancelled = false;
            SwingUtilities.invokeLater(() -> {
                btnCancel.setEnabled(true);
                btnChoose.setEnabled(false);
                btnDownload.setEnabled(false);
                log("[DOWNLOAD] Đang yêu cầu tải: " + fileName);
            });

            DatagramSocket controlSocket = null;
            DatagramSocket dataSocket = null;

            try {
                controlSocket = new DatagramSocket();
                controlSocket.setSoTimeout(5000);

                InetAddress serverIp = InetAddress.getByName(txtIp.getText().trim());
                int serverPort = Integer.parseInt(txtPort.getText().trim());
                String clientId = txtClientId.getText().trim();

                // 1. Gửi yêu cầu DOWNLOAD
                sendUdpMessage(controlSocket, "DOWNLOAD|" + clientId + "|" + fileName, serverIp, serverPort);

                // 2. Nhận phản hồi (dataPort + fileSize)
                byte[] buffer = new byte[PACKET_SIZE];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                controlSocket.receive(packet);

                String response = new String(packet.getData(), 0, packet.getLength(), "UTF-8");

                if (response.startsWith("ERROR")) {
                    log("[ERROR] Server báo lỗi: " + response.split("\\|")[1]);
                    return;
                }

                if (response.startsWith("DOWNLOAD_ACCEPT")) {
                    String[] parts = response.split("\\|");
                    int dataPort = Integer.parseInt(parts[1]);
                    long fileSize = Long.parseLong(parts[2]);

                    // ✅ QUAN TRỌNG: bind đúng port server cấp
                    dataSocket = new DatagramSocket(dataPort);
                    dataSocket.setSoTimeout(5000);

                    // 3. Gửi READY qua data socket
                    sendUdpMessage(dataSocket, "READY", serverIp, dataPort);

                    log("[DOWNLOAD] Bắt đầu nhận file qua Port " + dataPort);

                    // 4. Nhận file
                    try (FileOutputStream fos = new FileOutputStream(saveFile)) {
                        long totalRead = 0;
                        long startTime = System.currentTimeMillis();

                        while (totalRead < fileSize) {
                            if (isCancelled) {
                                log("[ERROR] Đã hủy tải: " + fileName);
                                break;
                            }

                            DatagramPacket dataPacket = new DatagramPacket(buffer, buffer.length);
                            dataSocket.receive(dataPacket);

                            fos.write(dataPacket.getData(), 0, dataPacket.getLength());
                            totalRead += dataPacket.getLength();

                            // Gửi ACK
                            sendUdpMessage(dataSocket, "ACK", serverIp, dataPort);

                            updateProgress(totalRead, fileSize, startTime);
                        }

                        if (!isCancelled) {
                            log("[OK] Tải thành công: " + saveFile.getName());
                        }
                    }
                }

            } catch (SocketTimeoutException e) {
                if (!isCancelled) log("[ERROR] Mất kết nối khi đang tải file!");
            } catch (Exception e) {
                if (!isCancelled) log("[ERROR] Lỗi mạng khi tải: " + e.getMessage());
            } finally {
                if (controlSocket != null && !controlSocket.isClosed()) controlSocket.close();
                if (dataSocket != null && !dataSocket.isClosed()) dataSocket.close();
                resetTransferState();
            }
        }).start();
    }

    // ================= CHỨC NĂNG UPLOAD =================
    private void enableDragAndDrop(JComponent component) {
        component.setDropTarget(new DropTarget() {
            public synchronized void drop(DropTargetDropEvent evt) {
                try {
                    evt.acceptDrop(DnDConstants.ACTION_COPY);
                    List<File> droppedFiles = (List<File>) evt.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    if (droppedFiles != null && !droppedFiles.isEmpty()) {
                        startUploadQueue(droppedFiles);
                    }
                } catch (Exception ex) {
                    log("[ERROR] Lỗi kéo thả: " + ex.getMessage());
                }
            }
        });
    }

    private void chooseFilesAndUpload() {
        JFileChooser fc = new JFileChooser();
        fc.setMultiSelectionEnabled(true);
        fc.setDialogTitle("Chọn các file muốn tải lên");

        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            startUploadQueue(List.of(fc.getSelectedFiles()));
        }
    }

    private void startUploadQueue(List<File> files) {
        if (isTransferring) {
            JOptionPane.showMessageDialog(this, "Hệ thống đang bận truyền file!", "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        new Thread(() -> {
            isTransferring = true;
            isCancelled = false;
            SwingUtilities.invokeLater(() -> {
                btnCancel.setEnabled(true);
                btnChoose.setEnabled(false);
                btnDownload.setEnabled(false);
            });

            for (int i = 0; i < files.size(); i++) {
                if (isCancelled) break;
                File file = files.get(i);
                
                if (file.isDirectory()) {
                    log("[SKIP] Bỏ qua thư mục: " + file.getName());
                    continue;
                }

                log("[UPLOAD] Đang tải lên (" + (i + 1) + "/" + files.size() + "): " + file.getName());
                uploadFileData(file);
            }

            resetTransferState();
            fetchServerFiles(); 
        }).start();
    }

    private void uploadFileData(File file) {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(5000);
            InetAddress serverIp = InetAddress.getByName(txtIp.getText().trim());
            int serverPort = Integer.parseInt(txtPort.getText().trim());
            String clientId = txtClientId.getText().trim();
            long fileSize = file.length();

            // 1. Xin phép Upload
            sendUdpMessage(socket, "UPLOAD|" + clientId + "|" + file.getName() + "|" + fileSize, serverIp, serverPort);

            // 2. Chờ Server phản hồi Data Port
            byte[] buffer = new byte[PACKET_SIZE];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            socket.receive(packet);
            String response = new String(packet.getData(), 0, packet.getLength(), "UTF-8");

            if (response.startsWith("UPLOAD_ACCEPT")) {
                int dataPort = Integer.parseInt(response.split("\\|")[1]);
                log("[UPLOAD] Bắt đầu đẩy file qua Port " + dataPort);

                // 3. Tiến hành gửi file (Stop-and-Wait)
                try (FileInputStream fis = new FileInputStream(file)) {
                    byte[] fileBuf = new byte[PACKET_SIZE];
                    int read;
                    long totalRead = 0;
                    long startTime = System.currentTimeMillis();

                    while ((read = fis.read(fileBuf)) != -1) {
                        if (isCancelled) {
                            log("[ERROR] Đã hủy tải lên: " + file.getName());
                            break;
                        }

                        DatagramPacket chunkPacket = new DatagramPacket(fileBuf, read, serverIp, dataPort);
                        boolean ackReceived = false;
                        int retries = 0;

                        // Cố gắng gửi khối dữ liệu (Thử lại tối đa 3 lần nếu timeout)
                        while (!ackReceived && retries < 3) {
                            socket.send(chunkPacket);
                            byte[] ackBuf = new byte[10];
                            DatagramPacket ackPacket = new DatagramPacket(ackBuf, ackBuf.length);
                            try {
                                socket.receive(ackPacket);
                                ackReceived = true;
                            } catch (SocketTimeoutException e) {
                                retries++;
                            }
                        }

                        if (!ackReceived) {
                            log("[ERROR] Mất kết nối, gửi file thất bại!");
                            return;
                        }

                        totalRead += read;
                        updateProgress(totalRead, fileSize, startTime);
                    }
                    if (!isCancelled) log("[OK] Tải lên thành công: " + file.getName());
                }
            }
        } catch (SocketTimeoutException e) {
            if (!isCancelled) log("[ERROR] Lỗi mạng: Server không phản hồi thao tác Upload!");
        } catch (Exception e) {
            if (!isCancelled) log("[ERROR] Lỗi mạng khi upload " + file.getName() + ": " + e.getMessage());
        }
    }

    // ================= TIỆN ÍCH CHUNG =================
    private void cancelTransfer() {
        if (isTransferring) {
            isCancelled = true;
            log("[WARNING] Đang ngắt truyền tải...");
            btnCancel.setEnabled(false);
        }
    }

    private void resetTransferState() {
        isTransferring = false;
        SwingUtilities.invokeLater(() -> {
            btnCancel.setEnabled(false);
            btnChoose.setEnabled(true);
            btnDownload.setEnabled(true);
            if (isCancelled) {
                progressBar.setString("Đã hủy truyền file!");
                progressBar.setValue(0);
            } else {
                progressBar.setString("Hoàn tất!");
                progressBar.setValue(100);
            }
        });
    }

    private void updateProgress(long totalRead, long fileSize, long startTime) {
        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - startTime;
        
        // Hạn chế cập nhật UI liên tục để tránh lag
        if (elapsedTime > 0 && (totalRead == fileSize || totalRead % (PACKET_SIZE * 5) == 0)) {
            int percent = (int) ((totalRead * 100) / fileSize);
            double speedMBps = (totalRead / 1048576.0) / (elapsedTime / 1000.0);
            String speedStr = speedMBps >= 1.0 
                    ? String.format("%.1f MB/s", speedMBps) 
                    : String.format("%.0f KB/s", (totalRead / 1024.0) / (elapsedTime / 1000.0));

            String progressText = String.format("%d%% | %s | %s / %s", 
                    percent, speedStr, formatSize(totalRead), formatSize(fileSize));

            SwingUtilities.invokeLater(() -> {
                progressBar.setValue(percent);
                progressBar.setString(progressText);
            });
        }
    }

    private String formatSize(long size) {
        if (size >= 1048576) return String.format("%.1f MB", size / 1048576.0);
        if (size >= 1024) return String.format("%.1f KB", size / 1024.0);
        return size + " B";
    }
}