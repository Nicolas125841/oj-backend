package com.osuacm.oj.controllers;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@CrossOrigin
@RestController
@RequestMapping("/auth")
public class AuthenticationController {

    @GetMapping("/test")
    public Mono<Boolean> verifyAuthentication() {
        return Mono.just(Boolean.TRUE);
    }
}
