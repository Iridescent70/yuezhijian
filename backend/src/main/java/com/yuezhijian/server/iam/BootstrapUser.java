package com.yuezhijian.server.iam;

public class BootstrapUser {
    private Long id;
    private final String username;
    private final String passwordHash;
    private final String fullName;

    public BootstrapUser(String username, String passwordHash, String fullName) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFullName() {
        return fullName;
    }
}
