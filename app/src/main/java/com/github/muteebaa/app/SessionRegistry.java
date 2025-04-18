package com.github.muteebaa.app;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * This class is responsible for managing the session registry.
 */
public class SessionRegistry {
    public static PeerNode peerNode;
    // FIXME: Hack to persist options list
    public static String _options;

    private static HttpClient client = HttpClient.newHttpClient();
    private static final List<String> registryServers = List.of(
            // FIXME: Set actual ngrok addresses
            "https://9072-2001-56a-6fda-66eb-30d8-b669-d80-f77.ngrok-free.app",
            "https://1678-2001-56a-7722-2000-8000-8c2c-6e07-999a.ngrok-free.app",
            "https://1678-2001-56a-7722-2000-8000-8c2c-6e07-999a.ngrok-free.app");

    private static String currRegistry = registryServers.get(0);

    /**
     * Saves the session to the registry.
     * 
     * @param host
     * @param port
     * @param options
     * @param status
     * @return
     */
    public static String saveSession(String host, int port, String options, String status) {
        // FIXME: Handle port number properly
        _options = options;
        Session session = new Session(host, port, Arrays.asList(options.split(",")), status);
        Gson gson = new Gson();

        HttpRequest req = buildRegistryReq("/sessions")
                .POST(BodyPublishers.ofString(gson.toJson(session)))
                .build();

        String sessionId;
        try {
            // TODO: Handle failing status codes
            HttpResponse<String> resp = sendWithRetry(req, BodyHandlers.ofString());
            sessionId = resp.body();
        } catch (InterruptedException | IOException e) {
            // FIXME: Ignored exception
            e.printStackTrace();
            return "";
        }

        return gson.fromJson(sessionId, String.class);
    }

    /**
     * Saves the session to the registry.
     * 
     * @param address The address of the leader of the session in the format
     *                "host:port".
     * @param options The voting options for the session, separated by commas.
     * @return
     */
    public static String saveSession(String address, String options) {
        String host = address.split(":")[0];
        // IP must be resolved client-side since server could be contacting different
        // DNS server
        InetAddress hostIp;

        try {
            hostIp = InetAddress.getByName(host);
        } catch (UnknownHostException ignored) {
            // Should be unreachable, unable to confirm
            throw new RuntimeException("FIXME: I have made an incorrect assumption");
        }

        int port = Integer.parseInt(address.split(":")[1]);
        return saveSession(hostIp.getHostAddress(), port, options, "");
    }

    /**
     * Loads the sessions from the registry.
     * 
     * @return A map of session IDs to their details.
     */
    public static Map<String, String> loadSessions() {
        Map<String, String> sessions = new HashMap<>();

        HttpRequest req = buildRegistryReq("/sessions").build();
        HttpResponse<String> resp;
        try {
            // TODO: Handle failing status codes
            resp = sendWithRetry(req, BodyHandlers.ofString());
        } catch (InterruptedException | IOException e) {
            // FIXME: Ignored exception
            e.printStackTrace();
            return sessions;
        }

        Gson gson = new Gson();
        TypeToken<Collection<Session>> collectionType = new TypeToken<Collection<Session>>() {
        };
        Collection<Session> sessionList = gson.fromJson(resp.body(), collectionType);

        sessionList.parallelStream()
                .forEach(e -> {
                    String details = String.format("%s:%d,%s,%s", e.host, e.port, String.join(",", e.options),
                            e.status);
                    sessions.put(e.getId(), details);
                });

        return sessions;
    }

    /**
     * Displays the available sessions in the given panel.
     * 
     * @param sessionListPanel The panel to display the sessions in.
     */
    public static void displayAvailableSessions(JPanel sessionListPanel) {
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                HttpRequest req = buildRegistryReq("/sessions").build();
                HttpResponse<String> resp = sendWithRetry(req, BodyHandlers.ofString());

                Gson gson = new Gson();
                Collection<Session> sessions = gson.fromJson(resp.body(),
                        new TypeToken<Collection<Session>>() {
                        }.getType());

                SwingUtilities.invokeLater(() -> {
                    sessionListPanel.removeAll();

                    if (sessions.isEmpty()) {
                        JLabel noSessionsLabel = new JLabel("No sessions available");
                        noSessionsLabel.setFont(new Font("Arial", Font.PLAIN, 16));
                        sessionListPanel.add(noSessionsLabel);
                    } else {
                        for (Session s : sessions) {
                            String raw = s.toString();
                            String code = raw.substring(raw.indexOf('[') + 1, raw.indexOf('@')).trim();
                            String ip = raw.substring(raw.indexOf('@') + 1, raw.indexOf(']')).trim();
                            String options = raw.substring(raw.indexOf("Options:") + 8, raw.indexOf("],") + 1).trim();
                            String status = raw.substring(raw.lastIndexOf("Status:") + 7).trim();

                            JPanel card = new JPanel();
                            card.setLayout(new GridLayout(0, 1));
                            card.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
                            card.setBackground(new Color(245, 245, 245));
                            card.setPreferredSize(new Dimension(400, 100));
                            card.setMaximumSize(new Dimension(500, 120));

                            card.add(new JLabel("Status: " + status));
                            card.add(new JLabel("Code: " + code));
                            card.add(new JLabel("Leader IP: " + ip));
                            card.add(new JLabel("Voting Options: " + options));

                            sessionListPanel.add(card);
                            sessionListPanel.add(Box.createVerticalStrut(10));
                        }
                    }

                    sessionListPanel.revalidate();
                    sessionListPanel.repaint();
                });

                return null;
            }
        }.execute();
    }

    /**
     * Gets the voting options for a session.
     * 
     * @param sessionCode The session code to get the voting options for.
     * @return A list of voting options for the session.
     */
    public static List<String> getVotingOptions(String sessionCode) {
        HttpRequest req = buildRegistryReq("/sessions/" + sessionCode).build();
        HttpResponse<String> resp;
        try {
            // TODO: Handle failing status codes
            resp = sendWithRetry(req, BodyHandlers.ofString());
        } catch (InterruptedException | IOException e) {
            // FIXME: Ignored exception
            e.printStackTrace();
            return new ArrayList<>();
        }

        Gson gson = new Gson();
        Session session = gson.fromJson(resp.body(), Session.class);
        return session.options;
    }

    /**
     * Gets the status of a session.
     * 
     * @param sessionCode The session code to get the status for.
     * @return The status of the session.
     */
    public static String getSessionStatus(String sessionCode) {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest req = buildRegistryReq("/sessions/" + sessionCode).build();

        HttpResponse<String> resp;
        try {
            // TODO: Handle failing status codes
            resp = client.send(req, BodyHandlers.ofString());
        } catch (InterruptedException e) {
            // FIXME: Ignored exception
            e.printStackTrace();
            return null;
        } catch (IOException e) {
            // FIXME: Ignored exception
            e.printStackTrace();
            return null;
        }

        Gson gson = new Gson();
        Session session = gson.fromJson(resp.body(), Session.class);
        return session.status;
    }

    /**
     * Updates the session with the given session code.
     * 
     * @param sessionCode
     * @param newStatus
     * @param newHost
     * @param newPort
     * @return true if the update was successful, false otherwise
     */
    public static boolean updateSession(String sessionCode, String newStatus, String newHost, Integer newPort) {
        HttpClient client = HttpClient.newHttpClient();
        Gson gson = new Gson();

        // Create JSON payload for updating the session
        Map<String, Object> updateData = new HashMap<>();
        if (newStatus != null)
            updateData.put("status", newStatus);
        if (newHost != null)
            updateData.put("host", newHost);
        if (newPort != null)
            updateData.put("port", newPort);

        if (updateData.isEmpty()) {
            System.out.println("No updates provided.");
            return false;
        }

        String jsonPayload = gson.toJson(updateData);

        HttpRequest req = buildRegistryReq("/sessions/" + sessionCode)
                .method("PATCH", BodyPublishers.ofString(jsonPayload))
                .header("Content-Type", "application/json")
                .build();

        HttpResponse<String> resp;
        try {
            resp = client.send(req, BodyHandlers.ofString());

            return resp.statusCode() == 200; // Assuming 200 means success
        } catch (InterruptedException | IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Deletes the session with the given session code.
     * 
     * @param sessionCode
     * @return true if the deletion was successful, false otherwise
     */
    private static <T> HttpResponse<T> sendWithRetry(HttpRequest req, BodyHandler<T> handler)
            throws InterruptedException, IOException {
        int maxRetries = 3;
        for (int i = 0; i < maxRetries; i++) {
            try {
                return client.send(req, handler);
            } catch (InterruptedException | IOException ignored) {
                // Ignored since registry should be swapped if server keeps 5xx-ing anyways
            }
        }

        chooseRegistry();
        try {
            URI newUri = replaceHost(req.uri(), currRegistry);
            // Creates new req w/ only URI changed
            HttpRequest newReq = HttpRequest.newBuilder(req, (e1, e2) -> true)
                    .uri(newUri)
                    .build();

            return sendWithRetry(newReq, handler);
        } catch (URISyntaxException e) {
            // NOTE: Unreachable unless the hardcoded URLs are wrong
            e.printStackTrace();
        }

        // Unreachable
        return null;
    }

    /**
     * Replaces the host in the given URI with the new host.
     * 
     * @param uri
     * @param host
     * @return
     * @throws URISyntaxException
     */
    private static URI replaceHost(URI uri, String host) throws URISyntaxException {
        // NOTE: Building a String rather than using URI constructors because hosts are
        // hardcoded as scheme + authority. If that changes this can be made into a
        // one-liner
        String newUri = host + uri.getPath();

        if (uri.getQuery() != null) {
            newUri += uri.getQuery();
        }

        if (uri.getFragment() != null) {
            newUri += uri.getFragment();
        }

        return URI.create(newUri);
    }

    /**
     * Checks the health of the current registry server.
     * 
     * @return true if the server is healthy, false otherwise.
     */
    public static boolean checkHealth() {
        HttpRequest req = buildRegistryReq(currRegistry, "/ping").build();

        try {
            HttpResponse<Void> resp = client.send(req, HttpResponse.BodyHandlers.discarding());
            if (resp.statusCode() != 200) {
                return false;
            }
        } catch (InterruptedException | IOException e) {
            return false;
        }

        return true;
    }

    /**
     * Chooses a new registry server if the current one is unhealthy.
     * 
     * @return true if a new server was chosen, false otherwise.
     */
    public static boolean chooseRegistry() {
        CompletableFuture<String> server = registryServers.stream()
                // Asynchronously ping each server
                .map(url -> {
                    HttpRequest req = buildRegistryReq(url, "/ping").build();
                    return client.sendAsync(req, HttpResponse.BodyHandlers.discarding())
                            .thenApply(resp -> resp.statusCode() == 200 ? url : null)
                            .exceptionally(e -> null);
                })
                // Chooses a single server based off the url's position in the list
                .reduce((r1, r2) -> r1.thenCombine(r2, (s1, s2) -> s1 != null ? s1 : s2))
                .orElse(CompletableFuture.completedFuture(null));

        try {
            String chosenServer = server.get();
            if (chosenServer != null && !currRegistry.equals(chosenServer)) {
                currRegistry = chosenServer;
                return true;
            }
        } catch (InterruptedException | ExecutionException e) {
            // FIXME: Ignored exception
            e.printStackTrace();
        }

        return false;
    }

    /**
     * Builds a request to the registry server.
     * 
     * @param path
     * @return
     */
    private static Builder buildRegistryReq(String path) {
        return buildRegistryReq(currRegistry, path);
    }

    /**
     * Builds a request to the registry server.
     * 
     * @param hostname
     * @param path
     * @return
     */
    private static Builder buildRegistryReq(String hostname, String path) {
        URI uri = URI.create(hostname + path);
        Duration timeout = Duration.ofSeconds(5);

        return HttpRequest.newBuilder(uri)
                .header("Content-Type", "application/json")
                .timeout(timeout);
    }
}
