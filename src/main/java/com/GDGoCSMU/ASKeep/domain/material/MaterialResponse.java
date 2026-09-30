package com.GDGoCSMU.ASKeep.domain.material;

import java.time.LocalDateTime;

public record MaterialResponse(Long id, Long sessionId, String fileName, String contentType, Long fileSize,
                              ProcessingStatus status, LocalDateTime createdAt, LocalDateTime updatedAt) {
    public static MaterialResponse from(Material material) {
        return new MaterialResponse(material.getId(), material.getSession().getId(), material.getFileName(),
                material.getContentType(), material.getFileSize(), material.getStatus(),
                material.getCreateAt(), material.getUpdateAt());
    }
}
