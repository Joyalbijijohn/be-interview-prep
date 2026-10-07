package com.interview.prep.auth;

import com.interview.prep.user.Role;
import com.interview.prep.user.User;
import com.interview.prep.user.UserRepository;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminInitializer implements ApplicationRunner {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;

    public AdminInitializer(UserRepository users, PasswordEncoder passwordEncoder,
            @Value("${app.admin.email:}") String email, @Value("${app.admin.password:}") String password) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (email.isBlank() || password.isBlank()) {
            return;
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (!users.existsByEmail(normalized)) {
            users.save(new User(normalized, passwordEncoder.encode(password), Role.ADMIN));
        }
    }
}
