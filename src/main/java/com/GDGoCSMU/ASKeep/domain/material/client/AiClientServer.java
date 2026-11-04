package com.GDGoCSMU.ASKeep.domain.material.client;


import com.GDGoCSMU.ASKeep.domain.material.dto.AiDocumentRequest;
import com.GDGoCSMU.ASKeep.domain.material.dto.AiDocumentResponse;
import com.GDGoCSMU.ASKeep.domain.question.dto.AiAnswerRequest;
import com.GDGoCSMU.ASKeep.domain.question.dto.AiAnswerResponse;
import com.GDGoCSMU.ASKeep.domain.session.dto.AiSummaryRequest;
import com.GDGoCSMU.ASKeep.domain.session.dto.AiSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class AiClientServer {
    private final RestClient aiRestClient;

    public AiDocumentResponse processDocument(AiDocumentRequest request) {
        var parts = new LinkedMultiValueMap<String, Object>();
        parts.add("materialId", request.materialId().toString());
        parts.add("sessionId", request.sessionId().toString());
        parts.add("file", new FileSystemResource(request.filePath()));
        return aiRestClient.post()
                .uri("/documents/process")
                .contentType(MediaType.MULTIPART_FORM_DATA).body(parts)
                .retrieve().body(AiDocumentResponse.class);
    }

    public AiAnswerResponse answer(AiAnswerRequest request) {
        return aiRestClient.post().uri("/ai/answer").body(request)
                .retrieve().body(AiAnswerResponse.class);
    }

    public Integer pageCount(String filePath) {
        var response = aiRestClient.post().uri("/documents/page-count")
                .contentType(MediaType.MULTIPART_FORM_DATA).body(pdfPart(filePath))
                .retrieve().body(PageCountResponse.class);
        return response == null ? null : response.pageCount();
    }

    public byte[] renderPage(String filePath, int pageNumber) {
        var parts = pdfPart(filePath);
        parts.add("pageNumber", Integer.toString(pageNumber));
        return aiRestClient.post().uri("/documents/render-page")
                .contentType(MediaType.MULTIPART_FORM_DATA).body(parts)
                .retrieve().body(byte[].class);
    }

    private static LinkedMultiValueMap<String, Object> pdfPart(String filePath) {
        var parts = new LinkedMultiValueMap<String, Object>();
        parts.add("file", new FileSystemResource(filePath));
        return parts;
    }

    public record PageCountResponse(Integer pageCount) {}

    public AiSummaryResponse summarize(AiSummaryRequest request) {
        return aiRestClient.post().uri("/sessions/summary").body(request)
                .retrieve().body(AiSummaryResponse.class);
    }
}
