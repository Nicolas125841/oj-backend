package com.osuacm.oj.stores.problems;

import com.osuacm.oj.stores.problems.Problem;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.data.repository.reactive.ReactiveSortingRepository;

public interface ProblemStore extends ReactiveCrudRepository<Problem, Long>, ReactiveSortingRepository<Problem, Long> {
}
