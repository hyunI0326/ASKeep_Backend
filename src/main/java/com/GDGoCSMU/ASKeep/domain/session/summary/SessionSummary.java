package com.GDGoCSMU.ASKeep.domain.session.summary;

import com.GDGoCSMU.ASKeep.domain.common.BaseEntity;
import com.GDGoCSMU.ASKeep.domain.session.entity.Session;
import com.GDGoCSMU.ASKeep.global.exception.BusinessException;
import com.GDGoCSMU.ASKeep.global.exception.ErrorCode;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 세션 종료 후 AI가 만든 요약 + 태그 (세션당 1개).
 * embedding(지식베이스 검색용, P2)은 아직 없음 — pgvector 컬럼이라 P2 때 AI 서버와 함께 추가.
 */
@Entity
@Table(name = "session_summaries")
public class SessionSummary extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false, unique = true)
    private Session session;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SummaryStatus status;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Convert(converter = TagListConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<String> tags = new ArrayList<>();

    protected SessionSummary() {
    }

    public SessionSummary(Session session) {
        this.session = session;
        this.status = SummaryStatus.PENDING;
    }

    /** PENDING일 때만 처리 시작. 이미 처리 중이거나 끝났으면 false (중복 실행 방지) */
    public boolean startProcessing() {
        if (status != SummaryStatus.PENDING) return false;
        this.status = SummaryStatus.PROCESSING;
        return true;
    }

    public void complete(String summary, List<String> tags) {
        this.summary = summary;
        this.tags = new ArrayList<>(tags);
        this.status = SummaryStatus.COMPLETED;
    }

    public void fail() {
        this.status = SummaryStatus.FAILED;
    }

    public void retry() {
        if (status != SummaryStatus.FAILED) {
            throw new BusinessException(ErrorCode.SUMMARY_RETRY_NOT_ALLOWED);
        }
        this.status = SummaryStatus.PENDING;
    }

    public Long getId() { return id; }
    public Session getSession() { return session; }
    public SummaryStatus getStatus() { return status; }
    public String getSummary() { return summary; }
    public List<String> getTags() { return tags; }
}
