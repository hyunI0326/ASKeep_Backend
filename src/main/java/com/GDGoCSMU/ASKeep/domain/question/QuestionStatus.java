package com.GDGoCSMU.ASKeep.domain.question;

/** 발표자 처리 상태. AI 처리 상태(AiStatus)와는 따로 관리한다. */
public enum QuestionStatus {
    OPEN,     // 발표자가 아직 처리하지 않음
    ANSWERED  // 발표자가 답변 완료로 표시함 (말로 답한 경우 포함)
}