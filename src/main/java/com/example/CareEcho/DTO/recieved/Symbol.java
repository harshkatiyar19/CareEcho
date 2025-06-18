package com.example.CareEcho.DTO.recieved;

import lombok.Getter;

@Getter
public enum Symbol {
    APPL(1),GOOGL(2),MSFT(3),AMZN(4),TSLA(5),META(6),NVDA(7);

    private final int code;

    Symbol(int code) {
        this.code = code;
    }

    // Lookup method
    public static Symbol fromCode(int code) {
        for (Symbol type : Symbol.values()) {
            if (type.getCode()==code) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid Symbol code: " + code);
    }
}
