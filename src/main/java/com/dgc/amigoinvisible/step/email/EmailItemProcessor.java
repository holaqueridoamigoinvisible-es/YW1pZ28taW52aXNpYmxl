package com.dgc.amigoinvisible.step.email;

import com.dgc.amigoinvisible.model.Assignment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@StepScope
public class EmailItemProcessor implements ItemProcessor<Assignment, SimpleMailMessage> {

    @Value("${amigo.mail.from}")
    private String fromAddress;

    @Value("${amigo.mail.from-name:Amigo Invisible}")
    private String fromName;

    @Override
    public SimpleMailMessage process(Assignment assignment) {
        String senderName = assignment.sender().name();
        String receiverName = assignment.receiver().name();
        String senderEmail = assignment.sender().email();

        String body = String.format(
                "Hola %s,\n\nTu amigo invisible es %s\n\n¡Mucha suerte con el regalo!\n\nAmigo Invisible App",
                senderName, receiverName);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(senderEmail);
        message.setFrom(fromName + " <" + fromAddress + ">");
        message.setSubject("Amigo Invisible - Tu asignación");
        message.setText(body);

        log.info("Prepared email for {} ({})", senderName, senderEmail);
        return message;
    }
}
