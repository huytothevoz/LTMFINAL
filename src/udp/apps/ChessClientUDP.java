package udp.apps;

import javax.swing.*;
import java.awt.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class ChessClientUDP extends JFrame {
    private JButton[][] squares = new JButton[8][8];
    private JPanel boardPanel;
    private JLabel statusLabel;
    private JLabel opponentLabel; 
    
    private boolean isWhite; 
    private boolean myTurn;  
    private boolean gameOver = false; 
    private int selectedRow = -1, selectedCol = -1;
    
    private DatagramSocket socket;
    private InetAddress serverIP;
    private int serverPort;

    // Bảng màu chuẩn Chess.com
    private final Color COLOR_LIGHT = Color.decode("#ebecd0");
    private final Color COLOR_DARK = Color.decode("#739552");
    private final Color COLOR_SELECTED = Color.decode("#bacb42");
    private final Color COLOR_HIGHLIGHT = Color.decode("#d6e685"); 

    private final String[][] INITIAL_BOARD = {
        {"♜", "♞", "♝", "♛", "♚", "♝", "♞", "♜"},
        {"♟", "♟", "♟", "♟", "♟", "♟", "♟", "♟"},
        {"", "", "", "", "", "", "", ""},
        {"", "", "", "", "", "", "", ""},
        {"", "", "", "", "", "", "", ""},
        {"", "", "", "", "", "", "", ""},
        {"♙", "♙", "♙", "♙", "♙", "♙", "♙", "♙"},
        {"♖", "♘", "♗", "♕", "♔", "♗", "♘", "♖"}
    };

    public ChessClientUDP(String ip, int port, boolean isWhite) {
        this.isWhite = isWhite;
        this.myTurn = isWhite; // Trắng luôn đi trước
        this.serverPort = port;
        
        setupUI();
        initializeBoard();
        connectToServer(ip);
        updateStatus(); // Cập nhật text lần đầu
    }

    private void setupUI() {
        setTitle(isWhite ? "Cờ Vua - Bạn cầm TRẮNG" : "Cờ Vua - Bạn cầm ĐEN");
        setSize(700, 750);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // --- Giao diện bên trên (Đối thủ) ---
        String oppColor = isWhite ? "ĐEN" : "TRẮNG";
        opponentLabel = new JLabel("ĐỐI THỦ CỦA BẠN (CẦM QUÂN " + oppColor + ")", SwingConstants.CENTER);
        opponentLabel.setFont(new Font("Arial", Font.BOLD, 16));
        opponentLabel.setOpaque(true);
        opponentLabel.setBackground(new Color(220, 220, 220));
        opponentLabel.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
        add(opponentLabel, BorderLayout.NORTH);

        // --- Giao diện Bàn cờ ---
        boardPanel = new JPanel(new GridLayout(8, 8));
        
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                JButton btn = new JButton();
                btn.setFocusPainted(false);
                btn.setOpaque(true);
                btn.setBorderPainted(false); 
                btn.setMargin(new Insets(0, 0, 0, 0)); // Ép lề về 0 để chữ to không bị ...
                btn.setBackground((r + c) % 2 == 0 ? COLOR_LIGHT : COLOR_DARK);
                
                int finalR = r;
                int finalC = c;
                btn.addActionListener(e -> handleSquareClick(finalR, finalC));
                
                squares[r][c] = btn;
                boardPanel.add(btn);
            }
        }

        // Tự động co giãn Font chữ khi resize cửa sổ
        boardPanel.addComponentListener(new java.awt.event.ComponentAdapter() {
            public void componentResized(java.awt.event.ComponentEvent evt) {
                int squareSize = Math.min(boardPanel.getWidth() / 8, boardPanel.getHeight() / 8);
                int fontSize = Math.max(10, squareSize - 15); 
                Font dynamicFont = new Font("Dialog", Font.PLAIN, fontSize);
                for (int r = 0; r < 8; r++) {
                    for (int c = 0; c < 8; c++) {
                        squares[r][c].setFont(dynamicFont);
                    }
                }
            }
        });

        // --- Giao diện bên dưới (Trạng thái của bạn) ---
        statusLabel = new JLabel("", SwingConstants.CENTER);
        statusLabel.setFont(new Font("Arial", Font.BOLD, 18));
        statusLabel.setOpaque(true);
        statusLabel.setPreferredSize(new Dimension(700, 40));

        add(boardPanel, BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);
    }

    private void initializeBoard() {
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                squares[r][c].setText(INITIAL_BOARD[r][c]);
            }
        }
    }

    // ================= LOGIC XỬ LÝ CLICK =================
    private void handleSquareClick(int r, int c) {
        if (gameOver) {
            JOptionPane.showMessageDialog(this, "Trò chơi đã kết thúc!");
            return;
        }
        
        if (!myTurn) return;

        String targetPiece = squares[r][c].getText();

        // 1. Nếu chưa chọn quân nào -> Chọn quân của mình
        if (selectedRow == -1) {
            if (!targetPiece.isEmpty() && isMyPiece(targetPiece)) {
                selectedRow = r;
                selectedCol = c;
                highlightValidMoves(r, c);
            }
        } 
        // 2. Nếu đã chọn quân -> Di chuyển hoặc Đổi quân
        else {
            if (!targetPiece.isEmpty() && isMyPiece(targetPiece)) {
                resetBoardColors();
                selectedRow = r;
                selectedCol = c;
                highlightValidMoves(r, c);
                return;
            }

            if (isValidMove(selectedRow, selectedCol, r, c)) {
                movePieceUI(selectedRow, selectedCol, r, c); // Di chuyển & Check End Game
                sendMoveUDP(selectedRow, selectedCol, r, c);
                
                if (!gameOver) {
                    myTurn = false;
                    updateStatus();
                }
            }
            
            resetBoardColors();
            selectedRow = -1;
            selectedCol = -1;
        }
    }

    private void highlightValidMoves(int fromR, int fromC) {
        resetBoardColors();
        squares[fromR][fromC].setBackground(COLOR_SELECTED); 
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (isValidMove(fromR, fromC, r, c)) {
                    squares[r][c].setBackground(COLOR_HIGHLIGHT); 
                }
            }
        }
    }

    private void resetBoardColors() {
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                squares[r][c].setBackground((r + c) % 2 == 0 ? COLOR_LIGHT : COLOR_DARK);
            }
        }
    }

    // ================= MẠNG & UI UPDATE =================
    private void updateStatus() {
        if (gameOver) return;
        String myColor = isWhite ? "TRẮNG" : "ĐEN";
        statusLabel.setText(myTurn ? "BẠN CẦM (" + myColor + ") - ĐẾN LƯỢT" : "BẠN CẦM (" + myColor + ") - ĐỐI THỦ ĐANG SUY NGHĨ...");
        statusLabel.setBackground(myTurn ? new Color(40, 167, 69) : Color.DARK_GRAY);
        statusLabel.setForeground(Color.WHITE);
    }

    // HÀM QUAN TRỌNG: Cập nhật nước đi và KIỂM TRA END GAME
    private void movePieceUI(int fromR, int fromC, int toR, int toC) {
        String targetPiece = squares[toR][toC].getText();
        
        // Cảnh báo nếu ăn được Vua (End Game)
        if (targetPiece.equals("♔") || targetPiece.equals("♚")) {
            gameOver = true;
            String winner = targetPiece.equals("♔") ? "QUÂN ĐEN" : "QUÂN TRẮNG";
            
            SwingUtilities.invokeLater(() -> {
                statusLabel.setText("GAME OVER! " + winner + " ĐÃ CHIẾN THẮNG!");
                statusLabel.setBackground(Color.RED);
                JOptionPane.showMessageDialog(this, "Trò chơi kết thúc!\nPhe " + winner + " đã bắt được Vua.", "Checkmate", JOptionPane.WARNING_MESSAGE);
            });
        }

        squares[toR][toC].setText(squares[fromR][fromC].getText());
        squares[fromR][fromC].setText("");
    }

    // ================= ENGINE LUẬT CỜ LITE =================
    private boolean isValidMove(int fromR, int fromC, int toR, int toC) {
        String piece = squares[fromR][fromC].getText();
        String target = squares[toR][toC].getText();
        
        boolean isPieceWhite = "♔♕♖♗♘♙".contains(piece);
        boolean isTargetWhite = "♔♕♖♗♘♙".contains(target);
        if (!target.isEmpty() && (isPieceWhite == isTargetWhite)) return false;

        int dr = toR - fromR;
        int dc = toC - fromC;
        int absDr = Math.abs(dr);
        int absDc = Math.abs(dc);

        if (piece.equals("♙") || piece.equals("♟")) {
            int dir = isPieceWhite ? -1 : 1; 
            int startRow = isPieceWhite ? 6 : 1;
            if (dc == 0 && dr == dir && target.isEmpty()) return true; 
            if (dc == 0 && dr == 2 * dir && fromR == startRow && target.isEmpty() && squares[fromR + dir][fromC].getText().isEmpty()) return true; 
            if (absDc == 1 && dr == dir && !target.isEmpty()) return true; 
            return false;
        }
        if (piece.equals("♖") || piece.equals("♜")) return (fromR == toR || fromC == toC) && isPathClear(fromR, fromC, toR, toC);
        if (piece.equals("♗") || piece.equals("♝")) return (absDr == absDc) && isPathClear(fromR, fromC, toR, toC);
        if (piece.equals("♕") || piece.equals("♛")) {
            if ((fromR == toR || fromC == toC) || (absDr == absDc)) return isPathClear(fromR, fromC, toR, toC);
            return false;
        }
        if (piece.equals("♘") || piece.equals("♞")) return (absDr == 2 && absDc == 1) || (absDr == 1 && absDc == 2);
        if (piece.equals("♔") || piece.equals("♚")) return absDr <= 1 && absDc <= 1;

        return false;
    }

    private boolean isPathClear(int fromR, int fromC, int toR, int toC) {
        int rDir = Integer.compare(toR, fromR);
        int cDir = Integer.compare(toC, fromC);
        int r = fromR + rDir;
        int c = fromC + cDir;
        while (r != toR || c != toC) {
            if (!squares[r][c].getText().isEmpty()) return false;
            r += rDir;
            c += cDir;
        }
        return true;
    }

    private boolean isMyPiece(String piece) {
        boolean isPieceWhite = "♔♕♖♗♘♙".contains(piece);
        return isWhite == isPieceWhite;
    }

    // ================= MẠNG UDP =================
    private void connectToServer(String ip) {
        try {
            socket = new DatagramSocket(); 
            serverIP = InetAddress.getByName(ip);
            new Thread(() -> {
                try {
                    for (int i = 0; i < 3; i++) {
                        sendData("JOIN|" + (isWhite ? "WHITE" : "BLACK"));
                        Thread.sleep(300);
                    }
                } catch (Exception ignored) {}
            }).start();

            startListening();

        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Không thể kết nối server!");
        }
    }

    private void sendMoveUDP(int fromR, int fromC, int toR, int toC) {
        sendData("MOVE|" + fromR + "|" + fromC + "|" + toR + "|" + toC);
    }

    private void sendData(String msg) {
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
                byte[] buffer = new byte[2048];

                while (!socket.isClosed()) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);

                    String msg = new String(packet.getData(), 0, packet.getLength(), "UTF-8");

                    // FIX: luôn update UI bằng EDT
                    SwingUtilities.invokeLater(() -> {

                        // ===== ACK từ server =====
                        if (msg.equals("CONNECTED")) {
                            statusLabel.setText("✅ Đã kết nối server!");
                            return;
                        }

                        // ===== MOVE =====
                        if (msg.startsWith("MOVE|")) {
                            try {
                                String[] parts = msg.split("\\|");

                                int fromR = Integer.parseInt(parts[1]);
                                int fromC = Integer.parseInt(parts[2]);
                                int toR = Integer.parseInt(parts[3]);
                                int toC = Integer.parseInt(parts[4]);

                                movePieceUI(fromR, fromC, toR, toC);

                                if (!gameOver) {
                                    myTurn = true;
                                    updateStatus();
                                }
                            } catch (Exception ignored) {}
                        }
                    });
                }
            } catch (Exception ignored) {}
        }).start();
    }
}