package com.GDGoCSMU.ASKeep.global.websocket;

import com.GDGoCSMU.ASKeep.domain.session.SessionAccessService;
import com.GDGoCSMU.ASKeep.global.security.JwtProvider;
import com.GDGoCSMU.ASKeep.global.security.LoginUser;
import com.GDGoCSMU.ASKeep.global.security.TokenBlacklist;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 웹소켓 "옆문 경비원".
 * REST 요청은 JwtAuthenticationFilter가 검사하지만, 웹소켓 안의 STOMP 메시지는 그 필터를 거치지 않아서 여기서 검사한다.
 *
 * - CONNECT  : Authorization: Bearer {토큰} 헤더 필수 (서명·만료·로그아웃 여부 확인)
 * - SUBSCRIBE: /topic/sessions/{sessionId} 만 허용, 해당 세션 host 또는 참여자만
 * - SEND     : 받기 전용이라 막음
 * 실패하면 예외 → 클라이언트에 STOMP ERROR 프레임이 가고 연결이 끊긴다.
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String BEARER = "Bearer ";
    private static final Pattern SESSION_TOPIC = Pattern.compile("^" + WebSocketConfig.SESSION_TOPIC_PREFIX + "(\\d+)$");

    private final JwtProvider jwtProvider;
    private final TokenBlacklist tokenBlacklist;
    private final SessionAccessService sessionAccess;

    public StompAuthChannelInterceptor(JwtProvider jwtProvider, TokenBlacklist tokenBlacklist,
                                       SessionAccessService sessionAccess) {
        this.jwtProvider = jwtProvider;
        this.tokenBlacklist = tokenBlacklist;
        this.sessionAccess = sessionAccess;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;  // heart-beat 등
        }
        StompCommand command = accessor.getCommand();
        if (command == StompCommand.CONNECT) {
            accessor.setUser(authenticate(accessor.getFirstNativeHeader("Authorization")));
        } else if (command == StompCommand.SUBSCRIBE) {
            checkSubscribe(accessor);
        } else if (command == StompCommand.SEND) {
            throw new MessageDeliveryException("웹소켓은 받기 전용입니다. 요청은 REST API를 사용하세요.");
        }
        return message;
    }

    private UsernamePasswordAuthenticationToken authenticate(String header) {
        if (header == null || !header.startsWith(BEARER)) {
            throw new MessageDeliveryException("로그인이 필요합니다.");
        }
        String token = header.substring(BEARER.length()).trim();
        if (tokenBlacklist.contains(token)) {
            throw new MessageDeliveryException("유효하지 않은 토큰입니다.");
        }
        try {
            Claims claims = jwtProvider.parse(token);
            LoginUser user = new LoginUser(Long.valueOf(claims.getSubject()), claims.get("role", String.class));
            return new UsernamePasswordAuthenticationToken(user, null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + user.role())));
        } catch (JwtException | IllegalArgumentException e) {
            throw new MessageDeliveryException("유효하지 않은 토큰입니다.");
        }
    }

    private void checkSubscribe(StompHeaderAccessor accessor) {
        LoginUser user = loginUser(accessor.getUser());
        String destination = accessor.getDestination();
        Matcher matcher = destination == null ? null : SESSION_TOPIC.matcher(destination);
        if (matcher == null || !matcher.matches()) {
            throw new MessageDeliveryException("구독할 수 없는 주소입니다: " + destination);
        }
        // 그 외 예외는 "Failed to send message..."로 감싸져 이유가 안 보이므로, 클라이언트가 읽을 수 있는 메시지로 바꾼다
        try {
            sessionAccess.requireMember(Long.valueOf(matcher.group(1)), user.userId());
        } catch (AccessDeniedException | EntityNotFoundException e) {
            throw new MessageDeliveryException(e.getMessage());
        }
    }

    private LoginUser loginUser(Principal principal) {
        if (principal instanceof UsernamePasswordAuthenticationToken auth && auth.getPrincipal() instanceof LoginUser user) {
            return user;
        }
        throw new MessageDeliveryException("로그인이 필요합니다.");
    }
}
