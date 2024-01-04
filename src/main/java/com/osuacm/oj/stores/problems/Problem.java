package com.osuacm.oj.stores.problems;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;

@Getter
@Setter
public class Problem {

    @Id
    private Long id;

    private String title;

    private String author;

    private Long tl;

    private Long ml;

    public Problem(String title, String author, Long tl, Long ml){
        setTitle(title);
        setAuthor(author);
        setTl(tl);
        setMl(ml);
    }
}
