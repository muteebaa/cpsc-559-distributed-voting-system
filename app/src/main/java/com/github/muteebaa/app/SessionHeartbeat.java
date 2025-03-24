package com.github.muteebaa.app;

public class SessionHeartbeat implements Runnable {
    private final PeerNode node;

    public SessionHeartbeat(PeerNode node) {
        this.node = node;
    }

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
