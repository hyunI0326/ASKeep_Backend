package com.GDGoCSMU.ASKeep.domain.answer;

import com.GDGoCSMU.ASKeep.domain.common.CurrentUser;
import com.GDGoCSMU.ASKeep.domain.question.QuestionService;
import com.GDGoCSMU.ASKeep.global.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class AnswerController {
    private final QuestionService questionService;

    @GetMapping("/questions/{questionId}/answers")
    ApiResponse<List<AnswerResponse>> list(@PathVariable Long questionId) {
        return ApiResponse.ok(questionService.answers(questionId, CurrentUser.id()).stream().map(AnswerResponse::from).toList());
    }

    @PostMapping("/questions/{questionId}/answers")
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<AnswerResponse> create(@PathVariable Long questionId, @Valid @RequestBody ContentRequest request) {
        return ApiResponse.ok(AnswerResponse.from(questionService.answer(questionId, CurrentUser.id(), request.content())));
    }

    @PatchMapping("/answers/{answerId}")
    ApiResponse<AnswerResponse> update(@PathVariable Long answerId, @Valid @RequestBody ContentRequest request) {
        return ApiResponse.ok(AnswerResponse.from(questionService.updateAnswer(answerId, CurrentUser.id(), request.content())));
    }

    @DeleteMapping("/answer/{answerId}")
    ApiResponse<Void> delete(@PathVariable Long answerId) {
        questionService.deleteAnswer(answerId, CurrentUser.id());
        return ApiResponse.ok();
    }

    public record ContentRequest(@NotBlank String content) {}
}
