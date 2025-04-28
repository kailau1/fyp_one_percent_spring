package com.example.one_percent.controller.core;

import com.example.one_percent.dto.UserDTO;
import com.example.one_percent.dto.auth.UserRegistrationDTO;
import com.example.one_percent.dto.auth.LoginRequestDTO;
import com.example.one_percent.dto.auth.AuthResponseDTO;
import com.example.one_percent.service.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@CrossOrigin
public class UserController {

    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<UserDTO> createUser(@RequestBody UserRegistrationDTO registrationDTO) {
        UserDTO savedUser = userService.createUser(registrationDTO);
        return ResponseEntity.status(201).body(savedUser);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> login(@RequestBody LoginRequestDTO loginDTO) {
        return userService.login(loginDTO);
    }
}
