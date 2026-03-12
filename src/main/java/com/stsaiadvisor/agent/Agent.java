package com.stsaiadvisor.agent;

import java.util.concurrent.CompletableFuture;

/**
 * Generic agent interface for multi-agent architecture.
 * @param <I> Input type
 * @param <O> Output type
 */
public interface Agent<I, O> {
    /**
     * Process input and produce output asynchronously.
     * @param input The input to process
     * @return CompletableFuture containing the output
     */
    CompletableFuture<O> process(I input);

    /**
     * Get the agent's name for logging and debugging.
     * @return The agent name
     */
    String getAgentName();
}