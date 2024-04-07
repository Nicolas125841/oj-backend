package com.osuacm.oj.runtimes;

import io.netty.util.CharsetUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class GccRuntime implements Runtime {

    final String COMPILER = "gcc";
    final String WRAPPER = "./runwrap.o";

    final List<String> RUN_ARGS = List.of(
        "bwrap",
        "--ro-bind",
        "/usr",
        "/usr",
        "--ro-bind",
        ". /subject",
        "--symlink usr/lib",
        "/lib",
        "--symlink",
        "usr/lib64",
        "/lib64",
        "--chdir",
        "/subject",
        "--unshare-all",
        "--cap-drop ALL",
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
    public CompletableFuture<Process> run(Path context, Path output, Path error, InputStream input, Long tl, Long ml) throws IOException {
        List<String> runArgs = new ArrayList<>(List.of(WRAPPER, tl.toString(), ml.toString()));

        runArgs.addAll(RUN_ARGS);

        Process runJava = new ProcessBuilder()
            .command(runArgs)
            .directory(context.toFile())
            .redirectOutput(output.toFile())
            .redirectError(error.toFile())
            .start();

        input.transferTo(runJava.getOutputStream());
        runJava.getOutputStream().write(CharsetUtil.UTF_8.encode("\n").array());
        runJava.getOutputStream().flush();
        runJava.getOutputStream().close();

        return runJava.onExit();
    }
}
