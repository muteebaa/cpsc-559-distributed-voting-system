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
    public static String saveSession(String host, int port, String options, String status) {
        // FIXME: Handle port number properly
        Session session = new Session(host, port, Arrays.asList(options.split(",")), status);
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
            e.printStackTrace();
            return "";
        } catch (IOException e) {
            // FIXME: Ignored exception
            e.printStackTrace();
            return "";
        }

        return gson.fromJson(resp.body(), String.class);
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
                    String details = String.format("%s:%d,%s,%s", e.host, e.port, String.join(",", e.options),
                            e.status);
                    sessions.put(e.getId(), details);
                });

        return sessions;
    }

    /**
     * Gets available session codes from the file.
     */
    public static void displayAvailableSessions() {
        HttpClient client = HttpClient.newHttpClient();
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
        HttpClient client = HttpClient.newHttpClient();
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

    private static Builder buildRegistryReq(String path) {
        String registryAddr = "https://8df7-136-159-213-26.ngrok-free.app";
        URI uri = URI.create(registryAddr + path);
        return HttpRequest.newBuilder(uri)
                .header("Content-Type", "application/json");
    }
}
