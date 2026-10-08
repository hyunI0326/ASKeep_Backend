package com.GDGoCSMU.ASKeep.domain.material;

import com.GDGoCSMU.ASKeep.domain.material.client.AiClientServer;
import com.GDGoCSMU.ASKeep.domain.material.dto.AiDocumentRequest;
import com.GDGoCSMU.ASKeep.domain.material.dto.AiDocumentResponse;
import com.GDGoCSMU.ASKeep.domain.session.SessionAccessService;
import com.GDGoCSMU.ASKeep.domain.session.entity.Session;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class MaterialService {
    private final MaterialRepository materialRepository;
    private final SessionAccessService sessionAccess;
    private final AiClientServer aiClientServer;

    @Value("${askeep.upload-dir:ai-server/uploads}")
    private String uploadDirectory;

    public Material upload(Long sessionId, Long userId, MultipartFile file) {
        Session session = sessionAccess.requireHost(sessionId, userId);
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("PDF 파일이 필요합니다.");
        if (file.getSize() > 50L * 1024 * 1024) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "파일은 50MB 이하여야 합니다.");
        String originalName = Path.of(file.getOriginalFilename() == null ? "document.pdf" : file.getOriginalFilename()).getFileName().toString();
        if (!originalName.toLowerCase().endsWith(".pdf")) throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "PDF 파일만 업로드할 수 있습니다.");
        try (InputStream input = file.getInputStream()) {
            if (!new String(input.readNBytes(5), java.nio.charset.StandardCharsets.US_ASCII).startsWith("%PDF-")) {
                throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "유효한 PDF 파일이 아닙니다.");
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("파일을 읽을 수 없습니다.", exception);
        }

        Path directory = Path.of(uploadDirectory).toAbsolutePath().normalize();
        Path destination = directory.resolve(UUID.randomUUID() + ".pdf").normalize();
        if (!destination.startsWith(directory)) throw new IllegalArgumentException("잘못된 파일 경로입니다.");
        try {
            Files.createDirectories(directory);
            file.transferTo(destination);
        } catch (IOException exception) {
            throw new java.io.UncheckedIOException("파일 저장에 실패했습니다.", exception);
        }

        Material material = materialRepository.save(Material.builder()
                .fileName(originalName).filePath(destination.toString())
                .contentType(file.getContentType()).fileSize(file.getSize()).session(session).build());
        processAsync(material.getId(), session.getId(), destination.toString());
        return material;
    }

    public Material get(Long id, Long userId) {
        Material material = find(id);
        sessionAccess.requireMember(material.getSession().getId(), userId);
        return material;
    }

    public org.springframework.data.domain.Page<Material> list(Long sessionId, Long userId, org.springframework.data.domain.Pageable pageable) {
        sessionAccess.requireMember(sessionId, userId);
        return materialRepository.findBySession_IdOrderByIdDesc(sessionId, pageable);
    }

    public void delete(Long id, Long userId) {
        Material material = find(id);
        sessionAccess.requireHost(material.getSession().getId(), userId);
        if (material.getStatus() == ProcessingStatus.PROCESSING) throw new IllegalStateException("처리 중인 자료는 삭제할 수 없습니다.");
        materialRepository.delete(material);
        try { Files.deleteIfExists(Path.of(material.getFilePath())); } catch (IOException ignored) { }
    }

    public void deleteSessionFiles(Long sessionId) {
        for (Material material : materialRepository.findBySession_Id(sessionId)) {
            if (material.getStatus() == ProcessingStatus.PROCESSING) {
                throw new IllegalStateException("자료 처리 중에는 세션을 삭제할 수 없습니다.");
            }
            try { Files.deleteIfExists(Path.of(material.getFilePath())); } catch (IOException ignored) { }
        }
    }

    public Material retry(Long id, Long userId) {
        Material material = find(id);
        sessionAccess.requireHost(material.getSession().getId(), userId);
        if (material.getStatus() != ProcessingStatus.FAILED) throw new IllegalStateException("실패한 자료만 재시도할 수 있습니다.");
        material.setStatus(ProcessingStatus.PENDING);
        materialRepository.save(material);
        processAsync(material.getId(), material.getSession().getId(), material.getFilePath());
        return material;
    }

    private Material find(Long id) {
        return materialRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("자료를 찾을 수 없습니다."));
    }

    private void processAsync(Long id, Long sessionId, String filePath) {
        // ponytail: common pool is sufficient for the initial single-instance service; use a bounded executor if AI volume grows.
        CompletableFuture.runAsync(() -> {
            Material material = materialRepository.findById(id).orElse(null);
            if (material == null) return;
            material.startProcessing();
            materialRepository.save(material);
            try {
                AiDocumentResponse response = aiClientServer.processDocument(
                        new AiDocumentRequest(id, sessionId, filePath));
                if (response != null && "success".equalsIgnoreCase(response.status())) material.completeProcessing();
                else material.failProcessing();
            } catch (Exception exception) {
                material.failProcessing();
            }
            materialRepository.save(material);
        });
    }
}
