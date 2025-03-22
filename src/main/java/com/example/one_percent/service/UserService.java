package com.example.one_percent.service;

import com.example.one_percent.dto.UserDTO;
import com.example.one_percent.dto.auth.AuthResponseDTO;
import com.example.one_percent.dto.auth.LoginRequestDTO;
import com.example.one_percent.dto.auth.UserRegistrationDTO;
import com.example.one_percent.mapper.UserMapper;
import com.example.one_percent.model.User;
import com.example.one_percent.repository.UserRepository;
import com.example.one_percent.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public List<UserDTO> getAllUsers() {
        return userRepository.findAll()
                .stream()
                .map(userMapper::toDto)
                .collect(Collectors.toList());
    }

    public UserDTO createUser(UserRegistrationDTO registrationDTO) {
        User user = new User();
        user.setFirstName(registrationDTO.getFirstName());
        user.setLastName(registrationDTO.getLastName());
        user.setEmail(registrationDTO.getEmail());
        user.setPassword(passwordEncoder.encode(registrationDTO.getPassword()));

        return userMapper.toDto(userRepository.save(user));
    }

    public void deleteUser(String id) {
        userRepository.deleteById(id);
    }

    public ResponseEntity<AuthResponseDTO> login(LoginRequestDTO loginDTO) {
        User foundUser = userRepository.findByEmail(loginDTO.getEmail());
        if (foundUser == null || !passwordEncoder.matches(loginDTO.getPassword(), foundUser.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String token = jwtUtil.generateToken(foundUser);

        AuthResponseDTO response = new AuthResponseDTO(
                token,
                foundUser.getId(),
                foundUser.getEmail(),
                foundUser.getFirstName(),
                foundUser.getLastName()
        );

        return ResponseEntity.ok(response);
    }
}
