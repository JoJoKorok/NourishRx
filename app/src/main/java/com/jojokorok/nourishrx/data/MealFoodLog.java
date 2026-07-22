package com.jojokorok.nourishrx.data;

public class MealFoodLog {
    public long id;
    public long profileId;
    public long foodId;
    public String mealName;
    public float servings;
    public long eatenAt;
    public NutritionFood food;

    public MealFoodLog(
            long id,
            long profileId,
            long foodId,
            String mealName,
            float servings,
            long eatenAt,
            NutritionFood food
    ) {
        this.id = id;
        this.profileId = profileId > 0 ? profileId : 1;
        this.foodId = foodId;
        this.mealName = clean(mealName).isEmpty() ? "Meal" : clean(mealName);
        this.servings = servings > 0.0f && !Float.isNaN(servings) && !Float.isInfinite(servings)
                ? servings
                : 1.0f;
        this.eatenAt = eatenAt > 0 ? eatenAt : System.currentTimeMillis();
        this.food = food;
    }

    public int calories() {
        return Math.round((food == null ? 0 : food.calories) * servings);
    }

    public float proteinGrams() {
        return scaled(food == null ? 0.0f : food.proteinGrams);
    }

    public float totalCarbsGrams() {
        return scaled(food == null ? 0.0f : food.totalCarbsGrams);
    }

    public float totalFatGrams() {
        return scaled(food == null ? 0.0f : food.totalFatGrams);
    }

    public float sodiumMg() {
        return scaled(food == null ? 0.0f : food.sodiumMg);
    }

    public float totalSugarsGrams() {
        return scaled(food == null ? 0.0f : food.totalSugarsGrams);
    }

    private float scaled(float valuePerServing) {
        return valuePerServing * servings;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
