package com.example.CareEcho.DTO.send;

import com.example.CareEcho.DTO.recieved.Symbol;

import java.util.Map;

public record CombinedBook(
        Map<Symbol, SymbolData> data

) {
}