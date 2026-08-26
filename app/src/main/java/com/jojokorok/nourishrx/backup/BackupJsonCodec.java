package com.jojokorok.nourishrx.backup;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

public final class BackupJsonCodec {
    public static final int MAX_BACKUP_JSON_CHARS = 50 * 1024 * 1024;

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
            backup = gson.fromJson(json, NourishRxBackup.class);
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
}
