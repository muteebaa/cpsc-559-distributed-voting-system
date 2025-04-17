package com.github.muteebaa.app;

import javax.swing.*;
import javax.swing.table.JTableHeader;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.ArrayList;
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

        JLabel welcomeLabel = new JLabel("Welcome to the Voting System!");
        welcomeLabel.setFont(new Font("Arial", Font.BOLD, 24));
        welcomeLabel.setForeground(new Color(23, 3, 18)); // Dark blue-gray text
        welcomeLabel.setHorizontalAlignment(SwingConstants.CENTER);
        welcomeLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 20, 0));

        JPanel optionsPanel = new JPanel(new GridLayout(3, 1, 10, 10));

        JButton newElectionBtn = new JButton("1. Start a new election");
        JButton joinElectionBtn = new JButton("2. Join an existing election");
        JButton viewSessionsBtn = new JButton("3. View all sessions");

        styleButton(newElectionBtn, new Color(83, 18, 83), Color.WHITE); // Steel blue
        styleButton(joinElectionBtn, new Color(83, 18, 83), Color.WHITE); // Green
        styleButton(viewSessionsBtn, new Color(83, 18, 83), Color.WHITE); // Purple

        // Set preferred size for consistency
        Dimension buttonSize = new Dimension(250, 50);
        newElectionBtn.setPreferredSize(buttonSize);
        joinElectionBtn.setPreferredSize(buttonSize);
        viewSessionsBtn.setPreferredSize(buttonSize);

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

    // ... [Previous imports and class declaration remain the same]

    private static JPanel createNewElectionPanel() {
        // Create main panel with border layout
        JPanel panel = new JPanel(new BorderLayout(20, 20));
        panel.setBorder(BorderFactory.createEmptyBorder(40, 60, 40, 60));
        panel.setBackground(new Color(240, 240, 245));

        // Header panel
        JPanel headerPanel = new JPanel();
        headerPanel.setBackground(new Color(240, 240, 245));
        JLabel titleLabel = new JLabel("Start New Election");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 24));
        titleLabel.setForeground(new Color(50, 50, 80));
        headerPanel.add(titleLabel);
        panel.add(headerPanel, BorderLayout.NORTH);

        // Center form panel
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBackground(new Color(240, 240, 245));
        formPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Port input
        JLabel portLabel = new JLabel("Your Node's Port Number:");
        portLabel.setFont(new Font("Arial", Font.PLAIN, 14));
        gbc.gridx = 0;
        gbc.gridy = 0;
        formPanel.add(portLabel, gbc);

        JTextField portField = new JTextField(20);
        portField.setFont(new Font("Arial", Font.PLAIN, 14));
        portField.setPreferredSize(new Dimension(200, 30));
        gbc.gridx = 1;
        formPanel.add(portField, gbc);

        // Options input
        JLabel optionsLabel = new JLabel("Voting Options (comma-separated):");
        optionsLabel.setFont(new Font("Arial", Font.PLAIN, 14));
        gbc.gridx = 0;
        gbc.gridy = 1;
        formPanel.add(optionsLabel, gbc);

        JTextField optionsField = new JTextField(20);
        optionsField.setFont(new Font("Arial", Font.PLAIN, 14));
        optionsField.setPreferredSize(new Dimension(200, 30));
        gbc.gridx = 1;
        formPanel.add(optionsField, gbc);

        panel.add(formPanel, BorderLayout.CENTER);

        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10));
        buttonPanel.setBackground(new Color(240, 240, 245));

        JButton createBtn = new JButton("Create Session");
        styleButton(createBtn, new Color(83, 18, 83), Color.WHITE);
        createBtn.setPreferredSize(new Dimension(180, 40));

        JButton backBtn = new JButton("Main Menu");
        styleButton(backBtn, new Color(220, 80, 60), Color.WHITE);
        backBtn.setPreferredSize(new Dimension(180, 40));

        createBtn.addActionListener(e -> {
            try {
                int myPort = Integer.parseInt(portField.getText());

                String options = optionsField.getText();

                if (options.isEmpty()) {
                    JOptionPane.showMessageDialog(mainFrame,
                            "Please enter voting options (comma-separated)",
                            "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                currentPeer = new PeerNode(myPort);

                // Create status panel
                JPanel statusPanel = new JPanel(new BorderLayout());
                statusPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
                statusPanel.setBackground(Color.WHITE);

                // Header with session info
                JPanel headerPanel2 = new JPanel(new GridLayout(0, 1, 5, 5));
                headerPanel2.setBackground(Color.WHITE);

                JLabel sessionTitle = new JLabel("Election Session Created", SwingConstants.CENTER);
                sessionTitle.setFont(new Font("Arial", Font.BOLD, 18));
                sessionTitle.setForeground(new Color(44, 62, 80));

                JLabel sessionCodeLabel = new JLabel("Session Code:", SwingConstants.CENTER);
                sessionCodeLabel.setFont(new Font("Arial", Font.BOLD, 14));

                JLabel codeDisplay = new JLabel("", SwingConstants.CENTER);
                codeDisplay.setFont(new Font("Arial", Font.BOLD, 24));
                codeDisplay.setForeground(new Color(41, 128, 185));
                codeDisplay.setBorder(BorderFactory.createEmptyBorder(10, 0, 20, 0));

                // Voting options panel (initially hidden)
                JPanel optionsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
                optionsPanel.setBackground(Color.WHITE);
                optionsPanel.setVisible(false);

                headerPanel2.add(sessionTitle);
                headerPanel2.add(sessionCodeLabel);
                headerPanel2.add(codeDisplay);

                // Status messages
                JPanel messagesPanel = new JPanel(new BorderLayout());
                messagesPanel.setBorder(BorderFactory.createTitledBorder("Session Activity"));

                DefaultListModel<String> messageListModel = new DefaultListModel<>();
                JList<String> messageList = new JList<>(messageListModel);
                messageList.setCellRenderer(new StatusMessageRenderer());
                messageList.setBackground(new Color(245, 245, 245));

                JScrollPane scrollPane = new JScrollPane(messageList);
                scrollPane.setBorder(BorderFactory.createEmptyBorder());
                messagesPanel.add(scrollPane, BorderLayout.CENTER);

                // Create button container for Start Election button
                JPanel buttonContainer = new JPanel(new FlowLayout(FlowLayout.CENTER));
                buttonContainer.setBackground(Color.WHITE);

                // Start Election button
                JButton startElectionBtn = new JButton("Start Election");
                styleButton(startElectionBtn, new Color(46, 204, 113), Color.WHITE);
                startElectionBtn.addActionListener(ev -> {
                    currentPeer.startVotingButtonClicked();
                    startElectionBtn.setEnabled(false);
                });
                buttonContainer.add(startElectionBtn);

                // Create south container to hold both options and button panel
                JPanel southContainer = new JPanel(new BorderLayout());
                southContainer.add(optionsPanel, BorderLayout.NORTH);
                southContainer.add(buttonContainer, BorderLayout.SOUTH);

                statusPanel.add(headerPanel2, BorderLayout.NORTH);
                statusPanel.add(messagesPanel, BorderLayout.CENTER);
                statusPanel.add(southContainer, BorderLayout.SOUTH);

                cardPanel.add(statusPanel, "WAITING");

                // Set up message consumers
                currentPeer.setStatusMessageConsumer(message -> {
                    SwingUtilities.invokeLater(() -> {
                        String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
                        String formattedMessage = "[" + timestamp + "] " + message;
                        messageListModel.addElement(formattedMessage);
                        messageList.ensureIndexIsVisible(messageListModel.size() - 1);
                    });
                });

                currentPeer.setGuiMessageConsumer(message -> {
                    SwingUtilities.invokeLater(() -> {
                        System.out.println("Processing message: " + message); // Debug

                        if (message.startsWith("SHOW_VOTING_OPTIONS:")) {
                            buttonContainer.removeAll();
                            String optionsString = message.substring("SHOW_VOTING_OPTIONS:".length()).trim();
                            System.out.println("Showing options: " + optionsString); // Debug

                            // Clear existing components
                            optionsPanel.removeAll();
                            optionsPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 10, 10));

                            // Add title
                            JLabel title = new JLabel("Vote Now:", SwingConstants.CENTER);
                            title.setFont(new Font("Arial", Font.BOLD, 16));
                            optionsPanel.add(title);

                            // Add voting buttons
                            for (String option : optionsString.split(",")) {
                                option = option.trim();
                                if (!option.isEmpty()) {
                                    JButton btn = new JButton(option);
                                    btn.setPreferredSize(new Dimension(150, 40));
                                    styleButton(btn, new Color(83, 18, 83), Color.WHITE); // Purple

                                    final String finalOption = option;
                                    btn.addActionListener(ev -> {
                                        currentPeer.sendVoteToLeader(finalOption);
                                    });
                                    optionsPanel.add(btn);
                                }
                            }

                            // Force UI update
                            optionsPanel.setVisible(true);
                            optionsPanel.revalidate();
                            optionsPanel.repaint();

                            // Debug print component hierarchy
                            System.out.println("Options panel visible: " + optionsPanel.isVisible());
                            System.out.println("Options panel parent: " + optionsPanel.getParent());

                            // Add to activity log
                            String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
                            messageListModel
                                    .addElement("[" + timestamp + "] Voting has started! Options: " + optionsString);
                        } else if (message.startsWith("HIDE_VOTING_OPTIONS")) {

                            if (message.contains("DUPLICATE")) {
                                JOptionPane.showMessageDialog(mainFrame,
                                        "You have already voted!",
                                        "Vote Submitted",
                                        JOptionPane.INFORMATION_MESSAGE);
                            } else {
                                JOptionPane.showMessageDialog(mainFrame,
                                        "Voted submitted successully!",
                                        "Vote Submitted",
                                        JOptionPane.INFORMATION_MESSAGE);
                            }

                            optionsPanel.setVisible(false);
                            optionsPanel.removeAll();
                            optionsPanel.revalidate();
                            optionsPanel.repaint();

                            String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());

                            // Remove existing buttons
                            buttonContainer.removeAll();

                            if (currentPeer.hasLeaderToken()) {
                                // Add End Election button
                                JButton endElectionBtn = new JButton("End Election");
                                styleButton(endElectionBtn, new Color(231, 76, 60), Color.WHITE); // Red color
                                endElectionBtn.addActionListener(ev -> {
                                    currentPeer.endVoting();
                                    endElectionBtn.setEnabled(false);
                                });
                                buttonContainer.add(endElectionBtn);

                                buttonContainer.revalidate();
                                buttonContainer.repaint();

                                // Hide voting options
                                optionsPanel.setVisible(false);
                                optionsPanel.removeAll();
                                optionsPanel.revalidate();
                                optionsPanel.repaint();

                                // Add to activity log
                                String timestamp2 = new SimpleDateFormat("HH:mm:ss").format(new Date());
                                messageListModel
                                        .addElement("[" + timestamp2 + "] All votes counted - ready to end election");
                            }
                        } else if (message.startsWith("FINAL_RESULT")) {
                            String results = message.substring("FINAL_RESULT:".length()).trim();
                            String cleanResults = results.replace("{", "").replace("}", "");

                            String[] entries = cleanResults.split(",");

                            StringBuilder formattedResults = new StringBuilder();
                            formattedResults.append("Thanks for joining the session! Here is the final count:\n\n");

                            String[] columnNames = { "Option", "Votes" };

                            List<String[]> rowDataList = new ArrayList<>();

                            for (String entry : entries) {
                                String[] keyValue = entry.split("=");

                                if (keyValue.length == 2) {
                                    String option = keyValue[0].trim();
                                    String votes = keyValue[1].trim();
                                    rowDataList.add(new String[] { option, votes });
                                }
                            }

                            String[][] rowData = rowDataList.toArray(new String[0][]);

                            JTable resultsTable = new JTable(rowData, columnNames);

                            resultsTable.setEnabled(false);
                            resultsTable.setFont(new Font("Arial", Font.PLAIN, 18));
                            resultsTable.setRowHeight(30);
                            resultsTable.setShowGrid(true);
                            resultsTable.setGridColor(Color.BLACK);

                            JTableHeader header = resultsTable.getTableHeader();
                            header.setFont(new Font("Arial", Font.BOLD, 20));
                            header.setPreferredSize(new Dimension(header.getPreferredSize().width, 40));

                            JPanel resultsPanel = new JPanel(new BorderLayout(20, 20));
                            resultsPanel.setBorder(BorderFactory.createEmptyBorder(40, 60, 40, 60));
                            resultsPanel.setBackground(Color.WHITE);

                            JLabel resultsTitleLabel = new JLabel("Election Results", SwingConstants.CENTER);
                            resultsTitleLabel.setFont(new Font("Arial", Font.BOLD, 28));
                            resultsTitleLabel.setForeground(new Color(52, 73, 94));

                            JScrollPane resultsScrollPane = new JScrollPane(resultsTable);
                            resultsScrollPane.setBorder(BorderFactory.createLineBorder(new Color(189, 195, 199), 1));
                            resultsScrollPane.setPreferredSize(new Dimension(400, 300));

                            JButton backToMenuButton = new JButton("Back to Main Menu");
                            styleButton(backToMenuButton, new Color(41, 128, 185), Color.WHITE);
                            backToMenuButton.setPreferredSize(new Dimension(220, 45));
                            backToMenuButton.setFocusPainted(false);
                            backToMenuButton.addActionListener(ev -> StartVoting.resetState());

                            JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
                            bottomPanel.setBackground(Color.WHITE);
                            bottomPanel.add(backToMenuButton);

                            // Assemble Results Panel
                            resultsPanel.add(resultsTitleLabel, BorderLayout.NORTH);
                            resultsPanel.add(new JLabel("Thanks for joining the session! Here is the final count:"));
                            resultsPanel.add(resultsScrollPane, BorderLayout.CENTER);
                            resultsPanel.add(bottomPanel, BorderLayout.SOUTH);

                            cardPanel.add(resultsPanel, "RESULTS");
                            cardLayout.show(cardPanel, "RESULTS");

                            if (beatHandle != null)
                                beatHandle.cancel(true);
                            if (currentPeer != null) {
                                currentPeer.setStatusMessageConsumer(null);
                                currentPeer.setGuiMessageConsumer(null);
                                currentPeer.setHeartbeatStatusConsumer(null);
                            }

                        } else {
                            // Regular status message
                            String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
                            messageListModel.addElement("[" + timestamp + "] " + message);
                        }
                    });
                });

                currentPeer.setHeartbeatStatusConsumer(message -> {
                    SwingUtilities.invokeLater(() -> {
                        String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
                        messageListModel.addElement("[" + timestamp + "] " + message);
                    });
                });

                // Start peer and session
                currentPeer.startPeer();
                String sessionCode = currentPeer.startNewSession(options);
                currentPeer.registerWithLeader(currentPeer.getMyIp() + ":" + myPort);

                codeDisplay.setText(sessionCode);
                cardLayout.show(cardPanel, "WAITING");

            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(mainFrame,
                        "Please enter a valid port number",
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        backBtn.addActionListener(e -> cardLayout.show(cardPanel, "MAIN"));

        buttonPanel.add(createBtn);
        buttonPanel.add(backBtn);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        return panel;
    }

    private static String getLatestLeaderAddress(String sessionCode) {
        Map<String, String> sessions = SessionRegistry.loadSessions();
        String sessionDetails = sessions.get(sessionCode);
        if (sessionDetails == null) {
            throw new IllegalStateException("No session found for code: " + sessionCode);
        }
        String[] parts = sessionDetails.split(",");
        return parts[0]; // leaderAddress
    }

    // Helper method to style buttons
    private static void styleButton(JButton button, Color bgColor, Color textColor) {
        button.setBackground(bgColor);
        button.setForeground(textColor);
        button.setFont(new Font("Arial", Font.BOLD, 14));
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(bgColor.darker(), 1),
                BorderFactory.createEmptyBorder(5, 15, 5, 15)));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private static void showJoinElectionPanel() {
        // Create main panel with border layout
        JPanel panel = new JPanel(new BorderLayout(20, 20));
        panel.setBorder(BorderFactory.createEmptyBorder(40, 60, 40, 60));
        panel.setBackground(new Color(240, 240, 245));

        // Header panel
        JPanel headerPanel = new JPanel();
        headerPanel.setBackground(new Color(240, 240, 245));
        JLabel titleLabel = new JLabel("Join Existing Election");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 24));
        titleLabel.setForeground(new Color(50, 50, 80));
        headerPanel.add(titleLabel);
        panel.add(headerPanel, BorderLayout.NORTH);

        // Center form panel
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBackground(new Color(240, 240, 245));
        formPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Session code input
        JLabel codeLabel = new JLabel("Session Code:");
        codeLabel.setFont(new Font("Arial", Font.PLAIN, 14));
        gbc.gridx = 0;
        gbc.gridy = 0;
        formPanel.add(codeLabel, gbc);

        JTextField codeField = new JTextField(20);
        codeField.setFont(new Font("Arial", Font.PLAIN, 14));
        codeField.setPreferredSize(new Dimension(200, 30));
        gbc.gridx = 1;
        formPanel.add(codeField, gbc);

        // Port input
        JLabel portLabel = new JLabel("Your Node's Port Number:");
        portLabel.setFont(new Font("Arial", Font.PLAIN, 14));
        gbc.gridx = 0;
        gbc.gridy = 1;
        formPanel.add(portLabel, gbc);

        JTextField portField = new JTextField(20);
        portField.setFont(new Font("Arial", Font.PLAIN, 14));
        portField.setPreferredSize(new Dimension(200, 30));
        gbc.gridx = 1;
        formPanel.add(portField, gbc);

        panel.add(formPanel, BorderLayout.CENTER);

        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10));
        buttonPanel.setBackground(new Color(240, 240, 245));

        JButton joinBtn = new JButton("Join Session");
        styleButton(joinBtn, new Color(70, 130, 180), Color.WHITE);
        joinBtn.setPreferredSize(new Dimension(180, 40));

        JButton backBtn = new JButton("Main Menu");
        styleButton(backBtn, new Color(220, 80, 60), Color.WHITE);
        backBtn.setPreferredSize(new Dimension(180, 40));

        joinBtn.addActionListener(e -> {
            String sessionCode = codeField.getText();
            Map<String, String> sessions = SessionRegistry.loadSessions();

            if (sessions.containsKey(sessionCode)) {
                String sessionDetails = sessions.get(sessionCode);
                String[] parts = sessionDetails.split(",");
                String leaderAddress = parts[0];
                String sessionStatus = parts.length > 1 ? parts[parts.length - 1] : "unknown";
                System.out.println(sessionDetails);
                System.out.println(sessionStatus);
                if ("ended".equals(sessionStatus)) {
                    JOptionPane.showMessageDialog(mainFrame,
                            "Sorry, this session has already ended!",
                            "Session Ended",
                            JOptionPane.ERROR_MESSAGE);
                    return;
                }

                try {
                    int myPort = Integer.parseInt(portField.getText());

                    currentPeer = new PeerNode(myPort);

                    // Create status panel (similar to createNewElectionPanel)
                    JPanel statusPanel = new JPanel(new BorderLayout());
                    statusPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
                    statusPanel.setBackground(Color.WHITE);

                    // Header with session info
                    JPanel headerPanel2 = new JPanel(new GridLayout(0, 1, 5, 5));
                    headerPanel2.setBackground(Color.WHITE);

                    JLabel sessionTitle = new JLabel("Joined Election Session", SwingConstants.CENTER);
                    sessionTitle.setFont(new Font("Arial", Font.BOLD, 18));
                    sessionTitle.setForeground(new Color(44, 62, 80));

                    JLabel sessionCodeLabel = new JLabel("Session Code:", SwingConstants.CENTER);
                    sessionCodeLabel.setFont(new Font("Arial", Font.BOLD, 14));

                    JLabel codeDisplay = new JLabel(sessionCode, SwingConstants.CENTER);
                    codeDisplay.setFont(new Font("Arial", Font.BOLD, 24));
                    codeDisplay.setForeground(new Color(41, 128, 185));
                    codeDisplay.setBorder(BorderFactory.createEmptyBorder(10, 0, 20, 0));

                    // Voting options panel (initially hidden)
                    JPanel optionsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
                    optionsPanel.setBackground(Color.WHITE);
                    optionsPanel.setVisible(false);

                    headerPanel2.add(sessionTitle);
                    headerPanel2.add(sessionCodeLabel);
                    headerPanel2.add(codeDisplay);

                    // Status messages
                    JPanel messagesPanel = new JPanel(new BorderLayout());
                    messagesPanel.setBorder(BorderFactory.createTitledBorder("Session Activity"));

                    DefaultListModel<String> messageListModel = new DefaultListModel<>();
                    JList<String> messageList = new JList<>(messageListModel);
                    messageList.setCellRenderer(new StatusMessageRenderer());
                    messageList.setBackground(new Color(245, 245, 245));

                    JScrollPane scrollPane = new JScrollPane(messageList);
                    scrollPane.setBorder(BorderFactory.createEmptyBorder());
                    messagesPanel.add(scrollPane, BorderLayout.CENTER);

                    // Create button container (empty for now)
                    JPanel buttonContainer = new JPanel(new FlowLayout(FlowLayout.CENTER));
                    buttonContainer.setBackground(Color.WHITE);

                    // Create south container
                    JPanel southContainer = new JPanel(new BorderLayout());
                    southContainer.add(optionsPanel, BorderLayout.NORTH);
                    southContainer.add(buttonContainer, BorderLayout.SOUTH);

                    statusPanel.add(headerPanel2, BorderLayout.NORTH);
                    statusPanel.add(messagesPanel, BorderLayout.CENTER);
                    statusPanel.add(southContainer, BorderLayout.SOUTH);

                    cardPanel.add(statusPanel, "WAITING");

                    // Set up message consumers
                    currentPeer.setStatusMessageConsumer(message -> {
                        SwingUtilities.invokeLater(() -> {
                            String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
                            String formattedMessage = "[" + timestamp + "] " + message;
                            messageListModel.addElement(formattedMessage);
                            messageList.ensureIndexIsVisible(messageListModel.size() - 1);
                        });
                    });

                    currentPeer.setGuiMessageConsumer(message -> {
                        SwingUtilities.invokeLater(() -> {
                            System.out.println("Processing message: " + message); // Debug

                            if (message.startsWith("SHOW_VOTING_OPTIONS:")) {
                                buttonContainer.removeAll();
                                String optionsString = message.substring("SHOW_VOTING_OPTIONS:".length()).trim();
                                System.out.println("Showing options: " + optionsString); // Debug

                                // Clear existing components
                                optionsPanel.removeAll();
                                optionsPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 10, 10));

                                // Add title
                                JLabel title = new JLabel("Vote Now:", SwingConstants.CENTER);
                                title.setFont(new Font("Arial", Font.BOLD, 16));
                                optionsPanel.add(title);

                                // Add voting buttons
                                for (String option : optionsString.split(",")) {
                                    option = option.trim();
                                    if (!option.isEmpty()) {
                                        JButton btn = new JButton(option);
                                        btn.setPreferredSize(new Dimension(150, 40));
                                        styleButton(btn, new Color(83, 18, 83), Color.WHITE); // Purple

                                        final String finalOption = option;
                                        btn.addActionListener(ev -> {
                                            currentPeer.sendVoteToLeader(finalOption);
                                        });
                                        optionsPanel.add(btn);
                                    }
                                }

                                // Force UI update
                                optionsPanel.setVisible(true);
                                optionsPanel.revalidate();
                                optionsPanel.repaint();

                                // Debug print component hierarchy
                                System.out.println("Options panel visible: " + optionsPanel.isVisible());
                                System.out.println("Options panel parent: " + optionsPanel.getParent());

                                // Add to activity log
                                String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
                                messageListModel.addElement(
                                        "[" + timestamp + "] Voting has started! Options: " + optionsString);
                            } else if (message.equals("LEADER_CHANGE")) {
                                System.out.println("LEADER CHANGE MESSAGE RECEIVED");
                                String currentStatus = SessionRegistry.getSessionStatus(sessionCode);
                                System.out.println("Current status: " + currentStatus);

                                if (currentPeer.hasLeaderToken()) {
                                    if ("waiting".equals(currentStatus)) {
                                        // Clear existing buttons first
                                        buttonContainer.removeAll();
                                        // Add Start Election button for new leader
                                        JButton startElectionBtn = new JButton("Start Election");
                                        styleButton(startElectionBtn, new Color(46, 204, 113), Color.WHITE); // Green
                                                                                                             // color
                                        startElectionBtn.addActionListener(ev -> {
                                            currentPeer.startVotingButtonClicked();
                                            startElectionBtn.setEnabled(false);
                                        });
                                        buttonContainer.add(startElectionBtn);

                                        // Add to activity log
                                        String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
                                        messageListModel.addElement(
                                                "[" + timestamp + "] You are now the leader - ready to start election");
                                        // Update UI
                                        buttonContainer.revalidate();
                                        buttonContainer.repaint();

                                        // Clear voting options if any
                                        optionsPanel.setVisible(false);
                                        optionsPanel.removeAll();
                                        optionsPanel.revalidate();
                                        optionsPanel.repaint();
                                    } else if ("started".equals(currentStatus) && currentPeer.getHasVoted()) {
                                        // Add End Election button for new leader
                                        // Clear existing buttons first
                                        buttonContainer.removeAll();
                                        JButton endElectionBtn = new JButton("End Election");
                                        styleButton(endElectionBtn, new Color(231, 76, 60), Color.WHITE); // Red color
                                        endElectionBtn.addActionListener(ev -> {
                                            currentPeer.endVoting();
                                            endElectionBtn.setEnabled(false);
                                        });
                                        buttonContainer.add(endElectionBtn);

                                        // Add to activity log
                                        String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
                                        messageListModel.addElement(
                                                "[" + timestamp + "] You are now the leader - ready to end election");

                                        // Update UI
                                        buttonContainer.revalidate();
                                        buttonContainer.repaint();

                                        // Clear voting options if any
                                        optionsPanel.setVisible(false);
                                        optionsPanel.removeAll();
                                        optionsPanel.revalidate();
                                        optionsPanel.repaint();
                                    }

                                }
                            } else if (message.startsWith("HIDE_VOTING_OPTIONS")) {
                                if (message.contains("DUPLICATE")) {
                                    JOptionPane.showMessageDialog(mainFrame,
                                            "You have already voted!",
                                            "Vote Submitted",
                                            JOptionPane.INFORMATION_MESSAGE);
                                } else {
                                    JOptionPane.showMessageDialog(mainFrame,
                                            "Voted submitted successully!",
                                            "Vote Submitted",
                                            JOptionPane.INFORMATION_MESSAGE);
                                }

                                optionsPanel.setVisible(false);
                                optionsPanel.removeAll();
                                optionsPanel.revalidate();
                                optionsPanel.repaint();

                                String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());

                                // Remove existing buttons
                                buttonContainer.removeAll();

                                if (currentPeer.hasLeaderToken()) {
                                    // Add End Election button
                                    JButton endElectionBtn = new JButton("End Election");
                                    styleButton(endElectionBtn, new Color(231, 76, 60), Color.WHITE); // Red color
                                    endElectionBtn.addActionListener(ev -> {
                                        currentPeer.endVoting();
                                        endElectionBtn.setEnabled(false);
                                    });
                                    buttonContainer.add(endElectionBtn);

                                    buttonContainer.revalidate();
                                    buttonContainer.repaint();

                                    // Hide voting options
                                    optionsPanel.setVisible(false);
                                    optionsPanel.removeAll();
                                    optionsPanel.revalidate();
                                    optionsPanel.repaint();

                                    // Add to activity log
                                    String timestamp2 = new SimpleDateFormat("HH:mm:ss").format(new Date());
                                    messageListModel.addElement(
                                            "[" + timestamp2 + "] All votes counted - ready to end election");
                                }

                            } else if (message.startsWith("FINAL_RESULT")) {
                                String results = message.substring("FINAL_RESULT:".length()).trim();
                                String cleanResults = results.replace("{", "").replace("}", "");

                                String[] entries = cleanResults.split(",");

                                StringBuilder formattedResults = new StringBuilder();
                                formattedResults.append("Thanks for joining the session! Here is the final count:\n\n");

                                String[] columnNames = { "Option", "Votes" };

                                List<String[]> rowDataList = new ArrayList<>();

                                for (String entry : entries) {
                                    String[] keyValue = entry.split("=");

                                    if (keyValue.length == 2) {
                                        String option = keyValue[0].trim();
                                        String votes = keyValue[1].trim();
                                        rowDataList.add(new String[] { option, votes });
                                    }
                                }

                                String[][] rowData = rowDataList.toArray(new String[0][]);

                                JTable resultsTable = new JTable(rowData, columnNames);

                                resultsTable.setEnabled(false);
                                resultsTable.setFont(new Font("Arial", Font.PLAIN, 18));
                                resultsTable.setRowHeight(30);
                                resultsTable.setShowGrid(true);
                                resultsTable.setGridColor(Color.BLACK);

                                JTableHeader header = resultsTable.getTableHeader();
                                header.setFont(new Font("Arial", Font.BOLD, 20));
                                header.setPreferredSize(new Dimension(header.getPreferredSize().width, 40));

                                JPanel resultsPanel = new JPanel(new BorderLayout(20, 20));
                                resultsPanel.setBorder(BorderFactory.createEmptyBorder(40, 60, 40, 60));
                                resultsPanel.setBackground(Color.WHITE);

                                JLabel resultsTitleLabel = new JLabel("Election Results", SwingConstants.CENTER);
                                resultsTitleLabel.setFont(new Font("Arial", Font.BOLD, 28));
                                resultsTitleLabel.setForeground(new Color(52, 73, 94));

                                JScrollPane resultsScrollPane = new JScrollPane(resultsTable);
                                resultsScrollPane
                                        .setBorder(BorderFactory.createLineBorder(new Color(189, 195, 199), 1));
                                resultsScrollPane.setPreferredSize(new Dimension(400, 300));

                                JButton backToMenuButton = new JButton("Back to Main Menu");
                                styleButton(backToMenuButton, new Color(41, 128, 185), Color.WHITE);
                                backToMenuButton.setPreferredSize(new Dimension(220, 45));
                                backToMenuButton.setFocusPainted(false);
                                backToMenuButton.addActionListener(ev -> StartVoting.resetState());

                                JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
                                bottomPanel.setBackground(Color.WHITE);
                                bottomPanel.add(backToMenuButton);

                                // Assemble Results Panel
                                resultsPanel.add(resultsTitleLabel, BorderLayout.NORTH);
                                resultsPanel
                                        .add(new JLabel("Thanks for joining the session! Here is the final count:"));
                                resultsPanel.add(resultsScrollPane, BorderLayout.CENTER);
                                resultsPanel.add(bottomPanel, BorderLayout.SOUTH);

                                cardPanel.add(resultsPanel, "RESULTS");
                                cardLayout.show(cardPanel, "RESULTS");

                                if (beatHandle != null)
                                    beatHandle.cancel(true);
                                if (currentPeer != null) {
                                    currentPeer.setStatusMessageConsumer(null);
                                    currentPeer.setGuiMessageConsumer(null);
                                    currentPeer.setHeartbeatStatusConsumer(null);
                                }

                            } else {
                                // Regular status message
                                String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
                                messageListModel.addElement("[" + timestamp + "] " + message);
                            }
                        });
                    });

                    currentPeer.setHeartbeatStatusConsumer(message -> {
                        SwingUtilities.invokeLater(() -> {
                            String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
                            messageListModel.addElement("[" + timestamp + "] " + message);
                        });
                    });

                    currentPeer.setSessionCode(sessionCode);
                    long startTime = System.currentTimeMillis();
                    long timeout = 15_000; // 15 seconds

                    while (true) {
                        try {
                            currentPeer.startPeer();
                            String leaderAddressNew = getLatestLeaderAddress(sessionCode);
                            currentPeer.registerWithLeader(leaderAddressNew);
                            break; // success! exit the loop
                        } catch (Exception c) {
                            if (System.currentTimeMillis() - startTime > timeout) {
                                System.err.println("Failed to start and register peer within 15 seconds.");
                                c.printStackTrace(); // or log it properly
                                break;
                            }

                            // Optional: small delay to avoid tight loop
                            try {
                                Thread.sleep(5000); // half a second delay between retries
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt(); // re-set the interrupt flag
                                break;
                            }
                        }
                    }

                    Runnable heartbeat = new SessionHeartbeat(currentPeer);
                    beatHandle = scheduler.scheduleAtFixedRate(heartbeat, 10, 10, TimeUnit.SECONDS);

                    cardLayout.show(cardPanel, "WAITING");

                    if ("started".equals(sessionStatus)) {
                        currentPeer.promptForVote();
                    }

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

        buttonPanel.add(joinBtn);
        buttonPanel.add(backBtn);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        cardPanel.add(panel, "JOIN_ELECTION");
        cardLayout.show(cardPanel, "JOIN_ELECTION");
    }

    private static JPanel createSessionsPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        sessionListPanel = new JPanel();
        sessionListPanel.setLayout(new BoxLayout(sessionListPanel, BoxLayout.Y_AXIS));

        JScrollPane scrollPane = new JScrollPane(sessionListPanel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

        JButton backButton = new JButton("Main Menu");
        backButton.addActionListener(e -> cardLayout.show(cardPanel, "MAIN"));
        styleButton(backButton, new Color(220, 80, 60), Color.WHITE);
        backButton.setPreferredSize(new Dimension(150, 35));

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

    private static void resetState() {
        System.out.println("Resetting state...");
        if (beatHandle != null) {
            beatHandle.cancel(true);
            System.out.println("Heartbeat handle cancelled.");
        }
        if (currentPeer != null) {
            currentPeer.shutdown();
            System.out.println("Current peer shut down.");
            currentPeer = null;
        }

        cardLayout.show(cardPanel, "MAIN");
        System.out.println("State reset.");

    }

    private static class StatusMessageRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            String message = (String) value;
            setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

            if (message.contains("[HEARTBEAT]")) {
                setForeground(new Color(52, 152, 219));
                setIcon(new ImageIcon("heartbeat_icon.png")); // You'd add your own icon
            } else if (message.contains("[VOTE]")) {
                setForeground(new Color(155, 89, 182));
                setIcon(new ImageIcon("vote_icon.png"));
            } else if (message.contains("[ELECTION]")) {
                setForeground(new Color(231, 76, 60));
                setIcon(new ImageIcon("election_icon.png"));
            } else {
                setForeground(new Color(44, 62, 80));
            }

            return this;
        }
    }
}
