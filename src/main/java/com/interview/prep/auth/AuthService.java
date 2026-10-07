package com.interview.prep.auth;

import com.interview.prep.common.ConflictException;
import com.interview.prep.common.UnauthorizedException;
import com.interview.prep.user.Role;
import com.interview.prep.user.User;
import com.interview.prep.user.UserRepository;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final String EMAIL_TAKEN = "Email is already registered";

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, TokenService tokenService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.dummyHash = passwordEncoder.encode("unused-password");
    }

    public User register(RegisterRequest request) {
        String email = normalize(request.email());
        if (users.existsByEmail(email)) {
            throw new ConflictException(EMAIL_TAKEN);
        }
        try {
            return users.saveAndFlush(new User(email, passwordEncoder.encode(request.password()), Role.USER));
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException(EMAIL_TAKEN);
        }
    }

    public LoginResponse login(LoginRequest request) {
        User user = users.findByEmail(normalize(request.email())).orElse(null);
        boolean passwordMatches = passwordEncoder.matches(request.password(),
                user == null ? dummyHash : user.getPasswordHash());
        if (user == null || !passwordMatches) {
            throw new UnauthorizedException("Invalid email or password");
        }
        return tokenService.issue(user);
    }

    private String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
