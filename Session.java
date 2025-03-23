
/**
 * The session class is a data storage class for the proxy server. 
 */
public class Session {
    private final String sessionCode;
    private String leaderIP;

    public Session(String sessionCode, String leaderIP) {
        this.sessionCode = sessionCode; 
        this.leaderIP = leaderIP;
    }
    
    public void updateLeaderIP(String newLeaderIP){
        this.leaderIP = newLeaderIP;
    }

    public String getSessionCode(){
        return this.sessionCode;
    }

    @Override
    public String toString() {
        return "Session Code: " + sessionCode + ", Leader IP: " + leaderIP;
    }
}
