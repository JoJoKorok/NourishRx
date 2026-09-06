package com.jojokorok.nourishrx.backup;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

public final class BackupJsonCodec {
    public static final int MAX_BACKUP_JSON_CHARS = 50 * 1024 * 1024;
    private static final String[] REQUIRED_TOP_LEVEL_FIELDS = {
            "format",
            "schemaVersion",
            "metadata",
            "settings",
            "profiles",
            "medications",
            "doseLogs",
            "nutritionMeals",
            "foods",
            "mealFoodLogs",
            "waterEntries",
            "weightEntries",
            "mealDefaults",
            "savedMeals",
            "savedMealItems"
    };

    private final Gson gson;

    public BackupJsonCodec() {
        gson = new GsonBuilder()
                .disableHtmlEscaping()
                .setPrettyPrinting()
                .create();
    }

    public String toJson(NourishRxBackup backup) {
        requireValid(backup);
        return gson.toJson(backup);
    }

    public NourishRxBackup fromJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            throw new BackupFormatException(java.util.Collections.singletonList("Backup file is empty"));
        }
        if (json.length() > MAX_BACKUP_JSON_CHARS) {
            throw new BackupFormatException(java.util.Collections.singletonList("Backup file is too large"));
        }

        final NourishRxBackup backup;
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) {
                throw new BackupFormatException(java.util.Collections.singletonList(
                        "Backup file must contain a JSON object"
                ));
            }
            JsonObject root = parsed.getAsJsonObject();
            requireTopLevelFields(root);
            backup = gson.fromJson(root, NourishRxBackup.class);
        } catch (JsonParseException | IllegalStateException exception) {
            throw new BackupFormatException("Backup file is not valid JSON", exception);
        }
        requireValid(backup);
        return backup;
    }

    private static void requireValid(NourishRxBackup backup) {
        BackupValidationResult result = BackupValidator.validate(backup);
        if (!result.isValid()) {
            throw new BackupFormatException(result.errors());
        }
    }

    private static void requireTopLevelFields(JsonObject root) {
        for (String field : REQUIRED_TOP_LEVEL_FIELDS) {
            if (!root.has(field) || root.get(field).isJsonNull()) {
                throw new BackupFormatException(java.util.Collections.singletonList(
                        "Backup is missing required field: " + field
                ));
            }
        }
    }
}
