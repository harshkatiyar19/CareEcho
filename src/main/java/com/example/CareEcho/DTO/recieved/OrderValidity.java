package com.example.CareEcho.DTO.recieved;

import lombok.Getter;

@Getter
public enum OrderValidity {
    Day(1),
    GTC(2),
    IOC(3),
    FOK(4),
    GTD(5);

    private final int code;

    OrderValidity(int code) {
        this.code = code;
    }

    // Lookup method
    public static OrderValidity fromCode(int code) {
        for (OrderValidity type : OrderValidity.values()) {
            if (type.getCode()==code) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid OrderValidity code: " + code);
    }
}
