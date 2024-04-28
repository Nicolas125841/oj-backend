package com.osuacm.oj.services;

import com.google.common.collect.Streams;
import com.osuacm.oj.data.TestForm;
import com.osuacm.oj.data.TestResult;
import com.osuacm.oj.runtimes.*;
import com.osuacm.oj.runtimes.Runtime;
import com.osuacm.oj.stores.problems.Problem;
import com.osuacm.oj.stores.problems.ProblemStore;
import org.apache.commons.io.input.ReversedLinesFileReader;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.FileSystemUtils;
import reactor.core.publisher.Mono;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class SubmissionService {

    public enum RESULT {SUCCESS, SERVER_ERROR, COMPILE_ERROR, RUNTIME_ERROR, TIMEOUT, WRONG_ANSWER, MEMORY_EXC}
    private static final Log log = LogFactory.getLog(SubmissionService.class);
    private final Path runtimeContainer = Path.of("/runtime");
    private final Long CUSTOM_TEST = -1L;

    private final ExecutorService submitQueue = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<>(20));

    private final String FILE_PATTERN = "run(?:[0-9]*)(?:\\.in|\\.out|\\.error)$";
    private final Map<String, Runtime> runtimes = Map.of(
        "C 98", new GccRuntime(),
        "Java 17", new OpenJDK17Runtime(),
        "C++ 17", new Gpp17Runtime(),
        "Python 3", new Python3Runtime()
    );
    @Autowired
    private ProblemStore problemDatabase;

    private List<Path> collectProblemFiles(Long id, String type) throws FileNotFoundException {
        Path inputDirectory = ProblemService.testDatasDirectory.resolve(String.valueOf(id));

        if(inputDirectory.toFile().exists() && inputDirectory.toFile().isDirectory()) {
            return Arrays.stream(Objects.requireNonNull(inputDirectory.toFile()
                    .listFiles(pathname -> pathname.getName().endsWith(type))))
                .sorted(Comparator.comparing(File::getName))
                .map(File::toPath)
                .toList();
        } else {
            throw new FileNotFoundException();
        }
    }

    private RESULT compileProgram(String runtime, Path errorTarget) {
        try {
            Process result = runtimes.get(runtime).compile(runtimeContainer, errorTarget);

            try {
                result = result.onExit().get(20, TimeUnit.SECONDS);

                if (result.exitValue() == 0) {
                    return RESULT.SUCCESS;
                }
            } catch (ExecutionException | InterruptedException | TimeoutException e) {
                result.destroyForcibly();

                log.error("Compilation error:", e);
            }
        } catch (IOException e) {
            log.error("Compilation error:", e);
        }

        return RESULT.COMPILE_ERROR;
    }

    private TestResult executeTest(Problem problem, TestForm testForm) throws IOException {
        try {
            FileSystemUtils.deleteRecursively(runtimeContainer);
            Files.createDirectory(runtimeContainer);
        } catch (IOException e) {
            return new TestResult(RESULT.SERVER_ERROR, 0L, 0L, "", "Error setting up runtime");
        }

        Path programSource = Files.createFile(runtimeContainer.resolve(runtimes.get(testForm.getType()).getSource()));
        Path errorFile = Files.createTempFile(runtimeContainer, "compile", ".error");
        List<Path> programInput, expectedOutput;

        testForm.getCode().transferTo(programSource.toFile()).block(Duration.ofSeconds(1));

        if(Objects.equals(problem.getId(), CUSTOM_TEST)) {
            programInput = List.of(Files.createTempFile(runtimeContainer, "test", ".in"));
            expectedOutput = List.of();

            testForm.getInput().transferTo(programInput.get(0)).block(Duration.ofSeconds(1));
        } else {
            programInput = collectProblemFiles(problem.getId(), ".in");
            expectedOutput = collectProblemFiles(problem.getId(), ".ans");
        }

        if(compileProgram(testForm.getType(), errorFile) == RESULT.COMPILE_ERROR) {
            return new TestResult(RESULT.COMPILE_ERROR, 0L, 0L, "", Files.readAllLines(errorFile).stream().limit(20).collect(Collectors.joining("\n")));
        }

        Long maxTime = 0L, maxMem = 0L;

        for(int i = 0; i < programInput.size(); i++) {
            Arrays.stream(Objects.requireNonNull(runtimeContainer.toFile().listFiles(file -> Pattern.matches(FILE_PATTERN, file.getName()))))
                .toList().forEach(FileSystemUtils::deleteRecursively);

            Path output = Files.createTempFile(runtimeContainer, "run", ".out");
            Path error = Files.createTempFile(runtimeContainer, "run", ".error");
            Process result = runtimes.get(testForm.getType()).run(runtimeContainer, output, error, programInput.get(i), problem.getTl(), problem.getMl());

            try {
                result = result.onExit().get(20, TimeUnit.SECONDS);

                RESULT runtimeResult = RESULT.values()[result.exitValue()];

                if(runtimeResult == RESULT.SERVER_ERROR) {
                    return new TestResult(RESULT.SERVER_ERROR, 0L, 0L, "", "Error running problem");
                } else {
                    try(ReversedLinesFileReader reader = new ReversedLinesFileReader(error.toFile())) {
                        maxTime = Math.max(maxTime, Long.parseLong(reader.readLine()));
                        maxMem = Math.max(maxMem, Long.parseLong(reader.readLine()));
                    }

                    if(Objects.equals(problem.getId(), CUSTOM_TEST)) {
                        Integer errorLength = Files.readAllLines(error).size();

                        return new TestResult(
                            runtimeResult,
                            maxTime,
                            maxMem,
                            Files.readAllLines(output).stream().limit(20).collect(Collectors.joining("\n")),
                            Files.readAllLines(error).subList(0, Math.max(0, errorLength - 2)).stream().limit(20).collect(Collectors.joining("\n")));
                    } else if(runtimeResult != RESULT.SUCCESS) {
                        return new TestResult(runtimeResult, maxTime, maxMem, runtimeResult.name(), "Test case: " + i);
                    }

                    try(BufferedReader outputReader = new BufferedReader(new FileReader(output.toFile()));
                        BufferedReader answerReader = new BufferedReader(new FileReader(expectedOutput.get(i).toFile()))) {
                        if(!Streams.zip(outputReader.lines(), answerReader.lines(), (outLine, ansLine) -> outLine.trim().equals(ansLine.trim())).allMatch(Boolean::booleanValue)) {
                            outputReader.close();
                            answerReader.close();

                            return new TestResult(RESULT.WRONG_ANSWER, maxTime, maxMem, "", "Test case: " + i);
                        }
                    }
                }
            } catch (ExecutionException | InterruptedException | TimeoutException e) {
                result.destroyForcibly();

                return new TestResult(RESULT.SERVER_ERROR, 0L, 0L, "", "Idleness limit exceeded");
            }
        }

        return new TestResult(RESULT.SUCCESS, maxTime, maxMem, "", "All tests passed");
    }

    public Mono<TestResult> runFormalTest(Long id, TestForm testForm) {
        return
            problemDatabase
                .findById(id)
                .flatMap(problem -> Mono.fromCallable(() -> submitQueue.submit(() -> executeTest(problem, testForm)).get()))
                .defaultIfEmpty(new TestResult(RESULT.SERVER_ERROR, 0L, 0L, "", "Problem does not exist"));
    }

    public Mono<TestResult> runCustomTest(TestForm testForm) {
        Problem problem = new Problem("CUSTOM", "CUSTOM", 15000L, 3000L);

        problem.setId(CUSTOM_TEST);

        return Mono.fromCallable(() -> submitQueue.submit(() -> executeTest(problem, testForm)).get());
    }
}
