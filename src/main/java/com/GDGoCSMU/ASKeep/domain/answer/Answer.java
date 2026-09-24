package com.GDGoCSMU.ASKeep.domain.answer;


import com.GDGoCSMU.ASKeep.domain.common.BaseEntity;
import com.GDGoCSMU.ASKeep.domain.question.Question;
import com.GDGoCSMU.ASKeep.domain.user.domain.User;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "answers")
public class Answer extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AnswerType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Builder
    public Answer(String content, AnswerType type, Question question, User user
    ) {
        this.content = content;
        this.type = type;
        this.question = question;
        this.user = user;
    }
}
