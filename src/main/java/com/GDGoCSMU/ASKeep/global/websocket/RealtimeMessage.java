package com.GDGoCSMU.ASKeep.global.websocket;

import java.time.LocalDateTime;

/**
 * 웹소켓으로 실제 전송되는 메시지 형식 (실시간 알림 명세 2장).
 * REST의 { success, data } 형식은 쓰지 않는다.
 */
public record RealtimeMessage(
        String eventId,
        String type,
        Long sessionId,
        LocalDateTime occurredAt,
        Object data
) {}
