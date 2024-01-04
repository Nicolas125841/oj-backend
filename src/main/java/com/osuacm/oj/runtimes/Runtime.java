package com.osuacm.oj.runtimes;

import org.springframework.scheduling.annotation.Async;
import reactor.core.publisher.Mono;

import java.nio.file.Path;

public interface Runtime {
    enum RESULT {SERVER_ERROR, COMPILE_ERROR, RUNTIME_ERROR, TIMEOUT, SUCCESS, WRONG_ANSWER};

    @Async
    Mono<RESULT> compile(Path context, Path source, Path output, Path error);

    @Async
    Mono<RESULT> run(Path context, Path output, Path error, Long tl, Long ml);

}
