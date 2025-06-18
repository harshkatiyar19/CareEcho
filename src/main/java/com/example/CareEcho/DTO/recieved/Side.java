package com.example.CareEcho.DTO.recieved;

import lombok.Getter;

@Getter
public enum Side {
    BUY(1),SELL(2);

    private final int code;

    Side(int code) {
        this.code = code;
    }

    // Lookup method
    public static Side fromCode(int code) {
        for (Side type : Side.values()) {
            if (type.getCode()==code) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid Side code: " + code);
    }
}
