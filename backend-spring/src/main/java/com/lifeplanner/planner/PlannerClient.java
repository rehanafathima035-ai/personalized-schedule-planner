package com.lifeplanner.planner;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lifeplanner.common.ApiExceptions;

@Component
public class PlannerClient {

    private static final Logger log = LoggerFactory.getLogger(PlannerClient.class);

    private final RestClient client;

    public PlannerClient(
            @Value("${app.planner.base-url:http://localhost:8001}") String baseUrl,
            @Value("${app.planner.timeout-seconds:10}") long timeoutSeconds) {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(timeoutSeconds));
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));

        ObjectMapper objectMapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .build();

        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        this.client = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .messageConverters(converters -> {
                    converters.removeIf(converter ->
                            converter.getClass().getName().contains("MappingJackson2HttpMessageConverter"));

                    org.springframework.http.converter.json.MappingJackson2HttpMessageConverter converter =
                            new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter();

                    converter.setObjectMapper(objectMapper);
                    converters.add(converter);
                })
                .build();
    }

    private <T, R> R post(String path, T body, Class<R> responseType) {
        try {
            log.debug("Sending planner request to {}", path);

            return client.post()
                    .uri(path)
                    .body(body)
                    .retrieve()
                    .body(responseType);

        } catch (RestClientException ex) {
            log.error("Planner call to {} failed", path, ex);
            throw new ApiExceptions.PlannerUnavailableException(
                    "Planner call to " + path + " failed", ex);
        }
    }

    public PlannerDtos.InterpretResponse interpret(String text) {
        return post(
                "/planner/interpret",
                new PlannerDtos.InterpretPayload(text),
                PlannerDtos.InterpretResponse.class);
    }

    public PlannerDtos.PlanResponse propose(PlannerDtos.PlanPayload plan) {
        return post(
                "/planner/propose",
                plan,
                PlannerDtos.PlanResponse.class);
    }
    public PlannerDtos.ConflictResponse conflicts(PlannerDtos.PlanPayload plan) {
        return post(
                "/planner/conflicts",
                plan,
                PlannerDtos.ConflictResponse.class);
    }
    public PlannerDtos.PlanResponse whatIf(PlannerDtos.WhatIfPayload payload) {
        return post(
                "/planner/what-if",
                payload,
                PlannerDtos.PlanResponse.class);
    }

    public PlannerDtos.PlanResponse rebalance(PlannerDtos.RebalancePayload payload) {
        return post(
                "/planner/rebalance",
                payload,
                PlannerDtos.PlanResponse.class);
    }

    public boolean isHealthy() {
        try {
            client.get()
                    .uri("/health")
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RestClientException ex) {
            return false;
        }
    }
}