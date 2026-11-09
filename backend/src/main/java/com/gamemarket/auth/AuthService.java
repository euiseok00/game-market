package com.gamemarket.auth;

import com.gamemarket.auth.dto.Credentials;
import com.gamemarket.auth.dto.Me;
import java.nio.charset.StandardCharsets;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder encoder;

    public AuthService(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    /** Creates the account; 409 when the username is taken, 400 when the password is too long for BCrypt. */
    public Me signup(Credentials credentials) {
        if (exceedsBcryptLimit(credentials.password())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "password longer than 72 bytes");
        }
        String username = credentials.username().trim();
        if (users.existsByUsername(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "username taken");
        }
        try {
            User saved = users.saveAndFlush(new User(username, encoder.encode(credentials.password())));
            return new Me(saved.getId(), saved.getUsername());
        } catch (DataIntegrityViolationException race) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "username taken");
        }
    }

    /** BCrypt only reads 72 bytes and Spring Security 7 rejects longer input, so check bytes, not chars. */
    public static boolean exceedsBcryptLimit(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length > 72;
    }
}
