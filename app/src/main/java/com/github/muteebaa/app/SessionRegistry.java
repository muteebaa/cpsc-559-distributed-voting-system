package com.github.muteebaa.app;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

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

public class SessionRegistry {
    public static PeerNode peerNode;
    // FIXME: Hack to persist options list
    public static String _options;

    private static HttpClient client = HttpClient.newHttpClient();
    private static final List<String> registryServers = List.of(
            // FIXME: Set actual ngrok addresses
            "https://8df7-136-159-213-26.ngrok-free.app",
            "https://8df7-136-159-213-26.ngrok-free.app",
            "https://8df7-136-159-213-26.ngrok-free.app"
            );

    private static String currRegistry = registryServers.get(0);

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

    public static void displayAvailableSessions() {
        HttpRequest req = buildRegistryReq("/sessions").build();
        HttpResponse<String> resp;
        try {
            // TODO: Handle failing status codes
            resp = sendWithRetry(req, BodyHandlers.ofString());
        } catch (InterruptedException | IOException e) {
            // FIXME: Ignored exception
            e.printStackTrace();
            return;
        }

        Gson gson = new Gson();
        TypeToken<Collection<Session>> collectionType = new TypeToken<Collection<Session>>() {
        };
        Collection<Session> sessionList = gson.fromJson(resp.body(), collectionType);

        if (sessionList.isEmpty()) {
            System.out.println("No available sessions found.");
        } else {
            System.out.println("Available sessions: ");
            sessionList.forEach(System.out::println);
        }
    }

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

    private static Builder buildRegistryReq(String path) {
        return buildRegistryReq(currRegistry, path);
    }

    private static Builder buildRegistryReq(String hostname, String path) {
        URI uri = URI.create(hostname + path);
        Duration timeout = Duration.ofSeconds(5);

        return HttpRequest.newBuilder(uri)
            .header("Content-Type", "application/json")
            .timeout(timeout);
    }
}
