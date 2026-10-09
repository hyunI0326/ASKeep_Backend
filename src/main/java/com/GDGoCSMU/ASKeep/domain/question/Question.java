package com.GDGoCSMU.ASKeep.domain.question;

import com.GDGoCSMU.ASKeep.domain.common.BaseEntity;
import com.GDGoCSMU.ASKeep.domain.session.entity.Session;
import com.GDGoCSMU.ASKeep.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.time.LocalDateTime;
import org.hibernate.annotations.BatchSize;

@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "questions")
public class Question extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AiStatus aiStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private Session session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "is_anonymous", nullable = false)
    private boolean anonymous;

    private LocalDateTime answeredAt;
    private LocalDateTime presenterRequestedAt;

    @ManyToMany
    @JoinTable(name = "question_likes", joinColumns = @JoinColumn(name = "question_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id"),
            uniqueConstraints = @UniqueConstraint(columnNames = {"question_id", "user_id"}))
    @BatchSize(size = 100)
    private Set<User> likedBy = new HashSet<>();

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<com.GDGoCSMU.ASKeep.domain.answer.Answer> answers = new ArrayList<>();

    @Builder
    public Question(String content, Session session, User user, boolean anonymous) {
        this.content = content;
        this.session = session;
        this.user = user;
        this.anonymous = anonymous;
        this.aiStatus = AiStatus.PENDING;
    }

    public void startAiProcessing() {
        this.aiStatus = AiStatus.PROCESSING;
    }

    public boolean isAnswered() { return answeredAt != null; }
    public boolean isPresenterRequested() { return presenterRequestedAt != null; }

    public void markAnswered(boolean answered) {
        if (!answered) answeredAt = null;
        else if (answeredAt == null) answeredAt = LocalDateTime.now();
    }

    public void requestPresenter() {
        if (presenterRequestedAt == null) presenterRequestedAt = LocalDateTime.now();
    }

    public void completeAiProcessing() {
        this.aiStatus = AiStatus.COMPLETED;
    }

    public void failAiProcessing() {
        this.aiStatus = AiStatus.FAILED;
    }

    public void retryAiProcessing() {
        if (this.aiStatus != AiStatus.FAILED) {
            throw new IllegalStateException("실패한 질문만 재시도할 수 있습니다.");
        }
        this.aiStatus = AiStatus.PENDING;
    }
}
