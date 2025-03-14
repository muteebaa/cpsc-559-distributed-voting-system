import java.io.*;
import java.net.*;
import java.util.*;




public class ProxyServer {
    private static final int PORT = 8080;
    private static final List<DatabaseMimic> replicas = new ArrayList<>();

    public static void main(String[] args) {
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

    private static class ClientHandler implements Runnable {
        private Socket clientSocket;

        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }
        
        @Override
        public void run(){
            try {
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

                
                in.close();
            }
            catch (Exception e) {
                e.printStackTrace();
            }
            
        }
        
    }
}
