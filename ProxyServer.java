import java.io.*;
import java.net.*;
import java.util.*;
import java.util.function.Consumer;




public class ProxyServer {
    private static final int PORT = 8080;
    private static final List<DatabaseMimic> replicas = new ArrayList<>(List.of(new DatabaseMimic(), new DatabaseMimic(), new DatabaseMimic()));
    private Consumer<String> messageHandler;

    public void startServer(Consumer<String> handler) {
        this.messageHandler = handler;
        try (ServerSocket serverSocket = new ServerSocket(PORT)){
            System.out.println("Proxy Server is running on port " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(new ClientHandler(clientSocket)).start();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static Session getSession(String sessionCode){
        for (DatabaseMimic databaseMimic : replicas) {
            try {
                return databaseMimic.getSession(sessionCode);
            } catch (NoSuchElementException e){
                continue;
            }
            catch (Exception e) {
                e.printStackTrace();    
            }
        }
        throw new NoSuchElementException();
    }

    private static class ClientHandler implements Runnable {
        private Socket clientSocket;

        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }
        
        @Override
        public void run(){
            try {
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter out = new PrintWriter(this.clientSocket.getOutputStream(), true);
                String message = in.readLine();
                if (message.startsWith("GETLEADERIP:")) {
                    String sessionCode = message.substring(9,message.length()-1);
                    try{
                        ProxyServer.getSession(sessionCode);
                    }
                    catch (NoSuchElementException e){
                        out.println("GETLEADERIP:SESSIONCODE_INVALID");
                        out.close();
                        in.close();
                        return;
                    }
                    
                }
                in.close();
                out.close();
            }
            catch (Exception e) {
                e.printStackTrace();
            }
            
        }
        
    }

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
}
