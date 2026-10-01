package com.GDGoCSMU.ASKeep.domain.session.summary;

import com.GDGoCSMU.ASKeep.domain.material.client.AiClientServer;
import com.GDGoCSMU.ASKeep.domain.session.summary.dto.AiSummaryRequest;
import com.GDGoCSMU.ASKeep.domain.session.summary.dto.AiSummaryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.concurrent.CompletableFuture;

/**
 * 세션 종료(또는 재시도)가 커밋된 뒤 AI 서버에 요약을 요청한다.
 * 요청 응답을 기다리지 않도록 비동기로 처리 (질문 AI 답변과 같은 방식).
 */
@Component
public class SessionSummaryProcessor {

    private static final Logger log = LoggerFactory.getLogger(SessionSummaryProcessor.class);

    private final SessionSummaryService summaryService;
    private final AiClientServer aiClient;

    public SessionSummaryProcessor(SessionSummaryService summaryService, AiClientServer aiClient) {
        this.summaryService = summaryService;
        this.aiClient = aiClient;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSummaryRequested(SummaryRequestedEvent event) {
        CompletableFuture.runAsync(() -> process(event.sessionId()));
    }

    void process(Long sessionId) {
        try {
            AiSummaryRequest request = summaryService.startProcessing(sessionId);
            if (request == null) return;
            AiSummaryResponse response = aiClient.summarize(request);
            if (response == null || response.summary() == null || response.summary().isBlank()) {
                throw new IllegalStateException("AI 요약이 비어 있습니다.");
            }
            summaryService.complete(sessionId, response.summary(), response.tags());
        } catch (Exception e) {
            log.warn("세션 {} 요약 실패: {}", sessionId, e.getMessage());
            summaryService.fail(sessionId);
        }
    }
}
