package com.GDGoCSMU.ASKeep.domain.question;

import com.GDGoCSMU.ASKeep.domain.common.BaseEntity;
import com.GDGoCSMU.ASKeep.domain.session.StudySession;
import com.GDGoCSMU.ASKeep.domain.user.domain.User;
import jakarta.persistence.*;
import lombok.*;

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
    private StudySession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)   // 변경
    private User user;

    @Column(name = "is_anonymous", nullable = false)   // 추가
    private boolean anonymous;

    @Builder
    public Question(String content, StudySession session, User user, boolean anonymous) {   // 변경
        this.content = content;
        this.session = session;
        this.user = user;
        this.anonymous = anonymous;   // 추가
        this.aiStatus = AiStatus.PENDING;
    }

    public void startAiProcessing() {
        this.aiStatus = AiStatus.PROCESSING;
    }

    public void completeAiProcessing() {
        this.aiStatus = AiStatus.COMPLETED;
    }

    public void failAiProcessing() {
        this.aiStatus = AiStatus.FAILED;
    }

    public void retryAiProcessing() {   // 추가
        if (this.aiStatus != AiStatus.FAILED) {
            throw new IllegalStateException("실패한 질문만 재시도할 수 있습니다.");
        }
        this.aiStatus = AiStatus.PENDING;
    }
}
