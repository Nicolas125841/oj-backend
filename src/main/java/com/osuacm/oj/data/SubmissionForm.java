package com.osuacm.oj.data;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.springframework.http.codec.multipart.FilePart;

@AllArgsConstructor
@Getter
@Setter
public class SubmissionForm {
    private String type;
    private FilePart code;
    private Long tl;
    private Long ml;
}
