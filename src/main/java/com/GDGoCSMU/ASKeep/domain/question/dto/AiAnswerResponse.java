package com.GDGoCSMU.ASKeep.domain.question.dto;

import java.util.List;

public record AiAnswerResponse(Long sessionId, String question, String answer, List<Object> sources) {}
