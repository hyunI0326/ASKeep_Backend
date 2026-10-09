package com.GDGoCSMU.ASKeep.domain.answer;

import com.GDGoCSMU.ASKeep.domain.common.BaseEntity;
import com.GDGoCSMU.ASKeep.domain.question.Question;
import com.GDGoCSMU.ASKeep.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

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
    @JoinColumn(name = "author_id")
    private User author;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    // AI 답변의 출처. 관련도 높은 순으로 정렬. 발표자 답변은 항상 비어 있다
    @OneToMany(mappedBy = "answer", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("similarity DESC")
    private List<AnswerSource> sources = new ArrayList<>();

    @Builder
    public Answer(String content, AnswerType type, Question question, User author) {
        this.content = content;
        this.type = type;
        this.question = question;
        this.author = author;
    }

    public void addSource(Long materialId, String fileName, Integer pageNumber, Double similarity) {
        sources.add(new AnswerSource(this, materialId, fileName, pageNumber, similarity));
    }
}