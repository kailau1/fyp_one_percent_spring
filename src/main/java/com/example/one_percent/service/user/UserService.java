package com.example.one_percent.service.user;

import com.example.one_percent.dto.UserDTO;
import com.example.one_percent.dto.auth.AuthResponseDTO;
import com.example.one_percent.dto.auth.LoginRequestDTO;
import com.example.one_percent.dto.auth.UserRegistrationDTO;
import com.example.one_percent.mapper.UserMapper;
import com.example.one_percent.model.User;
import com.example.one_percent.repository.UserRepository;
import com.example.one_percent.security.JwtUtil;
import com.example.one_percent.util.UserClock;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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

    private User getAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User user) {
            return user;
        }
        throw new IllegalStateException("No authenticated user");
    }

    public UserDTO getCurrentUser() {
        return userMapper.toDto(getAuthenticatedUser());
    }

    /** Stores the user's IANA time zone (e.g. "Europe/London"); rejects anything Java doesn't recognise. */
    public UserDTO updateTimeZone(String timeZone) {
        String zone = timeZone == null ? null : timeZone.trim();
        if (!UserClock.isValidZone(zone)) {
            throw new IllegalArgumentException("That isn't a time zone we recognise, e.g. \"Europe/London\".");
        }
        User user = getAuthenticatedUser();
        userRepository.updateTimeZone(user.getId(), zone);
        user.setTimeZone(zone);
        return userMapper.toDto(user);
    }

    public List<UserDTO> getAllUsers() {
        return userRepository.findAll()
                .stream()
                .map(userMapper::toDto)
                .collect(Collectors.toList());
    }

    public UserDTO createUser(UserRegistrationDTO registrationDTO) {
        // A second account with the same email would make findByEmail() match two documents,
        // which breaks login for *both* accounts.
        if (userRepository.findByEmail(registrationDTO.getEmail()) != null) {
            throw new IllegalArgumentException("An account with this email already exists.");
        }

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
                token
        );

        System.out.println("[DEBUG] User logged in: " + foundUser.getEmail());
        System.out.println("[DEBUG] Token generated: " + token);

        return ResponseEntity.ok(response);
    }
}
