package com.machinext.identity.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProviderTransferScheduler {
    private static final Logger log = LoggerFactory.getLogger(ProviderTransferScheduler.class);
    private final JdbcTemplate jdbc;
    private final ProviderTransferService transferService;

    public ProviderTransferScheduler(JdbcTemplate jdbc, ProviderTransferService transferService) {
        this.jdbc = jdbc;
        this.transferService = transferService;
    }

    @Scheduled(fixedDelayString = "${app.provider-transfer.scheduler-delay-ms:60000}")
    public void completeDueTransfers() {
        List<Long> transferIds = jdbc.queryForList("""
                SELECT transfer_id FROM provider_transfer
                WHERE status = 'WAITING' AND effective_at <= CURRENT_TIMESTAMP(6)
                ORDER BY effective_at
                """, Long.class);
        for (Long transferId : transferIds) {
            try {
                transferService.completeAutomatically(transferId);
                log.info("Provider transfer {} completed automatically", transferId);
            } catch (RuntimeException exception) {
                log.warn("Provider transfer {} could not be completed automatically: {}",
                        transferId, exception.getMessage());
            }
        }
    }
}
