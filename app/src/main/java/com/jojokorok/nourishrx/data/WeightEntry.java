package com.jojokorok.nourishrx.data;

public class WeightEntry {
    public long id;
    public long profileId;
    public float pounds;
    public long loggedAt;

    public WeightEntry(long id, long profileId, float pounds, long loggedAt) {
        this.id = id;
        this.profileId = profileId > 0 ? profileId : 1;
        this.pounds = Math.max(0.0f, pounds);
        this.loggedAt = loggedAt > 0 ? loggedAt : System.currentTimeMillis();
    }
}
