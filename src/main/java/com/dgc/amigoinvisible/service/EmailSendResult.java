package com.dgc.amigoinvisible.service;

import java.util.List;

/**
 * Result of a bulk email-send operation.
 *
 * @param drawId       short unique identifier for this draw (e.g. "A3F2B1C9")
 * @param sent         number of emails delivered successfully
 * @param failed       number of emails that could not be delivered
 * @param failedEmails masked addresses that failed (e.g. "ma***@gmail.com")
 */
public record EmailSendResult(String drawId, int sent, int failed, List<String> failedEmails) {

    public boolean hasFailures() {
        return failed > 0;
    }
}
