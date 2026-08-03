package com.jojokorok.nourishrx.data;

public class SavedMeal {
    public long id;
    public long profileId;
    public String name;
    public String notes;
    public long createdAt;

    public SavedMeal(
            long id,
            long profileId,
            String name,
            String notes,
            long createdAt
    ) {
        this.id = id;
        this.profileId = profileId > 0 ? profileId : 1;
        this.name = clean(name).isEmpty() ? "Saved meal" : clean(name);
        this.notes = clean(notes);
        this.createdAt = createdAt > 0 ? createdAt : System.currentTimeMillis();
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
