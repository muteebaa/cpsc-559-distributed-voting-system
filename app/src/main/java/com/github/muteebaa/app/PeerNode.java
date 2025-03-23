package com.github.muteebaa.app;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;

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
    private boolean acknowledgment = false;
    private String uuid;
    private ConcurrentSkipListSet<String> uuidSet;

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
        this.uuid = loadUUID();
        this.uuidSet = new ConcurrentSkipListSet<>();
    }

    private static String getSystemUUID(){
        String uuid = null;
        try {
            // For Linux
            if (System.getProperty("os.name").toLowerCase().contains("linux")) {
                Process process = Runtime.getRuntime().exec("cat /sys/class/dmi/id/product_uuid");
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                uuid = reader.readLine();
            }
            // For Windows
            else if (System.getProperty("os.name").toLowerCase().contains("win")) {
                Process process = Runtime.getRuntime().exec("wmic computersystem get UUID");
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                reader.readLine(); // Skip the header
                uuid = reader.readLine().trim();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return uuid;
    }

    private static UUID generateUUID(){
        String systemUUID = getSystemUUID();
        if (systemUUID != null) {
            return UUID.nameUUIDFromBytes(systemUUID.getBytes());
        }
        return null;
    }

    private static boolean saveUUID(UUID uuid){
        String filePath = System.getProperty("user.home") + File.separator + ".uuid" + File.separator + "uuid.txt";
        try {
            File file = new File(filePath);
            file.getParentFile().mkdirs();
            BufferedWriter writer = new BufferedWriter(new FileWriter(file));
            writer.write(uuid.toString());
            writer.close();
            file.setReadOnly();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private static String loadUUID(){
        //  This UUID is based in the location "user.home"/.uuid/uuid.txt
        String filePath = System.getProperty("user.home") + File.separator + ".uuid" + File.separator + "uuid.txt";
        File file = new File(filePath);
        if (!file.exists()) {
            saveUUID(generateUUID());
        }
        file = new File(filePath);
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append(System.lineSeparator());
            }
            if (content.length() == 0) {
                return null;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return content.toString();   
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
    public synchronized void registerWithLeader(String leaderAddress) {
        this.acknowledgment = false;
        this.leaderAddress = leaderAddress;
        String registrationMessage = "REGISTER:localhost:" + port;
        nodeComm.connectToNode(leaderAddress.split(":")[0], Integer.parseInt(leaderAddress.split(":")[1]));
        nodeComm.sendMessage(registrationMessage, nodeComm.getClientSocket());

        // Wait for acknowledgment from the leader
        while (!acknowledgment) {
            try {
                wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

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
            peerNodes.add(peer);
            nodeComm.broadcastMessage("UPDATE_NEW_PEER:" + peerNodes, peerNodes); 
            System.out.println("My peer list: " + peerNodes);
            nodeComm.connectToNode(peer.split(":")[0], Integer.parseInt(peer.split(":")[1]));
            nodeComm.sendMessage("ACK: You are successfully registered.", nodeComm.getClientSocket());
        }
        else if (message.startsWith("UPDATE_NEW_PEER:")) {
            System.out.println("My peer list before update: " + peerNodes);
            // Extract the peer list from the message
            String peerListString = message.substring("UPDATE_NEW_PEER:".length()).trim();

            // Remove the square brackets [ ] if present
            peerListString = peerListString.substring(1, peerListString.length() - 1);

            // Split the peers by ", " and add them to the peerNodes list
            List<String> newPeers = Arrays.asList(peerListString.split(", "));
    
            // Add only new peers that are not already in peerNodes
            for (String peer : newPeers) {
                if (!peerNodes.contains(peer)) {
                    peerNodes.add(peer);
                }
            }

            System.out.println("Updated peer list: " + peerNodes);
        }
        else if (message.startsWith("ACK:")) {
            synchronized (this) {
                acknowledgment = true;
                notifyAll(); // Notify waiting threads
            }
            System.out.println(message);
        }else if (message.startsWith("VOTE:")) {   
            String[] parts = message.split(":");
            String host = parts[1]; 
            int port = Integer.parseInt(parts[2]); 
            String vote = parts[3];
            // Adding section for handling the UUID
            String incomingUUID = parts[4];
            if (!(this.uuidSet.contains(incomingUUID))) {
                this.uuidSet.add(incomingUUID);
                updateVoteTally(vote);
                if (leaderToken) {                               
                    nodeComm.broadcastMessage("UPDATE_VOTE_TALLY:" + vote, peerNodes);    
                    nodeComm.connectToNode(host, port);
                    nodeComm.sendMessage("ACK: Your vote was successfully counted.", nodeComm.getClientSocket());
                }    
            }
            else{
                nodeComm.connectToNode(host, port);
                nodeComm.sendMessage("DENY: A vote has already been cast with your UUID.", nodeComm.getClientSocket());
            }
                   
        }else if(message.startsWith("DENY:")){
            
        }
        else if (message.startsWith("UPDATE_VOTE_TALLY:")) {
            String vote = message.substring(18).trim();
            updateVoteTally(vote);
        }
        else if (message.startsWith("START_VOTING")) {
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
    public synchronized void sendVoteToLeader(String vote) {       
        this.acknowledgment = false;
        nodeComm.connectToNode(leaderAddress.split(":")[0], Integer.parseInt(leaderAddress.split(":")[1]));
        nodeComm.sendMessage("VOTE:localhost:" + this.port + ":" + vote + ":" + this.uuid, nodeComm.getClientSocket());
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
                peerNodes);

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
        nodeComm.broadcastMessage(results, peerNodes);
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
        String sessionCode = SessionRegistry.saveSession(ip, port, options);
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
}
