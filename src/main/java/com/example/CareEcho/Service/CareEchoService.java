package com.example.CareEcho.Service;

import com.example.CareEcho.DTO.send.CombinedBook;
import com.example.CareEcho.WebSocketEndpoints.ProjectConnector;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Random;

@Service
public class CareEchoService {

    @Autowired
    private SimpMessagingTemplate template;
    @Autowired
    ProjectConnector projectConnector;

    private final Random random = new Random();
    @Scheduled(fixedDelay = 1000)
    public void periodicallySendTop5() {
        CombinedBook current = projectConnector.getTop5GroupedBooks();
        System.out.println(current);
        template.convertAndSend("/topic/top5", current);
//        simpMessagingTemplate.convertAndSend("/topic/book", combinedBookObject);

    }
}
