package udp.apps;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.net.*;

public class SharedNotepadClientUDP extends JFrame {
    private JTextArea textArea;
    private JLabel statusLabel;
    
    private DatagramSocket socket;
    private InetAddress serverIP;
    private int serverPort;
    private String username;
    
    private boolean isUpdatingFromNetwork = false;

    public SharedNotepadClientUDP(String ip, int port, String username) {
        this.serverPort = port;
        this.username = username;
        setupUI();
        connectToServer(ip);
    }

    private void setupUI() {
        setTitle("UDP Shared Notepad - " + username);
        setSize(600, 450);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
                dispose();
            }
        });

        textArea = new JTextArea();
        textArea.setFont(new Font("Arial", Font.PLAIN, 16));
        textArea.setMargin(new Insets(10, 10, 10, 10));
        
        textArea.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { sendUpdate(); }
            public void removeUpdate(DocumentEvent e) { sendUpdate(); }
            public void changedUpdate(DocumentEvent e) { sendUpdate(); }
        });

        statusLabel = new JLabel("Đang kết nối...", SwingConstants.CENTER);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        add(new JScrollPane(textArea), BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);
    }

    private void connectToServer(String ip) {
        try {
            socket = new DatagramSocket(); 

            serverIP = InetAddress.getByName(ip);

            sendUDPMessage("JOIN|" + username);

            startListening();

        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Không thể kết nối: " + e.getMessage());
        }
    }

    private void sendUpdate() {
        if (isUpdatingFromNetwork) return;

        if (socket == null || socket.isClosed()) return;

        String currentText = textArea.getText();
        sendUDPMessage("UPDATE|" + currentText);
    }

    private void sendUDPMessage(String msg) {
        try {
            if (socket != null && !socket.isClosed()) {
                byte[] data = msg.getBytes("UTF-8");
                DatagramPacket packet = new DatagramPacket(data, data.length, serverIP, serverPort);
                socket.send(packet);
            }
        } catch (Exception ignored) {}
    }

    private void startListening() {
        new Thread(() -> {
            try {
                // ===== BUFFER MAX =====
                byte[] buffer = new byte[65507];

                while (socket != null && !socket.isClosed()) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);

                    String message = new String(packet.getData(), 0, packet.getLength(), "UTF-8");

                    if (message.startsWith("SYNC|")) {
                        String serverText = message.substring(5);

                        SwingUtilities.invokeLater(() -> {
                            isUpdatingFromNetwork = true;

                            int caretPos = textArea.getCaretPosition();
                            textArea.setText(serverText);

                            try {
                                textArea.setCaretPosition(Math.min(caretPos, serverText.length()));
                            } catch (Exception ignored) {}

                            isUpdatingFromNetwork = false;

                            statusLabel.setText("Đã đồng bộ mạng. Online!");
                            statusLabel.setForeground(new Color(40, 167, 69));
                        });
                    }
                }
            } catch (SocketException se) {
                System.out.println("UDP listener stopped.");
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> statusLabel.setText("Mất kết nối!"));
            }
        }).start();
    }
}