package com.example.application.services;

public class ExifData {
    private final String label;
    private final String value;

    public ExifData(String label, String value) {
        this.label = label;
        this.value = value;
    }

    public String getLabel() {
        return label;
    }

    public String getValue() {
        return value;
    }
}