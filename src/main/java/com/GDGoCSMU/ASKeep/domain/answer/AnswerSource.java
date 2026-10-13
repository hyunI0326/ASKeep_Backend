package com.GDGoCSMU.ASKeep.domain.answer;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** AI 답변이 참고한 자료 위치 (자료 + 페이지). 답변 하나에 여러 개가 붙는다. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "answer_sources")
public class AnswerSource {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "answer_id", nullable = false)
    private Answer answer;

    @Column(nullable = false)
    private Long materialId;

    // 자료가 나중에 삭제돼도 출처 이름은 남도록 저장 시점의 파일 이름을 같이 저장한다
    private String fileName;

    private Integer pageNumber;

    // 질문과 자료 내용이 얼마나 비슷한지 (0~1, 클수록 관련 높음)
    private Double similarity;

    AnswerSource(Answer answer, Long materialId, String fileName, Integer pageNumber, Double similarity) {
        this.answer = answer;
        this.materialId = materialId;
        this.fileName = fileName;
        this.pageNumber = pageNumber;
        this.similarity = similarity;
    }
}