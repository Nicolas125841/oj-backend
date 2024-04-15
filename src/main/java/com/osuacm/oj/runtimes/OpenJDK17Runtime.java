package com.osuacm.oj.runtimes;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public class OpenJDK17Runtime implements Runtime {
    @Override
    public CompletableFuture<Process> compile(Path context, Path error) {
        return null;
    }

    @Override
    public CompletableFuture<Process> run(Path context, Path output, Path error, Path input, Long tl, Long ml) {
        return null;
    }
}
