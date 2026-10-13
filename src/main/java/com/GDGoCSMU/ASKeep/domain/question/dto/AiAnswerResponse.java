package com.GDGoCSMU.ASKeep.domain.question.dto;

import java.util.List;
import com.GDGoCSMU.ASKeep.domain.question.AiStatus;

public record AiAnswerResponse(Long sessionId, String question, String answer, List<Object> sources, AiStatus status) {}
