package com.GDGoCSMU.ASKeep.domain.session.entity;

public enum SessionStatus {
    READY,    // 생성됨 (시작 전)
    ONGOING,  // 진행중
    ACTIVE,   // 기존 데이터의 진행중 상태
    ENDED     // 종료
}
