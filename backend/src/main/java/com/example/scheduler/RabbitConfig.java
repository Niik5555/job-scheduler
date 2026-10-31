package com.example.scheduler;

import java.util.ArrayList;
import java.util.List;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "jobs.exchange";
    public static final String RUN_KEY = "run";
    public static final String JOBS_QUEUE = "jobs.queue";
    public static final String RESULTS_QUEUE = "results.queue";
    public static final String DLQ = "jobs.dlq";

    /** Backoff per retry tier. Short on purpose so the demo is quick; raise for real use. */
    public static final int[] RETRY_DELAYS_MS = {5_000, 15_000, 45_000};

    public static String retryQueueName(int tier) {
        return "jobs.retry." + tier;
    }

    @Bean
    DirectExchange jobsExchange() {
        return new DirectExchange(EXCHANGE);
    }

    @Bean
    Queue jobsQueue() {
        return QueueBuilder.durable(JOBS_QUEUE).build();
    }

    @Bean
    Binding jobsBinding() {
        return BindingBuilder.bind(jobsQueue()).to(jobsExchange()).with(RUN_KEY);
    }

    @Bean
    Queue resultsQueue() {
        return QueueBuilder.durable(RESULTS_QUEUE).build();
    }

    @Bean
    Queue deadLetterQueue() {
        return QueueBuilder.durable(DLQ).build();
    }

    /**
     * Retry queues have no consumers. Messages wait out the queue TTL, then are
     * dead-lettered back into the main jobs queue. One queue per delay tier avoids
     * head-of-line blocking that per-message TTLs would cause.
     */
    @Bean
    Declarables retryQueues() {
        List<Declarable> queues = new ArrayList<>();
        for (int i = 0; i < RETRY_DELAYS_MS.length; i++) {
            queues.add(QueueBuilder.durable(retryQueueName(i + 1))
                    .ttl(RETRY_DELAYS_MS[i])
                    .deadLetterExchange(EXCHANGE)
                    .deadLetterRoutingKey(RUN_KEY)
                    .build());
        }
        return new Declarables(queues);
    }

    @Bean
    MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
