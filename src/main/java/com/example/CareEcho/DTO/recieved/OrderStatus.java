package com.example.CareEcho.DTO.recieved;

import lombok.Getter;

@Getter
public enum OrderStatus {
    New(1),
    Partially(2),
    Filled(3),
    Cancelled(4);

    private final int code;

    OrderStatus(int code) {
        this.code = code;
    }

    // Lookup method
    public static OrderStatus fromCode(int code) {
        for (OrderStatus type : OrderStatus.values()) {
            if (type.getCode()==code) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid OrderStatus code: " + code);
    }
}
