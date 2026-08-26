package com.jojokorok.nourishrx.backup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class BackupValidationResult {
    private final List<String> errors;

    BackupValidationResult(List<String> errors) {
        this.errors = Collections.unmodifiableList(new ArrayList<>(errors));
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    public List<String> errors() {
        return errors;
    }
}
