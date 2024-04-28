package com.osuacm.oj.runtimes;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class OpenJDK17Runtime implements Runtime {
    final String SOURCE = "Solution.java";
    final String COMPILER = "javac";

    final List<String> RUN_ARGS = List.of(
        "bwrap",
        "--ro-bind", "/etc/alternatives", "/etc/alternatives",
        "--setenv", "JAVA_HOME", "/etc/alternatives/jre_17_openjdk",
        "--setenv", "LD_LIBRARY_PATH", "/etc/alternatives/jre_17_openjdk/lib",
        "--ro-bind", "/usr", "/usr",
        "--ro-bind", "/runtime", "/subject",
        "--symlink", "usr/lib", "/lib",
        "--symlink", "usr/lib64", "/lib64",
        "--chdir", "/subject",
        "--unshare-user",
        "--unshare-net",
        "--unshare-ipc",
        "--unshare-cgroup",
        "--unshare-uts",
        "--cap-drop", "ALL",
        "--die-with-parent",
        "--new-session",
        "java", "Solution"
    );

    @Override
    public Process compile(Path context, Path error) throws IOException {
        return new ProcessBuilder()
            .command(COMPILER, SOURCE)
            .directory(context.toFile())
            .redirectError(error.toFile())
            .start();
    }

    @Override
    public Process run(Path context, Path output, Path error, Path input, Long tl, Long ml) {
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

            return runJava;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getSource() {
        return SOURCE;
    }
}
