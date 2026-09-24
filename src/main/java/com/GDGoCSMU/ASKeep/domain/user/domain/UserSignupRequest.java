package com.GDGoCSMU.ASKeep.domain.user.domain;


import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter

public class UserSignupRequest {
    @NotEmpty(message = "사용자 ID는 필수입니다.")
    private String username;

    @NotEmpty(message = "이메일은 필수입니다.")
    @Email
    private String email;

    @NotEmpty(message = "비밀번호는 필수입니다.")
    private String password1;

    @NotEmpty(message = "비밀번호 확인은 필수입니다.")
    private String password2;
}
