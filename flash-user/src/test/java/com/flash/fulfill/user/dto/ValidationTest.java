package com.flash.fulfill.user.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DTO Bean Validation 注解规则单测(直接驱动 Validator,无 Spring 上下文)。
 */
class ValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void init() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void close() {
        factory.close();
    }

    @Test
    void registerAcceptsValidInput() {
        RegisterCommand cmd = new RegisterCommand();
        cmd.setUsername("alice");
        cmd.setPassword("123456");
        cmd.setNickname("爱丽丝");

        assertTrue(validator.validate(cmd).isEmpty());
    }

    @Test
    void registerRejectsBlankUsernameAndShortPassword() {
        RegisterCommand cmd = new RegisterCommand();
        cmd.setUsername(" ");
        cmd.setPassword("123");

        Set<ConstraintViolation<RegisterCommand>> violations = validator.validate(cmd);
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("username")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("password")));
    }

    @Test
    void registerRejectsOverlongNickname() {
        RegisterCommand cmd = new RegisterCommand();
        cmd.setUsername("alice");
        cmd.setPassword("123456");
        cmd.setNickname("x".repeat(51));

        assertTrue(validator.validate(cmd).stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("nickname")));
    }

    @Test
    void loginRejectsBlankCredentials() {
        LoginCommand cmd = new LoginCommand();
        cmd.setUsername(" ");
        cmd.setPassword(" ");

        Set<ConstraintViolation<LoginCommand>> violations = validator.validate(cmd);
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("username")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("password")));
    }

    @Test
    void loginAcceptsValidCredentials() {
        LoginCommand cmd = new LoginCommand();
        cmd.setUsername("alice");
        cmd.setPassword("123456");

        assertTrue(validator.validate(cmd).isEmpty());
    }
}