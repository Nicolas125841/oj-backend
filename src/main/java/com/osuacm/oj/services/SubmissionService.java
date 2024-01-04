package com.osuacm.oj.services;

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
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

@Component
public class SubmissionService {

    public enum RESULT {SERVER_ERROR, COMPILE_ERROR, RUNTIME_ERROR, TIMEOUT, SUCCESS, WRONG_ANSWER};
    private static final Log log = LogFactory.getLog(SubmissionService.class);
    private final Path runtimeContainer = Path.of("/runtime");

    @Autowired
    ProblemStore problemDatabase;

    @Autowired
    Map<String, Runtime> runtimes;

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
    private Mono<Pair<RESULT, Pair<Path, Path>>> runTest(Path source, Path input, long tl, boolean systemTest) throws RuntimeException {
        try {
            Mono<Tuple2<Path, RESULT>> compileProgram =
                Mono.fromCallable(() -> Files.createTempFile(runtimeContainer, "test", ".out"))
                    .zipWhen(outputFile ->
                        Mono.fromCallable(() -> compileJava(source, outputFile))
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

            Mono<Tuple2<Path, RESULT>> runProgram =
                Mono.fromCallable(() -> Files.createTempFile(runtimeContainer, "test", ".out"))
                    .zipWhen(outputFile ->
                        Mono.using(
                            () -> new FileInputStream(input.toFile()),
                            stream -> Mono.fromCallable(() -> runJava(source, outputFile, stream)),
                            stream -> {
                                try {
                                    stream.close();
                                } catch (IOException e) {
                                    log.error("Cannot close input stream.", e);
                                }
                            }
                        )
                        .flatMap(Mono::fromFuture)
                        .timeout(Duration.ofSeconds(tl))
                        .map(process -> {
                            if(process.exitValue() == 0)
                                return RESULT.SUCCESS;
                            else
                                return RESULT.RUNTIME_ERROR;
                        })
                        .onErrorReturn(RESULT.TIMEOUT)
                    );

            return compileProgram
                    .map(compileResult ->  Pair.of(compileResult.getT2(), Pair.of(compileResult.getT1(), Path.of(""))))
                    .flatMap(data -> {
                        if(data.getFirst() == RESULT.SUCCESS){
                            return runProgram.map(runResult -> Pair.of(runResult.getT2(), Pair.of(data.getSecond().getFirst(), runResult.getT1())));
                        }else{
                            return Mono.just(data);
                        }
                    })
                    .doFinally(signal -> {
                        try {
                            FileSystemUtils.deleteRecursively(source);

                            if(!systemTest)
                                FileSystemUtils.deleteRecursively(input);
                        } catch (IOException e) {
                            log.error("Cannot clean files!", e);
                        }
                    });
        }catch (Exception e){
            throw new RuntimeException(e);
        }
    }

    public Mono<Pair<RESULT, String>> finalizeResults(Pair<RESULT, Pair<Path, Path>> runData, Path answer){
        if(runData.getFirst() != RESULT.SUCCESS){
            if(runData.getFirst() == RESULT.COMPILE_ERROR || runData.getFirst() == RESULT.SERVER_ERROR){
                return Flux.using(
                            () -> new BufferedReader(new FileReader(runData.getSecond().getFirst().toFile())),
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
                    () -> new BufferedReader(new FileReader(runData.getSecond().getSecond().toFile())),
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
                Mono.fromCallable(() -> Files.createTempFile(runtimeContainer, "test", testForm.getType()))
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
                        .flatMapSequential(testFile -> runTest(testData.getT2(), testFile.toPath(), 2, true))
                        .zipWith(loadAnswerFiles)
                )
                .flatMap(runAndAnswerData -> finalizeResults(runAndAnswerData.getT1(), runAndAnswerData.getT2().toPath()))
                .<SubmissionStatus>handle((result, sink) -> {
                    if(result.getFirst() != RESULT.SUCCESS){
                        sink.next(new SubmissionStatus(false, result.getFirst().name(), result.getSecond()));
                        sink.complete();
                    }
                })
                .next()
                .log()
                .switchIfEmpty(Mono.just(new SubmissionStatus(true, RESULT.SUCCESS.name(), "All test cases passed!")));
    }

    public Mono<Pair<File, RESULT>> runCustomTest(TestForm testForm) {
        Mono<Path> loadSource =
                Mono.fromCallable(() -> Files.createTempFile(runtimeContainer, "test", testForm.getType()))
                .flatMap(path -> testForm.getCode().transferTo(path).then(Mono.just(path)));

        Mono<Path> loadInput =
                Mono.fromCallable(() -> Files.createTempFile(runtimeContainer, "test", ".in"))
                .flatMap(path -> testForm.getInput().transferTo(path).then(Mono.just(path)));

        return loadSource
                .zipWith(loadInput)
                .flatMap(files -> runTest(files.getT1(), files.getT2(), 15, false))
                .doOnSuccess(log::info)
                .map(data -> {
                    if(data.getFirst() == RESULT.COMPILE_ERROR){
                        FileSystemUtils.deleteRecursively(data.getSecond().getSecond().toFile());

                        return Pair.of(data.getSecond().getFirst().toFile(), data.getFirst());
                    }else {
                        FileSystemUtils.deleteRecursively(data.getSecond().getFirst().toFile());

                        return Pair.of(data.getSecond().getSecond().toFile(), data.getFirst());
                    }
                })
                .log();
    }
}
