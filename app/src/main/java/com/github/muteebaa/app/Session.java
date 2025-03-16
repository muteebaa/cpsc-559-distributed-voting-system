package com.github.muteebaa.app;

import java.util.Collections;
import java.util.List;

public class Session {
    // TODO: Add validation logic similar to go package, must check for `^[A-Z0-9]{6}$`
    public final String id;
    public final String host;
    private String ip;
    public final List<String> options;

    public Session(String id, String host, String ip, List<String> options) {
        this.id = id;
        this.host = host;
        this.ip = ip;
        this.options = Collections.unmodifiableList(options);
    }

    public Session(String host, String ip, List<String> options) {
        // TODO: Actual randomization
        this("AAAAAA", host, ip, options);
    }

    public String getIp() {
        return ip;
    }

    // TODO: Add validation logic
    public void setIp(String id) {
        this.ip = id;
    }
}
