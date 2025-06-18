package com.example.CareEcho.WebSocketEndpoints;

import com.example.CareEcho.DTO.recieved.Book;
import com.example.CareEcho.DTO.recieved.Side;
import com.example.CareEcho.DTO.recieved.Symbol;
import com.example.CareEcho.DTO.send.CombinedBook;
import com.example.CareEcho.DTO.send.OrderEntry;
import com.example.CareEcho.DTO.send.SymbolData;
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Service
public class ProjectConnector {

    private static final int MAX_BOOKS_BUFFER = 1000;
    private final List<Book> receivedBooks = Collections.synchronizedList(new ArrayList<>());
    private final List<StompSession> activeSessions = new CopyOnWriteArrayList<>(); // To manage active sessions

    // Use a scheduled executor for reconnection attempts
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2); // One for each connection

    public CombinedBook getTop5GroupedBooks() {
        Map<Symbol, SymbolData> result = receivedBooks.stream()
                .collect(Collectors.groupingBy(
                        Book::symbol,
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                (List<Book> books) -> {  //  Fix: explicitly declare type
                                    // Group and aggregate BUY side
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

                                    // Group and aggregate SELL side
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

                                    return new SymbolData(top5Sells, top5Buys);
                                }
                        )
                ));
        return new CombinedBook(result);
    }





    @PostConstruct
    public void init() {
        // Attempt to connect immediately on startup
        tryConnect("ws://localhost:8080/ws/book"); // nasdaq
        tryConnect("ws://localhost:8081/ws/book"); // nyse
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
        // *** CHANGE HERE: Use connectAsync() instead of connect() ***
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
        public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
            currentSession.set(session); // Store the current session
            System.out.println("STOMP session established for " + url + ": " + session.getSessionId());

            session.subscribe("/topic/book", new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) {
                    return Book.class;
                }

                @Override
                public void handleFrame(@NonNull StompHeaders headers, Object payload) {
                    Book book = (Book) payload;

                    synchronized (receivedBooks) {
                        receivedBooks.add(book);
                        if (receivedBooks.size() > MAX_BOOKS_BUFFER) {
                            // Remove the oldest element to keep size in check (sliding window)
                            receivedBooks.removeFirst();
                        }
                    }

                    System.out.println("Received from " + url + ": " + book);
                }
            });
        }

        @Override
        public void handleException(StompSession session, StompCommand command, StompHeaders headers, byte[] payload, Throwable exception) {
            System.err.println("Error in STOMP session for " + url + ": " + exception.getMessage());
            // This method handles exceptions occurring within the STOMP session itself
            // e.g., issues during message processing. It does not mean connection loss.
        }

        @Override
        public void handleTransportError(StompSession session, Throwable exception) {
            System.err.println("Transport error for " + url + ": " + exception.getMessage());
            if (session != null && session.isConnected()) {
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
        public void handleFrame(StompHeaders headers, Object payload) {
            // This method is primarily for handling UNKNOWN frame types or for general frame debugging.
            // For subscribed messages, handleFrame in StompFrameHandler is preferred.
        }
    }
}