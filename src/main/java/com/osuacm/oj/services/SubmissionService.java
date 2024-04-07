package com.osuacm.oj.services;

import com.osuacm.oj.runtimes.GccRuntime;
import com.osuacm.oj.runtimes.Runtime;
import com.osuacm.oj.stores.problems.ProblemStore;
import com.osuacm.oj.data.SubmissionStatus;
import com.osuacm.oj.data.TestForm;
import io.netty.util.CharsetUtil;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Component;
import org.springframework.util.FileSystemUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.AbstractMap;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

@Component
public class SubmissionService {

    public enum RESULT {SUCCESS, SERVER_ERROR, COMPILE_ERROR, RUNTIME_ERROR, TIMEOUT, WRONG_ANSWER, MEMORY_EXC};
    private static final Log log = LogFactory.getLog(SubmissionService.class);
    private final Path runtimeContainer = Path.of("/runtime");

    private final Map<String, Runtime> runtimes = Map.of("gcc", new GccRuntime());

    @Autowired
    ProblemStore problemDatabase;

    private CompletableFuture<Process> compileJava(Path source, Path outputFile) {
        try {
            return new ProcessBuilder()
                    .command("cmd.exe", "/c", "javac -encoding UTF-8 -sourcepath . -d . ".concat(source.toFile().getName()))
                    .directory(runtimeContainer.toFile())
                    .redirectOutput(outputFile.toFile())
                    .redirectError(outputFile.toFile())
                    .start()
                    .onExit();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    };

    private CompletableFuture<Process> runJava(Path source, Path outputFile, InputStream input) {
        try {
            Process runJava = new ProcessBuilder()
                                    .command("cmd.exe", "/c", "java Solution -Dfile.encoding=UTF-8 -XX:+UseSerialGC -Xss64m -Xms1920m -Xmx1920m")
                                    .directory(runtimeContainer.toFile())
                                    .redirectOutput(outputFile.toFile())
                                    .redirectError(outputFile.toFile())
                                    .start();

            input.transferTo(runJava.getOutputStream());
            runJava.getOutputStream().write(CharsetUtil.UTF_8.encode("\n").array());
            runJava.getOutputStream().flush();
            runJava.getOutputStream().close();

            return runJava.onExit();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    };
    private Mono<Pair<RESULT, Path[]>> runTest(String runtime, Path input, Long tl, Long ml, boolean systemTest) throws RuntimeException {
        try {
            Mono<Tuple2<Path, RESULT>> compileProgram =
                Mono.fromCallable(() -> Files.createTempFile(runtimeContainer, "compile", ".error"))
                    .zipWhen(outputFile ->
                        Mono.fromCallable(() -> runtimes.get(runtime).compile(runtimeContainer, outputFile))
                            .flatMap(Mono::fromFuture)
                            .timeout(Duration.ofSeconds(20))
                            .map(process -> {
                                if(process.exitValue() == 0)
                                    return RESULT.SUCCESS;
                                else
                                    return RESULT.COMPILE_ERROR;
                            })
                            .onErrorReturn(RESULT.COMPILE_ERROR)
                    );

            Mono<Tuple2<Path[], RESULT>> runProgram =
                Mono.fromCallable(() -> new Path[]{Files.createTempFile(runtimeContainer, "run", ".out"), Files.createTempFile(runtimeContainer, "run", ".error")})
                    .zipWhen(files ->
                        Mono.using(
                            () -> new FileInputStream(input.toFile()),
                            stream -> Mono.fromCallable(() -> runtimes.get(runtime).run(runtimeContainer, files[0], files[1], stream, tl, ml)),
                            stream -> {
                                try {
                                    stream.close();
                                } catch (IOException e) {
                                    log.error("Cannot close input stream.", e);
                                }
                            }
                        )
                        .flatMap(Mono::fromFuture)
                        .timeout(Duration.ofSeconds(20))
                        .map(process -> RESULT.values()[process.exitValue()])
                        .onErrorReturn(RESULT.SERVER_ERROR)
                    );

            return compileProgram
                    .flatMap(data -> {
                        if(data.getT2() == RESULT.SUCCESS){
                            return runProgram.map(runResult -> Pair.of(runResult.getT2(), runResult.getT1()));
                        }else{
                            return Mono.just(Pair.of(data.getT2(), new Path[] {data.getT1()}));
                        }
                    });
        }catch (Exception e){
            throw new RuntimeException(e);
        }
    }

    public Mono<Pair<RESULT, String>> finalizeResults(Pair<RESULT, Path[]> runData, Path answer){
        if(runData.getFirst() != RESULT.SUCCESS){
            if(runData.getFirst() == RESULT.COMPILE_ERROR || runData.getFirst() == RESULT.SERVER_ERROR){
                return Flux.using(
                            () -> new BufferedReader(new FileReader(runData.getSecond()[0].toFile())),
                            reader -> Flux.fromStream(reader.lines()),
                            reader -> {
                                try {
                                    reader.close();
                                } catch (IOException e) {
                                    log.error("Cannot close reader!", e);
                                }
                            }
                        )
                        .reduce("Compilation message: ", (str, line) -> str.concat("\n").concat(line))
                        .map(message -> Pair.of(runData.getFirst(), message));
            }else {
                return Mono.just(Pair.of(runData.getFirst(), "Runtime error!"));
            }
        }else{
            Flux<String> outputLines =
                Flux.using(
                    () -> new BufferedReader(new FileReader(runData.getSecond()[0].toFile())),
                    reader -> Flux.fromStream(reader.lines()),
                    reader -> {
                        try {
                            reader.close();
                        } catch (IOException e) {
                            log.error("Cannot close reader!", e);
                        }
                    }
                )
                .filter(line -> line.trim().length() > 0);

            Flux<String> answerLines =
                Flux.using(
                    () -> new BufferedReader(new FileReader(answer.toFile())),
                    reader -> Flux.fromStream(reader.lines()),
                    reader -> {
                        try {
                            reader.close();
                        } catch (IOException e) {
                            log.error("Cannot close reader!", e);
                        }
                    }
                )
                .filter(line -> line.trim().length() > 0);

            return outputLines
                    .zipWith(answerLines)
                    .doOnNext(log::info)
                    .all(linePair -> linePair.getT1().trim().equals(linePair.getT2().trim()))
                    .map(result -> Pair.of(result ? RESULT.SUCCESS : RESULT.WRONG_ANSWER, ""));
        }
    }

    public Mono<SubmissionStatus> runFormalTest(Long id, TestForm testForm) {
        Mono<Path> loadSource =
                Mono.fromCallable(() -> Files.createFile(runtimeContainer.resolve(runtimes.get(testForm.getType()).getSource())))
                        .flatMap(path -> testForm.getCode().transferTo(path).then(Mono.just(path)));

        Flux<File> loadInputFiles =
            Mono.fromCallable(() -> ProblemService.testDatasDirectory.resolve(String.valueOf(id)))
                .flatMapMany(path ->
                    Flux
                        .fromArray(
                            Objects.requireNonNull(
                                    path.resolve("secret")
                                        .toFile()
                                        .listFiles(pathname -> pathname.getName().endsWith(".in"))
                            )
                        )
                        .sort(Comparator.comparing(File::getName))
                );

        Flux<File> loadAnswerFiles =
            Mono.fromCallable(() -> ProblemService.testDatasDirectory.resolve(String.valueOf(id)))
                .flatMapMany(path ->
                    Flux
                        .fromArray(
                            Objects.requireNonNull(
                                path.resolve("secret")
                                    .toFile()
                                    .listFiles(pathname -> pathname.getName().endsWith(".ans"))
                            )
                        )
                        .sort(Comparator.comparing(File::getName))
                );

        return
            problemDatabase
                .findById(id)
                .zipWith(loadSource)
                .flatMapMany(testData ->
                    loadInputFiles
                        .flatMapSequential(testFile -> runTest(testForm.getType(), testFile.toPath(), testData.getT1().getTl(), testData.getT1().getMl(), true))
                        .zipWith(loadAnswerFiles)
                )
                .flatMap(runAndAnswerData -> finalizeResults(runAndAnswerData.getT1(), runAndAnswerData.getT2().toPath()))
                .<SubmissionStatus>handle((result, sink) -> {
                    if(result.getFirst() != RESULT.SUCCESS){
                        sink.next(new SubmissionStatus(false, result.getFirst().name(), result.getSecond()));
                        sink.complete();
                    }
                })
                .doFinally(signal -> {
                    //clean here
                })
                .next()
                .log()
                .switchIfEmpty(Mono.just(new SubmissionStatus(true, RESULT.SUCCESS.name(), "All test cases passed!")));
    }

    public Mono<Pair<File, RESULT>> runCustomTest(TestForm testForm) {
        Mono<Path> loadSource =
            Mono.fromCallable(() -> Files.createFile(runtimeContainer.resolve(runtimes.get(testForm.getType()).getSource())))
                .flatMap(path -> testForm.getCode().transferTo(path).then(Mono.just(path)));

        Mono<Path> loadInput =
                Mono.fromCallable(() -> Files.createTempFile(runtimeContainer, "test", ".in"))
                .flatMap(path -> testForm.getInput().transferTo(path).then(Mono.just(path)));

        return loadSource
                .zipWith(loadInput)
                .flatMap(files -> runTest(testForm.getType(), files.getT1(), 15L, 2000L, false))
                .doOnSuccess(log::info)
                .map(data -> {
                    if(data.getFirst() == RESULT.COMPILE_ERROR){
                        return Pair.of(data.getSecond()[0].toFile(), data.getFirst());
                    } else if(data.getFirst() == RESULT.SUCCESS){
                        return Pair.of(data.getSecond()[0].toFile(), data.getFirst());
                    } else {
                        return Pair.of(data.getSecond()[1].toFile(), data.getFirst());
                    }
                })
                .doFinally(signal -> {
                    //clean here
                })
                .log();
    }
}
