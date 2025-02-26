import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class NodeCommunication {
    private ServerSocket serverSocket;
    private Socket clientSocket;

    private Map<String, Integer> peerAddresses = new HashMap<>();

    private Map<String, Integer> voteTally = new HashMap<>();

    private Consumer<String> messageHandler; // Callback function to notify PeerNode

    /**
     * Starts a server socket to listen for incoming connections.
     */
    public void startServer(int port, Consumer<String> handler) {
        this.messageHandler = handler; // Store the handler function
        try {
            serverSocket = new ServerSocket(port);
            // System.out.println("Node started on port " + port);

            // NodeTracker tracker = NodeTracker.getInstance();
            // tracker.trackNode("localhost:" + port);

            while (true) {
                Socket socket = serverSocket.accept();
                handleIncomingMessage(socket);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Connects to another node.
     */
    public void connectToNode(String host, int port) {
        try {
            clientSocket = new Socket(host, port);
            // System.out.println("Connected to node at " + host + ":" + port);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Sends a message through the given socket.
     */
    public void sendMessage(String message, Socket socket) {
        try {
            // System.out.println("Sending message: '" + message + "' to " +
            // socket.getInetAddress().getHostAddress() + ":"
            // + socket.getPort());
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            out.println(message);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     */
    public void updatePeerNodeAddresses(String peer, int peerId) {
        peerAddresses.put(peer, peerId);

        String peerAddressesConcatenated = String.join(",", getPeerAddresses());

        // broadcast the updated peer addresses to all peers
        broadcastMessage("NEW PEER !!: " + peerAddressesConcatenated, getPeerAddresses());
    }

    /**
     * Receives and handles an incoming message.
     */
    private void handleIncomingMessage(Socket socket) {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String message = in.readLine();

            // System.out.println("handleIncomingMessage: " + message);
            if (messageHandler != null) {
                // System.out.println("messageHandler not null: " + messageHandler);
                messageHandler.accept(message); // Pass message to PeerNode
            } else {
                // TODO: should we not handle the message if there's no handler?
                if (message.startsWith("REGISTER:")) {
                    // trim the "VOTE" part
                    String peer = // peer address is the part after "REGISTER:"
                            message.substring(9);
                    int peerId = // peer id is the last character in the message
                            Integer.parseInt(message.substring(message.length() - 1));

                    updatePeerNodeAddresses(peer, peerId);
                } else if (message.startsWith("VOTE:")) {
                    // trim the "VOTE" part
                    String vote = message.substring(5);
                    // System.out.println("Received vote: " + vote);

                    updateVoteTally(vote);

                } else {
                    System.out.println("Received: " + message);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Socket getClientSocket() {
        return clientSocket;
    }

    public void updateVoteTally(String vote) {
        if (voteTally.containsKey(vote)) {
            voteTally.put(vote, voteTally.get(vote) + 1);
        } else {
            voteTally.put(vote, 1);
        }
    }
    // method to send Map<String, Integer> peernodes to other peers
    // public void sendPeerNodesToPeers(Map<String, Integer> peerNodes) {
    // // send peerNodes to other peers
    // for (Map.Entry<String, Integer> entry : peerNodes.entrySet()) {
    // String host = entry.getKey();
    // int port = entry.getValue();
    // connectToNode(host, port);
    // sendMessage(peerNodes.toString(), clientSocket);
    // }
    // }

    public void broadcastMessage(String message, List<String> peerAddresses) {
        // filter the peerAddresses to exclude the sender
        for (String peer : peerAddresses) {
            String[] parts = peer.split(":");
            String host = parts[0];
            int port = Integer.parseInt(parts[1]);

            // whats our server port?
            int serverPort = serverSocket.getLocalPort();
            // System.out.println("serverPort: " + serverPort);

            if (port == serverPort) {
                continue;
            }
            try (Socket socket = new Socket(host, port)) {

                sendMessage(message, socket);
            } catch (IOException e) {
                System.err.println("Failed to send message to " + peer);
            }
        }

    }

    public List<String> getPeerAddresses() {
        return new ArrayList<>(peerAddresses.keySet());
    }

    public Map<String, Integer> getVoteTally() {
        return voteTally;
    }
}