package com.GDGoCSMU.ASKeep.domain.material;

import com.GDGoCSMU.ASKeep.domain.common.BaseEntity;
import com.GDGoCSMU.ASKeep.domain.session.entity.Session;
import jakarta.persistence.*;
import lombok.*;


@Getter
@Entity
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "materials")
public class Material extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false)
    private String filePath;

    private String contentType;

    private Long fileSize;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProcessingStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private Session session;

    @Builder
    public Material(String fileName, String filePath, String contentType, Long fileSize, Session session) {
        this.fileName = fileName;
        this.filePath = filePath;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.session = session;
        this.status = ProcessingStatus.PENDING;
    }

    public void startProcessing() {
        this.status = ProcessingStatus.PROCESSING;
    }

    public void completeProcessing() {
        this.status = ProcessingStatus.COMPLETED;
    }

    public void failProcessing() {
        this.status = ProcessingStatus.FAILED;
    }
}
