package com.osuacm.oj.runtimes;

import reactor.core.publisher.Mono;

import java.nio.file.Path;

public class OpenJDK17Runtime implements Runtime {
    @Override
    public Mono<RESULT> compile(Path context, Path source, Path output, Path error) {
        return null;
    }

    @Override
    public Mono<RESULT> run(Path context, Path output, Path error, Long tl, Long ml) {
        return null;
    }
}
