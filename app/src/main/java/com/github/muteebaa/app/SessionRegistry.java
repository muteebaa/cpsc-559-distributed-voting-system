package com.github.muteebaa.app;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;
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
    private static HttpClient client = HttpClient.newHttpClient();
    private static final List<String> registryServers = List.of(
            "http://127.0.0.1:12020",
            "http://127.0.0.1:12021");

    private static String currRegistry = registryServers.get(0);

    public static String saveSession(String host, int port, String options) {
        // FIXME: Handle port number properly
        Session session = new Session(host, port, Arrays.asList(options.split(",")));
        Gson gson = new Gson();

        HttpRequest req = buildRegistryReq("/sessions")
                .POST(BodyPublishers.ofString(gson.toJson(session)))
                .build();

        HttpResponse<String> resp;
        try {
            // TODO: Handle failing status codes
            resp = client.send(req, BodyHandlers.ofString());
        } catch (InterruptedException e) {
            // FIXME: Ignored exception
            e.printStackTrace();
            return "";
        } catch (IOException e) {
            // FIXME: Ignored exception
            e.printStackTrace();
            return "";
        }

        return gson.fromJson(resp.body(), String.class);
    }

    public static Map<String, String> loadSessions() {
        Map<String, String> sessions = new HashMap<>();

        HttpRequest req = buildRegistryReq("/sessions").build();
        HttpResponse<String> resp;
        try {
            // TODO: Handle failing status codes
            resp = client.send(req, BodyHandlers.ofString());
        } catch (InterruptedException e) {
            // FIXME: Ignored exception
            e.printStackTrace();
            return sessions;
        } catch (IOException e) {
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
                    String details = String.format("%s:%d,%s", e.host, e.port, String.join(",", e.options));
                    sessions.put(e.getId(), details);
                });

        return sessions;
    }

    public static void displayAvailableSessions() {
        HttpRequest req = buildRegistryReq("/sessions").build();
        HttpResponse<String> resp;
        try {
            // TODO: Handle failing status codes
            resp = client.send(req, BodyHandlers.ofString());
        } catch (InterruptedException e) {
            // FIXME: Ignored exception
            e.printStackTrace();
            return;
        } catch (IOException e) {
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
            resp = client.send(req, BodyHandlers.ofString());
        } catch (InterruptedException e) {
            // FIXME: Ignored exception
            e.printStackTrace();
            return new ArrayList<>();
        } catch (IOException e) {
            // FIXME: Ignored exception
            e.printStackTrace();
            return new ArrayList<>();
        }

        Gson gson = new Gson();
        Session session = gson.fromJson(resp.body(), Session.class);
        return session.options;
    }

    private static void chooseRegistry() {
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
            if (chosenServer != null) {
                currRegistry = chosenServer;
            }
        } catch (InterruptedException | ExecutionException e) {
            // FIXME: Ignored exception
            e.printStackTrace();
        }
    }

    private static Builder buildRegistryReq(String path) {
        return buildRegistryReq(currRegistry, path).header("Content-Type", "application/json");
    }

    private static Builder buildRegistryReq(String hostname, String path) {
        URI uri = URI.create(hostname + path);
        Duration timeout = Duration.ofSeconds(5);

        return HttpRequest.newBuilder(uri).timeout(timeout);
    }
}
