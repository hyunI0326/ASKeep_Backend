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
    @JoinColumn(name = "user")
    private User user;

    @Builder
    public Question(String content, StudySession session, User user) {
        this.content = content;
        this.session = session;
        this.user = user;
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
}
