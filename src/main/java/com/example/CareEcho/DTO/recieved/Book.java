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


//public enum OrderStatus {
//    New,
//    Partially,
//    Filled,
//    Cancelled;}
//
//public enum OrderType {
//    Limit,Market,StopLoss,StopLimit,TrailingStop,FOK,IOC,AON;}
//public enum OrderValidity {
//    Day,
//    GTC,
//    IOC,
//    FOK,
//    GTD;}
//
//public enum Side {
//    BUY,SELL;}
//public enum Symbol {
//    AAPL,GOOGL,MSFT,AMZN,TSLA,META,NVDA;}