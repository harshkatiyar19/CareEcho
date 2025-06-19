package com.example.CareEcho.DTO.send;

import com.example.CareEcho.DTO.recieved.Symbol;

import java.util.Map;

public record SymbolData2(
        Symbol symbol,
        Map<Float, Integer> sell,
        Map<Float, Integer> buy
) {}
