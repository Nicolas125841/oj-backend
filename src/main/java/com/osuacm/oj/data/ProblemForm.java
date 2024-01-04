package com.osuacm.oj.data;

import com.osuacm.oj.stores.problems.Problem;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.springframework.http.codec.multipart.FilePart;

@AllArgsConstructor
@Getter
@Setter
public class ProblemForm {
    private String title;
    private String author;
    private Long tl;
    private Long ml;

    private FilePart statement;

    private FilePart tests;

    public Problem generateProblem(){
        return new Problem(getTitle(), getAuthor(), getTl(), getMl());
    }

    public ProblemMeta generateMeta(){
        return new ProblemMeta(getTitle(), getAuthor(), getTl(), getMl());
    }

    @Override
    public String toString() {
        return String.format("metadata: { title: %s, author: %s, tl: %d, ml: %d }, statement: %s, tests: %s", title, author, tl, ml, statement.filename(), tests.filename());
    }
}
