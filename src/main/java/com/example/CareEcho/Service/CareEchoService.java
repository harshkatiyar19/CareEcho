package com.example.CareEcho.Service;

import com.example.CareEcho.DTO.send.CombinedBook;
import com.example.CareEcho.DTO.send.OrderData;
import com.example.CareEcho.WebSocketEndpoints.ProjectConnector;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class CareEchoService {

    @Autowired
    private SimpMessagingTemplate template;
    @Autowired
    ProjectConnector projectConnector;

    @Scheduled(fixedDelay = 1000)
    public void periodicallySendTop5() {
        CombinedBook current = projectConnector.getTop5GroupedBooks();
//        System.out.println(current);
        template.convertAndSend("/topic/top5", current);
//        simpMessagingTemplate.convertAndSend("/topic/book", combinedBookObject);

    }

    @Scheduled(fixedDelay = 200)
    public void orders() {
//        OrderData order = projectConnector.allOrders();
        OrderData order = projectConnector.allOrdersWithCumulativeDepth();
//        System.out.println(current);
        template.convertAndSend("/topic/orders", order);
//        simpMessagingTemplate.convertAndSend("/topic/book", combinedBookObject);

    }
}
