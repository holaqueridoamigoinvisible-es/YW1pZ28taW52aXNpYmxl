package com.dgc.amigoinvisible.config;

import com.dgc.amigoinvisible.model.Assignment;
import com.dgc.amigoinvisible.step.DrawTasklet;
import com.dgc.amigoinvisible.step.email.AssignmentItemReader;
import com.dgc.amigoinvisible.step.email.EmailItemProcessor;
import com.dgc.amigoinvisible.step.email.EmailItemWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@RequiredArgsConstructor
public class BatchConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;

    private final DrawTasklet drawTasklet;
    private final AssignmentItemReader assignmentItemReader;
    private final EmailItemProcessor emailItemProcessor;
    private final EmailItemWriter emailItemWriter;

    @Bean
    public Step drawStep() {
        return new StepBuilder("drawStep", jobRepository)
                .tasklet(drawTasklet, transactionManager)
                .build();
    }

    @Bean
    public Step emailStep() {
        return new StepBuilder("emailStep", jobRepository)
                .<Assignment, SimpleMailMessage>chunk(1, transactionManager)
                .reader(assignmentItemReader)
                .processor(emailItemProcessor)
                .writer(emailItemWriter)
                .build();
    }

    @Bean
    public Job amigoInvisibleJob() {
        return new JobBuilder("amigoInvisibleJob", jobRepository)
                .start(drawStep())
                .next(emailStep())
                .build();
    }
}
