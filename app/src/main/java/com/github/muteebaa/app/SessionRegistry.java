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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SessionRegistry {
    /**
     * Saves a session code to a file.
     */
    public static void saveSession(String sessionCode, String host, int port, String options) {
        // FIXME: Handle port number properly
        Session session = new Session(host, Integer.toString(port), Arrays.asList(options.split(",")));
        Gson gson = new Gson();

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest req = buildRegistryReq("/sessions")
                .POST(BodyPublishers.ofString(gson.toJson(session)))
                .build();

        HttpResponse<String> resp;
        try {
            // TODO: Handle failing status codes
            resp = client.send(req, BodyHandlers.ofString());
        } catch (InterruptedException e) {
            // FIXME: Ignored exception
            return;
        } catch (IOException e) {
            // FIXME: Ignored exception
            return;
        }
    }

    /**
     * Loads all session codes from the file.
     */
    public static Map<String, String> loadSessions() {
        Map<String, String> sessions = new HashMap<>();

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest req = buildRegistryReq("/sessions").build();

        HttpResponse<String> resp;
        try {
            // TODO: Handle failing status codes
            resp = client.send(req, BodyHandlers.ofString());
        } catch (InterruptedException e) {
            // FIXME: Ignored exception
            return sessions;
        } catch (IOException e) {
            // FIXME: Ignored exception
            return sessions;
        }

        Gson gson = new Gson();
        TypeToken<Collection<Session>> collectionType = new TypeToken<Collection<Session>>() {
        };
        Collection<Session> sessionList = gson.fromJson(resp.body(), collectionType);

        sessionList.parallelStream()
                .forEach(e -> {
                    String details = String.format("%s:%s,%s", e.host, e.getIp(), String.join(",", e.options));
                    sessions.put(e.id, details);
                });

        return sessions;
    }

    /**
     * Gets available session codes from the file.
     */
    public static void displayAvailableSessions() {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest req = buildRegistryReq("/sessions/all").build();

        HttpResponse<String> resp;
        try {
            // TODO: Handle failing status codes
            resp = client.send(req, BodyHandlers.ofString());
        } catch (InterruptedException e) {
            // FIXME: Ignored exception
            return;
        } catch (IOException e) {
            // FIXME: Ignored exception
            return;
        }

        Gson gson = new Gson();
        TypeToken<Collection<String>> collectionType = new TypeToken<Collection<String>>() {
        };
        Collection<String> sessionList = gson.fromJson(resp.body(), collectionType);

        if (sessionList.isEmpty()) {
            System.out.println("No available sessions found.");
        } else {
            System.out.println("Available sessions: ");
            sessionList.forEach(System.out::println);
        }
    }

    public static List<String> getVotingOptions(String sessionCode) {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest req = buildRegistryReq("/sessions" + sessionCode).build();

        HttpResponse<String> resp;
        try {
            // TODO: Handle failing status codes
            resp = client.send(req, BodyHandlers.ofString());
        } catch (InterruptedException e) {
            // FIXME: Ignored exception
            return new ArrayList<>();
        } catch (IOException e) {
            // FIXME: Ignored exception
            return new ArrayList<>();
        }

        Gson gson = new Gson();
        Session session = gson.fromJson(resp.body(), Session.class);
        return session.options;
    }

    private static Builder buildRegistryReq(String path) {
        String registryAddr = "https://127.0.0.1:12020";
        URI uri = URI.create(registryAddr + path);
        return HttpRequest.newBuilder(uri)
                .header("Content-Type", "application/json");
    }
}
