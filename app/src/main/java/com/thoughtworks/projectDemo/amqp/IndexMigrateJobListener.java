package com.thoughtworks.projectDemo.amqp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thoughtworks.projectDemo.enums.JobStatus;
import com.thoughtworks.projectDemo.enums.LogType;
import com.thoughtworks.projectDemo.service.IndexService;
import com.thoughtworks.projectDemo.service.JobService;
import com.thoughtworks.projectDemo.tables.daos.IndexDao;
import com.thoughtworks.projectDemo.tables.daos.IndexDocRecordDao;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.*;
import org.springframework.stereotype.Component;

import java.util.Objects;

import static com.thoughtworks.projectDemo.Tables.INDEX_DOC_RECORD;

@Component
@RabbitListener(bindings = @QueueBinding(
        value = @Queue("indexMigrateJobQueue"),
        exchange = @Exchange(value = "indexMigrateJobExchange", type = ExchangeTypes.DIRECT),
        key = "job.indexMigrate"
))
@AllArgsConstructor
public class IndexMigrateJobListener {
    private final IndexDao indexDao;
    private final IndexDocRecordDao indexDocRecordDao;
    private final IndexService indexService;
    private final ObjectMapper objectMapper;
    private final JobService jobService;

    @RabbitHandler
    @SneakyThrows
    public void onMessage(AmqpJob job) {
        //var job = objectMapper.readValue(message,Job.class);
        assert job.getJobData() != null;
        jobService.updateJobStatus(job.getId(), JobStatus.Running);
        try {
            var jobData = objectMapper.readValue(job.getJobData(), IndexMigrateJobData.class);
            var index = indexDao.fetchOneById(jobData.getIndexId());
            if (Objects.isNull(index)) {
                throw new RuntimeException("Index not found");
            }
            switch (jobData.getType()) {
                case APPEND:
                    var indexDocRecord = indexDocRecordDao.fetchOne(INDEX_DOC_RECORD.INDEX_ID, index.getId());
                    if (Objects.isNull(indexDocRecord)) {
                        // 代表一个数据都没导入过
                        indexService.appendDataProcess(index.getName(), 0L, e -> {
                            jobService.addJobLog(job.getId(), LogType.Error, e.getMessage());
                        });
                    } else {
                        // 代表有数据导入过
                        indexService.appendDataProcess(index.getName(), indexDocRecord.getLatestDocId(), e -> {
                            jobService.addJobLog(job.getId(), LogType.Error, e.getMessage());
                        });
                    }
                    break;
                case REINSERT:
                    indexService.appendDataProcess(index.getName(), 0L, e -> {
                        jobService.addJobLog(job.getId(), LogType.Error, e.getMessage());
                    });
                    break;
            }
        } catch (Exception e) {
            jobService.updateJobStatus(job.getId(), JobStatus.Failed);
            return;
        }
        jobService.updateJobStatus(job.getId(), JobStatus.Succeed);
    }
}
