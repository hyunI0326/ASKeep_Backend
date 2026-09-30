package com.GDGoCSMU.ASKeep.domain.material;



import com.GDGoCSMU.ASKeep.domain.material.client.AiClientServer;
import com.GDGoCSMU.ASKeep.domain.material.dto.AiDocumentRequest;
import com.GDGoCSMU.ASKeep.domain.material.dto.AiDocumentResponse;
import com.GDGoCSMU.ASKeep.domain.session.SessionRepository;
import com.GDGoCSMU.ASKeep.domain.session.StudySession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class MaterialService {
    private final MaterialRepository materialRepository;
    private final SessionRepository sessionRepository;
    private final AiClientServer aiClientServer;

    public Long upload(Long sessionId, MultipartFile file) {
        StudySession session = sessionRepository.findById(sessionId).orElseThrow();
        String filePath = "/uploads" + file.getOriginalFilename();

        Material material = Material.builder().fileName(file.getOriginalFilename())
                .filePath(filePath)
                .contentType(file.getContentType())
                .fileSize(file.getSize())
                .session(session)
                .build();

        materialRepository.save(material);

        AiDocumentRequest aiRequest = new AiDocumentRequest(material.getId(), session.getId(), filePath);
        AiDocumentResponse response = aiClientServer.processDocument(aiRequest);
        if ("COMPLETED".equals(response.status())) {
            material.completeProcessing();
        } else {
            material.failProcessing();
        }
        return material.getId();
    }
}
