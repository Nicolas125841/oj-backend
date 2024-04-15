package com.osuacm.oj.runtimes;

import lombok.extern.java.Log;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Log
public class GccRuntime implements Runtime {

    final String COMPILER = "gcc";
    final String WRAPPER = "/source/security/runwrap.o";

    final List<String> RUN_ARGS = List.of(
        "bwrap",
        "--ro-bind",
        "/usr",
        "/usr",
        "--ro-bind",
        "/runtime",
        "/subject",
        "--symlink",
        "usr/lib",
        "/lib",
        "--symlink",
        "usr/lib64",
        "/lib64",
        "--chdir",
        "/subject",
        "--unshare-user",
        "--unshare-net",
        "--unshare-ipc",
        "--unshare-cgroup",
        "--unshare-uts",
        "--cap-drop",
        "ALL",
        "--die-with-parent",
        "--new-session",
        "./a.out"
    );

    @Override
    public CompletableFuture<Process> compile(Path context, Path error) throws IOException {
        return new ProcessBuilder()
            .command(COMPILER, "solution.c")
            .directory(context.toFile())
            .redirectError(error.toFile())
            .start()
            .onExit();
    }

    @Override
    public CompletableFuture<Process> run(Path context, Path output, Path error, Path input, Long tl, Long ml) {
        try {
            List<String> runArgs = new ArrayList<>(List.of(WRAPPER, tl.toString(), ml.toString()));

            runArgs.addAll(RUN_ARGS);

            Process runJava = new ProcessBuilder()
                .command(runArgs)
                .directory(context.toFile())
                .redirectOutput(output.toFile())
                .redirectError(error.toFile())
                .redirectInput(input.toFile())
                .start();

            return runJava.onExit();
        } catch (IOException e) {
            log.info(e.getMessage());
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getSource() {
        return "solution.c";
    }
}
