package com.arka.model.rebuild.gateways;

import com.arka.model.rebuild.RebuildJob;
import com.arka.model.rebuild.RebuildStatus;

import java.util.UUID;

public interface RebuildJobRepository {

    void save(RebuildJob job);

    void updateStatus(UUID id, RebuildStatus status, long eventsProcessed, String errorMessage);
}
