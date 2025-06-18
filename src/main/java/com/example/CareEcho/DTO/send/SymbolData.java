package com.example.CareEcho.DTO.send;

import java.util.List;

public record SymbolData(
        List<OrderEntry> sell,
        List<OrderEntry> buy
) {}
