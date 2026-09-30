package com.GDGoCSMU.ASKeep.domain.material.client;


import com.GDGoCSMU.ASKeep.domain.material.dto.AiDocumentRequest;
import com.GDGoCSMU.ASKeep.domain.material.dto.AiDocumentResponse;
import com.GDGoCSMU.ASKeep.domain.question.dto.AiAnswerRequest;
import com.GDGoCSMU.ASKeep.domain.question.dto.AiAnswerResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class AiClientServer {
    private final RestClient aiRestClient;

    public AiDocumentResponse processDocument(AiDocumentRequest request) {
        return aiRestClient.post()
                .uri("/documents/process").body(request)
                .retrieve().body(AiDocumentResponse.class);
    }

    public AiAnswerResponse answer(AiAnswerRequest request) {
        return aiRestClient.post().uri("/ai/answer").body(request)
                .retrieve().body(AiAnswerResponse.class);
    }
}
