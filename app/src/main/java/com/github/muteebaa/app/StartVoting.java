package com.github.muteebaa.app;

import java.util.Map;
import java.util.Scanner;

public class StartVoting {
    private static final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        System.out.println("1. Start a new election\n2. Join an existing election\n3. View available sessions");
        System.out.print("Enter choice: ");
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
                System.out.println("Invalid choice. Please enter 1, 2, or 3.");
        }
    }

    private static void startNewElection() {
        System.out.println("\nStarting a new election!");
        System.out.print("Enter your node's port number: ");
        int myPort = scanner.nextInt();
        scanner.nextLine(); // Consume newlines

        PeerNode peer = new PeerNode(myPort);
        peer.startPeer();

        System.out.print("Enter comma-separated voting options: ");
        String options = scanner.nextLine();

        // Generate session code and store it
        String sessionCode = peer.startNewSession(options);

        System.out.println("\nSession created! Share this code: " + sessionCode);
        System.out.println("Voting options: " + options);

        peer.registerWithLeader(peer.getMyIp() + ":" + myPort); // adds us to the peerNodes list, and give us an id

        peer.waitForStartVoting();
    }

    private static void joinExistingElection() {
        System.out.println("\nJoining an existing election!");
        System.out.print("Enter session code: ");
        String sessionCode = scanner.nextLine();

        Map<String, String> sessions = SessionRegistry.loadSessions();

        if (sessions.containsKey(sessionCode)) {
            /// get the session entry
            String sessionDetails = sessions.get(sessionCode);
            // everything before the first comma in sessionDetails
            String leaderAddress = sessionDetails.split(",")[0];

            System.out.print("Enter your node's port number: ");
            int myPort = scanner.nextInt();
            scanner.nextLine(); // Consume newlines

            PeerNode peer = new PeerNode(myPort);
            peer.setSessionCode(sessionCode);
            peer.startPeer();
            peer.registerWithLeader(leaderAddress);

            System.out.println("Waiting for leader to start voting...");

            System.out.println("or start fake leader election!");

            // int choice = scanner.nextInt();
            // scanner.nextLine(); // Consume newline

            // switch (choice) {
            // case 1:
            // peer.initiateElection();
            // break;
            // default:
            // System.out.println("Invalid choice. Please enter 1.");
            // }
        } else {
            System.out.println("Invalid session code!");
        }
    }

}
