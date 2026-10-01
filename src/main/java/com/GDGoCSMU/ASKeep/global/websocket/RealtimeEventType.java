package com.GDGoCSMU.ASKeep.global.websocket;

/** 실시간 알림 종류 (실시간 알림 명세 3장) */
public enum RealtimeEventType {
    QUESTION_CREATED,       // data: 질문 객체 (공개용)
    QUESTION_UPDATED,       // data: 질문 객체 (공개용, 답변 포함)
    QUESTION_DELETED,       // data: { questionId }
    VOTE_CHANGED,           // data: { questionId, voteCount, updatedAt }
    SESSION_STATUS_CHANGED  // data: { status }
}
