package com.example.CareEcho.DTO.send;

import com.example.CareEcho.DTO.recieved.Symbol;

import java.util.List;
import java.util.Map;

public record CombinedBook(
       List<SymbolData> data
) {
}