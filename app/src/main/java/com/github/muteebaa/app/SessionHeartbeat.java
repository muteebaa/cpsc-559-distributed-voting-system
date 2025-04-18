package com.github.muteebaa.app;

/**
 * This class is responsible for sending heartbeat signals to the session
 * registry.
 */
public class SessionHeartbeat implements Runnable {
    private final PeerNode node;

    /**
     * Constructor for SessionHeartbeat.
     * 
     * @param node The PeerNode instance representing the current node.
     */
    public SessionHeartbeat(PeerNode node) {
        this.node = node;
    }

    /**
     * This method is called to run the heartbeat task.
     * It checks if the node has a leader token and if the session registry is
     * healthy.
     * If not, it chooses a new registry and saves the session code.
     */
    @Override
    public void run() {
        // Possible perf impact since leader token is volatile
        if (!node.hasLeaderToken()) {
            return;
        }

        boolean isHealthy = SessionRegistry.checkHealth();
        if (isHealthy) {
            return;
        }

        boolean updated = SessionRegistry.chooseRegistry();
        if (updated) {
            // TODO: Invalidate old session
            String sessionCode = SessionRegistry.saveSession(node.getLeaderAddress(), SessionRegistry._options);
            System.out.println("Session code updated! Share this code: " + sessionCode);
        }
    }
}
