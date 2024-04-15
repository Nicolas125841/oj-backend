package com.osuacm.oj.services;

import com.osuacm.oj.data.TestResult;
import com.osuacm.oj.runtimes.GccRuntime;
import com.osuacm.oj.runtimes.Runtime;
import com.osuacm.oj.stores.problems.ProblemStore;
import com.osuacm.oj.data.SubmissionStatus;
import com.osuacm.oj.data.TestForm;
import org.apache.commons.io.input.ReversedLinesFileReader;
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
import java.nio.charset.Charset;
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

    private final Map<String, Runtime> runtimes = Map.of("C 98", new GccRuntime());

    @Autowired
    ProblemStore problemDatabase;

    private Mono<Pair<RESULT, Path[]>> runTest(String runtime, Path input, Long tl, Long ml) throws RuntimeException {
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
                        Mono.fromCallable(() -> runtimes.get(runtime).run(runtimeContainer, files[0], files[1], input, tl, ml))
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
            } else if(runData.getFirst() == RESULT.TIMEOUT) {
                return Mono.just(Pair.of(runData.getFirst(), "TLE"));
            } else if(runData.getFirst() == RESULT.MEMORY_EXC) {
                return Mono.just(Pair.of(runData.getFirst(), "MLE"));
            } else {
                return Mono.just(Pair.of(runData.getFirst(), "RTE"));
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
                        .flatMapSequential(testFile -> runTest(testForm.getType(), testFile.toPath(), testData.getT1().getTl(), testData.getT1().getMl()))
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

    public Mono<TestResult> runCustomTest(TestForm testForm) {
        Mono<Path> loadSource =
            Mono.fromCallable(() -> Files.createFile(runtimeContainer.resolve(runtimes.get(testForm.getType()).getSource())))
                .flatMap(path -> testForm.getCode().transferTo(path).then(Mono.just(path)));

        Mono<Path> loadInput =
                Mono.fromCallable(() -> Files.createTempFile(runtimeContainer, "test", ".in"))
                .flatMap(path -> testForm.getInput().transferTo(path).then(Mono.just(path)));

        try {
            FileSystemUtils.deleteRecursively(runtimeContainer);
            Files.createDirectory(runtimeContainer);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return loadSource
                .then(loadInput)
                .flatMap(file -> runTest(testForm.getType(), file, 15000L, 3000L))
                .doOnSuccess(log::info)
                .map(data -> {
                        if (data.getFirst() == RESULT.COMPILE_ERROR) {
                            return new TestResult(data.getFirst(), 0L, 0L, data.getSecond()[0]);
                        } else {
                            try(ReversedLinesFileReader rlr = new ReversedLinesFileReader(data.getSecond()[1].toFile(), Charset.defaultCharset())) {
                                if (data.getFirst() == RESULT.SUCCESS) {
                                    return new TestResult(data.getFirst(), Long.parseLong(rlr.readLine()), Long.parseLong(rlr.readLine()), data.getSecond()[0]);
                                } else {
                                    return new TestResult(data.getFirst(), Long.parseLong(rlr.readLine()), Long.parseLong(rlr.readLine()), data.getSecond()[1]);
                                }
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }
                })
                .log();
    }
}
