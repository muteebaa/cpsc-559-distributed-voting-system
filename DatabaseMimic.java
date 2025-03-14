import java.util.HashSet;
import java.util.Set;


public class DatabaseMimic {
    private Set<Session> sessions = new HashSet<>();

    public boolean addSession(Session session){
        return sessions.add(session);
    }

    public boolean removeSession(String sessionCode){
        for (Session session : sessions) {
            if (session.getSessionCode().equals(sessionCode)) {
                return sessions.remove(session);
            }
        }
        return false;
    }
}
