package com.lab.flametemp.web;

/** 400-class request error with a stable type tag. */
public class BadRequestException extends RuntimeException {

    private final String type;

    public BadRequestException(String type, String message) {
        super(message);
        this.type = type;
    }

    public String type() {
        return type;
    }
}
