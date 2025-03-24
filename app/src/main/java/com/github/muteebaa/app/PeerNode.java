package com.github.muteebaa.app;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Represents a peer node in a distributed voting system.
 * Handles communication, voting, and peer registration.
 */
public class PeerNode {
    private static final Scanner scanner = new Scanner(System.in);
    private final NodeCommunication nodeComm;
    private final int port;
    private int nodeId; // will be used in leader election
    private final Map<Number, String> peerNodes; // each peer will have a list of other peers
    private Map<String, Integer> voteTally;
    private String leaderAddress;
    private String sessionCode;
    private boolean acknowledgment = false;

    private volatile boolean leaderToken;
    private long lastHeartbeatTime = System.currentTimeMillis(); // Track last heartbeat
    private boolean running = false; // wether or not this node is running in the election
    // private boolean bullied = false;
    private volatile boolean bullied = false;// wether or not this node has been bullied
    private String voteBuffer = null;

    private static final int TIMEOUT = 5000; // T time units in milliseconds
    private static final int WAIT_TIME = 3000; // T' time units

    /**
     * Initializes a new PeerNode instance.
     *
     * @param port   The port the peer listens on.
     * @param nodeId The unique identifier for this node.
     */
    public PeerNode(int port) {
        this.nodeComm = new NodeCommunication();
        this.port = port;
        this.nodeId = 1; // will be set by the leader
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

    private void startHeartbeat() {
        new Thread(() -> {
            while (true) {
                // Only send if I'm the leader
                if (this.hasLeaderToken()) {
                    System.out.println("Sending heartbeat...");
                    nodeComm.broadcastMessage("HEARTBEAT", peerNodes.values());
                    try {
                        Thread.sleep(3000); // Send every 3 seconds
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }).start();
    }

    private void startHeartbeatMonitor() {
        new Thread(() -> {
            while (true) {
                // Only monitor if I'm not the leader and im not running in the leader election
                if (!this.hasLeaderToken() && !this.running) {
                    try {
                        Thread.sleep(5000); // Check every 5 seconds
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }

                    // If no heartbeat received for 10+ sec → Start election
                    if (System.currentTimeMillis() - lastHeartbeatTime > 10000) {
                        System.out.println("No heartbeat received. Starting election if not running.");
                        if (!this.running) {
                            this.initiateElection();
                        }
                    }
                }
            }
        }).start();
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

        changeLeader(leaderAddress);

        // start the heartbeat and monitor
        this.startHeartbeat();
        this.startHeartbeatMonitor();

        // TODO: Handle leader's response - do we need to?
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

            // Convert peerNodes to a formatted string with IDs and IPs
            String peerList = peerNodes.entrySet().stream()
                    .map(entry -> entry.getKey() + "," + entry.getValue()) // Format each entry as "id:ip"
                    .collect(Collectors.joining("-")); // Join with commas

            peerNodes.put(newId, peer);

            nodeComm.broadcastMessage("UPDATE_NEW_PEER:" + newId + "," + peer + "-" + peerList, peerNodes.values());

            System.out.println("My peer list: " + peerNodes);

            nodeComm.connectToNode(peer.split(":")[0], Integer.parseInt(peer.split(":")[1]));
            nodeComm.sendMessage("ACK: You are successfully registered.", nodeComm.getClientSocket());
        }

        else if (message.equals("HEARTBEAT")) {
            System.out.println("Heartbeat received.");
            lastHeartbeatTime = System.currentTimeMillis(); // Reset timer
        } else if (message.startsWith("UPDATE_NEW_PEER:")) {
            System.out.println("New peer message received: " + message);
            System.out.println("My peer list before update: " + peerNodes);

            // Extract data from message
            String newPeerData = message.substring("UPDATE_NEW_PEER:".length()).trim();
            String[] parts = newPeerData.split("-");

            System.out.println("Parts: " + Arrays.toString(parts));

            // if (parts.length < 2) {
            // System.out.println("Invalid peer update message format.");
            // return;
            // }

            // Extract new peer info
            // String newPeerIp = parts[0];
            // int newPeerId = Integer.parseInt(parts[1]);

            // Clear and update peerNodes
            peerNodes.clear();
            for (int i = 0; i < parts.length; i++) { // Start from index 2 to skip newPeer info
                String[] idAndIp = parts[i].split(",");
                if (idAndIp.length == 2) {
                    int peerId = Integer.parseInt(idAndIp[0]);
                    String peerIp = idAndIp[1];
                    peerNodes.put(peerId, peerIp);

                    // Check if the new peer is this node
                    if (peerIp.equals(getMyIp() + ":" + port)) {
                        this.nodeId = peerId;
                    }

                }
            }

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
            new Thread(this::promptForVote).start();
        } else if (message.startsWith("VOTING_ENDED:")) {
            System.out.println();
            System.out.println(message.substring(13));
        } else if (message.startsWith("ELECTION:")) {
            System.out.println("Election message received: " + message);
            int idOfNodeRunning = Integer.parseInt(
                    message.substring("ELECTION:".length()));

            if (this.nodeId > idOfNodeRunning) {
                // get the node's address and bully it
                String nodesAddress = this.peerNodes.get(idOfNodeRunning);
                String nodeIp = nodesAddress.split(":")[0];
                int nodePort = Integer.parseInt(nodesAddress.split(":")[1]);

                System.out.println("Bullying node " + idOfNodeRunning + " at " + nodeIp + ":" + nodePort);
                nodeComm.connectToNode(nodeIp, nodePort);
                nodeComm.sendMessage("BULLY", nodeComm.getClientSocket());

                // am i already the leader?
                if (!this.hasLeaderToken()) {
                    this.initiateElection();
                }

                // // get list of ids bigger than mine
                // Map<Number, String> biggerIds = peerNodes.entrySet().stream()
                // .filter(entry -> entry.getKey().intValue() > this.nodeId) // Filter keys > my
                // ID
                // .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)); //
                // Collect as Map

                // // send election(i) to all Pj, where j > i
                // nodeComm.broadcastMessage("ELECTION:" + this.nodeId, biggerIds.values());

            }
        } else if (message.startsWith("BULLY")) {
            System.out.println("Bully message received: " + message);
            // this.bullied = true;

            synchronized (this) {
                this.bullied = true;
                notifyAll();
            }

        } else if (message.startsWith("LEADER:")) {
            System.out.println("Leader message received: " + message);
            String newLeadersId = message.substring(7);

            String newLeaderIp = peerNodes.get(Integer.parseInt(newLeadersId));

            changeLeader(newLeaderIp);
        }
    }

    /**
     * Determines the leader node. (Currently hardcoded)
     */
    public void changeLeader(String leaderAddress) {
        System.out.println("leader address: " + leaderAddress);

        // remove peer with leader address from peerNodes
        peerNodes.values().removeIf(value -> value.equals(this.leaderAddress));

        this.leaderAddress = leaderAddress;

        // check buffer
        if (voteBuffer != null) {
            // send buffer to leader
            sendVoteToLeader(voteBuffer);
            voteBuffer = null;
        }
    }

    public boolean hasLeaderToken() {
        return leaderToken;
    }

    public void setLeaderToken() {
        this.leaderToken = true;
        // set leader address to my address
        this.leaderAddress = getMyIp() + ":" + this.port;

        System.out.println("Leader token set.");
        System.out.println(leaderAddress);

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
        System.out.println("leader address: " + leaderAddress);

        this.acknowledgment = false;

        if (nodeComm.connectToNode(leaderAddress.split(":")[0], Integer.parseInt(leaderAddress.split(":")[1]))) {
            nodeComm.sendMessage("VOTE:localhost:" + this.port + ":" + vote, nodeComm.getClientSocket());

            // Wait for acknowledgment from the leader
            while (!acknowledgment) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    // e.printStackTrace();

                    // add vote to buffer
                    voteBuffer = vote;

                    // initiate election
                    this.initiateElection();
                }
            }
        } else {
            // add vote to buffer
            voteBuffer = vote;

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
        nodeComm.broadcastMessage("START_VOTING:" + voteTally.keySet(),
                peerNodes.values());

    }

    public void promptForVote() {
        String options = SessionRegistry.getVotingOptions(sessionCode).toString();
        System.out.println("\nVoting started!");
        System.out.println("Voting options: " + options);

        System.out.print("Enter your vote: ");
        if (scanner.hasNextLine()) {
            System.out.println("got vote!!!!");
            String vote = scanner.nextLine();
            System.out.println(vote);

            sendVoteToLeader(vote);
            System.out.println("Vote submitted: " + vote);
            if (!leaderToken) {
                System.out.println("We will let you know when voting ends.");
            } else {
                this.waitForEndVoting();
            }
        } else {
            System.out.println("Input stream closed. Cannot receive votes.");
        }
    }

    private void waitForEndVoting() {
        while (true) {
            System.out.print("Enter 'end' to stop voting: ");
            String input = scanner.nextLine().trim().toLowerCase();

            if (input.equals("end")) {
                this.endVoting();
                break;
            }
            System.out.println("Invalid input. Type 'end' to end voting.");
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
        setLeaderToken(); // Leader token is initially with the session creator
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
    public void initiateElection() {
        System.out.println("Initiating election...");
        // remove peer with leader address from peerNodes
        peerNodes.values().removeIf(value -> value.equals(leaderAddress));
        this.leaderAddress = null;

        // runningi = true /* I am running in this elections */
        this.running = true;

        int highestCurrentId = peerNodes.keySet().stream()
                .mapToInt(Number::intValue) // Convert Number to int
                .max() // Get the maximum value
                .orElse(0); // Default value if the map is empty

        System.out.println("Highest current id: " + highestCurrentId);

        // if i is the highest id
        if (this.nodeId == highestCurrentId) {
            setLeaderToken();
            System.out.println("Node " + nodeId + " is the highest id. Declaring myself as leader.");
            // then
            // send leader(i) to all Pj, where j ≠ i else
            System.out.println("Sending leader message to all peers: " + peerNodes.values());

            nodeComm.broadcastMessage("LEADER:" + this.nodeId, peerNodes.values());
        } else {
            // get list of ids bigger than mine
            Map<Number, String> biggerIds = peerNodes.entrySet().stream()
                    .filter(entry -> entry.getKey().intValue() > this.nodeId) // Filter keys > my ID
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)); // Collect as Map

            System.out.println("Bigger ids: " + biggerIds);
            // send election(i) to all Pj, where j > i
            nodeComm.broadcastMessage("ELECTION:" + this.nodeId, biggerIds.values());

            // /* check if there are bigger guys out there */
            // wait for T time units // waitForResponse(TIMEOUT);
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
                    System.out.println("Node " + nodeId + " was bullied. Not declaring myself as leader.");
                    // Reset bullied flag
                    this.bullied = false;
                    return; // Exit the election process
                }

                // No response → Declare self as leader
                if (this.running && !hasLeaderToken()) {
                    System.out.println("Node " + nodeId + " received no response. Declaring myself as leader.");
                    setLeaderToken();
                    nodeComm.broadcastMessage("LEADER:" + getMyIp() + "," + this.port, peerNodes.values());
                }
            }
            // while (!this.bullied) {
            // try {
            // Thread.sleep(TIMEOUT);
            // } catch (InterruptedException e) {
            // Thread.currentThread().interrupt();
            // }
            // // if no response /* time out, no response */
            // if (this.running && !hasLeaderToken()) {
            // System.out.println("Node " + nodeId + " received no response. Declaring
            // myself as leader.");
            // // leaderi = i /* I am the leader */
            // setLeaderToken(true);
            // // send leader(i) to all Pj, where j ≠ i
            // nodeComm.broadcastMessage("LEADER:" + getMyIp() + "," + this.port,
            // peerNodes.values());

            // }
            // }
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

            //// else (leader(k) from k)
            ////// leaderi = k
            ////// runningi = false /* leader elected */

            this.running = false;
        }

    }

    private void waitForResponse(int timeout) {
        try {
            Thread.sleep(timeout);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // If still running and no leader message, restart election
        if (this.running && !hasLeaderToken()) {
            System.out.println("Node " + nodeId + " received no response. Declaring itself as leader.");
        }
    }
}
