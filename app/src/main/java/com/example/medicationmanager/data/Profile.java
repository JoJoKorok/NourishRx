package com.example.medicationmanager.data;

public class Profile {
    public long id;
    public String name;
    public long createdAt;

    public Profile(long id, String name, long createdAt) {
        this.id = id;
        this.name = clean(name);
        this.createdAt = createdAt > 0 ? createdAt : System.currentTimeMillis();
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
