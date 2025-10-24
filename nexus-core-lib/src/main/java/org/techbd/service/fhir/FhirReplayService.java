package org.techbd.service.fhir;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.techbd.config.Configuration;
import org.techbd.config.CoreAppConfig;
import org.techbd.config.CoreUdiPrimeJpaConfig;
import org.techbd.udi.auto.jooq.ingress.routines.GetFhirBundlesToReplay;
import org.techbd.udi.auto.jooq.ingress.routines.GetFhirPayloadForNyec;
import org.techbd.udi.auto.jooq.ingress.routines.UpdateFhirReplayStatus;
import org.techbd.util.AppLogger;
import org.techbd.util.TemplateLogger;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class FhirReplayService {

    private FHIRService fhirService;

    private TemplateLogger LOG;

    private CoreAppConfig appConfig;

    private final CoreUdiPrimeJpaConfig coreUdiPrimeJpaConfig;

    private final TaskExecutor asyncTaskExecutor;

    public FhirReplayService(FHIRService fhirService, AppLogger appLogger, CoreAppConfig appConfig,
            CoreUdiPrimeJpaConfig coreUdiPrimeJpaConfig,
            @Qualifier("asyncTaskExecutor") final TaskExecutor asyncTaskExecutor) {
        this.fhirService = fhirService;
        this.LOG = appLogger.getLogger(FhirReplayService.class);
        this.appConfig = appConfig;
        this.coreUdiPrimeJpaConfig = coreUdiPrimeJpaConfig;
        this.asyncTaskExecutor = asyncTaskExecutor;
    }

    public Map<String, Object> replayBundles(HttpServletRequest request, String replayId, OffsetDateTime startDate,
            OffsetDateTime endDate) {
        LOG.info("FHIR-REPLAY Starting replayBundles for replayId={} | startDate={} | endDate={}",
                replayId, startDate, endDate);
        final var dslContext = coreUdiPrimeJpaConfig.dsl();
        final var jooqCfg = dslContext.configuration();
        Map<String, Object> bundlesResponse = getBundlesToReplay(jooqCfg,replayId, startDate, endDate);

        if (bundlesResponse.isEmpty() || !bundlesResponse.containsKey("bundles")) {
            LOG.warn("FHIR-REPLAY No bundles found to replay for replayId={}", replayId);
            return Map.of(
                    "bundle_count", 0,
                    "message", "No bundles found to replay");
        }
        List<Map<String, Object>> bundlesList = (List<Map<String, Object>>) bundlesResponse.get("bundles");
        Map<String, Object> interimResponse = new HashMap<>();

        int bundleCount = Optional.ofNullable(bundlesResponse.get("bundle_count"))
        .map(obj -> {
            if (obj instanceof Number) {
                return ((Number) obj).intValue();
            } else {
                try {
                    return Integer.parseInt(obj.toString());
                } catch (NumberFormatException e) {
                    return 0;
                }
            }
        })
        .orElse(0);
        interimResponse.put("total_bundles", bundleCount);
        interimResponse.put("replay_id", replayId);

        if (bundleCount == 0) {
            interimResponse.put("message", "No bundles to replay during this period.");
        } else {
            interimResponse.put("message",
                    "Replay started. Please refer to the Hub UI Interactions > FHIR Data tab for detailed status updates.");
        }

        LOG.info("FHIR-REPLAY Replay started for replayId={} | bundle_count={}",
                replayId, bundleCount);
        CompletableFuture.runAsync(() -> {
            for (Map<String, Object> bundle : bundlesList) {
                boolean alreadyReplayed = false;
                final var bundleId = (String) bundle.get("bundleid");
                final var bundleInteractionId = (String) bundle.get("interactionid");
                final var groupInteractionId = (String) bundle.get("groupInteractionId");
                final var zipInteractionId = (String) bundle.get("zipInteractionID");
                final var tenantId = (String) bundle.get("tenantID");
                final var source = (String) bundle.get("source");
                final var requestUri = (String) bundle.get("uri");
                var errorMessage = (String) bundle.get("errorMessage");
                var status = "Success";
                final var provenance = "%s.replayBundles".formatted(FhirReplayService.class.getName());
                try {
                    LOG.info(
                            "FHIR-REPLAY Starting replay of bundle | replayId={} | bundleInteractionId={} | zipInteractionId={} | groupInteractionId={} | bundleId={} | tenantId={} | source={}",
                            replayId,
                            bundleInteractionId,
                            zipInteractionId,
                            groupInteractionId,
                            bundleId,
                            tenantId,
                            source);
                    if (errorMessage != null && !errorMessage.isEmpty()) {
                        status = "Failed";
                        LOG.info("FHIR-REPLAY Skipping replay due to existing error | replayId={} | bundleInteractionId={} | zipInteractionId={} | groupInteractionId={} | bundleId={} | errorMessage={}",
                                 replayId, bundleInteractionId, zipInteractionId, groupInteractionId, bundleId, errorMessage);
                        alreadyReplayed = true;
                    } else {
                        // Call scoring engine

                        fhirService.sendToScoringEngine(
                                jooqCfg,
                                null,
                                appConfig.getDefaultDatalakeApiUrl(),
                                MediaType.APPLICATION_JSON_VALUE,
                                tenantId,
                                null,
                                provenance,
                                null,
                                null,
                                bundleInteractionId,
                                groupInteractionId,
                                zipInteractionId,
                                source,
                                requestUri,
                                null,
                                bundleId,
                                true,
                                getNyecPayload(jooqCfg, bundleInteractionId));
                        LOG.info(
                                "FHIR-REPLAY Successfully sent bundle | replayId={} | bundleInteractionId={} | zipInteractionId={} | groupInteractionId={} | bundleId={} | tenantId={} | source={}",
                                replayId,
                                bundleInteractionId,
                                zipInteractionId,
                                groupInteractionId,
                                bundleId,
                                tenantId,
                                source);
                    }
                } catch (Exception e) {
                    LOG.error("FHIR-REPLAY Failed sending bundleId={} for replayId={} | error={}",
                            bundleId, replayId, e.getMessage(), e);
                    status = "Failed";

                    // Capture full stack trace
                    StringWriter sw = new StringWriter();
                    e.printStackTrace(new PrintWriter(sw));
                    errorMessage = sw.toString();
                } finally {
                    // Always update FHIR replay status and errorMessage
                    if (!alreadyReplayed) {
                        updateFhirStatus(jooqCfg, bundleInteractionId, status, errorMessage, replayId);
                    }
                }
            }

            LOG.info("FHIR-REPLAY Completed asynchronous processing for replayMasterId={}",
                    replayId);
        }, asyncTaskExecutor);
        return interimResponse;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getNyecPayload(final org.jooq.Configuration jooqCfg,String interactionId) {
        LOG.info("Fetching NYEC FHIR payload for interactionId={}", interactionId);
        try {
            GetFhirPayloadForNyec getFhirPayloadForNyec = new GetFhirPayloadForNyec();
            getFhirPayloadForNyec.setPInteractionId(interactionId);
            int executeResult = getFhirPayloadForNyec.execute(jooqCfg);
            JsonNode responseJson = getFhirPayloadForNyec.getReturnValue();

            if (responseJson == null || responseJson.isEmpty()) {
                LOG.warn("No NYEC payload found for interactionId={}", interactionId);
                return Collections.emptyMap();
            }
            Map<String, Object> payloadMap = Configuration.objectMapper.convertValue(responseJson, Map.class);
            LOG.info("Fetched NYEC payload for interactionId={} | keys={}", interactionId, payloadMap.keySet());
            return payloadMap;
        } catch (Exception e) {
            LOG.error("Error fetching NYEC payload for interactionId={}: {}", interactionId, e.getMessage(), e);
            throw new RuntimeException("Failed to fetch NYEC payload for interactionId=" + interactionId, e);
        }
    }

    /**
     * Private helper method to update FHIR replay status and errorMessage
     */
    private void updateFhirStatus(final org.jooq.Configuration jooqCfg,String bundleId, String status, String errorMessage, String replayMasterId) {
        try {
            UpdateFhirReplayStatus updateFhirReplayStatus = new UpdateFhirReplayStatus();
            updateFhirReplayStatus.setPInteractionId(bundleId);
            updateFhirReplayStatus.setPStatus(status);
            if (errorMessage != null && errorMessage.length() > 4000) { // adjust length as per DB field
                errorMessage = errorMessage.substring(0, 4000);
            }
            updateFhirReplayStatus.setPErrorMessage(errorMessage);
            updateFhirReplayStatus.execute(jooqCfg);

            LOG.info("FHIR-REPLAY Updated status={} for bundleId={} replayMasterId={}",
                    status, bundleId, replayMasterId);
        } catch (Exception e) {
            LOG.error("FHIR-REPLAY Failed to update FHIR replay status for bundleId={} | error={}",
                    bundleId, e.getMessage(), e);
        }
    }

    private Map<String, Object> getBundlesToReplay(final org.jooq.Configuration jooqCfg,
            final String interactionId,
            final OffsetDateTime startDate,
            final OffsetDateTime endDate) {
        LOG.info("FHIR-REPLAY Fetching bundles to replay for interactionId={} | startDate={} | endDate={}",
                interactionId, startDate, endDate);

        try {
            // Initialize and configure stored procedure call
            final var getFhirBundlesToReplay = new GetFhirBundlesToReplay();
            getFhirBundlesToReplay.setPReplayMasterId(interactionId);
            getFhirBundlesToReplay.setStartTime(startDate);
            getFhirBundlesToReplay.setEndTime(endDate);

            // Execute the stored procedure
            int executeResult = getFhirBundlesToReplay.execute(jooqCfg);
            final var responseJson = (JsonNode) getFhirBundlesToReplay.getReturnValue();

            if (responseJson == null || responseJson.isEmpty()) {
                LOG.warn("FHIR-REPLAY No bundles found for interactionId={} | startDate={} | endDate={}",
                        interactionId, startDate, endDate);
                return Map.of(
                        "bundle_count", 0,
                        "replay_master_id", interactionId,
                        "bundles", List.of());
            }

            // Convert JsonNode → Map directly
            final Map<String, Object> response = Configuration.objectMapper.convertValue(responseJson, Map.class);

            LOG.info("FHIR-REPLAY Found {} bundles to replay for replay_master_id={}",
                    response.getOrDefault("bundle_count", 0),
                    response.getOrDefault("replay_master_id", interactionId));

            return response;

        } catch (Exception e) {
            LOG.error("FHIR-REPLAY Error fetching bundles to replay for interactionId={} : {}", interactionId,
                    e.getMessage(), e);
            throw new RuntimeException("Failed to fetch bundles to replay", e);
        }
    }


    public static Map<String, Object> extractFields(JsonNode payload) {
        var result = new HashMap<String, Object>();

        payload.fieldNames().forEachRemaining(field -> {
            JsonNode value = payload.get(field);
            if (value.isValueNode()) {
                result.put(field, value.asText());
            } else {
                result.put(field, value);
            }
        });

        return result;
    }

}
