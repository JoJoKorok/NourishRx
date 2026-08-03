package com.jojokorok.nourishrx.data;

import java.util.List;

public class NutritionTotals {
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

    public static NutritionTotals fromLogs(List<MealFoodLog> logs) {
        NutritionTotals totals = new NutritionTotals();
        if (logs == null) {
            return totals;
        }
        for (MealFoodLog log : logs) {
            if (log != null) {
                totals.addFood(log.food, log.servings);
            }
        }
        return totals;
    }

    public void add(NutritionTotals other) {
        if (other == null) {
            return;
        }
        calories += Math.max(0, other.calories);
        totalFatGrams += nonNegative(other.totalFatGrams);
        saturatedFatGrams += nonNegative(other.saturatedFatGrams);
        transFatGrams += nonNegative(other.transFatGrams);
        cholesterolMg += nonNegative(other.cholesterolMg);
        sodiumMg += nonNegative(other.sodiumMg);
        totalCarbsGrams += nonNegative(other.totalCarbsGrams);
        fiberGrams += nonNegative(other.fiberGrams);
        totalSugarsGrams += nonNegative(other.totalSugarsGrams);
        addedSugarsGrams += nonNegative(other.addedSugarsGrams);
        proteinGrams += nonNegative(other.proteinGrams);
        vitaminDMcg += nonNegative(other.vitaminDMcg);
        calciumMg += nonNegative(other.calciumMg);
        ironMg += nonNegative(other.ironMg);
        potassiumMg += nonNegative(other.potassiumMg);
    }

    public void addFood(NutritionFood food, float servings) {
        if (food == null) {
            return;
        }
        float safeServings = servings > 0.0f && !Float.isNaN(servings) && !Float.isInfinite(servings)
                ? servings
                : 1.0f;
        calories += Math.round(food.calories * safeServings);
        totalFatGrams += food.totalFatGrams * safeServings;
        saturatedFatGrams += food.saturatedFatGrams * safeServings;
        transFatGrams += food.transFatGrams * safeServings;
        cholesterolMg += food.cholesterolMg * safeServings;
        sodiumMg += food.sodiumMg * safeServings;
        totalCarbsGrams += food.totalCarbsGrams * safeServings;
        fiberGrams += food.fiberGrams * safeServings;
        totalSugarsGrams += food.totalSugarsGrams * safeServings;
        addedSugarsGrams += food.addedSugarsGrams * safeServings;
        proteinGrams += food.proteinGrams * safeServings;
        vitaminDMcg += food.vitaminDMcg * safeServings;
        calciumMg += food.calciumMg * safeServings;
        ironMg += food.ironMg * safeServings;
        potassiumMg += food.potassiumMg * safeServings;
    }

    public boolean isEmpty() {
        return calories == 0
                && totalFatGrams <= 0.0f
                && saturatedFatGrams <= 0.0f
                && transFatGrams <= 0.0f
                && cholesterolMg <= 0.0f
                && sodiumMg <= 0.0f
                && totalCarbsGrams <= 0.0f
                && fiberGrams <= 0.0f
                && totalSugarsGrams <= 0.0f
                && addedSugarsGrams <= 0.0f
                && proteinGrams <= 0.0f
                && vitaminDMcg <= 0.0f
                && calciumMg <= 0.0f
                && ironMg <= 0.0f
                && potassiumMg <= 0.0f;
    }

    private static float nonNegative(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return 0.0f;
        }
        return Math.max(0.0f, value);
    }
}
