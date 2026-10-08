package com.GDGoCSMU.ASKeep.domain.session.entity;

/** 세션 요약 AI 처리 상태 */
public enum SummaryStatus {
    PENDING,     // 요청 대기
    PROCESSING,  // AI 생성 중
    COMPLETED,   // 저장 완료
    FAILED       // 실패 (발표자가 재시도 가능)
}
