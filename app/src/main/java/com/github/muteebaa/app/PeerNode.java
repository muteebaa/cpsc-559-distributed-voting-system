package com.github.muteebaa.app;

import java.net.InetAddress;
import java.net.UnknownHostException;
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
    private final Map<Number, String> peerNodes; // each peer will have a list of other peers
    private Map<String, Integer> voteTally;
    private boolean leaderToken;
    private String leaderAddress;
    private String sessionCode;
    private boolean acknowledgment = false;

    private boolean running = false; // wether or not this node is running in the election

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
        this.peerNodes = new HashMap<Number, String>();
        this.voteTally = new HashMap<>();
    }

    public void setSessionCode(String sessionCode) {
        this.sessionCode = sessionCode;
    }

    /**
     * Starts the peer as a server and registers with the leader.
     */
    public void startPeer() {
        new Thread(() -> nodeComm.startServer(port, this::handleMessage)).start();
    }

    public String getMyIp() {
        try {
            // Get the local host (your machine's IP address)
            String myIp = InetAddress.getLocalHost().getHostAddress();
            System.out.println("My IP Address: " + myIp);

            return myIp;
        } catch (UnknownHostException e) {
            System.err.println("Could not determine IP address: " + e.getMessage());
        }
        return null;
    }

    /**
     * Registers this peer with the leader node.
     *
     * @param leaderAddress The leader node's address in the format "host:port".
     */
    public synchronized void registerWithLeader(String leaderAddress) {
        // System.out.print("\n\nregistering with leader\n\n");
        this.acknowledgment = false;

        String leaderIp = leaderAddress.split(":")[0];
        int leaderPort = Integer.parseInt(leaderAddress.split(":")[1]);

        // System.out.print(leaderIp);
        // System.out.print(leaderPort);

        String myIp = getMyIp();

        String registrationMessage = "REGISTER:" + myIp + ":" + port;

        // System.out.print("\n\n" + registrationMessage + "\n\n");

        nodeComm.connectToNode(leaderIp, leaderPort);
        nodeComm.sendMessage(registrationMessage, nodeComm.getClientSocket());

        // Wait for acknowledgment from the leader
        while (!acknowledgment) {
            try {
                wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        setLeader(leaderAddress);

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

            // leader should set the id of the new peer, we will start with 1
            // get the highest id in the peerNodes map (Map<Number, String> peerNodes)
            // Get the highest key in the peerNodes map
            int highestCurrentId = peerNodes.keySet().stream()
                    .mapToInt(Number::intValue) // Convert Number to int
                    .max() // Get the maximum value
                    .orElse(0); // Default value if the map is empty

            int newId = highestCurrentId + 1;

            peerNodes.put(newId, peer);

            nodeComm.broadcastMessage("UPDATE_NEW_PEER:" + peer + "," + newId, peerNodes.values());

            System.out.println("My peer list: " + peerNodes);

            nodeComm.connectToNode(peer.split(":")[0], Integer.parseInt(peer.split(":")[1]));
            nodeComm.sendMessage("ACK: You are successfully registered.", nodeComm.getClientSocket());

            // also send UPDATE_NEW_PEER with leaders ip and id
            nodeComm.connectToNode(peer.split(":")[0], Integer.parseInt(peer.split(":")[1]));
            nodeComm.sendMessage("UPDATE_NEW_PEER:" + leaderAddress + "," + this.nodeId, nodeComm.getClientSocket());

        } else if (message.startsWith("UPDATE_NEW_PEER:")) {
            System.out.println("My peer list before update: " + peerNodes);
            // Extract the peer list from the message
            String newPeerIpAndId = message.substring("UPDATE_NEW_PEER:".length()).trim();

            // Remove the square brackets [ ] if present
            String newPeerIp = newPeerIpAndId.split(",")[0];
            int newPeerId = Integer.parseInt(newPeerIpAndId.split(",")[1]);

            peerNodes.put(newPeerId, newPeerIp);
            System.out.println("Updated peer list: " + peerNodes);
        } else if (message.startsWith("ACK:")) {
            synchronized (this) {
                acknowledgment = true;
                notifyAll(); // Notify waiting threads
            }
            System.out.println(message);
        } else if (message.startsWith("VOTE:")) {
            String[] parts = message.split(":");
            String host = parts[1];
            int port = Integer.parseInt(parts[2]);
            String vote = parts[3];
            updateVoteTally(vote);
            if (leaderToken) {
                nodeComm.broadcastMessage("UPDATE_VOTE_TALLY:" + vote, peerNodes.values());
                nodeComm.connectToNode(host, port);
                nodeComm.sendMessage("ACK: Your vote was successfully counted.", nodeComm.getClientSocket());
            }
        } else if (message.startsWith("UPDATE_VOTE_TALLY:")) {
            String vote = message.substring(18).trim();
            updateVoteTally(vote);
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
    public void setLeader(String leaderAddress) {
        this.leaderAddress = leaderAddress;
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
    public synchronized void sendVoteToLeader(String vote) {
        this.acknowledgment = false;
        nodeComm.connectToNode(leaderAddress.split(":")[0], Integer.parseInt(leaderAddress.split(":")[1]));
        nodeComm.sendMessage("VOTE:localhost:" + this.port + ":" + vote, nodeComm.getClientSocket());
        // Wait for acknowledgment from the leader
        while (!acknowledgment) {
            try {
                wait(); // Correct usage inside synchronized block
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Prompts the user for a vote.
     */
    public void startVoting() {
        // System.out.println("starting voting");
        // System.out.println("peer nodes: " + nodeComm.getPeerAddresses());
        nodeComm.broadcastMessage("START_VOTING:" + voteTally.keySet(),
                peerNodes.values());

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
        nodeComm.broadcastMessage(results, peerNodes.values());
    }

    /**
     * Starts a new voting session and saves it.
     *
     * @param ip      The IP address of the session host.
     * @param port    The port for the session.
     * @param options The available voting options.
     * @return The generated session code.
     */
    public String startNewSession(String options) {
        String myIp = getMyIp();
        String sessionCode = SessionRegistry.saveSession(myIp, this.port, options);
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

    // Initiate_Election(int i) /* process Pi */
    public void initiateElection(int nodeID) {
        // runningi = true /* I am running in this elections */
        running = true;

        // if i is the highest id
        // then
        // send leader(i) to all Pj, where j ≠ i else
        // send election(i) to all Pj, where j > i
        // /* check if there are bigger guys out there */
        // wait for T time units

    }
}
