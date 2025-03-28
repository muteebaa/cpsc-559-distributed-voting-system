package com.github.muteebaa.app;

import java.util.Collections;
import java.util.List;

public class Session {
    private String id;
    public final String host;
    public final int port;
    public final List<String> options;
    public String status; // the status of the voting session: waiting, started, ended

    protected Session(String id, String host, int port, List<String> options, String status) {
        this.id = id;
        this.host = host;
        this.port = port;
        this.options = Collections.unmodifiableList(options);
        this.status = status;
    }

    public Session(String host, int port, List<String> options, String status) {
        this(null, host, port, options, status);
    }

    public String getId() {
        return id;
    }

    /*
     * TODO: Add validation logic checking that:
     * 1. id is null
     * 2. id is 6-length upper-alphanumeric string
     * Probably return a runtime exception if either of above are false
     */
    public void setId(String id) {
        this.id = id;
    }

    @Override
    public String toString() {
        return String.format("[%s @ %s:%d] Options: %s, Status: %s", id, host, port, options, status);
    }
}
