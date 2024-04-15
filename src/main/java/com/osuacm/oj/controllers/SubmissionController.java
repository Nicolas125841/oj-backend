package com.osuacm.oj.controllers;

import com.osuacm.oj.services.SubmissionService;
import com.osuacm.oj.data.SubmissionStatus;
import com.osuacm.oj.data.TestForm;
import jakarta.validation.Valid;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.*;
import java.util.regex.Pattern;

@CrossOrigin
@RestController
@RequestMapping("/submit")
public class SubmissionController {

    private static final Log log = LogFactory.getLog(SubmissionController.class);
    private final Pattern dataRegex = Pattern.compile("[0-9]*");

    @Autowired
    SubmissionService submissionService;

    @PostMapping(path = "/{id}")
    public Mono<SubmissionStatus> submitProblem(@PathVariable Long id, @Valid Mono<TestForm> testFormMono){
        return testFormMono.flatMap(testForm -> submissionService.runFormalTest(id, testForm));
    }

    @PostMapping(path = "/customtest")
    public Flux<String> testProblem(@Valid Mono<TestForm> testFormMono){
        return testFormMono
                .flatMap(submissionService::runCustomTest)
                .flatMapMany(resultData ->
                    Flux.using(
                        () -> new BufferedReader(new FileReader(resultData.getOutput().toFile())),
                        reader ->
                            Flux.fromStream(reader.lines())
                                .startWith(resultData.getStatus().name(), "Time (ms): " + resultData.getTime() / 1000L, "Memory (mb): " + resultData.getMemory() / 1000000L)
                                .filter(str -> resultData.getStatus() == SubmissionService.RESULT.SUCCESS || !dataRegex.matcher(str).matches()),
                        reader -> {
                            try {
                                reader.close();
                            } catch (IOException e) {
                                log.error("Error closing output file.", e);
                            }
                        }
                    )
                )
                .take(21)
                .concatWithValues("[END/TRUNCATE]")
                .map(line -> line.concat("\n"))
                .log();
    }
}
