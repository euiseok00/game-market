package com.gamemarket.auth;

import com.gamemarket.auth.dto.Credentials;
import com.gamemarket.auth.dto.Me;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** HTTP and session handling only; account rules live in AuthService. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;
    private final AuthenticationManager authManager;
    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

    public AuthController(AuthService auth, AuthenticationManager authManager) {
        this.auth = auth;
        this.authManager = authManager;
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public Me signup(@Valid @RequestBody Credentials body) {
        return auth.signup(body);
    }

    @PostMapping("/login")
    public Me login(@Valid @RequestBody Credentials body, HttpServletRequest request, HttpServletResponse response) {
        if (AuthService.exceedsBcryptLimit(body.password())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "bad credentials");
        }
        Authentication authentication;
        try {
            authentication = authManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(body.username().trim(), body.password()));
        } catch (AuthenticationException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "bad credentials");
        }
        request.getSession(true);
        request.changeSessionId();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
        AuthUser user = (AuthUser) authentication.getPrincipal();
        return new Me(user.id(), user.username());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        new SecurityContextLogoutHandler().logout(request, response, authentication);
    }

    @GetMapping("/me")
    public Me me(@AuthenticationPrincipal AuthUser user) {
        return new Me(user.id(), user.username());
    }
}
