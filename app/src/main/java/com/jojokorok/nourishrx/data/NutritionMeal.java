package com.jojokorok.nourishrx.data;

public class NutritionMeal {
    public long id;
    public long profileId;
    public String name;
    public int calories;
    public float proteinGrams;
    public float carbsGrams;
    public float fatGrams;
    public long loggedAt;

    public NutritionMeal(
            long id,
            long profileId,
            String name,
            int calories,
            float proteinGrams,
            float carbsGrams,
            float fatGrams,
            long loggedAt
    ) {
        this.id = id;
        this.profileId = profileId > 0 ? profileId : 1;
        this.name = clean(name);
        this.calories = Math.max(0, calories);
        this.proteinGrams = Math.max(0.0f, proteinGrams);
        this.carbsGrams = Math.max(0.0f, carbsGrams);
        this.fatGrams = Math.max(0.0f, fatGrams);
        this.loggedAt = loggedAt > 0 ? loggedAt : System.currentTimeMillis();
    }

    public static NutritionMeal empty(long profileId) {
        return new NutritionMeal(0, profileId, "", 0, 0.0f, 0.0f, 0.0f, System.currentTimeMillis());
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
