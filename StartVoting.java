
// import java.util.Arrays;
import java.util.Map;
import java.util.Scanner;

public class StartVoting {
    private static final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {

        System.out.println("1. Start a new election\n2. Join an existing election\n3. View available sessions");
        System.out.print("Enter choice: ");
        int choice = scanner.nextInt();
        scanner.nextLine(); // Consume newline

        if (choice == 1) {
            System.out.print("Enter your node's port number: ");
            int myPort = scanner.nextInt();
            scanner.nextLine(); // Consume newline
            PeerNode peer = new PeerNode(myPort, 1);
            peer.startPeer();

            System.out.print("Enter comma-separated voting options: ");
            String options = scanner.nextLine();

            // Generate session code and store it
            String sessionCode = peer.startNewSession("192.168.1.100", myPort, options); // Replace with actual IP
            System.out.println("Session created! Share this code: " + sessionCode);
            System.out.println("Voting options: " + options);

            // wait for leader to start voting
            while (true) {
                System.out.print("Enter 'start' to begin voting: ");
                String input = scanner.nextLine().trim().toLowerCase();

                if (input.equals("start")) {
                    System.out.println("Voting started!");
                    peer.startVoting();

                    peer.promptForVote();// prompt leader to vote
                    break; // Exit loop and proceed with voting logic
                } else {
                    System.out.println("Invalid input. Type 'start' to begin.");
                }
            }

            // wait for leader to end voting
            while (true) {
                System.out.print("Enter 'end' to stop voting: ");
                String input = scanner.nextLine().trim().toLowerCase();

                if (input.equals("end")) {
                    peer.endVoting();
                    break; // Exit loop and proceed with voting logic
                } else {
                    System.out.println("Invalid input. Type 'end' to end voting.");
                }
            }

        } else if (choice == 2) {
            System.out.print("Enter session code: ");
            String sessionCode = scanner.nextLine();

            Map<String, String> sessions = SessionRegistry.loadSessions();

            if (sessions.containsKey(sessionCode)) {
                // String[] details = sessions.get(sessionCode).split(",");
                // System.out.println("Details: " + Arrays.toString(details));
                // String[] hostPort = details[0].split(":");
                // String ip = hostPort[0];
                // int port = Integer.parseInt(hostPort[1]);

                // print hostPort
                // System.out.println("host: " + ip + " port: " + port);
                // String hostAddr = ip + ":" + port;

                System.out.print("Enter your node's port number: ");
                int myPort = scanner.nextInt();
                // scanner.nextLine(); // Consume newline

                // String options = details[1] + "," + details[2] + "," + details[3];

                // PeerNode peer = new PeerNode(port, 1);
                PeerNode peer = new PeerNode(myPort, 1);
                peer.setSessionCode(sessionCode);
                // peer.startPeer();
                peer.startPeer();

                // peer.registerWithLeader(hostAddr);
                // System.out.println("Connected to leader at " + ip + ":" + port);
                // System.out.println("Voting options: " + options);
                System.out.println("Waiting for leader to start voting...");
            } else {
                System.out.println("Invalid session code!");
            }
        } else if (choice == 3) {
            SessionRegistry.displayAvailableSessions();
        }

    }

}