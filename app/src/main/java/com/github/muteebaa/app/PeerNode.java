package com.github.muteebaa.app;

import java.util.function.Consumer;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.FileSystemException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.List;
import javax.swing.*;
import java.awt.*;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * Represents a peer node in a distributed voting system.
 * Handles communication, voting, and peer registration.
 */
public class PeerNode {
    // ANSI color codes for console output
    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_BLACK = "\u001B[30m";
    public static final String ANSI_RED = "\u001B[31m"; // heartbeat
    public static final String ANSI_GREEN = "\u001B[32m"; // registration
    public static final String ANSI_YELLOW = "\u001B[33m"; // ACKS
    public static final String ANSI_BLUE = "\u001B[34m";
    public static final String ANSI_PURPLE = "\u001B[35m"; // CLI
    public static final String ANSI_CYAN = "\u001B[36m"; // election
    public static final String ANSI_WHITE = "\u001B[37m";

    private Thread serverThread;
    private Thread heartbeatThread;
    private Thread heartbeatMonitorThread;
    private Thread promptForVoteThread;
    private Thread startVotingThread;

    private Consumer<String> heartbeatStatusConsumer;
    private Consumer<String> statusMessageConsumer;

    private Consumer<String> guiMessageConsumer;

    private static final Scanner scanner = new Scanner(System.in);
    private final NodeCommunication nodeComm;
    private final int port;
    private int nodeId; // will be used in leader election
    private final Map<Number, String> peerNodes; // each peer will have a list of other peers
    private Map<String, Integer> voteTally;
    private String leaderAddress;
    private String sessionCode;
    private boolean acknowledgment = false;
    private boolean hasVoted = false;
    private String uuid;
    private ConcurrentSkipListSet<String> uuidSet;

    private volatile boolean leaderToken;
    private long lastHeartbeatTime = System.currentTimeMillis(); // Track last heartbeat
    private boolean running = false; // wether or not this node is running in the election
    private volatile boolean bullied = false;// wether or not this node has been bullied
    private String voteBuffer = null;

    // leader election lock
    private static final Object electionLock = new Object();

    private static final int TIMEOUT = 5000; // T time units in milliseconds
    private static final int WAIT_TIME = 3000; // T' time units

    /**
     * Initializes a new PeerNode instance.
     *
     * @param port The port the peer listens on.
     */
    public PeerNode(int port) {
        this.nodeComm = new NodeCommunication();
        this.port = port;
        this.nodeId = 1; // updated by the leader upon registration
        this.peerNodes = new HashMap<Number, String>();
        this.voteTally = new HashMap<>();
        try {
            this.uuid = loadUUID();
            if (this.uuid == null) {
                throw new IllegalStateException("UUID loading failed, received null.");
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            System.err.println("Failed to load UUID. Application closing.");
            System.exit(-1);
        }

        this.uuidSet = new ConcurrentSkipListSet<>();
    }

    public boolean getHasVoted() {
        return this.hasVoted;
    }

    private void broadcastMessage(String message, Collection<String> peerNodes) {
        Collection<String> broadcastTo = peerNodes != null ? peerNodes : this.peerNodes.values();
        Collection<String> failedToSendTo = nodeComm.broadcastMessage(message, broadcastTo);

        if (!failedToSendTo.isEmpty()) {
            System.out.println(ANSI_RED + "Failed to send message to: " + failedToSendTo + ANSI_RESET);

            for (String peer : failedToSendTo) {
                this.peerNodes.values().removeIf(value -> value.equals(peer));
            }
            System.out.println(ANSI_RED + "Updated peer list after failure: " + this.peerNodes + ANSI_RESET);
            this.broadcastPeerList();
        }
    }

    /**
     * This gets the System/Motherboard UUID which is unique to the motherboard.
     * This effectively means 1 machine one vote for our system.
     * This should be valid for both Linux and Windows machines. MAC is not
     * supported.
     * 
     * @return System UUID/Motherboard UUID
     */
    private static String getSystemUUID() {
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
                Process process = Runtime.getRuntime().exec("wmic path win32_computersystemproduct get UUID");
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                reader.readLine(); // Skip the header
                uuid = reader.readLine().trim();
            }
            // For macOS
            else if (System.getProperty("os.name").toLowerCase().contains("mac")) {
                Process process = Runtime.getRuntime()
                        .exec("ioreg -rd1 -c IOPlatformExpertDevice | awk '/IOPlatformUUID/ {print $3}'");
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                uuid = reader.readLine().replace("\"", "").trim();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return uuid;
    }

    /**
     * Uses the System/Motherboard UUID to generate a unique java UUID.
     * 
     * @return UUID based on the System/Motherboard UUID
     */
    private static UUID generateUUID() {
        String systemUUID = getSystemUUID();
        if (systemUUID != null) {
            return UUID.nameUUIDFromBytes(systemUUID.getBytes());
        }
        return null;
    }

    /**
     * Saves a UUID as a string to the user home directory in the folder .uuid in a
     * read only file uuid.txt.
     * 
     * @param uuid
     * @return Boolean based on if the saving was successful
     */
    private static boolean saveUUID(UUID uuid) {
        String filePath = System.getProperty("user.home") + File.separator + ".uuid" + File.separator + "uuid.txt";

        try {
            File file = new File(filePath);
            file.getParentFile().mkdirs(); // Ensure directory exists

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, false))) {
                writer.write(uuid.toString());
            }

            return file.setReadOnly(); // Ensure the file is read-only
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * This function tries to load from the uuid.txt file in the .uuid folder in the
     * user home directory.
     * If the file doesn't exist, it will *try* to generate a UUID file for the
     * user. This file is READ ONLY when generated.
     * Technically speaking there are ways around this current implementation, as in
     * there are no check sums, but for now
     * this is okay as we are not releasing this commercially.
     * If there is a failure to do the task, as in the file does not exist and fails
     * to generate, it will throw a FileNotFoundException.
     * 
     * @throws FileNotFoundException
     * @return The UUID as a string.
     */
    private static String loadUUID() throws FileNotFoundException {
        String filePath = System.getProperty("user.home") + File.separator + ".uuid" + File.separator + "uuid.txt";
        File file = new File(filePath);

        if (!file.exists()) {
            UUID newUUID = generateUUID();
            boolean success = saveUUID(newUUID);
            if (!success) {
                throw new FileNotFoundException(
                        "File uuid.txt in the 'user home'/.uuid folder does not exist and failed to generate properly.");
            }
            return newUUID.toString();
        }

        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line);
            }
            String uuidString = content.toString().trim();
            if (uuidString.isEmpty()) {
                throw new IOException("UUID file is empty.");
            }
            return uuidString;
        } catch (IOException e) {
            e.printStackTrace();
            throw new FileNotFoundException("Failed to read UUID from file: " + e.getMessage());
        }
    }

    public void setSessionCode(String sessionCode) {
        this.sessionCode = sessionCode;
    }

    /**
     * Starts the peer as a server and registers with the leader.
     */
    public void startPeer() {
        serverThread = new Thread(() -> nodeComm.startServer(port, this::handleMessage));
        serverThread.start();
    }

    public void setHeartbeatStatusConsumer(Consumer<String> consumer) {
        this.heartbeatStatusConsumer = consumer;
    }

    private void notifyHeartbeat(String message) {
        if (heartbeatStatusConsumer != null) {
            SwingUtilities.invokeLater(() -> heartbeatStatusConsumer.accept(message));
        }
    }

    private void startHeartbeat() {
        heartbeatThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                if (this.hasLeaderToken()) {
                    String message = "Sending heartbeat ... ❤️";
                    System.out.println(ANSI_RED + message + ANSI_RESET);
                    notifyHeartbeat(message);

                    this.broadcastMessage("HEARTBEAT", null);
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        });
        heartbeatThread.start();
    }

    private void startHeartbeatMonitor() {
        heartbeatMonitorThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) { // Check for interruption here
                // Only monitor if I'm not the leader and I'm not running in the leader election
                if (!this.hasLeaderToken() && !this.running) {
                    try {
                        Thread.sleep(5000); // Check every 5 seconds
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt(); // Set the interrupt flag again
                        break; // Exit the loop if interrupted
                    }

                    // If no heartbeat received for 10+ sec → Start election
                    if (System.currentTimeMillis() - lastHeartbeatTime > 10000) {
                        if (!this.running) {
                            System.out.println(ANSI_CYAN + "No heartbeat received. Starting election." + ANSI_RESET);
                            this.initiateElection();
                        }
                    }
                }
            }
        });
        heartbeatMonitorThread.start();
    }

    public String getMyIp() {
        try {
            String myIp = InetAddress.getLocalHost().getHostAddress();

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
        System.out.print("\n\nregistering with leader\n\n");
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

        if (this.leaderAddress == null) {
            setLeaderAddress(leaderAddress);
        }

        // start the heartbeat and monitor
        this.startHeartbeat();
        this.startHeartbeatMonitor();
    }

    /**
     * Handles incoming messages.
     *
     * @param message The received message.
     */
    // Add this field to PeerNode class
    // Add this setter method
    public void setStatusMessageConsumer(Consumer<String> consumer) {
        this.statusMessageConsumer = consumer;
    }

    // Helper method to send messages to GUI
    private void sendToGUIMessageConsumer(String message) {
        if (guiMessageConsumer != null) {
            SwingUtilities.invokeLater(() -> guiMessageConsumer.accept(message));
        }
    }

    private void sendToGUI(String message) {
        if (statusMessageConsumer != null) {
            SwingUtilities.invokeLater(() -> statusMessageConsumer.accept(message));
        }
    }

    private void broadcastPeerList() {
        String peerList = peerNodes.entrySet().stream()
                .map(entry -> entry.getKey() + "," + entry.getValue())
                .collect(Collectors.joining("-"));

        this.broadcastMessage("UPDATE_NEW_PEER:" + peerList, this.peerNodes.values());
        sendToGUI("Updated peer list: " + peerNodes);
    }

    // Modified handleMessage method
    public void handleMessage(String message) {
        if (message.startsWith("REGISTER:")) {
            String peer = message.substring(9);
            System.out.println(peer);
            int highestCurrentId = peerNodes.keySet().stream()
                    .mapToInt(Number::intValue)
                    .max()
                    .orElse(0);

            int newId = highestCurrentId + 1;

            peerNodes.put(newId, peer);
            this.broadcastPeerList();

            sendToGUI("New peer registered. Peer list: " + peerNodes);
            nodeComm.connectToNode(peer.split(":")[0], Integer.parseInt(peer.split(":")[1]));
            nodeComm.sendMessage("ACK: You are successfully registered.", nodeComm.getClientSocket());

            // send this peer the vote tally
            nodeComm.connectToNode(peer.split(":")[0], Integer.parseInt(peer.split(":")[1]));
            nodeComm.sendMessage("COMPLETE_VOTE_TALLY:" + voteTally.toString(), nodeComm.getClientSocket());

            // send the UUID's that have already voted
            nodeComm.connectToNode(peer.split(":")[0], Integer.parseInt(peer.split(":")[1]));
            nodeComm.sendMessage("UUID_SET:" + this.uuidSet.toString(), nodeComm.getClientSocket());

        } else if (message.equals("HEARTBEAT")) {
            sendToGUI("Heartbeat received from leader");
            lastHeartbeatTime = System.currentTimeMillis();
        } else if (message.startsWith("UUID_SET:")) {
            String uuidSetString = message.substring(9);
            uuidSetString = uuidSetString.substring(1, uuidSetString.length() - 1);
            System.out.println(uuidSetString);

            String[] uuids = uuidSetString.split(",");
            for (String uuid : uuids) {
                uuid = uuid.trim();
                System.out.println("--" + uuid);
                if (uuid.length() != 36) {
                    continue;
                }
                this.uuidSet.add(uuid);
            }
        } else if (message.startsWith("COMPLETE_VOTE_TALLY:")) {
            String voteTallyString = message.substring(20);
            voteTallyString = voteTallyString.substring(1, voteTallyString.length() - 1);
            String[] parts = voteTallyString.split(",");
            for (String part : parts) {
                String[] keyValue = part.split("=");
                if (keyValue.length == 2) {
                    String key = keyValue[0].trim();
                    int value = Integer.parseInt(keyValue[1]);
                    voteTally.put(key, value);
                }
            }
            sendToGUI("Vote tally received: " + voteTally);
        } else if (message.startsWith("UPDATE_NEW_PEER:")) {
            String newPeerData = message.substring("UPDATE_NEW_PEER:".length()).trim();
            String[] parts = newPeerData.split("-");

            peerNodes.clear();
            for (int i = 0; i < parts.length; i++) {
                String[] idAndIp = parts[i].split(",");
                if (idAndIp.length == 2) {
                    int peerId = Integer.parseInt(idAndIp[0]);
                    String peerIp = idAndIp[1];
                    peerNodes.put(peerId, peerIp);

                    if (peerIp.equals(getMyIp() + ":" + port)) {
                        this.nodeId = peerId;
                    }
                }
            }
            sendToGUI("Updated peer list: " + peerNodes);
        } else if (message.startsWith("ACK:")) {
            synchronized (this) {
                acknowledgment = true;
                notifyAll();
            }

            if (message.contains("Your vote was successfully counted")) {
                sendToGUIMessageConsumer("HIDE_VOTING_OPTIONS");

            }
            sendToGUI(message.substring(4));
        } else if (message.startsWith("VOTE:")) {
            String[] parts = message.split(":");
            int nodeId = Integer.parseInt(parts[1]);
            String address = peerNodes.get(nodeId);
            String host = address.split(":")[0];
            int port = Integer.parseInt(address.split(":")[1]);
            String vote = parts[2];
            String incomingUUID = parts[3];

            if (!(this.uuidSet.contains(incomingUUID))) {
                this.uuidSet.add(incomingUUID);
                updateVoteTally(vote);
                if (leaderToken) {
                    this.broadcastMessage("UPDATE_VOTE_TALLY:" + incomingUUID + ":" + vote, null);
                    nodeComm.connectToNode(host, port);
                    nodeComm.sendMessage("ACK: Your vote was successfully counted.", nodeComm.getClientSocket());
                    sendToGUI("Vote counted: " + vote);
                }
            } else {
                nodeComm.connectToNode(host, port);
                nodeComm.sendMessage("DUPLICATE: A vote has already been cast with your UUID.",
                        nodeComm.getClientSocket());
                sendToGUI("Duplicate vote detected from UUID: " + incomingUUID);
            }
        } else if (message.startsWith("DUPLICATE:")) {
            synchronized (this) {
                acknowledgment = true;
                notifyAll();
            }
            sendToGUI("Duplicate vote - your vote was not submitted");
            sendToGUIMessageConsumer("HIDE_VOTING_OPTIONS_DUPLICATE");
        } else if (message.startsWith("UPDATE_VOTE_TALLY:")) {
            String vote = message.substring(55).trim();
            String uuid = message.substring(18, 54).trim();
            updateVoteTally(vote);
            updateUUID(uuid);
            sendToGUI("Vote tally updated: " + vote);
        } else if (message.startsWith("START_VOTING")) {
            sendToGUI("Voting has started!");
            promptForVoteThread = new Thread(this::promptForVote);
            promptForVoteThread.start();
        } else if (message.startsWith("VOTING_ENDED:")) {
            sendToGUI("Voting ended: " + message.substring(13));
            sendToGUIMessageConsumer("FINAL_RESULT:" + message.substring(13));
        } else if (message.startsWith("ELECTION:")) {
            int idOfNodeRunning = Integer.parseInt(message.substring("ELECTION:".length()));
            sendToGUI("Election initiated by node: " + idOfNodeRunning);

            if (this.nodeId > idOfNodeRunning) {
                String nodesAddress = this.peerNodes.get(idOfNodeRunning);
                String nodeIp = nodesAddress.split(":")[0];
                int nodePort = Integer.parseInt(nodesAddress.split(":")[1]);

                sendToGUI("Bullying node " + idOfNodeRunning + " at " + nodeIp + ":" + nodePort);
                nodeComm.connectToNode(nodeIp, nodePort);
                nodeComm.sendMessage("BULLY", nodeComm.getClientSocket());

                if (!this.hasLeaderToken()) {
                    this.initiateElection();
                }
            }
        } else if (message.startsWith("BULLY")) {
            sendToGUI("Received bully message - entering election");
            synchronized (this) {
                this.bullied = true;
                notifyAll();
            }
        } else if (message.startsWith("LEADER:")) {
            String newLeadersId = message.substring(7);
            String newLeaderIp = peerNodes.get(Integer.parseInt(newLeadersId));
            sendToGUI("New leader elected: Node " + newLeadersId + " at " + newLeaderIp);
            setLeaderAddress(newLeaderIp);
        }
    }

    public boolean updateUUID(String uuid) {
        boolean succcess = uuidSet.add(uuid);
        return succcess;
    }

    /**
     * Determines the leader node. (Currently hardcoded)
     */
    public void setLeaderAddress(String leaderAddress) {
        // remove peer with previous leader address from peerNodes
        peerNodes.values().removeIf(value -> value.equals(this.leaderAddress));

        this.leaderAddress = leaderAddress;

        // check buffer
        if (voteBuffer != null) {
            // send buffer to leader
            sendVoteToLeader(voteBuffer);
            voteBuffer = null;
        }
    }

    public String getLeaderAddress() {
        return leaderAddress;
    }

    public boolean hasLeaderToken() {
        return leaderToken;
    }

    /**
     * for the first time
     */
    public void setLeaderToken() {
        this.leaderToken = true;
        // set leader address to my address
        this.leaderAddress = getMyIp() + ":" + this.port;

        // System.out.println("Leader token set.");
        // System.out.println(leaderAddress);
    }

    /**
     * when a new leader is elected use this
     */
    public void takeLeaderToken() {
        if (this.leaderToken) {
            return;
        }
        this.leaderToken = true;
        // set leader address to my address
        this.leaderAddress = getMyIp() + ":" + this.port;

        System.out.println(ANSI_CYAN + "Leader token set." + ANSI_RESET);
        // System.out.println(leaderAddress);

        this.broadcastMessage("LEADER:" + this.nodeId, null);

        SessionRegistry.updateSession(this.sessionCode, null, leaderAddress.split(":")[0],
                Integer.parseInt(leaderAddress.split(":")[1]));

        if (this.voteBuffer != null) {
            // send buffer to leader
            System.out.println("printing buffer");
            System.out.println(this.voteBuffer);
            sendVoteToLeader(voteBuffer);
            voteBuffer = null;
        }
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
        // add vote to buffer
        this.voteBuffer = vote;

        this.acknowledgment = false;
        if (nodeComm.connectToNode(leaderAddress.split(":")[0], Integer.parseInt(leaderAddress.split(":")[1]))) {

            nodeComm.sendMessage("VOTE:" + this.nodeId + ":" + vote + ":" + this.uuid, nodeComm.getClientSocket());

            // Wait for acknowledgment from the leader
            while (!acknowledgment) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    // e.printStackTrace();
                    // initiate election
                    this.initiateElection();
                }
            }
            this.hasVoted = true;
            this.voteBuffer = null;
        } else {
            // initiate election
            this.initiateElection();
        }
    }

    /**
     * Prompts the user for a vote.
     */
    public void startVoting() {
        // System.out.println("starting voting");
        // System.out.println("peer nodes: " + nodeComm.getPeerAddresses());
        this.broadcastMessage("START_VOTING:" + voteTally.keySet(), null);

    }

    public void promptForVote() {
        // This will now be handled by the GUI
        if (guiMessageConsumer != null) {
            sendToGUIMessageConsumer(
                    "SHOW_VOTING_OPTIONS:" + String.join(",", SessionRegistry.getVotingOptions(sessionCode)));

        }
    }

    public void setGuiMessageConsumer(Consumer<String> consumer) {
        this.guiMessageConsumer = consumer;
        System.out.print("sending to the frontend the voting options");
    }

    private void sendToGui(String message) {
        System.out.print(message);
        if (guiMessageConsumer != null) {
            SwingUtilities.invokeLater(() -> guiMessageConsumer.accept(message));
        }
    }

    // public void waitForStartVoting() {

    // SwingUtilities.invokeLater(() -> {
    // String input = JOptionPane.showInputDialog(
    // null,
    // "Type 'start' to begin voting:",
    // "Start Voting",
    // JOptionPane.PLAIN_MESSAGE);

    // if (input != null && input.trim().equalsIgnoreCase("start")) {
    // = new Thread(() -> {
    // if (SessionRegistry.updateSession(this.sessionCode, "started", null, null)) {
    // this.startVoting();
    // this.promptForVote();
    // }
    // });
    // startVotingThread.start();
    // } else {
    // sendToGui("Invalid input. Type 'start' to begin.");
    // }
    // });
    // }

    public void startVotingButtonClicked() {
        // Update session status
        if (SessionRegistry.updateSession(this.sessionCode, "started", null, null)) {
            // Leader-specific actions
            if (leaderToken) {
                this.startVoting();

                // Broadcast voting start to all peers
                this.broadcastMessage("START_VOTING:" + sessionCode, null);
            }

            // Notify GUI to show voting options
            String options = String.join(",", SessionRegistry.getVotingOptions(sessionCode));
            sendToGUIMessageConsumer("SHOW_VOTING_OPTIONS:" + options);
        }
    }

    /**
     * Ends the voting process and broadcasts results.
     */
    public void endVoting() {
        SessionRegistry.updateSession(this.sessionCode, "ended", null, null);
        String results = "VOTING_ENDED:" + voteTally;
        System.out.println(ANSI_PURPLE + results.substring(13) + ANSI_RESET);
        sendToGUIMessageConsumer("FINAL_RESULT:" + voteTally);
        this.broadcastMessage(results, null);

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
        String sessionCode = SessionRegistry.saveSession(myIp, this.port, options, "waiting");
        for (String option : options.split(",")) {
            voteTally.put(option.trim(), 0);
        }
        this.sessionCode = sessionCode;
        setLeaderToken(); // Leader token is initially with the session creator
        return sessionCode;
    }

    /**
     * Displays available voting sessions.
     */
    public static void displayAvailableSessions() {
        Map<String, String> sessions = SessionRegistry.loadSessions();
        if (sessions.isEmpty()) {
            System.out.println(ANSI_PURPLE + "No available sessions found." + ANSI_RESET);
        } else {
            System.out.println(ANSI_PURPLE + "Available sessions:" + ANSI_RESET);
            for (Map.Entry<String, String> entry : sessions.entrySet()) {
                System.out.println("Code: " + entry.getKey() + " | Details: " + entry.getValue());
            }
        }
    }

    // Initiate_Election(int i) /* process Pi */
    public void initiateElection() {
        System.out.println(ANSI_CYAN + "Initiating election..." + ANSI_RESET);
        // remove peer with leader address from peerNodes
        peerNodes.values().removeIf(value -> value.equals(leaderAddress));
        this.leaderAddress = null;

        // runningi = true /* I am running in this elections */
        this.running = true;

        System.out.println(ANSI_CYAN + "Peer nodes: " + peerNodes + ANSI_RESET);

        int highestCurrentId = peerNodes.keySet().stream()
                .mapToInt(Number::intValue) // Convert Number to int
                .max() // Get the maximum value
                .orElse(0); // Default value if the map is empty

        System.out.println(ANSI_CYAN + "Highest current id: " + highestCurrentId + ANSI_RESET);

        // if i is the highest id
        if (this.nodeId == highestCurrentId) {
            System.out.println(
                    ANSI_CYAN + "Node " + nodeId + " is the highest id. Declaring myself as leader." + ANSI_RESET);
            // then
            // send leader(i) to all Pj, where j ≠ i else
            System.out.println(ANSI_CYAN + "Sending leader message to all peers: " + peerNodes.values() + ANSI_RESET);
            takeLeaderToken();
            sendToGUIMessageConsumer("LEADER_CHANGE");
            sendToGUI("Leader Change");

        } else {
            // get list of ids bigger than mine
            Map<Number, String> biggerIds = peerNodes.entrySet().stream()
                    .filter(entry -> entry.getKey().intValue() > this.nodeId) // Filter keys > my ID
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)); // Collect as Map

            System.out.println(ANSI_CYAN + "Bigger ids: " + biggerIds + ANSI_RESET);
            // send election(i) to all Pj, where j > i
            this.broadcastMessage("ELECTION:" + this.nodeId, biggerIds.values());

            // /* check if there are bigger guys out there */
            // wait for T time units
            synchronized (this) {
                while (!this.bullied) {
                    try {
                        wait(TIMEOUT);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }

                // final check to prevent race condition
                if (this.bullied) {
                    System.out.println(ANSI_CYAN + "Node " + nodeId + " was bullied. Not declaring myself as leader."
                            + ANSI_RESET);
                    // Reset bullied flag
                    this.bullied = false;
                    return; // Exit the election process
                }

                // No response → Declare self as leader
                if (this.running && !hasLeaderToken()) {
                    System.out.println(ANSI_CYAN + "Node " + nodeId
                            + " received no response. Declaring myself as leader." + ANSI_RESET);
                    takeLeaderToken();
                    // nodeComm.broadcastMessage("LEADER:" + getMyIp() + "," + this.port,
                    // peerNodes.values());
                }
            }

            // else /* bully is received */
            while (this.leaderAddress == null) {

                //// wait for T’ time units
                try {
                    Thread.sleep(WAIT_TIME);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                //// if no leader(k) message Initiate_Election(i)
                initiateElection();
            }

            this.running = false;
        }

    }

    /**
     * Safely interrupts a thread if it's alive.
     *
     * @param t The thread to interrupt.
     */
    private void safeInterrupt(Thread t) {
        if (t == null) {
            System.out.println("Warning: Thread is null");
            return;
        }
        System.out.println(ANSI_RED + "Interrupting thread: " + t.getName() + ANSI_RESET);
        if (t != null && t.isAlive()) {
            // Interrupt the thread
            t.interrupt();

            // If the thread is blocking on something like sleep or accept, ensure it exits.
            try {
                System.out.println(ANSI_RED + "Waiting for thread to finish..." + ANSI_RESET);
                t.join(); // Make sure the thread finishes execution after interruption.
            } catch (InterruptedException e) {
                // Handle the exception if the current thread was interrupted while waiting for
                // t to finish
                Thread.currentThread().interrupt(); // Restore interrupt status for the current thread
            }
        }
    }

    /**
     * Shuts down the peer node and cleans up resources.
     */
    public void shutdown() {
        safeInterrupt(this.heartbeatThread);
        safeInterrupt(this.heartbeatMonitorThread);
        safeInterrupt(this.promptForVoteThread);
        safeInterrupt(this.startVotingThread);

        System.out.println(ANSI_RED + "Shutting down peer node..." + ANSI_RESET);

        this.running = false;
        this.bullied = false;
        this.leaderToken = false;
        this.acknowledgment = false;
        this.hasVoted = false;
        this.voteBuffer = null;
        this.sessionCode = null;
        this.leaderAddress = null;

        nodeComm.closeServer();
        safeInterrupt(this.serverThread);
    }
}
