package com.fraugz.filemanager;

import android.app.PendingIntent;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

public class TrashManager {

    public interface ProgressCallback {
        void onUnitDone();
    }

    private static final String TRASH_FOLDER = ".Trash";
    private static final String META_SUFFIX = ".meta";
    private static final String TRASH_SUFFIX = ".fichero";
    private static final long TRASH_RETENTION_MS = 30L * 24L * 60L * 60L * 1000L;
    private static volatile String lastError = "";

    public static String getLastError() {
        return lastError == null ? "" : lastError;
    }

    private static void setLastError(String message) {
        lastError = message == null ? "" : message;
    }

    public static class TrashEntry {
        private final File trashFile;
        private final String originalPath;
        private final String originalName;
        private final long deletedAt;

        TrashEntry(File trashFile, String originalPath, String originalName, long deletedAt) {
            this.trashFile = trashFile;
            this.originalPath = originalPath;
            this.originalName = originalName;
            this.deletedAt = deletedAt;
        }

        public File getTrashFile() { return trashFile; }
        public String getOriginalPath() { return originalPath; }
        public String getOriginalName() { return originalName; }
        public long getDeletedAt() { return deletedAt; }
    }

    public static File getTrashDir(Context ctx) {
        File trash = new File(ctx.getExternalFilesDir(null), TRASH_FOLDER);
        if (!trash.exists()) trash.mkdirs();
        return trash;
    }

    /**
     * Attempts to move the given files to the OS system trash (Android 11+).
     * Returns a PendingIntent to launch via startIntentSenderForResult, or null if
     * no file could be resolved in MediaStore (fall back to app trash in that case).
     */
    public static PendingIntent createSystemTrashRequest(Context ctx, List<File> files) {
        List<Uri> uris = new ArrayList<>();
        for (File f : files) {
            Uri uri = getMediaStoreUriForFile(ctx, f);
            if (uri != null) uris.add(uri);
        }
        if (uris.isEmpty()) return null;
        try {
            return MediaStore.createTrashRequest(ctx.getContentResolver(), uris, true);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Uri getMediaStoreUriForFile(Context ctx, File file) {
        if (file == null) return null;
        Uri externalUri = MediaStore.Files.getContentUri("external");
        String[] proj = {MediaStore.Files.FileColumns._ID};
        String sel = MediaStore.Files.FileColumns.DATA + "=?";
        String[] args = {file.getAbsolutePath()};
        try (Cursor c = ctx.getContentResolver().query(externalUri, proj, sel, args, null)) {
            if (c != null && c.moveToFirst()) {
                long id = c.getLong(0);
                return ContentUris.withAppendedId(externalUri, id);
            }
        } catch (Exception ignored) {}
        return null;
    }

    public static boolean moveToTrash(Context ctx, File file) {
        return moveToTrash(ctx, file, null);
    }

    public static boolean moveToTrash(Context ctx, File file, AtomicBoolean cancelled) {
        return moveToTrash(ctx, file, cancelled, null);
    }

    public static boolean moveToTrash(Context ctx, File file, AtomicBoolean cancelled, ProgressCallback progressCallback) {
        setLastError("");
        if (isCancelled(cancelled)) {
            setLastError("Operation cancelled.");
            return false;
        }
        if (file == null) {
            setLastError("The item is null.");
            return false;
        }
        if (!file.exists()) {
            setLastError("The item no longer exists.");
            return false;
        }

        purgeExpired(ctx);

        File trash = getTrashDir(ctx);
        String token = String.valueOf(System.currentTimeMillis());
        String baseName = safeFileName(file.getName());
        File dest = new File(trash, baseName + "_" + token + TRASH_SUFFIX);

        int i = 1;
        while (dest.exists()) {
            if (isCancelled(cancelled)) {
                setLastError("Operation cancelled.");
                return false;
            }
            dest = new File(trash, baseName + "_" + token + "_" + i + TRASH_SUFFIX);
            i++;
        }

        if (!file.renameTo(dest)) {
            // Fallback for cases where renameTo fails (common with directories on some devices).
            try {
                if (isCancelled(cancelled)) {
                    setLastError("Operation cancelled.");
                    return false;
                }
                if (file.isDirectory()) {
                    copyDirectory(file, dest, cancelled, progressCallback);
                } else {
                    copyFile(file, dest, cancelled, progressCallback);
                }
                if (isCancelled(cancelled)) {
                    FileOperations.delete(dest);
                    setLastError("Operation cancelled.");
                    return false;
                }
                if (!dest.exists() || !FileOperations.delete(file)) {
                    setLastError("Could not delete the original item after copying it to trash.");
                    return false;
                }
            } catch (Exception e) {
                FileOperations.delete(dest);
                setLastError("Error moving to trash: " + e.getMessage());
                return false;
            }
        } else {
            reportUnits(progressCallback, Math.max(1, countUnits(dest)));
        }

        boolean ok = writeMetadata(dest, file.getAbsolutePath(), file.getName(), System.currentTimeMillis());
        if (!ok && getLastError().isEmpty()) {
            setLastError("Could not save trash metadata.");
        }
        return ok;
    }

    public static boolean restore(Context ctx, TrashEntry entry) {
        setLastError("");
        if (entry == null || entry.getTrashFile() == null) {
            setLastError("Invalid trash entry.");
            return false;
        }
        if (!entry.getTrashFile().exists()) {
            setLastError("The item in trash no longer exists.");
            return false;
        }

        File target = new File(entry.getOriginalPath());
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            setLastError("Could not create destination folder: " + parent.getAbsolutePath());
            return false;
        }

        File finalTarget = target;
        if (finalTarget.exists()) {
            finalTarget = buildUniqueSibling(finalTarget);
        }

        File source = entry.getTrashFile();
        boolean restored = source.renameTo(finalTarget);
        if (!restored) {
            try {
                if (source.isDirectory()) {
                    copyDirectory(source, finalTarget);
                } else {
                    copyFile(source, finalTarget);
                }
                restored = FileOperations.delete(source);
                if (!restored) {
                    setLastError("Restored by copy, but could not clean the item in trash.");
                }
            } catch (Exception e) {
                setLastError("Error restoring: " + e.getMessage());
                restored = false;
            }
        }

        if (!restored && getLastError().isEmpty()) {
            setLastError("Could not move or copy the item to the original destination.");
        }

        if (restored) {
            deleteMetadata(source);
        }
        return restored;
    }

    public static List<TrashEntry> getTrashFiles(Context ctx) {
        purgeExpired(ctx);

        List<TrashEntry> list = new ArrayList<>();
        File trash = getTrashDir(ctx);
        File[] files = trash.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.getName().endsWith(TRASH_SUFFIX)) {
                    TrashEntry entry = readEntry(f);
                    if (entry != null) list.add(entry);
                }
            }
        }
        Collections.sort(list, Comparator.comparingLong(TrashEntry::getDeletedAt).reversed());
        return list;
    }

    public static long getTrashSize(Context ctx) {
        long size = 0;
        for (TrashEntry e : getTrashFiles(ctx)) size += calculateSize(e.getTrashFile());
        return size;
    }

    public static void emptyTrash(Context ctx) {
        for (TrashEntry e : getTrashFiles(ctx)) {
            deleteEntry(e);
        }
    }

    public static void deleteEntry(TrashEntry entry) {
        if (entry == null) return;
        FileOperations.delete(entry.getTrashFile());
        deleteMetadata(entry.getTrashFile());
    }

    public static void purgeExpired(Context ctx) {
        long now = System.currentTimeMillis();
        for (TrashEntry e : getTrashEntriesWithoutPurge(ctx)) {
            if ((now - e.getDeletedAt()) > TRASH_RETENTION_MS) {
                deleteEntry(e);
            }
        }
    }

    public static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private static List<TrashEntry> getTrashEntriesWithoutPurge(Context ctx) {
        List<TrashEntry> list = new ArrayList<>();
        File trash = getTrashDir(ctx);
        File[] files = trash.listFiles();
        if (files == null) return list;

        for (File f : files) {
            if (f.getName().endsWith(TRASH_SUFFIX)) {
                TrashEntry entry = readEntry(f);
                if (entry != null) list.add(entry);
            }
        }
        return list;
    }

    private static boolean writeMetadata(File trashedFile, String originalPath, String originalName, long deletedAt) {
        Properties p = new Properties();
        p.setProperty("originalPath", originalPath);
        p.setProperty("originalName", originalName);
        p.setProperty("deletedAt", String.valueOf(deletedAt));

        File meta = metadataFileFor(trashedFile);
        try (FileOutputStream out = new FileOutputStream(meta)) {
            p.store(out, "trash metadata");
            return true;
        } catch (IOException e) {
            FileOperations.delete(trashedFile);
            setLastError("Could not save metadata: " + e.getMessage());
            return false;
        }
    }

    private static TrashEntry readEntry(File trashedFile) {
        File meta = metadataFileFor(trashedFile);
        if (!meta.exists()) {
            return new TrashEntry(trashedFile, guessFallbackPath(trashedFile), stripTrashSuffix(trashedFile.getName()), trashedFile.lastModified());
        }

        Properties p = new Properties();
        try (FileInputStream in = new FileInputStream(meta)) {
            p.load(in);
            String originalPath = p.getProperty("originalPath", guessFallbackPath(trashedFile));
            String originalName = p.getProperty("originalName", stripTrashSuffix(trashedFile.getName()));
            long deletedAt = Long.parseLong(p.getProperty("deletedAt", String.valueOf(trashedFile.lastModified())));
            return new TrashEntry(trashedFile, originalPath, originalName, deletedAt);
        } catch (Exception e) {
            return new TrashEntry(trashedFile, guessFallbackPath(trashedFile), stripTrashSuffix(trashedFile.getName()), trashedFile.lastModified());
        }
    }

    private static File metadataFileFor(File trashedFile) {
        return new File(trashedFile.getParentFile(), trashedFile.getName() + META_SUFFIX);
    }

    private static long calculateSize(File file) {
        if (file == null || !file.exists()) return 0;
        if (file.isFile()) return file.length();
        long total = 0;
        File[] children = file.listFiles();
        if (children == null) return 0;
        for (File child : children) total += calculateSize(child);
        return total;
    }

    private static void copyDirectory(File src, File dst) throws IOException {
        copyDirectory(src, dst, null, null);
    }

    private static void copyDirectory(File src, File dst, AtomicBoolean cancelled) throws IOException {
        copyDirectory(src, dst, cancelled, null);
    }

    private static void copyDirectory(File src, File dst, AtomicBoolean cancelled, ProgressCallback progressCallback) throws IOException {
        if (isCancelled(cancelled)) throw new IOException("cancelled");
        if (!dst.exists() && !dst.mkdirs()) {
            throw new IOException("Could not create destination folder: " + dst.getAbsolutePath());
        }

        File[] children = src.listFiles();
        if (children == null || children.length == 0) {
            reportUnits(progressCallback, 1);
            return;
        }
        for (File child : children) {
            if (isCancelled(cancelled)) throw new IOException("cancelled");
            File target = new File(dst, child.getName());
            if (child.isDirectory()) {
                copyDirectory(child, target, cancelled, progressCallback);
            } else {
                copyFile(child, target, cancelled, progressCallback);
            }
        }
    }

    private static void copyFile(File src, File dst) throws IOException {
        copyFile(src, dst, null, null);
    }

    private static void copyFile(File src, File dst, AtomicBoolean cancelled) throws IOException {
        copyFile(src, dst, cancelled, null);
    }

    private static void copyFile(File src, File dst, AtomicBoolean cancelled, ProgressCallback progressCallback) throws IOException {
        if (isCancelled(cancelled)) throw new IOException("cancelled");
        try (FileInputStream in = new FileInputStream(src);
             FileOutputStream out = new FileOutputStream(dst)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                if (isCancelled(cancelled)) throw new IOException("cancelled");
                out.write(buffer, 0, read);
            }
        }
        reportUnits(progressCallback, 1);
    }

    private static boolean isCancelled(AtomicBoolean cancelled) {
        return cancelled != null && cancelled.get();
    }

    private static void reportUnits(ProgressCallback progressCallback, int units) {
        if (progressCallback == null) return;
        int safeUnits = Math.max(1, units);
        for (int i = 0; i < safeUnits; i++) {
            progressCallback.onUnitDone();
        }
    }

    private static int countUnits(File file) {
        if (file == null || !file.exists()) return 0;
        if (file.isFile()) return 1;
        File[] children = file.listFiles();
        if (children == null || children.length == 0) return 1;
        int total = 0;
        for (File child : children) total += countUnits(child);
        return Math.max(1, total);
    }

    private static void deleteMetadata(File trashedFile) {
        File meta = metadataFileFor(trashedFile);
        if (meta.exists()) {
            //noinspection ResultOfMethodCallIgnored
            meta.delete();
        }
    }

    private static File buildUniqueSibling(File originalTarget) {
        String name = originalTarget.getName();
        String base = name;
        String ext = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0 && dot < name.length() - 1) {
            base = name.substring(0, dot);
            ext = name.substring(dot);
        }

        File parent = originalTarget.getParentFile();
        int i = 1;
        File candidate = new File(parent, base + " (" + i + ")" + ext);
        while (candidate.exists()) {
            i++;
            candidate = new File(parent, base + " (" + i + ")" + ext);
        }
        return candidate;
    }

    private static String stripTrashSuffix(String fileName) {
        if (!fileName.endsWith(TRASH_SUFFIX)) return fileName;
        String noSuffix = fileName.substring(0, fileName.length() - TRASH_SUFFIX.length());
        int lastUnderscore = noSuffix.lastIndexOf('_');
        return lastUnderscore > 0 ? noSuffix.substring(0, lastUnderscore) : noSuffix;
    }

    private static String safeFileName(String name) {
        return name.replace('/', '_').replace('\\', '_');
    }

    private static String guessFallbackPath(File trashedFile) {
        // If there is no metadata, restore fallback is Downloads with original-like name.
        String baseName = stripTrashSuffix(trashedFile.getName());
        return android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS).getAbsolutePath() + File.separator + baseName;
    }
}

