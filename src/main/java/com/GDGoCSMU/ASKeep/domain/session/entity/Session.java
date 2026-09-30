package com.GDGoCSMU.ASKeep.domain.session.entity;

import com.GDGoCSMU.ASKeep.domain.user.entity.User;
import com.GDGoCSMU.ASKeep.global.exception.BusinessException;
import com.GDGoCSMU.ASKeep.global.exception.ErrorCode;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/** 발표/스터디 세션 (로그인 세션이 아님) */
@Entity
@Table(name = "sessions")
public class Session {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String title;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "presenter_id", nullable = false)
    private User presenter;

    @Column(name = "entry_code", nullable = false, unique = true, length = 6)
    private String entryCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SessionStatus status;

    /** 시작/종료 전에는 값이 없으므로 nullable */
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected Session() {
    }

    public Session(String title, User presenter, String entryCode) {
        this.title = title;
        this.presenter = presenter;
        this.entryCode = entryCode;
        this.status = SessionStatus.READY;
    }

    public boolean isPresenter(Long userId) {
        return presenter.getId().equals(userId);
    }

    public void changeTitle(String title) {
        if (status == SessionStatus.ENDED) {
            throw new BusinessException(ErrorCode.SESSION_ALREADY_ENDED);
        }
        this.title = title;
    }

    public void start() {
        if (status != SessionStatus.READY) {
            throw new BusinessException(ErrorCode.SESSION_ALREADY_STARTED);
        }
        this.status = SessionStatus.ONGOING;
        this.startedAt = LocalDateTime.now();
    }

    public void end() {
        if (status != SessionStatus.ONGOING) {
            throw new BusinessException(ErrorCode.SESSION_NOT_IN_PROGRESS);
        }
        this.status = SessionStatus.ENDED;
        this.endedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public User getPresenter() { return presenter; }
    public String getEntryCode() { return entryCode; }
    public SessionStatus getStatus() { return status; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getEndedAt() { return endedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
