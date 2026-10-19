package com.nawzblaze.momentwise.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nawzblaze.momentwise.R
import com.nawzblaze.momentwise.ui.components.MarkupText
import com.nawzblaze.momentwise.ui.components.SectionCard
import com.nawzblaze.momentwise.ui.components.VerticalSpacer
import com.nawzblaze.momentwise.ui.theme.LocalMomentwiseColors

/**
 * Screen 1 — Welcome / Onboarding.
 * Brand introduction and the three-step value proposition, mirroring the
 * approved prototype. No permissions are requested here; onboarding simply
 * routes into Apps ("Configure Your First App") or Home ("Go to Overview").
 */
@Composable
fun WelcomeScreen(
    onGetStarted: () -> Unit,
    onGoToOverview: () -> Unit,
) {
    val colors = LocalMomentwiseColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 32.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Image(
                painter = painterResource(R.drawable.ic_logo),
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier
                    .size(112.dp),
            )
            VerticalSpacer(12)
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            VerticalSpacer(4)
            Text(
                text = stringResource(R.string.tagline),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textSecondary,
            )
        }

        VerticalSpacer(28)

        SectionCard {
            Text(
                text = stringResource(R.string.welcome_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            VerticalSpacer(8)
            Text(
                text = stringResource(R.string.welcome_body),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            VerticalSpacer(16)
            WelcomeBullet(stringResource(R.string.welcome_point_1))
            VerticalSpacer(10)
            WelcomeBullet(stringResource(R.string.welcome_point_2))
            VerticalSpacer(10)
            WelcomeBullet(stringResource(R.string.welcome_point_3))
        }

        VerticalSpacer(24)

        Button(
            onClick = onGetStarted,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
        ) {
            Text(
                stringResource(R.string.welcome_get_started),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        }
        VerticalSpacer(8)
        TextButton(
            onClick = onGoToOverview,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                stringResource(R.string.welcome_skip),
                color = colors.textSecondary,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        VerticalSpacer(16)
    }
}

@Composable
private fun WelcomeBullet(richText: String) {
    val colors = LocalMomentwiseColors.current
    Row(verticalAlignment = Alignment.Top) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(top = 3.dp)
                .size(16.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(colors.accentSubtle),
        ) {
            Box(
                Modifier
                    .size(5.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.accentText)
            )
        }
        Spacer(Modifier.width(10.dp))
        MarkupText(
            source = richText,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
    }
}
