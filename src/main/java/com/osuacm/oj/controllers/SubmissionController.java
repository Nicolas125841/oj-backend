package com.osuacm.oj.controllers;

import com.osuacm.oj.data.TestResult;
import com.osuacm.oj.services.SubmissionService;
import com.osuacm.oj.data.TestForm;
import jakarta.validation.Valid;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@CrossOrigin
@RestController
@RequestMapping("/submit")
public class SubmissionController {

    private static final Log log = LogFactory.getLog(SubmissionController.class);

    @Autowired
    SubmissionService submissionService;

    @PostMapping(path = "/{id}")
    public Mono<TestResult> submitProblem(@PathVariable Long id, @Valid Mono<TestForm> testFormMono){
        return testFormMono
            .flatMap(testForm -> submissionService.runFormalTest(id, testForm));
    }

    @PostMapping(path = "/customtest")
    public Mono<TestResult> testProblem(@Valid Mono<TestForm> testFormMono){
        return testFormMono.flatMap(submissionService::runCustomTest);
//        return testFormMono
//            .flatMap(submissionService::runCustomTest)
//            .flatMapMany(resultData ->
//                Flux.fromIterable(List.of("Info:\n" + resultData.getInfo(), "Output:\n" + resultData.getOutput()))
//                    .startWith(resultData.getStatus().name(), "Time (ms): " + resultData.getTime() / 1000L, "Memory (mb): " + resultData.getMemory() / 1000000L)
//            )
//            .concatWithValues("[END/TRUNCATE]")
//            .map(line -> line.concat("\n"))
//            .log();
    }
}
