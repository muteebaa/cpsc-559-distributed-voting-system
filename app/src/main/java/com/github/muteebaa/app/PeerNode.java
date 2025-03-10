package com.github.muteebaa.app;

import java.util.*;

/**
 * Represents a peer node in a distributed voting system.
 * Handles communication, voting, and peer registration.
 */
public class PeerNode {
    private static final Scanner scanner = new Scanner(System.in);
    private final NodeCommunication nodeComm;
    private final int port;
    private final int nodeId; // will be used in leader election
    private final List<String> peerNodes; // each peer will have a list of other peers
    private Map<String, Integer> voteTally;
    private boolean leaderToken;
    private String leaderAddress;
    private String sessionCode;

    /**
     * Initializes a new PeerNode instance.
     *
     * @param port   The port the peer listens on.
     * @param nodeId The unique identifier for this node.
     */
    public PeerNode(int port, int nodeId) {
        this.nodeComm = new NodeCommunication();
        this.port = port;
        this.nodeId = nodeId;
        this.peerNodes = new ArrayList<>();
    }

    public void setSessionCode(String sessionCode) {
        this.sessionCode = sessionCode;
    }

    /**
     * Starts the peer as a server and registers with the leader.
     */
    public void startPeer() {
        new Thread(() -> nodeComm.startServer(port, this::handleMessage)).start();
        findLeader();
        registerWithLeader(leaderAddress);
    }

    /**
     * Registers this peer with the leader node.
     *
     * @param leaderAddress The leader node's address in the format "host:port".
     */
    public void registerWithLeader(String leaderAddress) {
        this.leaderAddress = leaderAddress;
        String registrationMessage = "REGISTER:localhost:" + port;
        nodeComm.connectToNode(leaderAddress.split(":")[0], Integer.parseInt(leaderAddress.split(":")[1]));
        nodeComm.sendMessage(registrationMessage, nodeComm.getClientSocket());
        voteTally = new HashMap<>();
        // TODO: Handle leader's response
    }

    /**
     * Handles incoming messages.
     *
     * @param message The received message.
     */
    public void handleMessage(String message) {
        if (message.startsWith("REGISTER:")) {
            String peer = message.substring(9);
            int peerId = Integer.parseInt(message.substring(message.length() - 1));
            nodeComm.updatePeerNodeAddresses(peer, peerId);
        } else if (message.startsWith("VOTE:")) {
            String vote = message.substring(5).trim();
            updateVoteTally(vote);
            if (leaderToken) {
                nodeComm.broadcastMessage("VOTE:" + vote, nodeComm.getPeerAddresses());
            }
        } else if (message.startsWith("START_VOTING")) {
            promptForVote();
        } else if (message.startsWith("VOTING_ENDED:")) {
            System.out.println();
            System.out.println(message.substring(13));
        }
    }

    /**
     * Determines the leader node. (Currently hardcoded)
     */
    private void findLeader() {
        leaderAddress = "localhost:5000";
    }

    public boolean hasLeaderToken() {
        return leaderToken;
    }

    public void setLeaderToken(boolean leaderToken) {
        this.leaderToken = leaderToken;
    }

    /**
     * Updates the vote tally for a given vote.
     *
     * @param vote The vote received.
     * @return The updated vote tally.
     */
    public Map<String, Integer> updateVoteTally(String vote) {
        voteTally.put(vote, voteTally.getOrDefault(vote, 0) + 1);
        return voteTally;
    }

    /**
     * Sends a vote to the leader node.
     *
     * @param vote The vote being submitted.
     */
    public void sendVoteToLeader(String vote) {
        nodeComm.connectToNode(leaderAddress.split(":")[0], Integer.parseInt(leaderAddress.split(":")[1]));
        nodeComm.sendMessage("VOTE:" + vote, nodeComm.getClientSocket());
    }

    /**
     * Prompts the user for a vote.
     */
    public void startVoting() {
        // System.out.println("starting voting");
        // System.out.println("peer nodes: " + nodeComm.getPeerAddresses());
        nodeComm.broadcastMessage("START_VOTING:" + voteTally.keySet(),
                nodeComm.getPeerAddresses());

    }

    public void promptForVote() {
        String options = SessionRegistry.getVotingOptions(sessionCode).toString();
        System.out.println("\nVoting started!");
        System.out.println("Voting options: " + options);

        System.out.print("Enter your vote: ");
        if (scanner.hasNextLine()) {
            String vote = scanner.nextLine();
            sendVoteToLeader(vote);
            System.out.println("Vote submitted: " + vote);
            if (!leaderToken) {
                System.out.println("We will let you know when voting ends.");
            }
        } else {
            System.out.println("Input stream closed. Cannot receive votes.");
        }
    }

    /**
     * Ends the voting process and broadcasts results.
     */
    public void endVoting() {
        String results = "VOTING_ENDED:Thanks for voting! Voting results: " + voteTally;
        System.out.println(results.substring(13));
        nodeComm.broadcastMessage(results, nodeComm.getPeerAddresses());
    }

    /**
     * Starts a new voting session and saves it.
     *
     * @param ip      The IP address of the session host.
     * @param port    The port for the session.
     * @param options The available voting options.
     * @return The generated session code.
     */
    public String startNewSession(String ip, int port, String options) {
        String sessionCode = generateRandomCode();
        SessionRegistry.saveSession(sessionCode, ip, port, options);
        for (String option : options.split(",")) {
            voteTally.put(option.trim(), 0);
        }
        this.sessionCode = sessionCode;
        setLeaderToken(true); // Leader token is initially with the session creator
        return sessionCode;
    }

    /**
     * Displays available voting sessions.
     */
    public static void displayAvailableSessions() {
        Map<String, String> sessions = SessionRegistry.loadSessions();
        if (sessions.isEmpty()) {
            System.out.println("No available sessions found.");
        } else {
            System.out.println("Available sessions:");
            for (Map.Entry<String, String> entry : sessions.entrySet()) {
                System.out.println("Code: " + entry.getKey() + " | Details: " + entry.getValue());
            }
        }
    }

    /**
     * Generates a random 6-character session code.
     *
     * @return A randomly generated session code.
     */
    private String generateRandomCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        Random random = new Random();
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            code.append(chars.charAt(random.nextInt(chars.length())));
        }
        return code.toString();
    }
}
