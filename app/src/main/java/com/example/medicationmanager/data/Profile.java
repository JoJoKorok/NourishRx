package com.example.medicationmanager.data;

public class Profile {
    public long id;
    public String name;
    public String avatarUri;
    public float avatarZoom;
    public float avatarOffsetX;
    public float avatarOffsetY;
    public float avatarAspectRatio;
    public long createdAt;

    public Profile(
            long id,
            String name,
            String avatarUri,
            float avatarZoom,
            float avatarOffsetX,
            float avatarOffsetY,
            float avatarAspectRatio,
            long createdAt
    ) {
        this.id = id;
        this.name = clean(name);
        this.avatarUri = clean(avatarUri);
        this.avatarZoom = clamp(avatarZoom, 1.0f, 3.0f, 1.0f);
        this.avatarOffsetX = clamp(avatarOffsetX, -1.0f, 1.0f, 0.0f);
        this.avatarOffsetY = clamp(avatarOffsetY, -1.0f, 1.0f, 0.0f);
        this.avatarAspectRatio = clamp(avatarAspectRatio, 0.75f, 1.65f, 1.0f);
        this.createdAt = createdAt > 0 ? createdAt : System.currentTimeMillis();
    }

    public boolean hasAvatar() {
        return !avatarUri.isEmpty();
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static float clamp(float value, float min, float max, float fallback) {
        if (Float.isNaN(value) || Float.isInfinite(value) || value <= 0 && min > 0) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }
}
