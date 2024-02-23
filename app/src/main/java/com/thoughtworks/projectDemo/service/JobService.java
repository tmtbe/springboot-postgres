package com.thoughtworks.projectDemo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thoughtworks.projectDemo.amqp.AmqpService;
import com.thoughtworks.projectDemo.amqp.IndexMigrateJobData;
import com.thoughtworks.projectDemo.amqp.IndexMigrateJobType;
import com.thoughtworks.projectDemo.convert.DataMapper;
import com.thoughtworks.projectDemo.enums.JobStatus;
import com.thoughtworks.projectDemo.enums.JobType;
import com.thoughtworks.projectDemo.enums.LogType;
import com.thoughtworks.projectDemo.tables.daos.JobDao;
import com.thoughtworks.projectDemo.tables.daos.JobLogDao;
import com.thoughtworks.projectDemo.tables.pojos.Job;
import com.thoughtworks.projectDemo.tables.pojos.JobLog;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import org.jooq.JSONB;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class JobService {
    private final JobDao jobDao;
    private final JobLogDao jobLogDao;
    private final ObjectMapper objectMapper;
    private final AmqpService amqpService;
    private final DataMapper dataMapper;

    public Job createReinsertIndexDocJob(Long indexId) {
        var jsonData = IndexMigrateJobData.builder()
                .indexId(indexId)
                .type(IndexMigrateJobType.REINSERT).build();
        var job = createJob(JobType.IndexMigrate, jsonData);
        amqpService.indexMigrateJob(dataMapper.toAmqpJob(job));
        return job;
    }

    public Job createAppendIndexDocJob(Long indexId) {
        var jsonData = IndexMigrateJobData.builder()
                .indexId(indexId)
                .type(IndexMigrateJobType.APPEND).build();
        var job = createJob(JobType.IndexMigrate, jsonData);
        amqpService.indexMigrateJob(dataMapper.toAmqpJob(job));
        return job;
    }

    @SneakyThrows
    public Job createJob(JobType jobType, Object jobData) {
        Job job = new Job();
        job.setStatus(JobStatus.Created);
        job.setJobType(jobType);
        var json = objectMapper.writeValueAsString(jobData);
        job.setJobData(JSONB.valueOf(json));
        jobDao.insert(job);
        return job;
    }

    public void updateJobStatus(Long jobId, JobStatus jobStatus) {
        jobDao.fetchOptionalById(jobId).ifPresent(job -> {
            job.setStatus(jobStatus);
            jobDao.update(job);
        });
    }

    public void addJobLog(Long jobId, LogType logType, String log) {
        jobLogDao.insert(new JobLog().setLogType(logType).setJobId(jobId).setLog(log));
    }
}
