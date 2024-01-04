package com.osuacm.oj.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.osuacm.oj.stores.problems.Problem;
import com.osuacm.oj.data.ProblemForm;
import com.osuacm.oj.stores.problems.ProblemStore;
import com.osuacm.oj.utils.FileUtil;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuple2;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class ProblemService {
    private static final Log log = LogFactory.getLog(ProblemService.class);
    private static final long PAGE_SIZE = 10;
    public static final Path problemsDirectory = Path.of("/data");
    public static final Path testDatasDirectory = Path.of("/tests");

    @Autowired
    ProblemStore problemDatabase;

    @Autowired
    FileUtil fileUtil;

    public Mono<Map<String, Long>> getMetadata(){
        return problemDatabase
                .count()
                .flux()
                .concatWithValues(PAGE_SIZE)
                .zipWithIterable(List.of("total", "size"))
                .collectMap(Tuple2::getT2, Tuple2::getT1);
    }

    public Flux<Problem> getPage(Long pageNumber){
        return problemDatabase
                .findAll()
                .skip(pageNumber * PAGE_SIZE)
                .take(PAGE_SIZE);
    }

    private Mono<Void> storeProblem(String id, ProblemForm formData) {
        final Path problemDirectory = problemsDirectory.resolve(id);
        final Path testDataDirectory = testDatasDirectory.resolve(id);
        final Path jsonFile = problemDirectory.resolve("metadata.json");
        final Path pdfFile = problemDirectory.resolve("statement.pdf");
        final Path zipFile = testDataDirectory.resolve("tests.zip");

        return Mono
            .fromFuture(fileUtil.createFile(problemDirectory, FileUtil.FileType.DIRECTORY))
            .and(Mono.fromFuture(fileUtil.createFile(testDataDirectory, FileUtil.FileType.DIRECTORY)))
            .then(
                Mono.fromCallable(() ->
                    Files.writeString(jsonFile, new ObjectMapper().writeValueAsString(formData.generateMeta()))
                )
                .and(formData.getStatement().transferTo(pdfFile))
                .and(formData.getTests()
                    .transferTo(zipFile)
                    .thenReturn(zipFile)
                    .flatMap(file -> Mono.fromFuture(fileUtil.unzip(zipFile)))
                )
                .subscribeOn(Schedulers.boundedElastic())
                .doOnError(e -> {
                    fileUtil.deleteFile(testDataDirectory, FileUtil.FileType.DIRECTORY_RECURSIVE);
                    fileUtil.deleteFile(problemDirectory, FileUtil.FileType.DIRECTORY_RECURSIVE);
                })
            );
    }

    public Mono<Void> createProblem(ProblemForm formData){
        return problemDatabase
                .save(formData.generateProblem())
                .flatMap(id ->
                    storeProblem(id.getId().toString(), formData)
                        .log()
                        .onErrorResume(Exception.class, (error) ->
                            problemDatabase.deleteById(id.getId())
                                .then(Mono.error(new RuntimeException("Could not store problem.")))
                        )
                )
                .timeout(Duration.ofSeconds(10));
    }

    public Mono<Void> deleteProblem(Long id) {
        return problemDatabase
            .findById(id)
            .single()
            .then(problemDatabase.deleteById(id))
            .and(Mono.fromFuture(fileUtil.deleteFile(problemsDirectory.resolve(id.toString()), FileUtil.FileType.DIRECTORY_RECURSIVE)))
            .and(Mono.fromFuture(fileUtil.deleteFile(testDatasDirectory.resolve(id.toString()), FileUtil.FileType.DIRECTORY_RECURSIVE)))
            .log()
            .onErrorStop()
            .timeout(Duration.ofSeconds(10));
    }
}
