import java.util.HashSet;
import java.util.NoSuchElementException;
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

    public Session getSession(String sessionCode){
        for (Session session : sessions) {
            if (session.getSessionCode().equals(sessionCode)) {
                return session;
            }
        }
        throw new NoSuchElementException("Element doesn't exist");
    }
}
