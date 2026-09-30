package com.GDGoCSMU.ASKeep.domain.material.client;


import com.GDGoCSMU.ASKeep.domain.material.dto.AiDocumentRequest;
import com.GDGoCSMU.ASKeep.domain.material.dto.AiDocumentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class AiClientServer {
    private final RestClient aiRestClient;

    public AiDocumentResponse processDocument(AiDocumentRequest request) {
        return aiRestClient.post()
                .uri("/document/process").body(request)
                .retrieve().body(AiDocumentResponse.class);
    }
}
