package com.thoughtworks.projectDemo.amqp;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class IndexMigrateJobData {
    private Long indexId;
    private IndexMigrateJobType type;
}
