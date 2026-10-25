package com.nawzblaze.momentwise.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nawzblaze.momentwise.MomentwiseViewModel
import com.nawzblaze.momentwise.R
import com.nawzblaze.momentwise.data.CatalogEntry
import com.nawzblaze.momentwise.ui.components.InfoNote
import com.nawzblaze.momentwise.ui.components.MomentwiseTopBar
import com.nawzblaze.momentwise.ui.components.RuleSwitch
import com.nawzblaze.momentwise.ui.components.VerticalSpacer
import com.nawzblaze.momentwise.ui.theme.LocalMomentwiseColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Apps the user can actually open. Enabling a rule goes through disclosure
 * until the required grants exist. The list is not a usage report.
 */
@Composable
fun AppsScreen(
    viewModel: MomentwiseViewModel,
    snackbarHostState: SnackbarHostState,
    onConfigureRule: (String) -> Unit,
    onRequestGuard: (String) -> Unit,
) {
    val catalog by viewModel.catalog.collectAsStateWithLifecycle()
    val notePending by viewModel.sampleMigrationNotePending.collectAsStateWithLifecycle()
    val colors = LocalMomentwiseColors.current
    val grants = rememberGrantStatus()
    val monitoringPaused by viewModel.monitoringPaused.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    val loaded = catalog.orEmpty()
    val filtered = loaded.filter { entry ->
        val q = query.trim()
        q.isEmpty() ||
            entry.rule.appName.contains(q, ignoreCase = true) ||
            entry.rule.appId.contains(q, ignoreCase = true)
    }
    val installed = filtered.filter { it.installed }
    val missing = filtered.filter { !it.installed }
    val activeCount = loaded.count { it.rule.enabled }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        MomentwiseTopBar(
            title = stringResource(R.string.apps_title),
            actions = {
                Text(
                    text = stringResource(R.string.apps_active_count, activeCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.accentText,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(colors.accentSubtle)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            },
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.apps_heading),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                VerticalSpacer(6)
                Text(
                    text = stringResource(R.string.apps_subtext),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
                VerticalSpacer(14)
                if (notePending) {
                    InfoNote(icon = Icons.Filled.Info, text = stringResource(R.string.apps_sample_removed))
                    TextButton(onClick = viewModel::dismissSampleMigrationNote) {
                        Text(stringResource(R.string.apps_sample_removed_dismiss))
                    }
                    VerticalSpacer(8)
                }
                SearchField(query = query, onQueryChange = { query = it })
                VerticalSpacer(8)
            }
            if (catalog == null) {
                item {
                    Text(
                        text = stringResource(R.string.apps_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textTertiary,
                    )
                }
            }
            items(installed, key = { it.rule.appId }) { entry ->
                AppRow(
                    entry = entry,
                    monitoringPaused = monitoringPaused,
                    canMonitor = grants.canMonitor,
                    onConfigure = { onConfigureRule(entry.rule.appId) },
                    onToggle = { enabled ->
                        if (!enabled) {
                            viewModel.setDisplayedRuleEnabled(entry.rule, false)
                        } else if (viewModel.needsDisclosure(grants.canMonitor)) {
                            onRequestGuard(entry.rule.appId)
                        } else {
                            val firstTime = !entry.rule.configuredEver
                            viewModel.setDisplayedRuleEnabled(entry.rule, true)
                            if (firstTime) onConfigureRule(entry.rule.appId)
                        }
                    },
                )
            }
            if (missing.isNotEmpty()) {
                item {
                    VerticalSpacer(8)
                    Text(
                        text = stringResource(R.string.apps_not_installed_heading),
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.textSecondary,
                    )
                }
                items(missing, key = { "missing-${it.rule.appId}" }) { entry ->
                    AppRow(
                        entry = entry,
                        monitoringPaused = monitoringPaused,
                        canMonitor = grants.canMonitor,
                        onConfigure = { onConfigureRule(entry.rule.appId) },
                        onToggle = { enabled ->
                            if (!enabled) viewModel.setDisplayedRuleEnabled(entry.rule, false)
                        },
                    )
                }
            }
            item {
                VerticalSpacer(8)
                InfoNote(icon = Icons.Filled.Info, text = stringResource(R.string.apps_tip))
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    val colors = LocalMomentwiseColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surfaceCard)
            .border(1.dp, colors.border, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        if (query.isEmpty()) {
            Text(
                text = stringResource(R.string.apps_search),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted,
            )
        }
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(colors.accent),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun AppRow(
    entry: CatalogEntry,
    monitoringPaused: Boolean,
    canMonitor: Boolean,
    onConfigure: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    val rule = entry.rule
    val colors = LocalMomentwiseColors.current
    val activelyMonitoring = entry.installed && rule.enabled && canMonitor && !monitoringPaused
    val statusText = when {
        !entry.installed -> stringResource(R.string.apps_status_not_installed)
        rule.enabled && monitoringPaused -> stringResource(R.string.apps_status_paused)
        rule.enabled && !canMonitor -> stringResource(R.string.apps_status_needs_permission)
        else -> rule.appsRowStatusLabel(
            notGuarded = stringResource(R.string.apps_status_not_guarded),
            openingOnly = stringResource(R.string.apps_status_opening_only),
        )
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceCard)
            .border(
                1.dp,
                if (rule.enabled) colors.border else colors.borderSubtle,
                RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onConfigure)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        AppIcon(packageName = rule.appId, label = rule.appName, enabled = rule.enabled)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = rule.appName,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            VerticalSpacer(2)
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = if (activelyMonitoring) colors.statusGreen else colors.textTertiary,
            )
        }
        IconButton(onClick = onConfigure, modifier = Modifier.size(34.dp)) {
            Icon(
                Icons.Filled.Edit,
                contentDescription = stringResource(R.string.apps_configure, rule.appName),
                tint = colors.textTertiary,
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(Modifier.width(4.dp))
        RuleSwitch(
            checked = rule.enabled,
            onCheckedChange = onToggle,
        )
    }
}

/**
 * Recent app icons are cached to avoid repeatedly asking PackageManager for
 * the same drawable as LazyColumn items leave and re-enter the viewport.
 * Bitmap allocation and Drawable rendering happen off the Compose main thread.
 */
private object AppIconBitmapCache {
    // Cap decoded icon memory, not just the number of entries. Pixel sizes vary
    // by screen density, so a byte budget is more predictable on tablets/high-DPI.
    private val cache = object : LruCache<String, ImageBitmap>(4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int =
            (value.width.toLong() * value.height.toLong() * 4L)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
                .coerceAtLeast(1)
    }

    fun get(key: String): ImageBitmap? = cache.get(key)

    suspend fun load(context: android.content.Context, packageName: String, key: String): ImageBitmap? {
        cache.get(key)?.let { return it }
        val loaded = withContext(Dispatchers.IO) {
            runCatching {
                val drawable = context.packageManager.getApplicationIcon(packageName)
                val size = (42 * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, size, size)
                drawable.draw(canvas)
                bitmap.asImageBitmap()
            }.getOrNull()
        }
        if (loaded != null) cache.put(key, loaded)
        return loaded
    }
}

@Composable
private fun AppIcon(packageName: String, label: String, enabled: Boolean) {
    val context = LocalContext.current
    val colors = LocalMomentwiseColors.current
    val densityDpi = context.resources.displayMetrics.densityDpi
    val cacheKey = "$packageName@$densityDpi"
    val bitmap by produceState<ImageBitmap?>(AppIconBitmapCache.get(cacheKey), cacheKey) {
        value = AppIconBitmapCache.load(context, packageName, cacheKey)
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(if (enabled) colors.accentSubtle else colors.surfaceSubtle),
    ) {
        val currentBitmap = bitmap
        if (currentBitmap != null) {
            Image(bitmap = currentBitmap, contentDescription = null, modifier = Modifier.size(42.dp).clip(RoundedCornerShape(11.dp)))
        } else {
            Text(
                text = label.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (enabled) colors.accentText else colors.textTertiary,
            )
        }
    }
}
