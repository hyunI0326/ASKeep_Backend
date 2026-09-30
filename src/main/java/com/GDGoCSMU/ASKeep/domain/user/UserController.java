package com.GDGoCSMU.ASKeep.domain.user;


import com.GDGoCSMU.ASKeep.domain.user.domain.UserSignupRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/user")
public class UserController {
    private UserService userService;
    private UserSignupRequest request;
    //To-do
    // @GetMapping("/signup")

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@Valid UserSignupRequest request, BindingResult bindingResult) {
        if(bindingResult.hasErrors()) {
            Map<String, String> errors = new HashMap<>();
            bindingResult.getFieldError()
                    .forEach(error->errors.put(error.getField(), error.getOrDefault()));

            return ResponseEntity.badRequest().body(errors);
        }
        if (!request.getPassword1().equals(request.getPassword2())) {
            return ResponseEntity.badRequest().body(Map.of(
                    "password2", "2개의 비밀번호가 일치하지 않습니다."
            ));
        }
        try {
            userService.create(request.getUsername(), request.getPassword1(), request.getEmail());
        } catch (DataIntegrityViolationException e) {
            log.warn(
                    "회원가입 실패 - 이미 존재하는 사용자 username={}", request.getUsername(), e);
            return ResponseEntity.badRequest().body(Map.of("message", "이미 존재하는 사용자입니다."));
        } catch (Exception e) {
            log.error(
                    "회원가입중 예상치 못한 에러 발생: username={}", request.getUsername(), e);
            return ResponseEntity.badRequest().body(Map.of("message", "회원가입중 오류가 발생했습니다."));
        }
    }
}
