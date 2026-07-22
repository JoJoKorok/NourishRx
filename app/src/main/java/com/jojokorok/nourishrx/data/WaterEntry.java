package com.jojokorok.nourishrx.data;

public class WaterEntry {
    public long id;
    public long profileId;
    public int ounces;
    public long loggedAt;

    public WaterEntry(long id, long profileId, int ounces, long loggedAt) {
        this.id = id;
        this.profileId = profileId > 0 ? profileId : 1;
        this.ounces = Math.max(0, ounces);
        this.loggedAt = loggedAt > 0 ? loggedAt : System.currentTimeMillis();
    }
}
