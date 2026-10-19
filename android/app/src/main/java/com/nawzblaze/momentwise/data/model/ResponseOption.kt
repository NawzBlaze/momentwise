package com.nawzblaze.momentwise.data.model

/**
 * A single button on the opening notice: a user-written label bound to a fixed action.
 */
data class ResponseOption(
    val label: String,
    val action: RuleAction,
)
