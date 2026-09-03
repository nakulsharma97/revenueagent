package com.razorpay.recovery.recovery.execution;

/*
 * REMOVED — dead code. The pluggable-executor design (RecoveryActionExecutor,
 * RecoveryContext, ExecutionResult) was never wired into the pipeline; execution
 * lives in RecoveryOrchestratorService's exhaustive switch statements backed by
 * MockPaymentGatewayService / MockNotificationService.
 *
 * This file is a comment-only placeholder because shell-based file deletion is
 * unavailable in the current workspace. Delete this entire directory:
 *     rm -rf backend/src/main/java/com/razorpay/recovery/recovery/execution
 */