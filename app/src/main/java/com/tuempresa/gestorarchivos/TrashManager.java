package com.tuempresa.gestorarchivos;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;

public class TrashManager {

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

    public static boolean moveToTrash(Context ctx, File file) {
        setLastError("");
        if (file == null) {
            setLastError("El elemento es nulo.");
            return false;
        }
        if (!file.exists()) {
            setLastError("El elemento ya no existe.");
            return false;
        }

        purgeExpired(ctx);

        File trash = getTrashDir(ctx);
        String token = String.valueOf(System.currentTimeMillis());
        String baseName = safeFileName(file.getName());
        File dest = new File(trash, baseName + "_" + token + TRASH_SUFFIX);

        int i = 1;
        while (dest.exists()) {
            dest = new File(trash, baseName + "_" + token + "_" + i + TRASH_SUFFIX);
            i++;
        }

        if (!file.renameTo(dest)) {
            // Fallback for cases where renameTo fails (common with directories on some devices).
            try {
                if (file.isDirectory()) {
                    copyDirectory(file, dest);
                } else {
                    copyFile(file, dest);
                }
                if (!dest.exists() || !FileOperations.delete(file)) {
                    setLastError("No se pudo eliminar el elemento original tras copiarlo a la papelera.");
                    return false;
                }
            } catch (Exception e) {
                setLastError("Error moviendo a papelera: " + e.getMessage());
                return false;
            }
        }

        boolean ok = writeMetadata(dest, file.getAbsolutePath(), file.getName(), System.currentTimeMillis());
        if (!ok && getLastError().isEmpty()) {
            setLastError("No se pudo guardar la metadata de la papelera.");
        }
        return ok;
    }

    public static boolean restore(Context ctx, TrashEntry entry) {
        setLastError("");
        if (entry == null || entry.getTrashFile() == null) {
            setLastError("Entrada de papelera invalida.");
            return false;
        }
        if (!entry.getTrashFile().exists()) {
            setLastError("El elemento en papelera ya no existe.");
            return false;
        }

        File target = new File(entry.getOriginalPath());
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            setLastError("No se pudo crear la carpeta de destino: " + parent.getAbsolutePath());
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
                    setLastError("Restaurado por copia, pero no se pudo limpiar el elemento en papelera.");
                }
            } catch (Exception e) {
                setLastError("Error al restaurar: " + e.getMessage());
                restored = false;
            }
        }

        if (!restored && getLastError().isEmpty()) {
            setLastError("No se pudo mover ni copiar el elemento al destino original.");
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
            setLastError("No se pudo guardar metadata: " + e.getMessage());
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
        if (!dst.exists() && !dst.mkdirs()) {
            throw new IOException("No se pudo crear carpeta destino: " + dst.getAbsolutePath());
        }

        File[] children = src.listFiles();
        if (children == null) return;
        for (File child : children) {
            File target = new File(dst, child.getName());
            if (child.isDirectory()) {
                copyDirectory(child, target);
            } else {
                copyFile(child, target);
            }
        }
    }

    private static void copyFile(File src, File dst) throws IOException {
        try (FileInputStream in = new FileInputStream(src);
             FileOutputStream out = new FileOutputStream(dst)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
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
