package com.GDGoCSMU.ASKeep.domain.user.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {

    /** 탈퇴한 사용자의 이름 앞에 붙는 값. 일반 회원은 이 값으로 시작하는 이름을 쓸 수 없다. */
    public static final String WITHDRAWN_NAME_PREFIX = "탈퇴한 사용자#";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    /** BCrypt로 암호화된 비밀번호 (원문 저장 금지) */
    @Column(nullable = false)
    private String password;

    @Column(name = "username", nullable = false, unique = true, length = 100)
    private String name;

    /** 학과. 기존에 가입한 회원은 값이 없을 수 있어서 DB에서는 null을 허용한다. */
    @Column(length = 50)
    private String department;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Role role;

    /** 탈퇴 시각. null이면 활동 중인 회원 */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @CreationTimestamp
    @Column(name = "create_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "update_at")
    private LocalDateTime updatedAt;

    protected User() {
    }

    public User(String email, String encodedPassword, String name, String department) {
        this.email = email;
        this.password = encodedPassword;
        this.name = name;
        this.department = department;
        this.role = Role.USER;
    }

    public void changeName(String name) {
        this.name = name;
    }

    public void changeDepartment(String department) {
        this.department = department;
    }

    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    /**
     * 회원 탈퇴. 행을 지우면 이 사용자가 남긴 질문, 참여 기록, 다른 사람 세션의 기록이 함께 깨지므로
     * 개인정보만 지우고(익명화) 행은 남긴다. 이메일이 바뀌므로 같은 이메일로 다시 가입할 수 있다.
     */
    public void withdraw(String unusableEncodedPassword) {
        this.email = "withdrawn-" + id + "@askeep.invalid";
        this.name = WITHDRAWN_NAME_PREFIX + id;
        this.department = null;
        this.password = unusableEncodedPassword;
        this.deletedAt = LocalDateTime.now();
    }

    public boolean isWithdrawn() { return deletedAt != null; }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getPassword() { return password; }
    public String getName() { return name; }
    public String getDepartment() { return department; }
    public Role getRole() { return role == null ? Role.USER : role; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
