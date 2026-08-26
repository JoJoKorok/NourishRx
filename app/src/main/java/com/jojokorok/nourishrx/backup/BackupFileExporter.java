package com.jojokorok.nourishrx.backup;

import android.content.ContentResolver;
import android.net.Uri;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

public final class BackupFileExporter {
    private final ContentResolver contentResolver;
    private final BackupSnapshotReader snapshotReader;
    private final BackupJsonCodec jsonCodec;

    public BackupFileExporter(
            ContentResolver contentResolver,
            BackupSnapshotReader snapshotReader,
            BackupJsonCodec jsonCodec
    ) {
        this.contentResolver = contentResolver;
        this.snapshotReader = snapshotReader;
        this.jsonCodec = jsonCodec;
    }

    public void export(Uri destination, long selectedProfileId, String appMode) throws IOException {
        NourishRxBackup backup = snapshotReader.read(selectedProfileId, appMode);
        String json = jsonCodec.toJson(backup);
        if (json.length() > BackupJsonCodec.MAX_BACKUP_JSON_CHARS) {
            throw new IOException("Backup is larger than the supported 50 MB limit");
        }

        try (OutputStream output = contentResolver.openOutputStream(destination, "wt")) {
            if (output == null) {
                throw new IOException("The selected backup file could not be opened");
            }
            try (OutputStreamWriter writer = new OutputStreamWriter(output, StandardCharsets.UTF_8)) {
                writer.write(json);
            }
        }
    }
}
