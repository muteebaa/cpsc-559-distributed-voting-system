package com.github.muteebaa.app;

import java.util.Collections;
import java.util.List;

/**
 * Represents a voting session.
 */
public class Session {
    private String id;
    public final String host;
    public final int port;
    public final List<String> options;
    public String status; // the status of the voting session: waiting, started, ended

    /**
     * Constructor for Session.
     * 
     * @param id      The session ID.
     * @param host    The leader host address.
     * @param port    The leader port number.
     * @param options The list of options for the voting session.
     * @param status  The status of the voting session.
     */
    protected Session(String id, String host, int port, List<String> options, String status) {
        this.id = id;
        this.host = host;
        this.port = port;
        this.options = Collections.unmodifiableList(options);
        this.status = status;
    }

    /**
     * Constructor for Session without ID.
     * 
     * @param host
     * @param port
     * @param options
     * @param status
     */
    public Session(String host, int port, List<String> options, String status) {
        this(null, host, port, options, status);
    }

    /**
     * Get the session ID.
     * 
     * @return The session ID.
     */
    public String getId() {
        return id;
    }

    /*
     * Set the session ID.
     * 
     * @param id The session ID.
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Returns a string representation of the session.
     */
    @Override
    public String toString() {
        return String.format("[%s @ %s:%d] Options: %s, Status: %s", id, host, port, options, status);
    }
}
