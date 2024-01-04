package com.osuacm.oj.data;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
@Getter
@Setter
public class SubmissionStatus {
    private Boolean success;
    private String statusMessage;
    private String infoMessage;
}
