package com.tuempresa.gestorarchivos;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

public class FileOperations {

    public interface Callback {
        void onSuccess(String message);
        void onError(String error);
    }

    /** Delete a file or directory recursively */
    public static boolean delete(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (!delete(child)) return false;
                }
            }
        }
        return file.delete();
    }

    /** Rename a file or directory */
    public static boolean rename(File file, String newName) {
        File dest = new File(file.getParent(), newName);
        return file.renameTo(dest);
    }

    /** Copy file to destination directory */
    public static void copy(File source, File destDir, Callback callback) {
        new Thread(() -> {
            try {
                copySync(source, destDir);
                callback.onSuccess("Copiado correctamente");
            } catch (IOException e) {
                callback.onError("Error al copiar: " + e.getMessage());
            }
        }).start();
    }

    /** Move file to destination directory */
    public static void move(File source, File destDir, Callback callback) {
        new Thread(() -> {
            try {
                moveSync(source, destDir);
                callback.onSuccess("Movido correctamente");
            } catch (IOException e) {
                callback.onError("Error al mover: " + e.getMessage());
            }
        }).start();
    }

    /** Synchronous copy, useful for batch operations. */
    public static void copySync(File source, File destDir) throws IOException {
        File dest = buildUniqueDestination(destDir, source.getName());
        if (source.isDirectory()) copyDir(source, dest);
        else copyFile(source, dest);
    }

    /** Synchronous move, useful for batch operations. */
    public static void moveSync(File source, File destDir) throws IOException {
        File dest = buildUniqueDestination(destDir, source.getName());
        if (source.renameTo(dest)) {
            return;
        }

        // renameTo can fail across different storage mounts, fallback to copy + delete
        if (source.isDirectory()) copyDir(source, dest);
        else copyFile(source, dest);
        if (!delete(source)) {
            throw new IOException("No se pudo eliminar el origen tras mover: " + source.getName());
        }
    }

    /** Create new directory */
    public static boolean createDirectory(File parent, String name) {
        File newDir = new File(parent, name);
        return newDir.mkdirs();
    }

    // --- Private helpers ---

    private static void copyFile(File src, File dst) throws IOException {
        try (FileChannel in = new FileInputStream(src).getChannel();
             FileChannel out = new FileOutputStream(dst).getChannel()) {
            out.transferFrom(in, 0, in.size());
        }
    }

    private static void copyDir(File src, File dst) throws IOException {
        if (!dst.exists()) dst.mkdirs();
        File[] files = src.listFiles();
        if (files == null) return;
        for (File f : files) {
            File target = new File(dst, f.getName());
            if (f.isDirectory()) copyDir(f, target);
            else copyFile(f, target);
        }
    }

    private static File buildUniqueDestination(File destDir, String sourceName) {
        File dest = new File(destDir, sourceName);
        if (!dest.exists()) return dest;

        String base = sourceName;
        String ext = "";
        int dot = sourceName.lastIndexOf('.');
        if (dot > 0 && dot < sourceName.length() - 1) {
            base = sourceName.substring(0, dot);
            ext = sourceName.substring(dot);
        }

        int index = 1;
        while (dest.exists()) {
            dest = new File(destDir, base + " (" + index + ")" + ext);
            index++;
        }
        return dest;
    }
}
