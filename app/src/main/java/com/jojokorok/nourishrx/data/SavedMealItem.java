package com.jojokorok.nourishrx.data;

public class SavedMealItem {
    public long id;
    public long savedMealId;
    public long foodId;
    public float servings;
    public int sortOrder;
    public NutritionFood food;

    public SavedMealItem(
            long id,
            long savedMealId,
            long foodId,
            float servings,
            int sortOrder,
            NutritionFood food
    ) {
        this.id = id;
        this.savedMealId = savedMealId;
        this.foodId = foodId;
        this.servings = servings > 0.0f && !Float.isNaN(servings) && !Float.isInfinite(servings)
                ? servings
                : 1.0f;
        this.sortOrder = Math.max(0, sortOrder);
        this.food = food;
    }

    public int calories() {
        return Math.round((food == null ? 0 : food.calories) * servings);
    }
}
