package com.razorpay.recovery.recovery.execution;

import com.razorpay.recovery.recovery.RecoveryAttempt;
import com.razorpay.recovery.recovery.LlmDecision;

/**
 * Strategy Pattern interface for executing recovery actions.
 * Each recovery action type has its own executor implementation.
 * 
 * This ensures:
 * - Single Responsibility: each action type has focused execution logic
 * - Open/Closed Principle: new actions added without modifying existing code
 * - Testability: each executor can be tested independently
 */
public interface RecoveryActionExecutor {

    /**
     * The action type this executor handles.
     */
    RecoveryAttempt.RecoveryAction getActionType();

    /**
     * Execute the recovery action.
     * @param action the recovery action details
     * @param context execution context with entity references
     * @return execution result
     */
    ExecutionResult execute(RecoveryAttempt.RecoveryAction action, RecoveryContext context);

    /**
     * Whether this executor requires the mock gateway to execute.
     * Some actions (like ESCALATE_TO_HUMAN) don't need gateway calls.
     */
    default boolean requiresGatewayExecution() {
        return true;
    }
}
