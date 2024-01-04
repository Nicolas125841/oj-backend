package com.osuacm.oj.controllers;

import com.osuacm.oj.stores.problems.Problem;
import com.osuacm.oj.data.ProblemForm;
import com.osuacm.oj.services.ProblemService;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

@CrossOrigin
@RestController
@RequestMapping("/problems")
public class ProblemController {

    private static final Log log = LogFactory.getLog(ProblemController.class);

    @Autowired
    ProblemService problemService;

    @GetMapping(path = "/meta")
    public Mono<Map<String, Long>> getMetadata(){
        return problemService.getMetadata();
    }

    @GetMapping(path = "/page/{pageNumber}")
    public Flux<Problem> getPage(@PathVariable Long pageNumber){
        return problemService.getPage(pageNumber);
    }

    @CrossOrigin(originPatterns = "http://127.0.0.1:5173", allowCredentials = "true")
    @PostMapping(path = "/create")
    public Mono<Void> createProblem(Mono<ProblemForm> formData){
        return formData.flatMap(problemService::createProblem);
    }

    @CrossOrigin(originPatterns = "http://127.0.0.1:5173", allowCredentials = "true")
    @DeleteMapping(path = "/delete/{id}")
    public Mono<Void> deleteProblem(@PathVariable Long id) {
        return problemService.deleteProblem(id);
    }
}
