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
        return ApiResponse.ok(QuestionResponse.from(questionService.create(sessionId, CurrentUser.id(), request.content(), request.isAnonymous()), answerRepository));
    }

    @GetMapping("/sessions/{sessionId}/questions")
    ApiResponse<?> list(@PathVariable Long sessionId, @RequestParam(required = false) Long afterId,
                @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        if (afterId != null) {
            if (afterId < 0) throw new IllegalArgumentException("afterId는 0 이상이어야 합니다.");
            List<QuestionResponse> items = questionService.after(sessionId, CurrentUser.id(), afterId).stream()
                    .map(question -> QuestionResponse.from(question, answerRepository)).toList();
            long next = items.isEmpty() ? afterId : items.getLast().id();
            return ApiResponse.ok(Map.of("items", items, "nextAfterId", next));
        }
        validatePage(page, size);
        var results = questionService.list(sessionId, CurrentUser.id(), PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")));
        return ApiResponse.ok(PageResponse.from(results, question -> QuestionResponse.from(question, answerRepository)));
    }

    @GetMapping("/questions/{questionId}")
    ApiResponse<QuestionResponse> get(@PathVariable Long questionId) {
        return ApiResponse.ok(QuestionResponse.from(questionService.get(questionId, CurrentUser.id()), answerRepository));
    }

    @PatchMapping("/questions/{questionId}")
    ApiResponse<QuestionResponse> update(@PathVariable Long questionId, @RequestBody QuestionRequests.Update request) {
        return ApiResponse.ok(QuestionResponse.from(questionService.update(questionId, CurrentUser.id(), request), answerRepository));
    }

    @DeleteMapping("/questions/{questionId}")
    ApiResponse<Void> delete(@PathVariable Long questionId) {
        questionService.delete(questionId, CurrentUser.id());
        return ApiResponse.ok();
    }

    @PostMapping("/questions/{questionId}/ai-answer/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    ApiResponse<QuestionResponse> retry(@PathVariable Long questionId) {
        return ApiResponse.ok(QuestionResponse.from(questionService.retry(questionId, CurrentUser.id()), answerRepository));
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("page/size 범위가 올바르지 않습니다.");
    }
}
