package com.github.muteebaa.app;

import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class StartVoting {
    private static final Scanner scanner = new Scanner(System.in);
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static ScheduledFuture<?> beatHandle;

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
        scanner.nextLine(); // Consume newline

        PeerNode peer = new PeerNode(myPort, 1);
        peer.startPeer();

        System.out.print("Enter comma-separated voting options: ");
        String options = scanner.nextLine();

        // Generate session code and store it
        String sessionCode = peer.startNewSession("192.168.1.100", myPort, options); // Replace with actual IP
        System.out.println("\nSession created! Share this code: " + sessionCode);
        System.out.println("Voting options: " + options);

        waitForLeaderToStartVoting(peer);
        waitForLeaderToEndVoting(peer);
    }

    private static void joinExistingElection() {
        System.out.println("\nJoining an existing election!");
        System.out.print("Enter session code: ");
        String sessionCode = scanner.nextLine();

        Map<String, String> sessions = SessionRegistry.loadSessions();

        if (sessions.containsKey(sessionCode)) {
            System.out.print("Enter your node's port number: ");
            int myPort = scanner.nextInt();

            PeerNode peer = new PeerNode(myPort, 1);
            peer.setSessionCode(sessionCode);
            peer.startPeer();

            System.out.println("Waiting for leader to start voting...");
        } else {
            System.out.println("Invalid session code!");
        }
    }

    private static void waitForLeaderToStartVoting(PeerNode peer) {
        Runnable heartbeat = new SessionHeartbeat(peer);
        beatHandle = scheduler.scheduleAtFixedRate(heartbeat, 10, 10, TimeUnit.SECONDS);

        while (true) {
            System.out.print("Enter 'start' to begin voting: ");
            String input = scanner.nextLine().trim().toLowerCase();

            if (input.equals("start")) {
                peer.startVoting();
                peer.promptForVote(); // Prompt leader to vote
                break;
            }
            System.out.println("Invalid input. Type 'start' to begin.");
        }
    }

    private static void waitForLeaderToEndVoting(PeerNode peer) {
        while (true) {
            System.out.print("Enter 'end' to stop voting: ");
            String input = scanner.nextLine().trim().toLowerCase();

            if (input.equals("end")) {
                peer.endVoting();
                break;
            }
            System.out.println("Invalid input. Type 'end' to end voting.");
        }
    }
}
