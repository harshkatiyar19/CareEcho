package com.example.CareEcho.WebSocketEndpoints;

import com.example.CareEcho.DTO.recieved.Book;
import com.example.CareEcho.DTO.recieved.OrderStatus;
import com.example.CareEcho.DTO.recieved.Side;
import com.example.CareEcho.DTO.recieved.Symbol;
import com.example.CareEcho.DTO.send.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.lang.NonNull;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.CompletableFuture; // Import CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Service
public class ProjectConnector {

    private static final int MAX_BOOKS_BUFFER = 250;
    private final List<Book> receivedBooks = Collections.synchronizedList(new ArrayList<>());
    private final List<StompSession> activeSessions = new CopyOnWriteArrayList<>(); // To manage active sessions

    // Use a scheduled executor for reconnection attempts
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2); // One for each connection


    public void sendToExchange(Book book) {
        for (StompSession session : activeSessions) {
            if (session.isConnected()) {
                String destination = null;
                try {
                    switch (book.exchange()) {
                        case "NYSE" -> destination = "/app/nyse/sendOrder";
                        case "NASDAQ" -> destination = "/app/nasdaq/sendOrder";
                    }
                    assert destination != null;
                    session.send(destination, book); // Project A should map this
                    System.out.println("Forwarded book to " + destination + " : " + book);
                } catch (Exception e) {
                    System.err.println("Failed to send to " + destination + " : " + e.getMessage());
                }
            }
        }
    }
    public OrderData allOrders() {
        List<SymbolData2> symbolDataList = receivedBooks.stream()
                .filter(book -> book.orderStatus() != OrderStatus.Filled) // Only active orders
                .collect(Collectors.groupingBy(Book::symbol))
                .entrySet()
                .stream()
                .map(entry -> {
                    Symbol symbol = entry.getKey();
                    List<Book> books = entry.getValue();

                    // Aggregate BUY orders using remainingQty for accurate depth
                    Map<Float, Integer> buyMap = books.stream()
                            .filter(book -> book.side() == Side.BUY && book.remainingQty() > 0)
                            .collect(Collectors.groupingBy(
                                    Book::price,
                                    Collectors.summingInt(Book::remainingQty) // Use remainingQty
                            ));

                    // Aggregate SELL orders using remainingQty for accurate depth
                    Map<Float, Integer> sellMap = books.stream()
                            .filter(book -> book.side() == Side.SELL && book.remainingQty() > 0)
                            .collect(Collectors.groupingBy(
                                    Book::price,
                                    Collectors.summingInt(Book::remainingQty) // Use remainingQty
                            ));

                    // Optional: Sort and limit to top levels for better performance
                    // You can uncomment this if you want to limit the depth levels sent to frontend

                    // Map<Float, Integer> topBuys = buyMap.entrySet().stream()
                    //         .sorted(Map.Entry.<Float, Integer>comparingByKey().reversed()) // Highest price first
                    //         .limit(20) // Top 20 price levels
                    //         .collect(Collectors.toMap(
                    //                 Map.Entry::getKey,
                    //                 Map.Entry::getValue,
                    //                 (e1, e2) -> e1,
                    //                 LinkedHashMap::new
                    //         ));

                    // Map<Float, Integer> topSells = sellMap.entrySet().stream()
                    //         .sorted(Map.Entry.comparingByKey()) // Lowest price first
                    //         .limit(20) // Top 20 price levels
                    //         .collect(Collectors.toMap(
                    //                 Map.Entry::getKey,
                    //                 Map.Entry::getValue,
                    //                 (e1, e2) -> e1,
                    //                 LinkedHashMap::new
                    //         ));

                    return new SymbolData2(symbol, sellMap, buyMap);
                    // return new SymbolData2(symbol, topSells, topBuys); // Use this if limiting levels
                })
                .toList();

        return new OrderData(symbolDataList);
    }

    // Optional: Add a method to get cumulative depth (for better visualization)
    public OrderData allOrdersWithCumulativeDepth() {
        float tickSize = 0.5f;

        List<SymbolData2> symbolDataList = receivedBooks.stream()
                .filter(book -> book.orderStatus() != OrderStatus.Filled)
                .collect(Collectors.groupingBy(Book::symbol))
                .entrySet()
                .stream()
                .map(entry -> {
                    Symbol symbol = entry.getKey();
                    List<Book> books = entry.getValue();

                    // === BUY Orders ===
                    TreeMap<Float, Integer> buyMap = books.stream()
                            .filter(book -> book.side() == Side.BUY && book.remainingQty() > 0)
                            .collect(Collectors.groupingBy(
                                    book -> roundToTick(book.price(), tickSize),
                                    TreeMap::new,
                                    Collectors.summingInt(Book::remainingQty)
                            ));

                    Map<Float, Integer> cumulativeBuyMap = new LinkedHashMap<>();
                    AtomicInteger buyDepth = new AtomicInteger(0);
                    buyMap.descendingMap().forEach((price, qty) -> {
                        buyDepth.addAndGet(qty);
                        cumulativeBuyMap.put(price, buyDepth.get());
                    });

                    // === SELL Orders ===
                    TreeMap<Float, Integer> sellMap = books.stream()
                            .filter(book -> book.side() == Side.SELL && book.remainingQty() > 0)
                            .collect(Collectors.groupingBy(
                                    book -> roundToTick(book.price(), tickSize),
                                    TreeMap::new,
                                    Collectors.summingInt(Book::remainingQty)
                            ));

                    Map<Float, Integer> cumulativeSellMap = new LinkedHashMap<>();
                    AtomicInteger sellDepth = new AtomicInteger(0);
                    sellMap.forEach((price, qty) -> {
                        sellDepth.addAndGet(qty);
                        cumulativeSellMap.put(price, sellDepth.get());
                    });

//                    if (!cumulativeBuyMap.isEmpty() && !cumulativeSellMap.isEmpty()) {
//                        Float highestBid = cumulativeBuyMap.keySet().iterator().next();
//                        Float lowestAsk = cumulativeSellMap.keySet().iterator().next();
////                        System.out.printf("Symbol: %s | Best Bid: %.2f | Best Ask: %.2f%n", symbol, highestBid, lowestAsk);
//                    }

                    return new SymbolData2(symbol, cumulativeSellMap, cumulativeBuyMap);
                })
                .toList();

        return new OrderData(symbolDataList);
    }

    private float roundToTick(float price, float tickSize) {
        return Math.round(price / tickSize) * tickSize;
    }


    /// -----///

//    public OrderData allOrders() {
//        List<SymbolData2> symbolDataList = receivedBooks.stream()
//                .collect(Collectors.groupingBy(Book::symbol))
//                .entrySet()
//                .stream()
//                .map(entry -> {
//                    Symbol symbol = entry.getKey();
//                    List<Book> books = entry.getValue();
//
//                    // Aggregate BUY orders
//                    Map<Float, Integer> buyMap = books.stream()
//                            .filter(book -> book.side() == Side.BUY)
//                            .collect(Collectors.groupingBy(
//                                    Book::price,
//                                    Collectors.summingInt(Book::qty)
//                            ));
//
////                    List<OrderEntry> top5Buys = buyMap.entrySet().stream()
////                            .map(e -> new OrderEntry(e.getKey(), e.getValue()))
////                            .sorted(Comparator.comparing(OrderEntry::price).reversed())
////                            .limit(5)
////                            .toList();
//
//                    // Aggregate SELL orders
//                    Map<Float, Integer> sellMap = books.stream()
//                            .filter(book -> book.side() == Side.SELL)
//                            .collect(Collectors.groupingBy(
//                                    Book::price,
//                                    Collectors.summingInt(Book::qty)
//                            ));
//
////                    List<OrderEntry> top5Sells = sellMap.entrySet().stream()
////                            .map(e -> new OrderEntry(e.getKey(), e.getValue()))
////                            .sorted(Comparator.comparing(OrderEntry::price))
////                            .limit(5)
////                            .toList();
//
//                    return new SymbolData2(symbol, sellMap, buyMap);
//                })
//                .toList();
//
//        return new OrderData(symbolDataList);
//    }

/// -----///
    public CombinedBook getTop5GroupedBooks() {
        List<SymbolData> symbolDataList = receivedBooks.stream()
                .collect(Collectors.groupingBy(Book::symbol))
                .entrySet()
                .stream()
                .map(entry -> {
                    Symbol symbol = entry.getKey();
                    List<Book> books = entry.getValue();

                    // Aggregate BUY orders
                    Map<Float, Integer> buyMap = books.stream()
                            .filter(book -> book.side() == Side.BUY)
                            .collect(Collectors.groupingBy(
                                    Book::price,
                                    Collectors.summingInt(Book::qty)
                            ));

                    List<OrderEntry> top5Buys = buyMap.entrySet().stream()
                            .map(e -> new OrderEntry(e.getKey(), e.getValue()))
                            .sorted(Comparator.comparing(OrderEntry::price).reversed())
                            .limit(5)
                            .toList();

                    // Aggregate SELL orders
                    Map<Float, Integer> sellMap = books.stream()
                            .filter(book -> book.side() == Side.SELL)
                            .collect(Collectors.groupingBy(
                                    Book::price,
                                    Collectors.summingInt(Book::qty)
                            ));

                    List<OrderEntry> top5Sells = sellMap.entrySet().stream()
                            .map(e -> new OrderEntry(e.getKey(), e.getValue()))
                            .sorted(Comparator.comparing(OrderEntry::price))
                            .limit(5)
                            .toList();

                    return new SymbolData(symbol, top5Sells, top5Buys);
                })
                .toList();

        return new CombinedBook(symbolDataList);
    }

    @PostConstruct
    public void init() {
        // Attempt to connect immediately on startup
        tryConnect("wss://exchange-nasdaq.onrender.com/ws/nasdaq"); // nasdaq
        tryConnect("wss://exchange-nyse.onrender.com/ws/nyse"); // nyse
    }

    @PreDestroy
    public void cleanup() {
        // Disconnect all active sessions when the bean is destroyed (application shuts down)
        activeSessions.forEach(session -> {
            if (session.isConnected()) {
                session.disconnect();
                System.out.println("Disconnected STOMP session: " + session.getSessionId());
            }
        });
        // Shut down the scheduler cleanly
        scheduler.shutdownNow(); // Attempt to stop all actively executing tasks
        System.out.println("ProjectConnector shutdown complete.");
    }

    private void tryConnect(String url) {
        WebSocketStompClient stompClient = createStompClient();

        System.out.println("Attempting to connect to: " + url);

        CompletableFuture<StompSession> connectFuture = stompClient.connectAsync(url, new StompSessionHandler(url));

        connectFuture.handle((session, ex) -> {
            if (ex != null) {
                System.err.println("Failed to connect to " + url + ": " + ex.getMessage());
                // Schedule a reconnect attempt
                scheduler.schedule(() -> tryConnect(url), 5, TimeUnit.SECONDS); // Retry after 5 seconds
            } else if (session != null) {
                System.out.println("Successfully connected to " + url);
                activeSessions.add(session);
            }
            return session; // Return the session (even if null)
        });
    }

    private WebSocketStompClient createStompClient() {
        StandardWebSocketClient standardWebSocketClient = new StandardWebSocketClient();
        SockJsClient sockJsClient = new SockJsClient(
                List.of(new WebSocketTransport(standardWebSocketClient))
        );
        WebSocketStompClient stompClient = new WebSocketStompClient(sockJsClient);
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
        return stompClient;
    }

    private class StompSessionHandler extends StompSessionHandlerAdapter {
        private final String url;
        private final AtomicReference<StompSession> currentSession = new AtomicReference<>();

        public StompSessionHandler(String url) {
            this.url = url;
        }

        @Override
        public void afterConnected(@NonNull StompSession session,@NonNull  StompHeaders connectedHeaders) {
            currentSession.set(session); // Store the current session
            System.out.println("STOMP session established for " + url + ": " + session.getSessionId());

            session.subscribe("/topic/order", new BookFrameHandler(url));
        }

        private class BookFrameHandler implements StompFrameHandler {
            private final String source;

            public BookFrameHandler(String source) {
                this.source = source;
            }

            @Override
            public Type getPayloadType(@NonNull StompHeaders headers) {
                return Book.class;
            }

            @Override
            public void handleFrame(@NonNull StompHeaders headers, Object payload) {
                Book book = (Book) payload;
                synchronized (receivedBooks) {
                    receivedBooks.add(book);
                    if (receivedBooks.size() > MAX_BOOKS_BUFFER) {
                        receivedBooks.removeFirst();
                    }
                }
                System.out.println("Received from " + source + ": " + book);
            }
        }


        @Override
        public void handleException(@NonNull StompSession session, StompCommand command,@NonNull  StompHeaders headers, @NonNull byte[] payload, Throwable exception) {
            System.err.println("Error in STOMP session for " + url + ": " + exception.getMessage());
            // This method handles exceptions occurring within the STOMP session itself
            // e.g., issues during message processing. It does not mean connection loss.
        }

        @Override
        public void handleTransportError(@NonNull StompSession session, Throwable exception) {
            System.err.println("Transport error for " + url + ": " + exception.getMessage());
            if (session.isConnected()) {
                System.err.println("Session " + session.getSessionId() + " is still connected, but transport error occurred.");
            } else {
                System.err.println("Connection lost for " + url + ". Attempting to reconnect...");
                // Remove the disconnected session from active sessions
                activeSessions.remove(currentSession.get());
                // Schedule a reconnect attempt
                scheduler.schedule(() -> tryConnect(url), 5, TimeUnit.SECONDS);
            }
        }

        @Override
        public void handleFrame(@NonNull StompHeaders headers, Object payload) {
            // This method is primarily for handling UNKNOWN frame types or for general frame debugging.
            // For subscribed messages, handleFrame in StompFrameHandler is preferred.
        }
    }
}