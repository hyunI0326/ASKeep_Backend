package com.GDGoCSMU.ASKeep.domain.question;

import com.GDGoCSMU.ASKeep.domain.answer.AnswerRepository;
import com.GDGoCSMU.ASKeep.domain.common.CurrentUser;
import com.GDGoCSMU.ASKeep.domain.common.PageResponse;
import com.GDGoCSMU.ASKeep.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class QuestionController {
    private final QuestionService questionService;
    private final AnswerRepository answerRepository;

    @PostMapping("/sessions/{sessionId}/questions")
    @ResponseStatus(HttpStatus.ACCEPTED)
    ApiResponse<QuestionResponse> create(@PathVariable Long sessionId, @Valid @RequestBody QuestionRequests.Create request) {
        Long viewerId = CurrentUser.id();
        Question question = questionService.create(sessionId, viewerId, request.content(), request.isAnonymous());
        return ApiResponse.ok(QuestionResponse.forViewer(question, answerRepository, viewerId));
    }

    @GetMapping("/sessions/{sessionId}/questions")
    ApiResponse<?> list(@PathVariable Long sessionId, @RequestParam(required = false) Long afterId,
                @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        Long viewerId = CurrentUser.id();
        if (afterId != null) {
            if (afterId < 0) throw new IllegalArgumentException("afterId는 0 이상이어야 합니다.");
            List<QuestionResponse> items = questionService.after(sessionId, viewerId, afterId).stream()
                    .map(question -> QuestionResponse.forViewer(question, answerRepository, viewerId)).toList();
            long next = items.isEmpty() ? afterId : items.getLast().id();
            return ApiResponse.ok(Map.of("items", items, "nextAfterId", next));
        }
        validatePage(page, size);
        var results = questionService.list(sessionId, viewerId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")));
        return ApiResponse.ok(PageResponse.from(results, question -> QuestionResponse.forViewer(question, answerRepository, viewerId)));
    }

    @GetMapping("/questions/{questionId}")
    ApiResponse<QuestionResponse> get(@PathVariable Long questionId) {
        Long viewerId = CurrentUser.id();
        return ApiResponse.ok(QuestionResponse.forViewer(questionService.get(questionId, viewerId), answerRepository, viewerId));
    }

    @PatchMapping("/questions/{questionId}")
    ApiResponse<QuestionResponse> update(@PathVariable Long questionId, @RequestBody QuestionRequests.Update request) {
        Long viewerId = CurrentUser.id();
        return ApiResponse.ok(QuestionResponse.forViewer(questionService.update(questionId, viewerId, request), answerRepository, viewerId));
    }

    @DeleteMapping("/questions/{questionId}")
    ApiResponse<Void> delete(@PathVariable Long questionId) {
        questionService.delete(questionId, CurrentUser.id());
        return ApiResponse.ok();
    }

    @PostMapping("/questions/{questionId}/ai-answer/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    ApiResponse<QuestionResponse> retry(@PathVariable Long questionId) {
        Long viewerId = CurrentUser.id();
        return ApiResponse.ok(QuestionResponse.forViewer(questionService.retry(questionId, viewerId), answerRepository, viewerId));
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("page/size 범위가 올바르지 않습니다.");
    }
}