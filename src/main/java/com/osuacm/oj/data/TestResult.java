package com.osuacm.oj.data;

import com.osuacm.oj.services.SubmissionService;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.nio.file.Path;

@AllArgsConstructor
@Getter
@Setter
public class TestResult {
    SubmissionService.RESULT status;
    Long time;
    Long memory;
    String output;
    String info;
}
