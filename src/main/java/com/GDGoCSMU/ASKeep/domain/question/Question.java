package com.GDGoCSMU.ASKeep.domain.question;

import com.GDGoCSMU.ASKeep.domain.common.BaseEntity;
import com.GDGoCSMU.ASKeep.domain.session.entity.Session;
import com.GDGoCSMU.ASKeep.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.DynamicUpdate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "questions")
@DynamicUpdate  // 저장할 때 바뀐 칸만 UPDATE (AI 처리와 답변 완료 처리가 겹쳐도 서로 덮어쓰지 않게)
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

    // 기존 질문 행에도 값이 채워지도록 DB 기본값을 함께 지정한다
    @Enumerated(EnumType.STRING)
    @ColumnDefault("'OPEN'")
    @Column(nullable = false)
    private QuestionStatus status = QuestionStatus.OPEN;

    private LocalDateTime answeredAt;

    @ColumnDefault("false")
    @Column(nullable = false)
    private boolean presenterRequested;

    private LocalDateTime presenterRequestedAt;

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<com.GDGoCSMU.ASKeep.domain.answer.Answer> answers = new ArrayList<>();

    @Builder
    public Question(String content, Session session, User user, boolean anonymous) {
        this.content = content;
        this.session = session;
        this.user = user;
        this.anonymous = anonymous;
        this.aiStatus = AiStatus.PENDING;
        this.status = QuestionStatus.OPEN;
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

    public void retryAiProcessing() {
        if (this.aiStatus != AiStatus.FAILED) {
            throw new IllegalStateException("실패한 질문만 재시도할 수 있습니다.");
        }
        this.aiStatus = AiStatus.PENDING;
    }

    /** 답변 완료로 표시. 이미 완료면 처음 완료 시각을 유지한다. */
    public void markAnswered() {
        if (this.status != QuestionStatus.ANSWERED) {
            this.status = QuestionStatus.ANSWERED;
            this.answeredAt = LocalDateTime.now();
        }
    }

    /** 답변 완료 취소 */
    public void reopen() {
        this.status = QuestionStatus.OPEN;
        this.answeredAt = null;
    }

        /**
     * 발표자에게 직접 묻기 요청.
     * - 이미 요청 중이면 처음 요청 시각을 유지한다.
     * - 답변 완료된 질문이면 완료를 풀고 새로 요청한다 (꼬리질문처럼 "더 묻고 싶어요" 용도).
     */
    public void requestPresenter() {
        boolean askAgain = this.status == QuestionStatus.ANSWERED;
        if (askAgain) reopen();
        if (askAgain || !this.presenterRequested) {
            this.presenterRequested = true;
            this.presenterRequestedAt = LocalDateTime.now();
        }
    }

    /** 발표자에게 직접 묻기 요청 취소 */
    public void cancelPresenterRequest() {
        this.presenterRequested = false;
        this.presenterRequestedAt = null;
    }
}