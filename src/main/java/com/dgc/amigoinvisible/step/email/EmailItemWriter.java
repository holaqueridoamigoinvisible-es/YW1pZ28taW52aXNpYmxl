package com.dgc.amigoinvisible.step.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.stereotype.Component;

/**
 * Legacy batch writer — kept for Spring Batch wiring but not used
 * in the web version. Email sending is handled by EmailService (HTTP API).
 */
@Slf4j
@Component
public class EmailItemWriter implements ItemWriter<SimpleMailMessage> {

    @Override
    public void write(Chunk<? extends SimpleMailMessage> chunk) {
        log.info("EmailItemWriter: {} messages (no-op in web mode)", chunk.size());
    }
}
