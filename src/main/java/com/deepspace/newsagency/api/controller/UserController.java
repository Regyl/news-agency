package com.deepspace.newsagency.api.controller;

import com.deepspace.newsagency.api.controller.dto.request.Credentials;
import com.deepspace.newsagency.api.controller.dto.request.UserDto;
import com.deepspace.newsagency.api.controller.dto.response.CredentialsResponse;
import com.deepspace.newsagency.api.controller.dto.response.UserDtoResponse;
import com.deepspace.newsagency.entity.User;
import com.deepspace.newsagency.exception.EntityAlreadyExistsException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.deepspace.newsagency.Utils;
import com.deepspace.newsagency.api.mapper.UserMapper;
import com.deepspace.newsagency.mail.EmailService;
import com.deepspace.newsagency.security.jwt.JwtProvider;
import com.deepspace.newsagency.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.UUID;

@Tag(name = "Authorization")
@Validated
@RestController
public class UserController {

    private final AuthenticationManager authenticationManager;
    private final JwtProvider jwtProvider;
    private final UserService userService;
    private final UserMapper userMapper;
    private final EmailService emailService;
    private final long tokenLifetime;

    public UserController(@Value("${jwt.tokenLifetime}") Long tokenLifetime,
                          AuthenticationManager authenticationManager,
                          JwtProvider jwtProvider,
                          UserService userService,
                          UserMapper userMapper,
                          EmailService emailService) {
        this.tokenLifetime = tokenLifetime;
        this.authenticationManager = authenticationManager;
        this.jwtProvider = jwtProvider;
        this.userService = userService;
        this.userMapper = userMapper;
        this.emailService = emailService;
    }

    @PostMapping("/refresh")
    @Operation(summary = "Get new access/refresh tokens")
    public CredentialsResponse refreshToken(@RequestParam String refreshToken) {

    }

    @PostMapping("/sign-in")
    @Operation(summary = "Authorization")
    public CredentialsResponse signIn(@RequestBody @Valid Credentials credentials) {
        Authentication auth = new UsernamePasswordAuthenticationToken(credentials.getLogin(), credentials.getPassword());
        authenticationManager.authenticate(auth);
        String token = jwtProvider.generateAccessToken(credentials.getLogin());
        return CredentialsResponse.of(token, tokenLifetime);
    }

    @PostMapping("/sign-up")
    @Operation(summary = "Registration")
    public UserDtoResponse signUp(@RequestBody @Valid UserDto dto) {
        if(userService.isExists(dto.getLogin())) {
            throw new EntityAlreadyExistsException(User.class, dto.getLogin());
        }

        User user = userMapper.toEntity(dto);
        String temporaryKey = UUID.randomUUID().toString();
        user.setTemporaryKey(temporaryKey);
        emailService.doSend(dto.getLogin(),
                "Glad to see you!", "http://195.2.214.167:8082/auth/"+temporaryKey); //fixme сделать профили и запускать с параметрами
        user = userService.save(user);
        return userMapper.toDto(user);
    }

    @GetMapping("/info")
    @Operation(summary = "Account information")
    public UserDtoResponse getData() {
        User user = Utils.getAuthenticatedUser();
        return userMapper.toDto(user);
    }

    @GetMapping("/auth/{value}")
    public void verifyEmail(@PathVariable String value) {
        User user = userService.findByTemporaryKey(value);
        userMapper.verify(user);
        userService.save(user);
    }

}
