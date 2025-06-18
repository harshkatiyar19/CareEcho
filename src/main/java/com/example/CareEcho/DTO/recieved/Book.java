package com.example.CareEcho.DTO.recieved;

import java.util.Date;

public record Book(
        int orderId,
        Symbol symbol,
        Side side,
        float price,
        int qty,
        int filledQty,
        int remainingQty,
        OrderType orderType,
        OrderStatus orderStatus,
        OrderValidity orderValidity,
        Date timeStamp,
        String exchange
        //int userId,

) {}
