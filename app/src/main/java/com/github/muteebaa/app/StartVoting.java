package com.github.muteebaa.app;

import java.util.Map;
import java.util.Scanner;

public class StartVoting {
    // ANSI color codes for console output
    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_BLACK = "\u001B[30m";
    public static final String ANSI_RED = "\u001B[31m"; // heartbeat
    public static final String ANSI_GREEN = "\u001B[32m";
    public static final String ANSI_YELLOW = "\u001B[33m"; // vote submission
    public static final String ANSI_BLUE = "\u001B[34m";
    public static final String ANSI_PURPLE = "\u001B[35m"; // CLI
    public static final String ANSI_CYAN = "\u001B[36m"; // election
    public static final String ANSI_WHITE = "\u001B[37m";

    private static final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        System.out.println(ANSI_PURPLE + "Welcome to the voting system!" + ANSI_RESET);

        while (true) {
            displayOptions();
        }
    }

    private static void displayOptions() {
        System.out.println(ANSI_PURPLE
                + "\n1. Start a new election\n2. Join an existing election\n3. View all sessions" + ANSI_RESET);
        System.out.print(ANSI_PURPLE + "Enter choice: " + ANSI_RESET);
        int choice = scanner.nextInt();
        scanner.nextLine(); // Consume newline

        switch (choice) {
            case 1:
                startNewElection();
                break;
            case 2:
                joinExistingElection();
                break;
            case 3:
                SessionRegistry.displayAvailableSessions();
                break;
            default:
                System.out.println(ANSI_PURPLE + "Invalid choice. Please enter 1, 2, or 3." + ANSI_RESET);
                displayOptions();
        }
    }

    private static void startNewElection() {
        System.out.println(ANSI_PURPLE + "\nStarting a new election!" + ANSI_RESET);
        System.out.print(ANSI_PURPLE + "Enter your node's port number: " + ANSI_RESET);
        int myPort = scanner.nextInt();
        scanner.nextLine(); // Consume newlines

        PeerNode peer = new PeerNode(myPort);
        peer.startPeer();

        System.out.print(ANSI_PURPLE + "Enter comma-separated voting options: " + ANSI_RESET);
        String options = scanner.nextLine();

        // Generate session code and store it
        String sessionCode = peer.startNewSession(options);

        System.out.println(ANSI_PURPLE + "\nSession created! Share this code: " + sessionCode + ANSI_RESET);
        System.out.println(ANSI_PURPLE + "Voting options: " + options + ANSI_RESET);

        peer.registerWithLeader(peer.getMyIp() + ":" + myPort); // adds us to the peerNodes list, and give us an id

        peer.waitForStartVoting();
    }

    private static void joinExistingElection() {
        System.out.println(ANSI_PURPLE + "\nJoining an existing election!" + ANSI_RESET);
        System.out.print(ANSI_PURPLE + "Enter session code: " + ANSI_RESET);
        String sessionCode = scanner.nextLine();

        Map<String, String> sessions = SessionRegistry.loadSessions();

        if (sessions.containsKey(sessionCode)) {
            /// get the session entry
            String sessionDetails = sessions.get(sessionCode);

            String sessionStatus = sessionDetails.substring(sessionDetails.lastIndexOf(",") + 1);
            if (sessionStatus.equals("ended")) {
                System.out.println(ANSI_PURPLE + "Sorry, this session has already ended! Please pick another option:"
                        + ANSI_RESET);
                return;
            } else if (sessionStatus.equals("started")) {
                System.out.println(ANSI_PURPLE
                        + "Sorry, this session is already in progress! Please pick another option:" + ANSI_RESET);
                return;
            }
            // everything before the first comma in sessionDetails
            String leaderAddress = sessionDetails.split(",")[0];

            System.out.print(ANSI_PURPLE + "Enter your node's port number: " + ANSI_RESET);
            int myPort = scanner.nextInt();
            scanner.nextLine(); // Consume newlines

            PeerNode peer = new PeerNode(myPort);
            peer.setSessionCode(sessionCode);
            peer.startPeer();
            peer.registerWithLeader(leaderAddress);

            System.out.println(ANSI_PURPLE + "Waiting for leader to start voting..." + ANSI_RESET);
        } else {
            System.out.println(ANSI_PURPLE + "Invalid session code!" + ANSI_RESET);
        }
    }

}
