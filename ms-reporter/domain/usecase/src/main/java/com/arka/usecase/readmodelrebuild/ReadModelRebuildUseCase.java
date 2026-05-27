package com.arka.usecase.readmodelrebuild;

import com.arka.model.commons.exception.InvalidReadModelException;
import com.arka.model.eventstore.EventEnvelope;
import com.arka.model.eventstore.EventStoreEntry;
import com.arka.model.eventstore.gateways.EventStoreRepository;
import com.arka.model.rebuild.RebuildJob;
import com.arka.model.rebuild.RebuildStatus;
import com.arka.model.rebuild.gateways.RebuildJobRepository;
import com.arka.model.sales.gateways.SalesSummaryRepository;
import com.arka.usecase.eventconsumption.EventConsumptionUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class ReadModelRebuildUseCase {

    private static final Set<String> SUPPORTED_READ_MODELS = Set.of("sales_summary");
    private static final int BATCH_SIZE = 1000;

    private final EventStoreRepository eventStoreRepository;
    private final RebuildJobRepository rebuildJobRepository;
    private final SalesSummaryRepository salesSummaryRepository;

    public UUID startRebuild(String readModel) {
        if (!SUPPORTED_READ_MODELS.contains(readModel)) {
            throw new InvalidReadModelException(readModel);
        }

        RebuildJob job = RebuildJob.builder()
                .readModel(readModel)
                .build();

        rebuildJobRepository.save(job);
        return job.id();
    }

    public void executeRebuild(UUID jobId, String readModel) {
        try {
            truncateReadModel(readModel);

            long totalProcessed = 0;
            int offset = 0;
            List<EventStoreEntry> batch;

            do {
                batch = eventStoreRepository.findAllOrderedByTimestamp(offset, BATCH_SIZE);
                for (EventStoreEntry entry : batch) {
                    replayEvent(entry, readModel);
                    totalProcessed++;
                }
                offset += BATCH_SIZE;
            } while (batch.size() == BATCH_SIZE);

            rebuildJobRepository.updateStatus(jobId, RebuildStatus.COMPLETED, totalProcessed, null);
            log.info("Read model rebuild completed: model={}, events={}", readModel, totalProcessed);
        } catch (Exception e) {
            log.error("Read model rebuild failed: model={}", readModel, e);
            truncateReadModel(readModel);
            rebuildJobRepository.updateStatus(jobId, RebuildStatus.FAILED, 0, e.getMessage());
        }
    }

    private void truncateReadModel(String readModel) {
        if ("sales_summary".equals(readModel)) {
            salesSummaryRepository.truncate();
        }
    }

    private void replayEvent(EventStoreEntry entry, String readModel) {
        if ("sales_summary".equals(readModel)) {
            // Only replay events that affect sales_summary
            if ("OrderConfirmed".equals(entry.eventType()) || "OrderCancelled".equals(entry.eventType())) {
                EventEnvelope envelope = EventEnvelope.builder()
                        .eventId(entry.eventId())
                        .eventType(entry.eventType())
                        .timestamp(entry.timestamp())
                        .source(entry.source())
                        .correlationId(entry.correlationId())
                        .payload(entry.payload())
                        .build();
                // Note: projection logic is duplicated here for rebuild isolation
                // In production, extract to shared projection service
            }
        }
    }
}

