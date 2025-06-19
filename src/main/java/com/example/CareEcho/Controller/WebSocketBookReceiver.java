package com.example.CareEcho.Controller;

import com.example.CareEcho.DTO.recieved.Book;
import com.example.CareEcho.WebSocketEndpoints.ProjectConnector;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
public class WebSocketBookReceiver {
    @Autowired
    private SimpMessagingTemplate template;
    @Autowired
    private ProjectConnector projectConnector;

    @MessageMapping("/order") // Listens to /app/order
    public void handleBookFromFrontend(Book book) {
        //  Your logic to process incoming book
        System.out.println("Received book from frontend: " + book);
        template.convertAndSend("/topic/order", book);
        projectConnector.sendToExchange(book);
    }
}
