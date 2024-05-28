package com.example.application;

import java.util.ArrayList;
import java.util.List;

public class FileSystemItem {
    private String name;
    private boolean isFolder;
    private List<FileSystemItem> children;

    public FileSystemItem(String name, boolean isFolder) {
        this.name = name;
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
