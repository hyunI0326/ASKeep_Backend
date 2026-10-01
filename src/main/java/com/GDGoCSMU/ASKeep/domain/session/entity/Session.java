package com.GDGoCSMU.ASKeep.domain.session.entity;

import com.GDGoCSMU.ASKeep.domain.user.entity.User;
import com.GDGoCSMU.ASKeep.global.exception.BusinessException;
import com.GDGoCSMU.ASKeep.global.exception.ErrorCode;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 발표/스터디 세션 (로그인 세션이 아님) */
@Entity
@Table(name = "sessions")
public class Session {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "host_id", nullable = false)
    private User presenter;

    @Column(name = "entry_code", unique = true, length = 6)
    private String entryCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SessionStatus status;

    /** 시작/종료 전에는 값이 없으므로 nullable */
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;

    @CreationTimestamp
    @Column(name = "create_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "update_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<com.GDGoCSMU.ASKeep.domain.session.SessionParticipant> participants = new ArrayList<>();

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<com.GDGoCSMU.ASKeep.domain.material.Material> materials = new ArrayList<>();

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<com.GDGoCSMU.ASKeep.domain.question.Question> questions = new ArrayList<>();

    protected Session() {
    }

    public Session(String title, String description, User presenter, String entryCode) {
        this.title = title;
        this.description = description;
        this.presenter = presenter;
        this.entryCode = entryCode;
        this.status = SessionStatus.READY;
    }

    public boolean isPresenter(Long userId) {
        return presenter.getId().equals(userId);
    }

    /** 진행 중인 세션인지 (ACTIVE는 기존 데이터의 진행중 상태) */
    public boolean isLive() {
        return status == SessionStatus.ONGOING || status == SessionStatus.ACTIVE;
    }

    public void changeTitle(String title) {
        if (status == SessionStatus.ENDED) {
            throw new BusinessException(ErrorCode.SESSION_ALREADY_ENDED);
        }
        this.title = title;
    }

    public void changeDescription(String description) {
        if (status == SessionStatus.ENDED) {
            throw new BusinessException(ErrorCode.SESSION_ALREADY_ENDED);
        }
        this.description = description;
    }

    public void start() {
        if (status != SessionStatus.READY) {
            throw new BusinessException(ErrorCode.SESSION_ALREADY_STARTED);
        }
        this.status = SessionStatus.ONGOING;
        this.startedAt = LocalDateTime.now();
    }

    public void end() {
        if (!isLive()) {
            throw new BusinessException(ErrorCode.SESSION_NOT_IN_PROGRESS);
        }
        this.status = SessionStatus.ENDED;
        this.endedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public User getPresenter() { return presenter; }
    public String getEntryCode() { return entryCode; }
    public SessionStatus getStatus() { return status; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getEndedAt() { return endedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
