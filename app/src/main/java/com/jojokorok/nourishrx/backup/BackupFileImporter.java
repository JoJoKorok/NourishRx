package com.jojokorok.nourishrx.backup;

import android.content.ContentResolver;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class BackupFileImporter {
    private static final int MAX_BACKUP_BYTES = 50 * 1024 * 1024;

    private final ContentResolver contentResolver;
    private final BackupJsonCodec jsonCodec;

    public BackupFileImporter(ContentResolver contentResolver, BackupJsonCodec jsonCodec) {
        this.contentResolver = contentResolver;
        this.jsonCodec = jsonCodec;
    }

    public NourishRxBackup read(Uri source) throws IOException {
        try (InputStream input = contentResolver.openInputStream(source)) {
            if (input == null) {
                throw new IOException("The selected backup file could not be opened");
            }
            return jsonCodec.fromJson(readUtf8(input));
        } catch (SecurityException exception) {
            throw new IOException("NourishRx does not have permission to read that file", exception);
        }
    }

    private static String readUtf8(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8 * 1024];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > MAX_BACKUP_BYTES) {
                throw new IOException("Backup is larger than the supported 50 MB limit");
            }
            output.write(buffer, 0, read);
        }
        return output.toString(StandardCharsets.UTF_8.name());
    }
}
