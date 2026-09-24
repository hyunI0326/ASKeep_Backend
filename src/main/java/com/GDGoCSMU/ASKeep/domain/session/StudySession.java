package com.GDGoCSMU.ASKeep.domain.session;

import com.GDGoCSMU.ASKeep.domain.common.BaseEntity;
import com.GDGoCSMU.ASKeep.domain.user.domain.User;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "sessions")
public class StudySession extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "host_id", nullable = false)
    private User user;

    @Builder
    public StudySession(String title, String description, User host) {
        this.title = title;
        this.description = description;
        this.user = host;
        this.status = SessionStatus.READY;
    }
    public void start() {
        this.status = SessionStatus.ACTIVE;
    }
    public void end() {
        this.status = SessionStatus.ENDED;
    }
}
