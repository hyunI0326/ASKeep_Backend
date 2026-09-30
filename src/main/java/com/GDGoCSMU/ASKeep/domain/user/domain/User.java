package com.GDGoCSMU.ASKeep.domain.user.domain;


import com.GDGoCSMU.ASKeep.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@Table(name = "users")
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String username;

    @Column(nullable = false, length =100, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

   @Builder
    public User(String username, String email, String password) {
       this.username = username;
       this.email = email;
       this.password = password;
   }
}
