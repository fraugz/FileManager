package com.fraugz.filemanager;

import java.io.File;

public class FileItem {
    private final File file;
    private boolean selected;

    public FileItem(File file) {
        this.file = file;
        this.selected = false;
    }

    public File getFile() { return file; }
    public String getName() { return file.getName(); }
    public boolean isDirectory() { return file.isDirectory(); }
    public boolean isSelected() { return selected; }
    public void setSelected(boolean selected) { this.selected = selected; }

    public String getExtension() {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        return (dot >= 0) ? name.substring(dot + 1).toLowerCase() : "";
    }

    public String getFormattedSize() {
        if (file.isDirectory()) {
            String[] children = file.list();
            int count = (children != null) ? children.length : 0;
            return count + " elemento" + (count != 1 ? "s" : "");
        }
        long size = file.length();
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        if (size < 1024 * 1024 * 1024) return String.format("%.1f MB", size / (1024.0 * 1024));
        return String.format("%.2f GB", size / (1024.0 * 1024 * 1024));
    }

    public String getFormattedDate() {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault());
        return sdf.format(new java.util.Date(file.lastModified()));
    }
}

