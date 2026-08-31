package com.jojokorok.nourishrx.backup;

public final class BackupImportResult {
    public final BackupImportMode mode;
    public final int profileCount;
    public final int recordCount;
    public final long restoredSelectedProfileId;
    public final String restoredAppMode;

    BackupImportResult(
            BackupImportMode mode,
            int profileCount,
            int recordCount,
            long restoredSelectedProfileId,
            String restoredAppMode
    ) {
        this.mode = mode;
        this.profileCount = profileCount;
        this.recordCount = recordCount;
        this.restoredSelectedProfileId = restoredSelectedProfileId;
        this.restoredAppMode = restoredAppMode;
    }
}
