package com.GDGoCSMU.ASKeep.domain.session;

import com.GDGoCSMU.ASKeep.domain.user.entity.User;
import com.GDGoCSMU.ASKeep.domain.session.entity.Session;
import com.GDGoCSMU.ASKeep.domain.user.domain.UserRole;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "session_participants", uniqueConstraints = @UniqueConstraint(columnNames = {"session_id", "user_id"}))
public class SessionParticipant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private Session session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    public SessionParticipant(Session session, User user) {
        this.session = session;
        this.user = user;
        this.role = UserRole.AUDIENCE;
    }
}
