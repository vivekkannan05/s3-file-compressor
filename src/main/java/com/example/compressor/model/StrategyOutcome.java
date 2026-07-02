package com.example.compressor.model;

import java.util.List;

/**
 * Sealed result of a compression strategy run. (§NFR-6.2)
 * The compiler guarantees exhaustive handling via pattern matching.
 */
public sealed interface StrategyOutcome
        permits StrategyOutcome.Success, StrategyOutcome.Failure {

    record Success(String formatName, List<OutputEntry> outputs) implements StrategyOutcome {}

    record Failure(String formatName, String reason) implements StrategyOutcome {}
}
