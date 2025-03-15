package com.hsbc.interview.common;

public class TransException extends RuntimeException {
    private int code;

    public TransException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
