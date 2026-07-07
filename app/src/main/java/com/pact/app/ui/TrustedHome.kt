package com.pact.app.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pact.app.R
import com.pact.app.core.PactState
import com.pact.app.core.TrustNetwork
import kotlinx.coroutines.launch
import com.pact.app.ui.theme.Amber
import com.pact.app.ui.theme.CardBorder
import com.pact.app.ui.theme.Mint
import com.pact.app.ui.theme.PactGradient
import com.pact.app.ui.theme.Periwinkle
import com.pact.app.ui.theme.Rose
import com.pact.app.ui.theme.Surface1
import com.pact.app.ui.theme.Surface2
import com.pact.app.ui.theme.TextSecondary
import com.pact.app.ui.theme.TextTertiary

/**
 * The trusted person's side: the people whose locks they hold, their open
 * requests, and a chat with each. One identity, many relationships.
 */

// -------------------------------------------------------------- setup flow

@Composable
fun TrustedSetupFlow(state: PactState, onBack: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val network = remember { TrustNetwork.get(context) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var myName by remember { mutableStateOf(network.myName) }
    var scanning by remember { mutableStateOf(false) }
    var pairedName by remember { mutableStateOf<String?>(null) }
    var code by remember { mutableStateOf("") }
    var redeeming by remember { mutableStateOf(false) }

    if (scanning) {
        ScanScreen(
            title = stringResource(R.string.trusted_scan),
            onResult = { content ->
                scanning = false
                val contact = network.acceptPairing(content)
                if (contact != null) {
                    pairedName = contact.name
                } else {
                    Toast.makeText(context, context.getString(R.string.scan_not_pact), Toast.LENGTH_LONG).show()
                }
            },
            onClose = { scanning = false },
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 28.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.common_back),
                    tint = TextSecondary,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.trusted_setup_title), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.trusted_setup_body),
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = myName,
            onValueChange = { myName = it },
            label = { Text(stringResource(R.string.my_name_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {}),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Periwinkle,
                unfocusedBorderColor = Surface2,
            ),
        )
        Spacer(Modifier.height(16.dp))
        PactCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, tint = Periwinkle)
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.sponsor_scan_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            }
            Spacer(Modifier.height(16.dp))
            PactButton(
                stringResource(R.string.trusted_scan),
                onClick = {
                    network.myName = myName
                    scanning = true
                },
                enabled = myName.trim().length >= 2,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(16.dp))
        PactCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Dialpad, contentDescription = null, tint = Periwinkle)
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.pair_enter_code_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            }
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.uppercase() },
                label = { Text(stringResource(R.string.pair_enter_code_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {}),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Periwinkle,
                    unfocusedBorderColor = Surface2,
                ),
            )
            Spacer(Modifier.height(14.dp))
            PactButton(
                if (redeeming) stringResource(R.string.pair_connecting) else stringResource(R.string.pair_connect),
                onClick = {
                    network.myName = myName
                    redeeming = true
                    scope.launch {
                        val contact = network.redeemPairingCode(code)
                        redeeming = false
                        if (contact != null) {
                            pairedName = contact.name
                        } else {
                            Toast.makeText(context, context.getString(R.string.pair_code_not_found), Toast.LENGTH_LONG).show()
                        }
                    }
                },
                enabled = myName.trim().length >= 2 && code.trim().length >= 6 && !redeeming,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        pairedName?.let { name ->
            Spacer(Modifier.height(16.dp))
            PactCard(background = Surface2) {
                Text(
                    stringResource(R.string.trusted_paired, name),
                    style = MaterialTheme.typography.titleSmall,
                    color = Mint,
                )
            }
            Spacer(Modifier.height(16.dp))
            PactButton(
                stringResource(R.string.common_done),
                onClick = {
                    state.becomeSponsor()
                    onDone()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

// -------------------------------------------------------------------- home

@Composable
fun TrustedHome(state: PactState, onOpenChat: (String) -> Unit) {
    val context = LocalContext.current
    val network = remember { TrustNetwork.get(context) }
    val snapshot by network.snapshot.collectAsState()
    var scanning by remember { mutableStateOf(false) }
    var answering by remember { mutableStateOf<TrustNetwork.IncomingRequest?>(null) }

    if (scanning) {
        BackHandler { scanning = false }
        ScanScreen(
            title = stringResource(R.string.trusted_scan),
            onResult = { content ->
                scanning = false
                if (network.acceptPairing(content) == null) {
                    Toast.makeText(context, context.getString(R.string.scan_not_pact), Toast.LENGTH_LONG).show()
                }
            },
            onClose = { scanning = false },
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 4.dp),
        ) {
            PactLogo(36)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(R.string.role_trusted_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = Periwinkle,
                )
            }
            IconButton(onClick = { scanning = true }) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.circle_add_person),
                    tint = TextSecondary,
                )
            }
        }
        Text(
            stringResource(R.string.trusted_home_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = TextTertiary,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // open requests first — they're the point
            val open = snapshot.openIncoming()
            items(open, key = { "req_${it.id}" }) { request ->
                IncomingRequestCard(
                    network = network,
                    request = request,
                    onAnswerCustom = { answering = request },
                )
            }

            if (snapshot.wards().isEmpty()) {
                item {
                    PactCard {
                        Text(
                            stringResource(R.string.trusted_no_wards),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                        )
                    }
                }
            }

            items(snapshot.wards(), key = { "ward_${it.id}" }) { ward ->
                val unread = snapshot.unread[ward.id] ?: 0
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Surface1)
                        .clickable { onOpenChat(ward.id) }
                        .padding(16.dp),
                ) {
                    NameBubble(ward.name)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(ward.name, style = MaterialTheme.typography.titleMedium)
                        val last = snapshot.messagesWith(ward.id).lastOrNull()
                        if (last != null) {
                            Text(
                                last.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextTertiary,
                                maxLines = 1,
                            )
                        }
                    }
                    if (unread > 0) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(PactGradient),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("$unread", style = MaterialTheme.typography.labelMedium, color = com.pact.app.ui.theme.Ink)
                        }
                    } else {
                        Icon(
                            Icons.AutoMirrored.Rounded.Chat,
                            contentDescription = stringResource(R.string.chat_open),
                            tint = TextTertiary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }

    answering?.let { request ->
        CustomGrantDialog(
            onDismiss = { answering = null },
            onGrant = { minutes, note ->
                network.respond(request.id, approve = true, customMinutes = minutes, message = note)
                answering = null
            },
        )
    }
}

@Composable
fun NameBubble(name: String, sizeDp: Int = 44) {
    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(Surface2),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trim().take(1).uppercase(),
            style = MaterialTheme.typography.titleMedium,
            color = Periwinkle,
        )
    }
}

@Composable
fun IncomingRequestCard(
    network: TrustNetwork,
    request: TrustNetwork.IncomingRequest,
    onAnswerCustom: () -> Unit,
) {
    val fromName = network.contact(request.fromContactId)?.name ?: "?"
    val now by rememberNow()
    PactCard(background = Surface2) {
        Text(
            stringResource(R.string.incoming_title, fromName),
            style = MaterialTheme.typography.titleMedium,
            color = Amber,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            when {
                request.kind == TrustNetwork.RequestKind.UNLOCK ->
                    stringResource(R.string.incoming_unlock, request.label, request.minutes)
                request.changeAction == TrustNetwork.CHANGE_REMOVE_APP ->
                    stringResource(R.string.incoming_change_remove, request.label)
                request.changeAction == TrustNetwork.CHANGE_TIER_DOWN ->
                    stringResource(R.string.incoming_change_tier, request.label)
                request.changeAction == TrustNetwork.CHANGE_LIMIT_UP ->
                    stringResource(R.string.incoming_change_limit, request.label, request.minutes)
                request.changeAction == TrustNetwork.CHANGE_STRICT_OFF ->
                    stringResource(R.string.incoming_change_strict)
                else -> stringResource(R.string.incoming_change_reset)
            },
            style = MaterialTheme.typography.titleSmall,
        )
        request.usageNote?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
        request.reason?.let {
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.incoming_reason, it),
                style = MaterialTheme.typography.bodyMedium,
                color = TextTertiary,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.request_expires_in, formatCountdown(request.exp - now)),
            style = MaterialTheme.typography.labelMedium,
            color = TextTertiary,
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PactButton(
                stringResource(R.string.incoming_approve),
                onClick = { network.respond(request.id, approve = true, customMinutes = null, message = null) },
                modifier = Modifier.weight(1f),
            )
            PactButton(
                stringResource(R.string.incoming_deny),
                onClick = { network.respond(request.id, approve = false, customMinutes = null, message = null) },
                tonal = true,
                modifier = Modifier.weight(1f),
            )
        }
        if (request.kind == TrustNetwork.RequestKind.UNLOCK) {
            TextButton(onClick = onAnswerCustom) {
                Text(stringResource(R.string.incoming_custom), color = Periwinkle)
            }
        }
    }
}

@Composable
private fun CustomGrantDialog(onDismiss: () -> Unit, onGrant: (Int, String?) -> Unit) {
    var minutes by remember { mutableStateOf(10) }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.incoming_custom_title), style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 10, 30, 60).forEach { m ->
                        val selected = minutes == m
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) Periwinkle else Surface2)
                                .clickable { minutes = m }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        ) {
                            Text(
                                stringResource(R.string.request_min_generic, m),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) com.pact.app.ui.theme.Ink else TextSecondary,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.incoming_message_hint)) },
                    singleLine = true,
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
            TextButton(onClick = { onGrant(minutes, note.takeIf { it.isNotBlank() }) }) {
                Text(stringResource(R.string.incoming_approve))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}
