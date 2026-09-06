package com.jojokorok.nourishrx.backup;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class BackupAvatarStoreTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void preparesPortableAvatarAndDeletesItWhenDiscarded() throws Exception {
        File directory = new File(temporaryFolder.getRoot(), "avatars");
        BackupAvatarStore store = new BackupAvatarStore(directory);
        List<NourishRxBackup.ProfileRecord> profiles = profilesWithAvatar(
                Base64.getEncoder().encodeToString("image-data".getBytes(StandardCharsets.UTF_8))
        );

        BackupAvatarStore.PreparedAvatars prepared = store.prepare(profiles);
        File avatar = new File(new java.net.URI(prepared.uriFor(1)));

        assertTrue(avatar.isFile());
        prepared.discard();
        assertFalse(avatar.exists());
    }

    @Test
    public void rejectsInvalidAvatarWithoutLeavingFiles() {
        File directory = new File(temporaryFolder.getRoot(), "avatars");
        BackupAvatarStore store = new BackupAvatarStore(directory);

        assertThrows(
                BackupImportException.class,
                () -> store.prepare(profilesWithAvatar("not valid base64"))
        );
        assertEquals(0, directory.exists() ? directory.listFiles().length : 0);
    }

    @Test
    public void removesEarlierAvatarWhenLaterProfileCannotBePrepared() {
        File directory = new File(temporaryFolder.getRoot(), "avatars");
        BackupAvatarStore store = new BackupAvatarStore(directory);
        List<NourishRxBackup.ProfileRecord> profiles = profilesWithAvatar(
                Base64.getEncoder().encodeToString("image-data".getBytes(StandardCharsets.UTF_8))
        );
        NourishRxBackup.ProfileRecord invalidProfile = profileWithAvatar(2, "Casey", "bad data");
        profiles.add(invalidProfile);

        assertThrows(BackupImportException.class, () -> store.prepare(profiles));

        assertEquals(0, directory.exists() ? directory.listFiles().length : 0);
    }

    private static List<NourishRxBackup.ProfileRecord> profilesWithAvatar(String base64Data) {
        NourishRxBackup.ProfileRecord profile = profileWithAvatar(1, "Jordan", base64Data);
        List<NourishRxBackup.ProfileRecord> profiles = new ArrayList<>();
        profiles.add(profile);
        return profiles;
    }

    private static NourishRxBackup.ProfileRecord profileWithAvatar(
            long id,
            String name,
            String base64Data
    ) {
        NourishRxBackup.ProfileRecord profile = new NourishRxBackup.ProfileRecord();
        profile.id = id;
        profile.name = name;
        profile.avatar = new NourishRxBackup.AvatarRecord();
        profile.avatar.mimeType = "image/png";
        profile.avatar.base64Data = base64Data;
        return profile;
    }
}
