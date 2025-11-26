//package cloudStorage.server.controller;
//
//import cloudStorage.server.db.entity.User;
//import cloudStorage.server.db.jpaRepository.UserRepository;
//import cloudStorage.server.model.UsersDto;
//import lombok.RequiredArgsConstructor;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.ResponseEntity;
//import org.springframework.security.crypto.password.PasswordEncoder;
//import org.springframework.web.bind.annotation.*;
//
//import java.util.List;
//
//@RestController
//@RequestMapping("/api/users")
//@RequiredArgsConstructor
//public class UsersController {
//
//    private final UserRepository userRepository;
//    private final PasswordEncoder passwordEncoder;
//
//    @PostMapping
//    public ResponseEntity<?> createUser(@RequestBody UsersDto userDto) {
//
//        User user = new User();
//        user.setName(userDto.username());
//        user.setPassword(passwordEncoder.encode(userDto.rawPassword()));
//        userRepository.save(user);
//        return ResponseEntity.status(HttpStatus.CREATED).body("Пользователь создан");
//    }
//
//    @GetMapping
//    public List<User> getAllUsers() {
//        return userRepository.findAll();
//    }
//
//    @PutMapping("/{id}")
//    public ResponseEntity<?> updateUser(@PathVariable Long id, @RequestBody UsersDto userDto) {
//        return userRepository
//                .findById(id)
//                .map(user -> {
//                    user.setName(userDto.username());
//                    user.setPassword(passwordEncoder.encode(userDto.rawPassword()));
//                    userRepository.save(user);
//                    return ResponseEntity.ok("Пользователь обновлён");
//                })
//                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).body("Пользователь не найден"));
//    }
//
//    @DeleteMapping("/{id}")
//    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
//        if (!userRepository.existsById(id)) {
//            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Пользователь не найден");
//        }
//
//        userRepository.deleteById(id);
//        return ResponseEntity.ok("Пользователь удалён");
//    }
//}
