package com.nawzblaze.momentwise.ui.components

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nawzblaze.momentwise.monitoring.GrantPresentation

/**
 * The same control for a missing grant and a present grant.
 * Allowed is green. It is not a separate status label plus a review button.
 */
@Composable
fun GrantControl(
    allowed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = GrantPresentation.label(allowed)
    val shape = RoundedCornerShape(10.dp)
    if (allowed) {
        Button(
            onClick = onClick,
            modifier = modifier.heightIn(min = 40.dp),
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF1B7340),
                contentColor = Color(0xFFFAF7F2),
            ),
        ) {
            Text(label, fontWeight = FontWeight.SemiBold)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier.heightIn(min = 40.dp),
            shape = shape,
        ) {
            Text(label, fontWeight = FontWeight.SemiBold)
        }
    }
}
