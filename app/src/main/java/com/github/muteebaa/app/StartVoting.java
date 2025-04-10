package com.github.muteebaa.app;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class StartVoting {
    private static ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static ScheduledFuture<?> beatHandle;
    private static PeerNode currentPeer;
    private static JFrame mainFrame;
    private static CardLayout cardLayout;
    private static JPanel cardPanel;
    private static JTextArea sessionsTextArea;
    private static JTextArea statusTextArea;
    private static JPanel buttonPanel; // Added for waiting panel button access
    private static JPanel sessionListPanel; // To update later in displayAvailableSessions()

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            createAndShowGUI();
        });
    }

    private static void createAndShowGUI() {
        mainFrame = new JFrame("Voting System");
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        mainFrame.setSize(800, 600);

        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);

        JPanel mainMenuPanel = createMainMenuPanel();
        JPanel newElectionPanel = createNewElectionPanel();
        JPanel sessionsPanel = createSessionsPanel();
        JPanel waitingPanel = createWaitingPanel();

        cardPanel.add(mainMenuPanel, "MAIN");
        cardPanel.add(newElectionPanel, "NEW_ELECTION");
        cardPanel.add(sessionsPanel, "SESSIONS");
        cardPanel.add(waitingPanel, "WAITING");

        mainFrame.add(cardPanel);
        mainFrame.setLocationRelativeTo(null);
        mainFrame.setVisible(true);
    }

    private static JPanel createMainMenuPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel welcomeLabel = new JLabel("Welcome to the voting system!");
        welcomeLabel.setFont(new Font("Arial", Font.BOLD, 18));
        welcomeLabel.setHorizontalAlignment(SwingConstants.CENTER);

        JPanel optionsPanel = new JPanel(new GridLayout(3, 1, 10, 10));
        
        JButton newElectionBtn = new JButton("1. Start a new election");
        JButton joinElectionBtn = new JButton("2. Join an existing election");
        JButton viewSessionsBtn = new JButton("3. View all sessions");

        newElectionBtn.addActionListener(e -> cardLayout.show(cardPanel, "NEW_ELECTION"));
        joinElectionBtn.addActionListener(e -> showJoinElectionPanel());
        viewSessionsBtn.addActionListener(e -> {
            cardLayout.show(cardPanel, "SESSIONS");
            SessionRegistry.displayAvailableSessions(sessionListPanel); // <-- this is the fix
        });

        optionsPanel.add(newElectionBtn);
        optionsPanel.add(joinElectionBtn);
        optionsPanel.add(viewSessionsBtn);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(10, 0, 10, 0);
        
        panel.add(welcomeLabel, gbc);
        panel.add(optionsPanel, gbc);

        return panel;
    }

    private static JPanel createNewElectionPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel titleLabel = new JLabel("Start New Election");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 16));
        
        JLabel portLabel = new JLabel("Enter your node's port number:");
        JTextField portField = new JTextField(15);
        
        JLabel optionsLabel = new JLabel("Enter comma-separated voting options:");
        JTextField optionsField = new JTextField(15);
        
        JButton createBtn = new JButton("Create Session");
        JButton backBtn = new JButton("Back to Main Menu");

        createBtn.addActionListener(e -> {
    try {
        int myPort = Integer.parseInt(portField.getText());
        String options = optionsField.getText();

        currentPeer = new PeerNode(myPort);
    
        // Unified message consumer for all status messages
        Consumer<String> statusConsumer = message -> {
            SwingUtilities.invokeLater(() -> {
                String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
                
                // Color code different message types
                if (message.contains("Heartbeat")) {
                    statusTextArea.append("[" + timestamp + "] [HEARTBEAT] " + message + "\n");
                } else if (message.contains("Vote") || message.contains("tally")) {
                    statusTextArea.append("[" + timestamp + "] [VOTE] " + message + "\n");
                } else if (message.contains("Election") || message.contains("Leader")) {
                    statusTextArea.append("[" + timestamp + "] [ELECTION] " + message + "\n");
                } else {
                    statusTextArea.append("[" + timestamp + "] " + message + "\n");
                }
                
                statusTextArea.setCaretPosition(statusTextArea.getDocument().getLength());
            });
        };

        // Set up all consumers to use the same status handler
        currentPeer.setStatusMessageConsumer(statusConsumer);
        currentPeer.setHeartbeatStatusConsumer(statusConsumer);
        currentPeer.setGuiMessageConsumer(statusConsumer);

        currentPeer.startPeer();
        String sessionCode = currentPeer.startNewSession(options);
        currentPeer.registerWithLeader(currentPeer.getMyIp() + ":" + myPort);

        statusTextArea.setText("Session created!\nShare this code: " + sessionCode + 
                         "\nVoting options: " + options + 
                         "\n\nWaiting for participants...");
        cardLayout.show(cardPanel, "WAITING");

        // Add Start Election button to waiting panel
        JButton startElectionBtn = new JButton("Start Election");
        startElectionBtn.addActionListener(ev -> {
                currentPeer.startVotingButtonClicked();
            
        });

        buttonPanel.add(startElectionBtn, 0);
        buttonPanel.revalidate();
        buttonPanel.repaint();

    } catch (NumberFormatException ex) {
        JOptionPane.showMessageDialog(mainFrame, 
            "Please enter a valid port number", 
            "Error", JOptionPane.ERROR_MESSAGE);
    }
});

        backBtn.addActionListener(e -> cardLayout.show(cardPanel, "MAIN"));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 0, 5, 0);
        
        panel.add(titleLabel, gbc);
        panel.add(portLabel, gbc);
        panel.add(portField, gbc);
        panel.add(optionsLabel, gbc);
        panel.add(optionsField, gbc);
        
        JPanel buttonPanel = new JPanel(new GridLayout(1, 2, 10, 0));
        buttonPanel.add(createBtn);
        buttonPanel.add(backBtn);
        
        panel.add(buttonPanel, gbc);
        return panel;
    }

    private static void showJoinElectionPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel titleLabel = new JLabel("Join Existing Election");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 16));
    
        JLabel codeLabel = new JLabel("Enter session code:");
        JTextField codeField = new JTextField(15);
    
        JLabel portLabel = new JLabel("Enter your node's port number:");
        JTextField portField = new JTextField(15);
    
        JButton joinBtn = new JButton("Join Session");
        JButton backBtn = new JButton("Back to Main Menu");

        joinBtn.addActionListener(e -> {
            String sessionCode = codeField.getText();
            Map<String, String> sessions = SessionRegistry.loadSessions();

            if (sessions.containsKey(sessionCode)) {
                String sessionDetails = sessions.get(sessionCode);
                String[] parts = sessionDetails.split(",");
                String leaderAddress = parts[0];
                String sessionStatus = parts.length > 1 ? parts[1] : "unknown";

                if ("ended".equals(sessionStatus)) {
                    JOptionPane.showMessageDialog(mainFrame, 
                        "Sorry, this session has already ended!", 
                        "Session Ended", 
                        JOptionPane.ERROR_MESSAGE);
                    return;
                } else if ("started".equals(sessionStatus)) {
                    JOptionPane.showMessageDialog(mainFrame, 
                        "Sorry, this session is already in progress!", 
                        "Session In Progress", 
                        JOptionPane.ERROR_MESSAGE);
                    return;
                }

                try {
                    int myPort = Integer.parseInt(portField.getText());
                
                    currentPeer = new PeerNode(myPort);
                
                    currentPeer.setStatusMessageConsumer(status -> {
                        SwingUtilities.invokeLater(() -> {
                            String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
                            statusTextArea.append("[" + timestamp + "] " + status + "\n");
                            statusTextArea.setCaretPosition(statusTextArea.getDocument().getLength());
                        });
                    });

                    currentPeer.setHeartbeatStatusConsumer(status -> {
                        SwingUtilities.invokeLater(() -> {
                            String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
                            statusTextArea.append("[" + timestamp + "] HEARTBEAT: " + status + "\n");
                            statusTextArea.setCaretPosition(statusTextArea.getDocument().getLength());
                        });
                    });

                    currentPeer.setSessionCode(sessionCode);
                    currentPeer.startPeer();
                    currentPeer.registerWithLeader(leaderAddress);

                    Runnable heartbeat = new SessionHeartbeat(currentPeer);
                    beatHandle = scheduler.scheduleAtFixedRate(heartbeat, 10, 10, TimeUnit.SECONDS);

                    statusTextArea.setText("Successfully joined session: " + sessionCode + 
                                     "\nLeader: " + leaderAddress +
                                     "\n\nWaiting for voting to start...");
                    cardLayout.show(cardPanel, "WAITING");
                
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(mainFrame, 
                        "Please enter a valid port number", 
                        "Error", JOptionPane.ERROR_MESSAGE);
                }
            } else {
                JOptionPane.showMessageDialog(mainFrame, 
                    "Invalid session code!", 
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        backBtn.addActionListener(e -> cardLayout.show(cardPanel, "MAIN"));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 0, 5, 0);
    
        panel.add(titleLabel, gbc);
        panel.add(codeLabel, gbc);
        panel.add(codeField, gbc);
        panel.add(portLabel, gbc);
        panel.add(portField, gbc);
    
        JPanel buttonPanel = new JPanel(new GridLayout(1, 2, 10, 0));
        buttonPanel.add(joinBtn);
        buttonPanel.add(backBtn);
    
        panel.add(buttonPanel, gbc);

        cardPanel.add(panel, "JOIN_ELECTION");
        cardLayout.show(cardPanel, "JOIN_ELECTION");
    }

    private static JPanel createSessionsPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        sessionListPanel = new JPanel();
        sessionListPanel.setLayout(new BoxLayout(sessionListPanel, BoxLayout.Y_AXIS));

        JScrollPane scrollPane = new JScrollPane(sessionListPanel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

        JButton backButton = new JButton("Back to Main Menu");
        backButton.addActionListener(e -> cardLayout.show(cardPanel, "MAIN"));

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(backButton);

        panel.add(scrollPane, BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        // Call this here or wherever appropriate
        SessionRegistry.displayAvailableSessions(sessionListPanel);

        return panel;
    }

    private static JPanel createWaitingPanel() {
        JPanel panel = new JPanel(new BorderLayout());
    
        statusTextArea = new JTextArea();
        statusTextArea.setEditable(false);
        statusTextArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(statusTextArea);
    
        buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
    
        JButton clearButton = new JButton("Clear Messages");
        clearButton.addActionListener(e -> statusTextArea.setText(""));
    
        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> {
            if (beatHandle != null) {
                beatHandle.cancel(true);
            }
            if (currentPeer != null) {
                currentPeer.setStatusMessageConsumer(null);
            }
            cardLayout.show(cardPanel, "MAIN");
        });
    
        buttonPanel.add(clearButton);
        buttonPanel.add(cancelButton);
    
        panel.add(scrollPane, BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);
    
        return panel;
    }
}
