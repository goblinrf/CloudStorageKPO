package cloudStorage.server.controller;


import cloudStorage.server.db.entity.User;
import cloudStorage.server.db.jpaRepository.UserRepository;
import cloudStorage.server.model.UsersDto;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody UsersDto userDto, HttpSession session) {
        Optional<User> user = userRepository.findByName(userDto.getUsername());
        System.out.println(userDto.getRawPassword() + userDto.getUsername());
        if (user.isEmpty()
                || !passwordEncoder.matches(userDto.getRawPassword(), user.get().getPassword())) {
            log.warn("Ошибка входа: пользователь '{}' ввёл неверные данные", userDto.getUsername());
            return ResponseEntity.status(401).body("Неверный логин или пароль");
        }

        session.setAttribute("user", userDto.getUsername());
        log.info("Успешный вход пользователя '{}'", userDto.getUsername());
        return ResponseEntity.ok("Успешный вход");
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpSession session) {
        session.invalidate();
        return ResponseEntity.ok("Вы вышли из системы");
    }

    @GetMapping("/me")
    public ResponseEntity<?> currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getPrincipal().equals("anonymousUser")) {
            return ResponseEntity.status(401).body("Не авторизован");
        }

        String username = authentication.getName();
        log.info("Сессия продолжается для пользователя '{}'", username);
        return ResponseEntity.ok(username);
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody UsersDto userDto) {

        if (userRepository.findByName(userDto.getUsername()).isPresent()) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body("Пользователь уже существует");
        }

        User user = new User();
        user.setName(userDto.getUsername());
        user.setPassword(passwordEncoder.encode(userDto.getRawPassword()));
        userRepository.save(user);
        return ResponseEntity.status(HttpStatus.CREATED).body("Пользователь создан");
    }

    @GetMapping("/")
    public String home(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return "redirect:/login.html";
        }
        return "index.html";
    }
}
