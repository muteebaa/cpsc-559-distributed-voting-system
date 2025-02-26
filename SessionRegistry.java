
import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SessionRegistry {
    private static final String FILE_PATH = "sessions.txt";

    /**
     * Saves a session code to a file.
     */
    public static void saveSession(String sessionCode, String host, int port, String options) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(FILE_PATH, true))) {
            writer.write(sessionCode + "," + host + "," + port + "," + options);
            writer.newLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Loads all session codes from the file.
     */
    public static Map<String, String> loadSessions() {
        Map<String, String> sessions = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(FILE_PATH))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length >= 4) {
                    String sessionCode = parts[0];
                    String details = parts[1] + ":" + parts[2] + ","
                            + String.join(",", Arrays.copyOfRange(parts, 3, parts.length)); // Store as
                                                                                            // "IP:Port,Options"
                    sessions.put(sessionCode, details);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return sessions;
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

    public static List<String> getVotingOptions(String sessionCode) {
        Map<String, String> sessions = SessionRegistry.loadSessions();
        if (sessions.containsKey(sessionCode)) {
            String[] details = sessions.get(sessionCode).split(",", 2); // Split into ["IP:Port", "Options"]
            if (details.length < 2)
                return new ArrayList<>();
            return Arrays.asList(details[1].split(",")); // Split options
        }
        return new ArrayList<>();
    }
}
