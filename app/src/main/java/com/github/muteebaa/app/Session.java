package com.github.muteebaa.app;

import java.util.Collections;
import java.util.List;

public class Session {
    private String id;
    public final String host;
    public final int port;
    public final List<String> options;

    protected Session(String id, String host, int port, List<String> options) {
        this.id = id;
        this.host = host;
        this.port = port;
        this.options = Collections.unmodifiableList(options);
    }

    public Session(String host, int port, List<String> options) {
        this(null, host, port, options);
    }

    public String getId() {
        return id;
    }

    /*
     * TODO: Add validation logic checking that:
     *   1. id is null
     *   2. id is 6-length upper-alphanumeric string
     * Probably return a runtime exception if either of above are false
     */
    public void setId(String id) {
        this.id = id;
    }
}
