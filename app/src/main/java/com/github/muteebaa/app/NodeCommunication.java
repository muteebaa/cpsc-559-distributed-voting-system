package com.github.muteebaa.app;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.function.Consumer;

/**
 * Handles peer-to-peer communication between nodes.
 * Supports message sending, receiving, peer tracking, and voting tallying.
 */
public class NodeCommunication {
    private ServerSocket serverSocket;
    private Socket clientSocket;
    private final Map<String, Integer> voteTally = new HashMap<>();
    private Consumer<String> messageHandler; // Callback function for message handling

    /**
     * Starts a server to listen for incoming peer connections.
     *
     * @param port    The port to listen on.
     * @param handler A callback to handle received messages.
     */
    public void startServer(int port, Consumer<String> handler) {
        this.messageHandler = handler;
        try {
            serverSocket = new ServerSocket(port);
            // System.out.println("server started!");
            while (true) {
                Socket socket = serverSocket.accept();
                new Thread(() -> handleIncomingMessage(socket)).start(); // Run message handling on a new thread
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Establishes a connection to another node.
     *
     * @param host The hostname or IP address.
     * @param port The port number.
     */
    public boolean connectToNode(String host, int port) {
        try {
            clientSocket = new Socket(host, port);
        } catch (IOException e) {
            // e.printStackTrace();
            return false;
        }

        return true;
    }

    /**
     * Sends a message through the specified socket.
     *
     * @param message The message to send.
     * @param socket  The socket to send through.
     */
    public void sendMessage(String message, Socket socket) {
        try (PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            out.println(message);
        } catch (IOException e) {
            e.printStackTrace(); // Print the full exception stack trace
        }
    }

    /**
     * Handles an incoming message from a peer.
     *
     * @param socket The socket receiving the message.
     */
    private void handleIncomingMessage(Socket socket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            String message = in.readLine();
            if (messageHandler != null) {
                messageHandler.accept(message);
            } else {
                processMessage(message);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Processes incoming messages such as peer registration and votes.
     *
     * @param message The received message.
     */
    private void processMessage(String message) {
        if (message.startsWith("REGISTER:")) {
            String peer = message.substring(9, message.length() - 1);
            int peerId = Integer.parseInt(message.substring(message.length() - 1));
        } else if (message.startsWith("VOTE:")) {
            updateVoteTally(message.substring(5));
        } else {
            System.out.println("Received: " + message);
        }
    }

    /**
     * Updates the vote tally with a new vote.
     *
     * @param vote The vote received.
     */
    public void updateVoteTally(String vote) {
        voteTally.put(vote, voteTally.getOrDefault(vote, 0) + 1);
    }

    /**
     * Broadcasts a message to all known peers, excluding the sender.
     *
     * @param message       The message to broadcast.
     * @param peerAddresses The list of peer addresses.
     */
    public void broadcastMessage(String message, Collection<String> peerAddresses) {
        for (String peer : peerAddresses) {
            String[] parts = peer.split(":");
            String host = parts[0];
            int port = Integer.parseInt(parts[1]);
            if (port == serverSocket.getLocalPort())
                continue;
            try (Socket socket = new Socket(host, port)) {
                sendMessage(message, socket);
            } catch (IOException e) {
                System.err.println("Failed to send message to " + peer);
            }
        }
    }

    /**
     * Retrieves the current vote tally.
     *
     * @return A map of votes and their counts.
     */
    public Map<String, Integer> getVoteTally() {
        return voteTally;
    }

    /**
     * Gets the client socket instance.
     *
     * @return The client socket.
     */
    public Socket getClientSocket() {
        return clientSocket;
    }
}
