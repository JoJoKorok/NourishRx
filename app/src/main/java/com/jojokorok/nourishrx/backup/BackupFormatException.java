package com.jojokorok.nourishrx.backup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class BackupFormatException extends IllegalArgumentException {
    private final List<String> validationErrors;

    BackupFormatException(String message, Throwable cause) {
        super(message, cause);
        this.validationErrors = Collections.singletonList(message);
    }

    BackupFormatException(List<String> validationErrors) {
        super(String.join("; ", validationErrors));
        this.validationErrors = Collections.unmodifiableList(new ArrayList<>(validationErrors));
    }

    public List<String> validationErrors() {
        return validationErrors;
    }
}
