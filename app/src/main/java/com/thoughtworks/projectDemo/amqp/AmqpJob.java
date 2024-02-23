package com.thoughtworks.projectDemo.amqp;

import com.thoughtworks.projectDemo.enums.JobStatus;
import com.thoughtworks.projectDemo.enums.JobType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AmqpJob {
    private Long id;
    private JobType jobType;
    private String jobData;
    private JobStatus status;
    private LocalDateTime createAt;
    private LocalDateTime updateAt;
}
