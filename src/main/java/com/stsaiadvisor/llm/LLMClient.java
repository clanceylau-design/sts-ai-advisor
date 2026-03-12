package com.stsaiadvisor.llm;

import com.stsaiadvisor.model.BattleContext;
import com.stsaiadvisor.model.Recommendation;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for LLM API clients.
 */
public interface LLMClient {

    /**
     * Request a recommendation asynchronously.
     */
    CompletableFuture<Recommendation> requestAsync(BattleContext context);

    /**
     * Request a recommendation synchronously.
     */
    Recommendation request(BattleContext context) throws IOException;

    /**
     * Test the API connection.
     */
    boolean testConnection();

    /**
     * Get the client name for logging.
     */
    String getClientName();
}