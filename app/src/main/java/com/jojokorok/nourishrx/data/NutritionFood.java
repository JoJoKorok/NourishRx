package com.jojokorok.nourishrx.data;

public class NutritionFood {
    public long id;
    public long profileId;
    public String brand;
    public String name;
    public String servingSize;
    public float servingsPerContainer;
    public int calories;
    public float totalFatGrams;
    public float saturatedFatGrams;
    public float transFatGrams;
    public float cholesterolMg;
    public float sodiumMg;
    public float totalCarbsGrams;
    public float fiberGrams;
    public float totalSugarsGrams;
    public float addedSugarsGrams;
    public float proteinGrams;
    public float vitaminDMcg;
    public float calciumMg;
    public float ironMg;
    public float potassiumMg;
    public long createdAt;

    public NutritionFood(
            long id,
            long profileId,
            String brand,
            String name,
            String servingSize,
            float servingsPerContainer,
            int calories,
            float totalFatGrams,
            float saturatedFatGrams,
            float transFatGrams,
            float cholesterolMg,
            float sodiumMg,
            float totalCarbsGrams,
            float fiberGrams,
            float totalSugarsGrams,
            float addedSugarsGrams,
            float proteinGrams,
            float vitaminDMcg,
            float calciumMg,
            float ironMg,
            float potassiumMg,
            long createdAt
    ) {
        this.id = id;
        this.profileId = profileId > 0 ? profileId : 1;
        this.brand = clean(brand);
        this.name = clean(name);
        this.servingSize = clean(servingSize);
        this.servingsPerContainer = nonNegative(servingsPerContainer);
        this.calories = Math.max(0, calories);
        this.totalFatGrams = nonNegative(totalFatGrams);
        this.saturatedFatGrams = nonNegative(saturatedFatGrams);
        this.transFatGrams = nonNegative(transFatGrams);
        this.cholesterolMg = nonNegative(cholesterolMg);
        this.sodiumMg = nonNegative(sodiumMg);
        this.totalCarbsGrams = nonNegative(totalCarbsGrams);
        this.fiberGrams = nonNegative(fiberGrams);
        this.totalSugarsGrams = nonNegative(totalSugarsGrams);
        this.addedSugarsGrams = nonNegative(addedSugarsGrams);
        this.proteinGrams = nonNegative(proteinGrams);
        this.vitaminDMcg = nonNegative(vitaminDMcg);
        this.calciumMg = nonNegative(calciumMg);
        this.ironMg = nonNegative(ironMg);
        this.potassiumMg = nonNegative(potassiumMg);
        this.createdAt = createdAt > 0 ? createdAt : System.currentTimeMillis();
    }

    public String displayName() {
        if (brand.isEmpty()) {
            return name;
        }
        return brand + " - " + name;
    }

    public String servingSummary() {
        if (servingSize.isEmpty()) {
            return servingsPerContainer > 0.0f
                    ? format(servingsPerContainer) + " servings/container"
                    : "Serving not set";
        }
        if (servingsPerContainer <= 0.0f) {
            return servingSize;
        }
        return servingSize + ", " + format(servingsPerContainer) + " servings/container";
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static float nonNegative(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return 0.0f;
        }
        return Math.max(0.0f, value);
    }

    private static String format(float value) {
        if (Math.abs(value - Math.round(value)) < 0.05f) {
            return String.valueOf(Math.round(value));
        }
        return String.format(java.util.Locale.getDefault(), "%.1f", value);
    }
}
