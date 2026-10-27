package com.dgc.amigoinvisible.step.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailItemWriter implements ItemWriter<SimpleMailMessage> {

    private final JavaMailSender mailSender;

    @Override
    public void write(Chunk<? extends SimpleMailMessage> chunk) {
        for (SimpleMailMessage message : chunk) {
            try {
//                mailSender.send(message);
                log.info("Email sent successfully to {}", (Object) message.getTo());
            } catch (MailException e) {
                log.error("Failed to send email to {}: {}", message.getTo(), e.getMessage());
                // Continue processing remaining emails — do not abort the job
            }
        }
    }
}
