package com.GDGoCSMU.ASKeep.global.websocket;

/**
 * 세션 구독자(/topic/sessions/{sessionId})에게 보낼 알림.
 *
 * 서비스에서 ApplicationEventPublisher로 발행만 하면 된다:
 *   eventPublisher.publishEvent(new SessionTopicEvent(sessionId, RealtimeEventType.QUESTION_CREATED, questionDto));
 *
 * 트랜잭션 안에서 발행하면 DB 커밋 후에 발송되고(롤백되면 발송 안 됨),
 * 트랜잭션 밖(비동기 AI 처리 등)에서 발행하면 바로 발송된다. → RealtimeBroadcaster
 */
public record SessionTopicEvent(Long sessionId, RealtimeEventType type, Object data) {}
