package com.jojokorok.nourishrx.api;

import com.jojokorok.nourishrx.data.NutritionFood;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class OpenFoodFactsClient {
    public static final int PAGE_SIZE = 12;

    private static final String USER_AGENT = "NourishRx/1.0 (local Android app)";
    private static final String SEARCH_URL = "https://search.openfoodfacts.org/search";
    private static final String PRODUCT_URL = "https://world.openfoodfacts.org/api/v3/product/";

    public List<SearchResult> searchFoods(String query) throws IOException, JSONException {
        return searchFoods(query, 1);
    }

    public List<SearchResult> searchFoods(String query, int page) throws IOException, JSONException {
        String encodedQuery = URLEncoder.encode(query == null ? "" : query.trim(), StandardCharsets.UTF_8.name());
        String url = SEARCH_URL + "?q=" + encodedQuery +
                "&langs=en&page_size=" + PAGE_SIZE +
                "&page=" + Math.max(1, page);
        JSONObject root = new JSONObject(get(url));
        JSONArray hits = root.optJSONArray("hits");
        List<SearchResult> results = new ArrayList<>();
        if (hits == null) {
            return results;
        }

        for (int i = 0; i < hits.length(); i++) {
            JSONObject item = hits.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String code = clean(item.optString("code"));
            String name = firstClean(
                    item.optString("product_name_en"),
                    item.optString("product_name"),
                    item.optString("generic_name")
            );
            if (code.isEmpty() || name.isEmpty()) {
                continue;
            }
            results.add(new SearchResult(
                    code,
                    name,
                    brandsFrom(item.opt("brands")),
                    clean(item.optString("quantity")),
                    clean(item.optString("nutrition_grades"))
            ));
        }
        return results;
    }

    public NutritionFood fetchNutritionFood(String code, long profileId) throws IOException, JSONException {
        String encodedCode = URLEncoder.encode(code == null ? "" : code.trim(), StandardCharsets.UTF_8.name());
        String fields = "product_name,brands,quantity,serving_size,servings_per_package,nutrition_data_per,nutriments";
        JSONObject root = new JSONObject(get(PRODUCT_URL + encodedCode + "?fields=" + fields));
        JSONObject product = root.optJSONObject("product");
        if (product == null) {
            throw new IOException("Product was not found.");
        }

        JSONObject nutriments = product.optJSONObject("nutriments");
        String servingSize = clean(product.optString("serving_size"));
        if (servingSize.isEmpty()) {
            String dataPer = clean(product.optString("nutrition_data_per"));
            servingSize = dataPer.equalsIgnoreCase("100g") ? "100g" : "1 serving";
        }

        return new NutritionFood(
                0,
                profileId,
                brandsFrom(product.opt("brands")),
                firstClean(product.optString("product_name"), "OpenFoodFacts food"),
                servingSize,
                parseFloat(product.opt("servings_per_package"), 0.0f),
                Math.round(nutrientCalories(nutriments)),
                nutrientGrams(nutriments, "fat"),
                nutrientGrams(nutriments, "saturated-fat"),
                nutrientGrams(nutriments, "trans-fat"),
                nutrientMg(nutriments, "cholesterol"),
                nutrientMg(nutriments, "sodium"),
                nutrientGrams(nutriments, "carbohydrates"),
                nutrientGrams(nutriments, "fiber"),
                nutrientGrams(nutriments, "sugars"),
                nutrientGrams(nutriments, "added-sugars"),
                nutrientGrams(nutriments, "proteins"),
                nutrientMcg(nutriments, "vitamin-d"),
                nutrientMg(nutriments, "calcium"),
                nutrientMg(nutriments, "iron"),
                nutrientMg(nutriments, "potassium"),
                System.currentTimeMillis()
        );
    }

    private String get(String urlString) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(15_000);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Accept", "application/json");
        int status = connection.getResponseCode();
        InputStream stream = status >= 200 && status < 300
                ? connection.getInputStream()
                : connection.getErrorStream();
        String body = read(stream);
        connection.disconnect();
        if (status < 200 || status >= 300) {
            throw new IOException("OpenFoodFacts returned HTTP " + status);
        }
        return body;
    }

    private String read(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        }
        return builder.toString();
    }

    private static float nutrientCalories(JSONObject nutriments) {
        float kcal = nutrientValue(nutriments, "energy-kcal");
        if (kcal > 0.0f) {
            return kcal;
        }
        return nutrientValue(nutriments, "energy") / 4.184f;
    }

    private static float nutrientGrams(JSONObject nutriments, String key) {
        return convertUnit(nutrientValue(nutriments, key), nutrientUnit(nutriments, key), "g");
    }

    private static float nutrientMg(JSONObject nutriments, String key) {
        return convertUnit(nutrientValue(nutriments, key), nutrientUnit(nutriments, key), "mg");
    }

    private static float nutrientMcg(JSONObject nutriments, String key) {
        return convertUnit(nutrientValue(nutriments, key), nutrientUnit(nutriments, key), "mcg");
    }

    private static float nutrientValue(JSONObject nutriments, String key) {
        if (nutriments == null) {
            return 0.0f;
        }

        String[] candidates = new String[]{
                key + "_serving",
                key + "_100g",
                key + "_value",
                key
        };
        for (String candidate : candidates) {
            if (nutriments.has(candidate) && !nutriments.isNull(candidate)) {
                return parseFloat(nutriments.opt(candidate), 0.0f);
            }
        }
        return 0.0f;
    }

    private static String nutrientUnit(JSONObject nutriments, String key) {
        if (nutriments == null) {
            return "";
        }
        return clean(nutriments.optString(key + "_unit")).toLowerCase(Locale.US);
    }

    private static float convertUnit(float value, String unit, String target) {
        if (value <= 0.0f) {
            return 0.0f;
        }

        String cleanUnit = unit == null ? "" : unit.trim().toLowerCase(Locale.US);
        if (target.equals("g")) {
            if (cleanUnit.equals("mg")) {
                return value / 1000.0f;
            }
            if (cleanUnit.equals("\u00b5g") || cleanUnit.equals("ug") || cleanUnit.equals("mcg")) {
                return value / 1_000_000.0f;
            }
            return value;
        }

        if (target.equals("mg")) {
            if (cleanUnit.equals("g")) {
                return value * 1000.0f;
            }
            if (cleanUnit.equals("\u00b5g") || cleanUnit.equals("ug") || cleanUnit.equals("mcg")) {
                return value / 1000.0f;
            }
            return value;
        }

        if (cleanUnit.equals("g")) {
            return value * 1_000_000.0f;
        }
        if (cleanUnit.equals("mg")) {
            return value * 1000.0f;
        }
        return value;
    }

    private static float parseFloat(Object value, float fallback) {
        if (value == null || JSONObject.NULL.equals(value)) {
            return fallback;
        }
        if (value instanceof Number) {
            return Math.max(0.0f, ((Number) value).floatValue());
        }
        try {
            return Math.max(0.0f, Float.parseFloat(String.valueOf(value).trim()));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static String brandsFrom(Object value) {
        if (value instanceof JSONArray) {
            JSONArray brands = (JSONArray) value;
            for (int i = 0; i < brands.length(); i++) {
                String brand = clean(brands.optString(i));
                if (!brand.isEmpty()) {
                    return brand;
                }
            }
            return "";
        }

        String brands = clean(String.valueOf(value == null || JSONObject.NULL.equals(value) ? "" : value));
        int comma = brands.indexOf(',');
        return comma >= 0 ? brands.substring(0, comma).trim() : brands;
    }

    private static String firstClean(String... values) {
        for (String value : values) {
            String clean = clean(value);
            if (!clean.isEmpty() && !"null".equalsIgnoreCase(clean)) {
                return clean;
            }
        }
        return "";
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    public static class SearchResult {
        public final String code;
        public final String name;
        public final String brand;
        public final String quantity;
        public final String nutritionGrade;

        SearchResult(String code, String name, String brand, String quantity, String nutritionGrade) {
            this.code = code;
            this.name = name;
            this.brand = brand;
            this.quantity = quantity;
            this.nutritionGrade = nutritionGrade;
        }

        public String displayName() {
            if (brand.isEmpty()) {
                return name;
            }
            return brand + " - " + name;
        }
    }
}
