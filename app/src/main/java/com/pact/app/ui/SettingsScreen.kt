package com.pact.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.pact.app.R
import com.pact.app.core.Backup
import com.pact.app.core.PactState
import com.pact.app.core.TrustNetwork
import com.pact.app.ui.theme.Mint
import com.pact.app.ui.theme.Periwinkle
import com.pact.app.ui.theme.Surface2
import com.pact.app.ui.theme.TextSecondary
import com.pact.app.ui.theme.TextTertiary

/**
 * Settings for the person locking their apps. Anything that loosens the Pact
 * — turning off strict mode, ending the Pact — is a change the circle must
 * approve, sent as a signed request. No codes, no secrets: just people.
 */
@Composable
fun SettingsScreen(state: PactState, onBack: () -> Unit) {
    val context = LocalContext.current
    val network = remember { TrustNetwork.get(context) }
    val snapshot by state.snapshot.collectAsState()
    val netSnap by network.snapshot.collectAsState()
    val hasCircle = netSnap.approvers().isNotEmpty()
    var requested by remember { mutableStateOf(false) }
    var confirmIdentity by remember { mutableStateOf(false) }

    fun requestChange(action: String, label: String) {
        if (hasCircle) {
            network.createRequest(
                kind = TrustNetwork.RequestKind.CHANGE,
                changeAction = action,
                pkg = null,
                label = label,
                minutes = 0,
                reason = null,
                usageNote = null,
            )
            requested = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 12.dp, bottom = 16.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.common_back),
                    tint = TextSecondary,
                )
            }
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall)
        }

        // strict mode
        SectionLabel(stringResource(R.string.settings_strict_section))
        Spacer(Modifier.height(8.dp))
        PactCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.strict_title), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.strict_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = snapshot.strictMode,
                    onCheckedChange = { wantOn ->
                        // Turning ON is free; turning OFF asks the circle.
                        if (wantOn) state.setStrictMode(true)
                        else if (hasCircle) requestChange(TrustNetwork.CHANGE_STRICT_OFF, context.getString(R.string.strict_title))
                        else state.setStrictMode(false)
                    },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = Periwinkle,
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            }
        }

        // reliability
        Spacer(Modifier.height(20.dp))
        SectionLabel(stringResource(R.string.settings_reliability_section))
        Spacer(Modifier.height(8.dp))
        ReliabilityCard()

        // how it works
        Spacer(Modifier.height(20.dp))
        SectionLabel(stringResource(R.string.settings_how_section))
        Spacer(Modifier.height(8.dp))
        PactCard {
            Text(
                stringResource(R.string.how_body),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
        }

        // backup
        Spacer(Modifier.height(20.dp))
        SectionLabel(stringResource(R.string.backup_section))
        Spacer(Modifier.height(8.dp))
        BackupCard(state = state)

        // danger zone
        Spacer(Modifier.height(20.dp))
        SectionLabel(stringResource(R.string.settings_danger))
        Spacer(Modifier.height(8.dp))
        PactCard {
            TextButton(onClick = {
                if (hasCircle) requestChange(TrustNetwork.CHANGE_RESET, context.getString(R.string.reset_action))
                else state.reset()
            }) {
                Text(stringResource(R.string.reset_action), color = MaterialTheme.colorScheme.error)
            }
            Text(
                stringResource(R.string.reset_hint, netSnap.approvers().firstOrNull()?.name ?: ""),
                style = MaterialTheme.typography.bodyMedium,
                color = TextTertiary,
            )
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = { confirmIdentity = true }) {
                Text(stringResource(R.string.identity_reset), color = TextTertiary)
            }
            Text(
                stringResource(R.string.identity_reset_note),
                style = MaterialTheme.typography.bodyMedium,
                color = TextTertiary,
            )
        }
        Spacer(Modifier.height(32.dp))
    }

    if (requested) {
        AlertDialog(
            onDismissRequest = { requested = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(stringResource(R.string.request_sent), style = MaterialTheme.typography.headlineSmall) },
            text = {
                Text(
                    stringResource(R.string.request_change_sent),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = { requested = false }) { Text(stringResource(R.string.common_done)) }
            },
        )
    }

    if (confirmIdentity) {
        AlertDialog(
            onDismissRequest = { confirmIdentity = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(stringResource(R.string.identity_reset), style = MaterialTheme.typography.headlineSmall) },
            text = {
                Text(
                    stringResource(R.string.identity_reset_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    state.reset()
                    confirmIdentity = false
                }) { Text(stringResource(R.string.common_remove), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmIdentity = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
}


/** Encrypted export/import plus a stats-only CSV. All through the system file picker. */
@Composable
private fun BackupCard(state: PactState) {
    val context = LocalContext.current
    var passphraseFor by remember { mutableStateOf<BackupAction?>(null) }
    var passphrase by remember { mutableStateOf("") }
    var pendingImport by remember { mutableStateOf<android.net.Uri?>(null) }

    fun toast(resId: Int) =
        android.widget.Toast.makeText(context, context.getString(resId), android.widget.Toast.LENGTH_LONG).show()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null && passphrase.isNotEmpty()) {
            runCatching {
                val json = Backup.export(state.snapshot.value).toString()
                val blob = Backup.encrypt(json, passphrase.toCharArray())
                context.contentResolver.openOutputStream(uri)?.use { it.write(blob.toByteArray()) }
                toast(R.string.backup_done)
            }.onFailure { toast(R.string.backup_failed) }
        }
        passphrase = ""
    }

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)
                    ?.use { it.write(Backup.statsCsv(state.snapshot.value).toByteArray()) }
                toast(R.string.backup_done)
            }.onFailure { toast(R.string.backup_failed) }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingImport = uri
            passphraseFor = BackupAction.IMPORT
        }
    }

    PactCard {
        TextButton(onClick = { passphraseFor = BackupAction.EXPORT }) {
            Text(stringResource(R.string.backup_export), color = Periwinkle)
        }
        Text(
            stringResource(R.string.backup_export_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = TextTertiary,
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { csvLauncher.launch("pact-stats.csv") }) {
            Text(stringResource(R.string.backup_export_csv), color = Periwinkle)
        }
        TextButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) {
            Text(stringResource(R.string.backup_import), color = Periwinkle)
        }
        Text(
            stringResource(R.string.backup_import_note),
            style = MaterialTheme.typography.bodyMedium,
            color = TextTertiary,
        )
    }

    passphraseFor?.let { action ->
        AlertDialog(
            onDismissRequest = {
                passphraseFor = null
                passphrase = ""
            },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(stringResource(R.string.backup_passphrase), style = MaterialTheme.typography.headlineSmall) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.backup_passphrase_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Periwinkle,
                            unfocusedBorderColor = Surface2,
                        ),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = passphrase.length >= 4,
                    onClick = {
                        when (action) {
                            BackupAction.EXPORT -> {
                                passphraseFor = null
                                exportLauncher.launch("pact-backup.pact")
                            }
                            BackupAction.IMPORT -> {
                                val uri = pendingImport
                                passphraseFor = null
                                if (uri != null) {
                                    runCatching {
                                        val blob = context.contentResolver.openInputStream(uri)
                                            ?.use { it.readBytes().toString(Charsets.UTF_8) }
                                            ?: error("empty")
                                        val json = Backup.decrypt(blob, passphrase.toCharArray())
                                            ?: error("bad passphrase")
                                        if (state.applyBackup(org.json.JSONObject(json))) {
                                            toast(R.string.backup_restored)
                                        } else {
                                            toast(R.string.backup_failed)
                                        }
                                    }.onFailure { toast(R.string.backup_failed) }
                                    passphrase = ""
                                    pendingImport = null
                                }
                            }
                        }
                    },
                ) {
                    Text(
                        stringResource(
                            if (action == BackupAction.EXPORT) R.string.backup_encrypt_and_save
                            else R.string.backup_decrypt_and_restore
                        )
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    passphraseFor = null
                    passphrase = ""
                }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
}

private enum class BackupAction { EXPORT, IMPORT }

/**
 * Optional extra reliability: granting Usage Access lets a background poll act
 * as a second foreground-app detector. The shield already works without it;
 * this just makes it harder to slip past.
 */
@Composable
private fun ReliabilityCard() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val granted by androidx.compose.runtime.produceState(
        initialValue = com.pact.app.service.ShieldService.hasUsageAccess(context)
    ) {
        while (true) {
            value = com.pact.app.service.ShieldService.hasUsageAccess(context)
            kotlinx.coroutines.delay(1500L)
        }
    }
    PactCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (granted) Icons.Rounded.Check else Icons.Rounded.Shield,
                contentDescription = null,
                tint = if (granted) Mint else Periwinkle,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.reliability_title), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(if (granted) R.string.reliability_on else R.string.reliability_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (granted) Mint else TextSecondary,
                )
            }
        }
        if (!granted) {
            Spacer(Modifier.height(12.dp))
            PactButton(
                stringResource(R.string.reliability_grant),
                onClick = {
                    runCatching {
                        context.startActivity(
                            android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
