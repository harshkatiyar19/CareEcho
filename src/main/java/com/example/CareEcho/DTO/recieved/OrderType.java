package com.example.CareEcho.DTO.recieved;

import lombok.Getter;

@Getter
public enum OrderType {
    Limit(1),Market(2),StopLoss(3),StopLimit(4),TrailingStop(5),FOK(6),IOC(7),AON(8);

    private final int code;

    OrderType(int code) {
        this.code = code;
    }

    // Lookup method
    public static OrderType fromCode(int code) {
        for (OrderType type : OrderType.values()) {
            if (type.getCode()==code) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid OrderType code: " + code);
    }
}
