package com.jojokorok.nourishrx.backup;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class BackupAvatarStore {
    private static final int MAX_AVATAR_BYTES = 10 * 1024 * 1024;
    private static final int MAX_ENCODED_AVATAR_CHARS = ((MAX_AVATAR_BYTES + 2) / 3) * 4;

    static final class PreparedAvatars {
        private final Map<Long, String> uriByProfileId;
        private final List<File> createdFiles;

        PreparedAvatars(Map<Long, String> uriByProfileId, List<File> createdFiles) {
            this.uriByProfileId = uriByProfileId;
            this.createdFiles = createdFiles;
        }

        String uriFor(long profileId) {
            String uri = uriByProfileId.get(profileId);
            return uri == null ? "" : uri;
        }

        void discard() {
            for (File file : createdFiles) {
                deleteQuietly(file);
            }
        }
    }

    private final File directory;

    BackupAvatarStore(File directory) {
        this.directory = directory;
    }

    PreparedAvatars prepare(List<NourishRxBackup.ProfileRecord> profiles)
            throws BackupImportException {
        Map<Long, String> uris = new HashMap<>();
        List<File> createdFiles = new ArrayList<>();
        try {
            for (NourishRxBackup.ProfileRecord profile : profiles) {
                if (profile.avatar == null) {
                    continue;
                }
                byte[] imageBytes = decode(profile.avatar.base64Data, profile.name);
                ensureDirectory();
                File destination = new File(
                        directory,
                        UUID.randomUUID() + extensionFor(profile.avatar.mimeType)
                );
                try (FileOutputStream output = new FileOutputStream(destination)) {
                    output.write(imageBytes);
                }
                createdFiles.add(destination);
                uris.put(profile.id, destination.toURI().toString());
            }
            return new PreparedAvatars(uris, createdFiles);
        } catch (IOException | BackupImportException exception) {
            for (File file : createdFiles) {
                deleteQuietly(file);
            }
            if (exception instanceof BackupImportException) {
                throw (BackupImportException) exception;
            }
            throw new BackupImportException("Imported profile photos could not be prepared", exception);
        }
    }

    void deleteManagedUris(List<String> avatarUris) {
        for (String avatarUri : avatarUris) {
            File file = managedFile(avatarUri);
            if (file != null) {
                deleteQuietly(file);
            }
        }
    }

    private byte[] decode(String encoded, String profileName) throws BackupImportException {
        if (encoded == null || encoded.length() > MAX_ENCODED_AVATAR_CHARS) {
            throw new BackupImportException("Profile photo is too large for " + profileName);
        }
        final byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException exception) {
            throw new BackupImportException("Profile photo data is invalid for " + profileName, exception);
        }
        if (bytes.length == 0 || bytes.length > MAX_AVATAR_BYTES) {
            throw new BackupImportException("Profile photo size is invalid for " + profileName);
        }
        return bytes;
    }

    private void ensureDirectory() throws IOException {
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Could not create the imported profile photo folder");
        }
        if (!directory.isDirectory()) {
            throw new IOException("Imported profile photo location is not a folder");
        }
    }

    private File managedFile(String avatarUri) {
        if (avatarUri == null || avatarUri.trim().isEmpty()) {
            return null;
        }
        try {
            URI uri = new URI(avatarUri);
            if (!"file".equalsIgnoreCase(uri.getScheme())) {
                return null;
            }
            File candidate = new File(uri).getCanonicalFile();
            File managedDirectory = directory.getCanonicalFile();
            String prefix = managedDirectory.getPath() + File.separator;
            return candidate.getPath().startsWith(prefix) ? candidate : null;
        } catch (IOException | IllegalArgumentException | URISyntaxException exception) {
            return null;
        }
    }

    private static String extensionFor(String mimeType) {
        if (mimeType == null) {
            return ".img";
        }
        String normalized = mimeType.trim().toLowerCase(java.util.Locale.ROOT);
        if ("image/png".equals(normalized)) {
            return ".png";
        }
        if ("image/jpeg".equals(normalized) || "image/jpg".equals(normalized)) {
            return ".jpg";
        }
        if ("image/webp".equals(normalized)) {
            return ".webp";
        }
        return ".img";
    }

    private static void deleteQuietly(File file) {
        if (file != null && file.isFile()) {
            file.delete();
        }
    }
}
