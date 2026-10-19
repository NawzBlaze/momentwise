package com.nawzblaze.momentwise.intervention

/**
 * Whether enabling a rule must go through the disclosure screen first.
 * Required grants missing always re-opens it, even after an earlier acceptance.
 */
fun shouldExplainBeforeGuarding(disclosureAccepted: Boolean, canMonitor: Boolean): Boolean =
    !disclosureAccepted || !canMonitor
