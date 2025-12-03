package io.github.darkryh.katalyst.transactions.workflow

import org.slf4j.LoggerFactory

/**
 * State machine for managing workflow lifecycle and transitions.
 *
 * **Workflow States**:
 * ```
 * CREATED → RUNNING → COMMITTED
 *              ↓
 *           FAILED → UNDOING → UNDONE
 *              ↓
 *          FAILED_UNDO (requires manual intervention)
 * ```
 *
 * **State Transitions**:
 * - CREATED → RUNNING: When execution begins
 * - RUNNING → COMMITTED: When all steps complete successfully
 * - RUNNING → FAILED: When any step fails
 * - FAILED → UNDOING: When undo is initiated
 * - UNDOING → UNDONE: When undo completes successfully
 * - UNDOING → FAILED_UNDO: When undo fails
 * - Any → PAUSED: Can pause from CREATED or RUNNING
 * - PAUSED → RUNNING: Can resume from PAUSED
 *
 * **Usage**:
 * ```kotlin
 * val stateMachine = WorkflowStateMachine(workflowId)
 * stateMachine.transition(WorkflowStateTransition.BEGIN_EXECUTION)
 * // ... execute steps ...
 * stateMachine.transition(WorkflowStateTransition.COMMIT)
 * ```
 */
class WorkflowStateMachine(
    val workflowId: String,
    initialState: WorkflowMachineState = WorkflowMachineState.CREATED
) {
    private val logger = LoggerFactory.getLogger(WorkflowStateMachine::class.java)

    private var currentState: WorkflowMachineState = initialState
    private val stateHistory = mutableListOf<StateTransitionRecord>()
    private val onStateChangeListeners = mutableListOf<(WorkflowMachineState, WorkflowMachineState) -> Unit>()

    init {
        logger.debug("Created workflow state machine: id={}, initialState={}", workflowId, initialState)
        stateHistory.add(StateTransitionRecord(currentState, System.currentTimeMillis()))
    }

    /**
     * Get current state.
     */
    fun getState(): WorkflowMachineState = currentState

    /**
     * Attempt a state transition.
     *
     * @param transition The transition to attempt
     * @return true if transition was valid and executed, false if transition is invalid for current state
     */
    fun transition(transition: WorkflowStateTransition): Boolean {
        val nextState = getNextState(currentState, transition)
        if (nextState == null) {
            logger.warn(
                "Invalid transition for workflow {}: {} -> {} (from state {})",
                workflowId, transition, null, currentState
            )
            return false
        }

        logger.info(
            "Workflow state transition: {} {} -> {}",
            workflowId, currentState, nextState
        )

        val previousState = currentState
        currentState = nextState
        stateHistory.add(StateTransitionRecord(nextState, System.currentTimeMillis()))

        // Notify listeners
        onStateChangeListeners.forEach { it(previousState, nextState) }

        return true
    }

    /**
     * Register a listener for state changes.
     *
     * @param listener Called with (previousState, newState)
     */
    fun onStateChange(listener: (WorkflowMachineState, WorkflowMachineState) -> Unit) {
        onStateChangeListeners.add(listener)
    }

    /**
     * Get full state transition history.
     */
    fun getStateHistory(): List<StateTransitionRecord> = stateHistory.toList()

    /**
     * Check if workflow can be undone from current state.
     */
    fun canUndo(): Boolean = currentState == WorkflowMachineState.FAILED

    /**
     * Check if workflow is in a terminal state.
     */
    fun isTerminal(): Boolean = currentState in listOf(
        WorkflowMachineState.COMMITTED,
        WorkflowMachineState.UNDONE,
        WorkflowMachineState.FAILED_UNDO
    )

    /**
     * Check if workflow is in a running/active state.
     */
    fun isActive(): Boolean = currentState in listOf(
        WorkflowMachineState.CREATED,
        WorkflowMachineState.RUNNING,
        WorkflowMachineState.PAUSED
    )

    /**
     * Get a human-readable description of current state.
     */
    fun describe(): String = buildString {
        append("Workflow(id=$workflowId, state=$currentState)")
        val lastRecord = stateHistory.lastOrNull()
        if (lastRecord != null) {
            val durationMs = System.currentTimeMillis() - lastRecord.timestamp
            append(", duration=${durationMs}ms")
        }
    }

    /**
     * Determine the next valid state for a given transition.
     *
     * Implements the state machine transition rules.
     */
    private fun getNextState(
        currentState: WorkflowMachineState,
        transition: WorkflowStateTransition
    ): WorkflowMachineState? = when (currentState) {
        WorkflowMachineState.CREATED -> when (transition) {
            WorkflowStateTransition.BEGIN_EXECUTION -> WorkflowMachineState.RUNNING
            WorkflowStateTransition.PAUSE -> WorkflowMachineState.PAUSED
            else -> null
        }

        WorkflowMachineState.RUNNING -> when (transition) {
            WorkflowStateTransition.COMMIT -> WorkflowMachineState.COMMITTED
            WorkflowStateTransition.FAIL -> WorkflowMachineState.FAILED
            WorkflowStateTransition.PAUSE -> WorkflowMachineState.PAUSED
            else -> null
        }

        WorkflowMachineState.FAILED -> when (transition) {
            WorkflowStateTransition.BEGIN_UNDO -> WorkflowMachineState.UNDOING
            WorkflowStateTransition.RETRY -> WorkflowMachineState.RUNNING  // Retry from FAILED
            else -> null
        }

        WorkflowMachineState.UNDOING -> when (transition) {
            WorkflowStateTransition.UNDO_COMPLETE -> WorkflowMachineState.UNDONE
            WorkflowStateTransition.UNDO_FAIL -> WorkflowMachineState.FAILED_UNDO
            else -> null
        }

        WorkflowMachineState.PAUSED -> when (transition) {
            WorkflowStateTransition.RESUME -> WorkflowMachineState.RUNNING
            WorkflowStateTransition.ABORT -> WorkflowMachineState.FAILED
            else -> null
        }

        WorkflowMachineState.COMMITTED, WorkflowMachineState.UNDONE, WorkflowMachineState.FAILED_UNDO -> {
            // Terminal states - no transitions allowed
            null
        }
    }
}

/**
 * States in the workflow state machine.
 */
enum class WorkflowMachineState {
    /** Initial state, workflow created but not started */
    CREATED,

    /** Workflow is executing steps */
    RUNNING,

    /** Workflow execution paused (can resume) */
    PAUSED,

    /** All steps completed successfully */
    COMMITTED,

    /** A step failed, undo needed */
    FAILED,

    /** Undo is in progress */
    UNDOING,

    /** Undo completed successfully, all changes reversed */
    UNDONE,

    /** Undo failed, requires manual intervention */
    FAILED_UNDO
}

/**
 * Possible state transitions in the workflow state machine.
 */
enum class WorkflowStateTransition {
    /** Start execution */
    BEGIN_EXECUTION,

    /** All steps succeeded, commit transaction */
    COMMIT,

    /** A step failed */
    FAIL,

    /** Begin undo/rollback */
    BEGIN_UNDO,

    /** Undo completed successfully */
    UNDO_COMPLETE,

    /** Undo failed */
    UNDO_FAIL,

    /** Pause execution (for checkpoints or manual intervention) */
    PAUSE,

    /** Resume from paused state */
    RESUME,

    /** Abort workflow from paused state */
    ABORT,

    /** Retry execution from FAILED state */
    RETRY
}

/**
 * Record of a state transition.
 */
data class StateTransitionRecord(
    val state: WorkflowMachineState,
    val timestamp: Long
) {
    fun getDurationSinceMs(): Long = System.currentTimeMillis() - timestamp
}
