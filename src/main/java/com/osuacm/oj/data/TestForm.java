package com.osuacm.oj.data;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.springframework.http.codec.multipart.FilePart;

@AllArgsConstructor
@Getter
@Setter
public class TestForm {
    private String type;
    private FilePart code;
    private FilePart input;
}
