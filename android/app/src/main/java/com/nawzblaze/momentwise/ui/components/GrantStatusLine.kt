package com.nawzblaze.momentwise.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nawzblaze.momentwise.R
import com.nawzblaze.momentwise.ui.theme.LocalMomentwiseColors

/**
 * Green only when Android reports the grant. Optional access uses "Not allowed"
 * so a skipped overlay does not look like the whole app is broken.
 */
@Composable
fun GrantStatusLine(allowed: Boolean, optional: Boolean = false) {
    val colors = LocalMomentwiseColors.current
    val tint = if (allowed) colors.statusGreen else colors.textSecondary
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(tint),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(
                when {
                    allowed -> R.string.grant_allowed
                    optional -> R.string.grant_not_allowed
                    else -> R.string.grant_required
                },
            ),
            color = tint,
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
fun grantActionLabel(allowed: Boolean): String =
    stringResource(if (allowed) R.string.grant_review_access else R.string.grant_allow)
