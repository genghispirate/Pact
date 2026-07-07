package com.pact.app.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pact.app.R
import com.pact.app.core.PactState
import com.pact.app.core.TrustNetwork
import com.pact.app.ui.theme.CardBorder
import com.pact.app.ui.theme.Mint
import com.pact.app.ui.theme.Periwinkle
import com.pact.app.ui.theme.Surface1
import com.pact.app.ui.theme.Surface2
import com.pact.app.ui.theme.TextSecondary
import com.pact.app.ui.theme.TextTertiary

/**
 * Manage the circle: the trusted people, the approval rule, per-person
 * permissions, and a way in to each chat. Adding someone is a QR scan;
 * removing someone needs the circle's approval.
 */
@Composable
fun CircleScreen(
    state: PactState,
    onBack: () -> Unit,
    onOpenChat: (String) -> Unit,
) {
    val context = LocalContext.current
    val network = remember { TrustNetwork.get(context) }
    val snapshot by network.snapshot.collectAsState()
    var scanning by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<TrustNetwork.Contact?>(null) }

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

    BackHandler { onBack() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 12.dp, bottom = 12.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.common_back),
                    tint = TextSecondary,
                )
            }
            Text(stringResource(R.string.circle_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            IconButton(onClick = { scanning = true }) {
                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.circle_add_person), tint = TextSecondary)
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                SectionLabel(stringResource(R.string.circle_add_section))
                Spacer(Modifier.height(8.dp))
                PactCard {
                    Text(
                        stringResource(R.string.pair_code_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                    Spacer(Modifier.height(6.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        InviteByCode(network)
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            item {
                SectionLabel(stringResource(R.string.rule_section))
                Spacer(Modifier.height(8.dp))
                PactCard {
                    RuleOption(TrustNetwork.Rule.ANY, snapshot.rule, R.string.rule_any, R.string.rule_any_desc) { network.rule = it }
                    Spacer(Modifier.height(8.dp))
                    RuleOption(TrustNetwork.Rule.MAJORITY, snapshot.rule, R.string.rule_majority, R.string.rule_majority_desc) { network.rule = it }
                    Spacer(Modifier.height(8.dp))
                    RuleOption(TrustNetwork.Rule.ALL, snapshot.rule, R.string.rule_all, R.string.rule_all_desc) { network.rule = it }
                }
            }

            item {
                SectionLabel(stringResource(R.string.circle_title), Modifier.padding(top = 10.dp))
                Spacer(Modifier.height(8.dp))
            }

            if (snapshot.supporters().isEmpty()) {
                item {
                    PactCard {
                        Text(
                            stringResource(R.string.circle_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                        )
                    }
                }
            }

            items(snapshot.supporters(), key = { it.id }) { contact ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Surface1)
                        .clickable { editing = contact }
                        .padding(16.dp),
                ) {
                    NameBubble(contact.name)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(contact.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(
                                if (contact.canApprove) R.string.perm_can_approve else R.string.perm_chat_only
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextTertiary,
                        )
                    }
                    IconButton(onClick = { onOpenChat(contact.id) }) {
                        Icon(
                            Icons.AutoMirrored.Rounded.Chat,
                            contentDescription = stringResource(R.string.chat_open),
                            tint = Periwinkle,
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }

    editing?.let { contact ->
        ContactSheet(
            network = network,
            state = state,
            contact = contact,
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun RuleOption(
    value: TrustNetwork.Rule,
    current: TrustNetwork.Rule,
    titleRes: Int,
    descRes: Int,
    onSelect: (TrustNetwork.Rule) -> Unit,
) {
    val selected = value == current
    val shape = RoundedCornerShape(14.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) Surface2 else MaterialTheme.colorScheme.surface)
            .border(1.dp, if (selected) Periwinkle.copy(alpha = 0.6f) else CardBorder, shape)
            .clickable { onSelect(value) }
            .padding(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(titleRes), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(descRes), style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
    }
}

@Composable
private fun ContactSheet(
    network: TrustNetwork,
    state: PactState,
    contact: TrustNetwork.Contact,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var canApprove by remember { mutableStateOf(contact.canApprove) }
    var canViewStats by remember { mutableStateOf(contact.canViewStats) }

    AlertDialog(
        onDismissRequest = {
            network.setPermissions(contact.id, canApprove, canViewStats)
            onDismiss()
        },
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(contact.name, style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column {
                PermissionToggle(stringResource(R.string.perm_can_approve), canApprove) { canApprove = it }
                Spacer(Modifier.height(6.dp))
                PermissionToggle(stringResource(R.string.perm_can_stats), canViewStats) { canViewStats = it }
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = {
                    // Removing a trusted person is itself a change the circle approves.
                    if (network.snapshot.value.approvers().size > 1) {
                        network.createRequest(
                            kind = TrustNetwork.RequestKind.CHANGE,
                            changeAction = TrustNetwork.CHANGE_RESET, // placeholder unused for contact removal
                            pkg = null,
                            label = contact.name,
                            minutes = 0,
                            reason = null,
                            usageNote = null,
                        )
                    }
                    network.removeContact(contact.id)
                    onDismiss()
                }) {
                    Text(stringResource(R.string.contact_remove), color = MaterialTheme.colorScheme.error)
                }
                Text(
                    stringResource(R.string.contact_remove_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextTertiary,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                network.setPermissions(contact.id, canApprove, canViewStats)
                onDismiss()
            }) { Text(stringResource(R.string.common_done)) }
        },
    )
}

@Composable
private fun PermissionToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = Periwinkle,
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
            ),
        )
    }
}
