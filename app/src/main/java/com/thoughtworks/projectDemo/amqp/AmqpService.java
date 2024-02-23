package com.thoughtworks.projectDemo.amqp;

import lombok.AllArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;


@Service
@AllArgsConstructor
public class AmqpService {
    private final RabbitTemplate rabbitTemplate;

    public void indexMigrateJob(AmqpJob job) {
        rabbitTemplate.convertAndSend("indexMigrateJobExchange", "job.indexMigrate", job);
    }
}
