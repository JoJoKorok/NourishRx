package com.jojokorok.nourishrx.backup;

public final class BackupImportException extends Exception {
    public BackupImportException(String message) {
        super(message);
    }

    public BackupImportException(String message, Throwable cause) {
        super(message, cause);
    }
}
