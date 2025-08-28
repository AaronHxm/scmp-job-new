package org.example.scheduled.module;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProcessResult {
    private String contractNo;
    private boolean success;
    private String message;
    private int attempts;
}
