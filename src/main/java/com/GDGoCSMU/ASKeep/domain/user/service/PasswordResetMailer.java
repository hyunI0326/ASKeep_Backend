package com.GDGoCSMU.ASKeep.domain.user.service;

import com.GDGoCSMU.ASKeep.global.exception.BusinessException;
import com.GDGoCSMU.ASKeep.global.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

import java.util.Properties;

/**
 * 비밀번호 재설정 인증코드 메일 발송.
 *
 * MAIL_HOST가 비어 있으면(로컬 개발) 메일을 보내지 않고 서버 로그에 코드를 찍는다.
 * 스프링 기본 메일 자동설정(spring.mail.*)은 host가 빈 문자열이어도 켜져 버려서 쓰지 않고 직접 만든다.
 */
@Component
public class PasswordResetMailer {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetMailer.class);

    private final JavaMailSenderImpl sender;
    private final String from;

    public PasswordResetMailer(
            @Value("${askeep.mail.host:}") String host,
            @Value("${askeep.mail.port:587}") int port,
            @Value("${askeep.mail.username:}") String username,
            @Value("${askeep.mail.password:}") String password,
            @Value("${askeep.mail.from:}") String from) {
        if (host.isBlank()) {
            this.sender = null;
        } else {
            JavaMailSenderImpl impl = new JavaMailSenderImpl();
            impl.setHost(host);
            impl.setPort(port);
            impl.setUsername(username);
            impl.setPassword(password);
            impl.setDefaultEncoding("UTF-8");
            Properties props = impl.getJavaMailProperties();
            props.put("mail.smtp.auth", String.valueOf(!username.isBlank()));
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.connectiontimeout", "5000");
            props.put("mail.smtp.timeout", "5000");
            props.put("mail.smtp.writetimeout", "5000");
            this.sender = impl;
        }
        this.from = from.isBlank() ? username : from;
    }

    public void send(String email, String code, long validMinutes) {
        if (sender == null) {
            log.warn("[개발용] MAIL_HOST가 설정되지 않아 메일 대신 로그로 출력합니다. {} 비밀번호 재설정 코드: {}", email, code);
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        if (!from.isBlank()) message.setFrom(from);
        message.setTo(email);
        message.setSubject("[ASKeep] 비밀번호 재설정 인증코드");
        message.setText("""
                ASKeep 비밀번호 재설정 인증코드입니다.

                인증코드: %s

                이 코드는 %d분 동안 유효합니다.
                본인이 요청하지 않았다면 이 메일을 무시해 주세요.
                """.formatted(code, validMinutes));
        try {
            sender.send(message);
        } catch (MailException e) {
            log.error("비밀번호 재설정 메일 발송 실패", e);
            throw new BusinessException(ErrorCode.MAIL_SEND_FAILED);
        }
    }
}
