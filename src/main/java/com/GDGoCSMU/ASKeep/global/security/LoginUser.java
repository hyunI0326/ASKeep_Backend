package com.GDGoCSMU.ASKeep.global.security;

/**
 * JWT에서 꺼낸 로그인 사용자 정보.
 * 컨트롤러에서 @AuthenticationPrincipal LoginUser loginUser 로 받아서 사용한다.
 * (다른 팀원도 질문/답변 작성자 ID가 필요할 때 이걸 쓰면 된다)
 */
public record LoginUser(Long userId, String role) {}
