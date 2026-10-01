package com.GDGoCSMU.ASKeep.global.websocket;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.util.UUID;

/** SessionTopicEvent를 받아 /topic/sessions/{sessionId} 구독자 전원에게 보낸다. */
@Component
public class RealtimeBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    public RealtimeBroadcaster(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    // AFTER_COMMIT: 저장이 확정된 뒤에만 알림을 보낸다
    // fallbackExecution: 트랜잭션 밖에서 발행된 이벤트도 바로 보낸다
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void broadcast(SessionTopicEvent event) {
        RealtimeMessage message = new RealtimeMessage(UUID.randomUUID().toString(), event.type().name(),
                event.sessionId(), LocalDateTime.now(), event.data());
        messagingTemplate.convertAndSend(WebSocketConfig.SESSION_TOPIC_PREFIX + event.sessionId(), message);
    }
}
