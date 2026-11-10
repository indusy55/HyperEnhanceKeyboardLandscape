package com.hyperenhance.keyboardlandscape

import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.libxposed.service.XposedService
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.icons.useful.Cancel
import top.yukonga.miuix.kmp.icon.icons.useful.Confirm
import top.yukonga.miuix.kmp.icon.icons.useful.Info
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MiuixTheme(
                colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            ) {
                SettingsScreen()
            }
        }
    }
}

private enum class Status { ACTIVE, INACTIVE, UNKNOWN }

@Composable
private fun SettingsScreen() {
    val context = LocalContext.current
    var service by remember { mutableStateOf(FrameworkService.service) }
    var landscapeEnabled by remember { mutableStateOf(true) }
    var testInput by remember { mutableStateOf("") }
    var currentIme by remember { mutableStateOf("") }
    var refreshKey by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf(Status.UNKNOWN) }

    val serviceUnavailable = stringResource(R.string.service_unavailable)

    DisposableEffect(Unit) {
        val listener: (XposedService?) -> Unit = {
            service = it
            refreshKey++
        }
        FrameworkService.addListener(listener)
        onDispose { FrameworkService.removeListener(listener) }
    }

    LaunchedEffect(service, refreshKey) {
        val imePackage = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD
        )?.substringBefore('/') ?: ""
        currentIme = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD
        ) ?: ""
        val current = service
        status = if (current == null) {
            Status.UNKNOWN
        } else {
            val scope = runCatching { current.scope }.getOrNull().orEmpty()
            when {
                imePackage.isNotEmpty() && scope.contains(imePackage) -> Status.ACTIVE
                else -> Status.INACTIVE
            }
        }
    }

    LaunchedEffect(service) {
        val prefs = service?.getRemotePreferences(SettingsKeys.PREFS_GROUP) ?: return@LaunchedEffect
        landscapeEnabled = runCatching {
            prefs.getBoolean(SettingsKeys.LANDSCAPE_ENABLED, true)
        }.getOrDefault(true)
    }

    Scaffold(
        topBar = { SmallTopAppBar(title = stringResource(R.string.app_name)) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 840.dp)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        ) {
            item {
                StatusCard(status = status, onClick = { refreshKey++ })
            }

            item {
                Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    BasicComponent(
                        title = stringResource(R.string.landscape_support),
                        summary = stringResource(R.string.landscape_support_summary),
                        rightActions = {
                            Switch(
                                checked = landscapeEnabled,
                                onCheckedChange = { checked ->
                                    val current = FrameworkService.service
                                    if (current == null) {
                                        Toast.makeText(
                                            context, serviceUnavailable, Toast.LENGTH_LONG
                                        ).show()
                                    } else {
                                        runCatching {
                                            current.getRemotePreferences(SettingsKeys.PREFS_GROUP)
                                                .edit()
                                                .putBoolean(
                                                    SettingsKeys.LANDSCAPE_ENABLED, checked
                                                )
                                                .apply()
                                        }.onSuccess {
                                            landscapeEnabled = checked
                                        }.onFailure {
                                            Toast.makeText(
                                                context, serviceUnavailable, Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                }
                            )
                        }
                    )
                }
            }

            item {
                SmallTitle(stringResource(R.string.test_section))
                Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    TextField(
                        value = testInput,
                        onValueChange = { testInput = it },
                        label = stringResource(R.string.test_input_label),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item {
                SmallTitle(stringResource(R.string.ime_section))
                Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    BasicComponent(
                        title = stringResource(R.string.current_ime),
                        summary = currentIme.ifEmpty { stringResource(R.string.ime_not_found) },
                    )
                }
            }


        }
    }
    }
}

@Composable
private fun StatusCard(status: Status, onClick: () -> Unit) {
    val dark = isSystemInDarkTheme()
    val color = when (status) {
        Status.ACTIVE -> if (dark) Color(0xFF34C759) else Color(0xFF1D9E48)
        Status.INACTIVE -> if (dark) Color(0xFFFF453A) else Color(0xFFD70015)
        Status.UNKNOWN -> MiuixTheme.colorScheme.onSurfaceVariantSummary
    }
    val background = when (status) {
        Status.ACTIVE -> if (dark) Color(0xFF12281B) else Color(0xFFE8F7EC)
        Status.INACTIVE -> if (dark) Color(0xFF2A1313) else Color(0xFFFDEBEA)
        Status.UNKNOWN -> MiuixTheme.colorScheme.surfaceContainer
    }
    val icon = when (status) {
        Status.ACTIVE -> MiuixIcons.Useful.Confirm
        Status.INACTIVE -> MiuixIcons.Useful.Cancel
        Status.UNKNOWN -> MiuixIcons.Useful.Info
    }
    Card(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        insideMargin = PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(color = background),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = color)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.module_status),
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = MiuixTheme.colorScheme.onSurface
                )
                Text(
                    text = when (status) {
                        Status.ACTIVE -> stringResource(R.string.status_active_summary)
                        Status.INACTIVE -> stringResource(R.string.status_inactive_summary)
                        Status.UNKNOWN -> stringResource(R.string.status_unknown_summary)
                    },
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
            Text(
                text = when (status) {
                    Status.ACTIVE -> stringResource(R.string.status_active)
                    Status.INACTIVE -> stringResource(R.string.status_inactive)
                    Status.UNKNOWN -> stringResource(R.string.status_unknown)
                },
                color = color,
                fontSize = 14.sp
            )
        }
    }
}
