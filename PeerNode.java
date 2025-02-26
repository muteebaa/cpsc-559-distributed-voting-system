import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;

public class PeerNode {
    private static final Scanner scanner = new Scanner(System.in);
    private NodeCommunication nodeComm;
    private int port;

    // vote tally
    private Map<String, Integer> voteTally;

    // host and port of other peers
    private List<String> peerNodes;

    // leader token
    private boolean leaderToken;

    // node id
    private int nodeId;

    private String leaderAddress; // Store the leader's address

    private String sessionCode;

    public void registerWithLeader(String leaderAddress) {
        this.leaderAddress = leaderAddress;
        String registrationMessage = "REGISTER:localhost:" + port;
        nodeComm.connectToNode(leaderAddress.split(":")[0], Integer.parseInt(leaderAddress.split(":")[1]));
        nodeComm.sendMessage(registrationMessage, nodeComm.getClientSocket());

        voteTally = new HashMap<>();
        // todo: handle leader's response
    }

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
     * Starts the peer as a server.
     */
    public void startPeer() {
        new Thread(() -> nodeComm.startServer(port, this::handleMessage)).start();
        findLeader();
        registerWithLeader(leaderAddress);
    }

    public void handleMessage(String message) {
        // System.out.println("handleMessage: " + message);
        // nodeComm.connectToNode(leaderAddress.split(":")[0],
        // Integer.parseInt(leaderAddress.split(":")[1]));
        // nodeComm.sendMessage("VOTE:" + vote, nodeComm.getClientSocket());
        if (message.startsWith("REGISTER:")) {
            // trim the "VOTE" part
            String peer = // peer address is the part after "REGISTER:"
                    message.substring(9);
            int peerId = // peer id is the last character in the message
                    Integer.parseInt(message.substring(message.length() - 1));

            nodeComm.updatePeerNodeAddresses(peer, peerId);
        } else if (message.startsWith("VOTE:")) {
            // trim the "VOTE" part
            String vote = message.substring(5).trim();
            // System.out.println("Received vote: " + vote);
            updateVoteTally(vote);

            // if this is the leader, broadcast the vote to other peers
            if (leaderToken) {
                nodeComm.broadcastMessage("VOTE:" + vote, nodeComm.getPeerAddresses());
            }
        } else if (message.startsWith("START_VOTING")) {
            promptForVote();
        } else if (message.startsWith("VOTING_ENDED:")) {
            System.out.println(message.substring(13));
        }
    }

    private void findLeader() {
        // hardcoding the leader address for now
        leaderAddress = "localhost:5000";
    }

    /**
     * Connects to another peer and sends a test message.
     */
    public void sendMessageToPeer(String host, int targetPort, String message) {
        nodeComm.connectToNode(host, targetPort);
        nodeComm.sendMessage(message, nodeComm.getClientSocket());
    }

    public int getNodeId() {
        return nodeId;
    }

    public boolean hasLeaderToken() {
        return leaderToken;
    }

    public void setLeaderToken(boolean leaderToken) {
        this.leaderToken = leaderToken;
    }

    public Map<String, Integer> updateVoteTally(String vote) {
        if (voteTally.containsKey(vote)) {
            voteTally.put(vote, voteTally.get(vote) + 1);
        } else {
            voteTally.put(vote, 1);
        }

        // System.out.println("updated vote tally: " + voteTally);
        return voteTally;
    }

    // method to send vote to other peers
    public void sendVoteToLeader(String vote) {
        nodeComm.connectToNode(leaderAddress.split(":")[0], Integer.parseInt(leaderAddress.split(":")[1]));
        nodeComm.sendMessage("VOTE:" + vote, nodeComm.getClientSocket());
    }

    // method to add peer nodes
    public void addPeerNode(String host, int newPort) {
        peerNodes.add(host + ":" + newPort);

        broadcastPeerNodes(newPort);
    }

    public List<String> getPeerNodes() {
        return nodeComm.getPeerAddresses();
    }

    // method to send the peer node to other peers
    public void broadcastPeerNodes(int newPort) {
        String message = "NEW_PEER" + "localhost" + ":" + newPort;

        nodeComm.broadcastMessage(message, peerNodes);

    }

    public void startVoting() {
        // System.out.println("starting voting");
        // System.out.println("peer nodes: " + nodeComm.getPeerAddresses());
        nodeComm.broadcastMessage("START_VOTING:" + voteTally.keySet(),
                nodeComm.getPeerAddresses());

    }

    public void promptForVote() {
        String options = SessionRegistry.getVotingOptions(sessionCode).toString(); // TODO: broadcasst vote so each node
                                                                                   // has a local copy? is this
                                                                                   // necessary?
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

    public void endVoting() {
        String results = "VOTING_ENDED:Thanks for voting! Voting results: " + voteTally;
        System.out.println(results.substring(13)); // display result to leader
        nodeComm.broadcastMessage(results, nodeComm.getPeerAddresses()); // this sends to everyone except the leader
    }

    public Map<String, Integer> getVoteTally() {
        return voteTally;
    }

    /**
     * Starts a new session and saves it in the session registry.
     */
    public String startNewSession(String ip, int port, String options) {
        String sessionCode = generateRandomCode();
        SessionRegistry.saveSession(sessionCode, ip, port, options);
        for (String option : options.split(",")) {
            voteTally.put(option.trim(), 0);
        }

        this.sessionCode = sessionCode;
        return sessionCode;
    }

    /**
     * Gets available session codes from the file.
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
