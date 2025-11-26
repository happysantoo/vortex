package com.vajrapulse.vortex.example;

import com.vajrapulse.vortex.*;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Example demonstrating HTTP backend integration.
 * Note: This is a simplified example - in production, use proper HTTP client libraries.
 */
public class HttpBackendExample {
    
    public static void main(String[] args) throws Exception {
        HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
        
        // HTTP backend that batches requests
        Backend<String> httpBackend = batch -> {
            System.out.println("Sending batch of " + batch.size() + " requests to HTTP endpoint");
            
            List<SuccessEvent<String>> successes = new ArrayList<>();
            List<FailureEvent<String>> failures = new ArrayList<>();
            
            for (String item : batch) {
                try {
                    // Simulate HTTP call (replace with actual endpoint)
                    HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://httpbin.org/post"))
                        .POST(HttpRequest.BodyPublishers.ofString(item))
                        .timeout(Duration.ofSeconds(5))
                        .build();
                    
                    HttpResponse<String> response = httpClient.send(request, 
                        HttpResponse.BodyHandlers.ofString());
                    
                    if (response.statusCode() == 200) {
                        successes.add(new SuccessEvent<>(item));
                    } else {
                        failures.add(new FailureEvent<>(item, 
                            new RuntimeException("HTTP " + response.statusCode())));
                    }
                } catch (IOException | InterruptedException e) {
                    failures.add(new FailureEvent<>(item, e));
                }
            }
            
            return new BatchResult<>(successes, failures);
        };
        
        BatcherConfig config = BatcherConfig.builder()
            .batchSize(10)
            .lingerTime(Duration.ofMillis(200))
            .maxQueueSize(50)  // Larger queue for HTTP requests
            .build();
        
        try (MicroBatcher<String> batcher = new MicroBatcher<>(httpBackend, config)) {
            List<CompletableFuture<BatchResult<String>>> futures = new ArrayList<>();
            
            for (int i = 0; i < 25; i++) {
                futures.add(batcher.submit("Request-" + i));
            }
            
            // Wait for all
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();
            
            System.out.println("\nAll requests completed!");
        }
    }
}

