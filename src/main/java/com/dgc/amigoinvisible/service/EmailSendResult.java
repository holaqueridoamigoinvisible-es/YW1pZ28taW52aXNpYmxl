package com.dgc.amigoinvisible.service;

import java.util.List;

/**
 * Result of a bulk email-send operation.
 *
 * @param sent         number of emails delivered successfully
 * @param failed       number of emails that could not be delivered
 * @param failedEmails masked addresses that failed (e.g. "ma***@gmail.com")
 */
public record EmailSendResult(int sent, int failed, List<String> failedEmails) {

    public boolean hasFailures() {
        return failed > 0;
    }
}
