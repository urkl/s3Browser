package com.example.application;

import java.util.ArrayList;
import java.util.List;

public class FileSystemItem {
    private String name;
    private boolean isFolder;
    private List<FileSystemItem> children;
    private final long size;
    public FileSystemItem(String name, long size, boolean isFolder) {
        this.name = name;
        this.size = size;
        this.isFolder = isFolder;
        this.children = new ArrayList<>();
    }

    public String getName() {
        return name;
    }

    public boolean isFolder() {
        return isFolder;
    }

    public List<FileSystemItem> getChildren() {
        return children;
    }

    public void addChild(FileSystemItem child) {
        this.children.add(child);
    }
}
