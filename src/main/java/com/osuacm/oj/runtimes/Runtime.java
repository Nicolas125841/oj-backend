package com.osuacm.oj.runtimes;

import org.springframework.scheduling.annotation.Async;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public interface Runtime {
    String WRAPPER = "/source/security/runwrap.o";
    @Async
    CompletableFuture<Process> compile(Path context, Path error) throws IOException;

    @Async
    CompletableFuture<Process> run(Path context, Path output, Path error, Path input, Long tl, Long ml) throws IOException;

    String getSource();
}
