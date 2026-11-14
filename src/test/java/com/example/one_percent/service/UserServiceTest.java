package com.example.one_percent.service;

import com.example.one_percent.dto.UserDTO;
import com.example.one_percent.dto.auth.AuthResponseDTO;
import com.example.one_percent.dto.auth.LoginRequestDTO;
import com.example.one_percent.dto.auth.UserRegistrationDTO;
import com.example.one_percent.mapper.UserMapper;
import com.example.one_percent.model.User;
import com.example.one_percent.repository.UserRepository;
import com.example.one_percent.security.JwtUtil;
import com.example.one_percent.service.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private JwtUtil jwtUtil;

    // low cost factor keeps the test fast; real BCrypt so hashing is genuinely checked
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private final UserMapper userMapper = Mappers.getMapper(UserMapper.class);
    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, userMapper, passwordEncoder, jwtUtil);
    }

    private User storedUser(String email, String rawPassword) {
        return User.builder().id("u1").email(email).firstName("Kai").lastName("L")
                .password(passwordEncoder.encode(rawPassword)).build();
    }

    @Test
    void createUser_hashesPasswordAndNeverReturnsIt() {
        when(userRepository.save(any())).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId("u1");
            return u;
        });

        UserDTO dto = userService.createUser(new UserRegistrationDTO("Kai", "L", "kai@example.com", "s3cret!"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getPassword()).isNotEqualTo("s3cret!");
        assertThat(passwordEncoder.matches("s3cret!", saved.getValue().getPassword())).isTrue();
        assertThat(dto.getId()).isEqualTo("u1");
        assertThat(dto.getEmail()).isEqualTo("kai@example.com");
    }

    @Test
    void createUser_rejectsAnEmailThatIsAlreadyRegistered() {
        when(userRepository.findByEmail("kai@example.com")).thenReturn(storedUser("kai@example.com", "x"));

        assertThatThrownBy(() -> userService.createUser(new UserRegistrationDTO("Kai", "L", "kai@example.com", "y")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
        verify(userRepository, never()).save(any());
    }

    @Test
    void login_succeedsWithCorrectPassword() {
        User user = storedUser("kai@example.com", "s3cret!");
        when(userRepository.findByEmail("kai@example.com")).thenReturn(user);
        when(jwtUtil.generateToken(user)).thenReturn("jwt");

        ResponseEntity<AuthResponseDTO> response = userService.login(new LoginRequestDTO("kai@example.com", "s3cret!"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getToken()).isEqualTo("jwt");
    }

    @Test
    void login_rejectsWrongPassword() {
        when(userRepository.findByEmail("kai@example.com")).thenReturn(storedUser("kai@example.com", "s3cret!"));

        ResponseEntity<AuthResponseDTO> response = userService.login(new LoginRequestDTO("kai@example.com", "wrong"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(jwtUtil, never()).generateToken(any());
    }

    @Test
    void login_rejectsUnknownEmail() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(null);

        ResponseEntity<AuthResponseDTO> response = userService.login(new LoginRequestDTO("nobody@example.com", "x"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
