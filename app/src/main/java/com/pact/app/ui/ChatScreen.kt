package com.pact.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.pact.app.core.TrustNetwork
import com.pact.app.ui.theme.Ink
import com.pact.app.ui.theme.PactGradient
import com.pact.app.ui.theme.Periwinkle
import com.pact.app.ui.theme.Surface1
import com.pact.app.ui.theme.Surface2
import com.pact.app.ui.theme.TextSecondary
import com.pact.app.ui.theme.TextTertiary
import java.text.DateFormat
import java.util.Date

/**
 * 1:1 end-to-end encrypted chat with one contact. Every bubble travelled as
 * ciphertext signed by the person who wrote it; none of that is visible here
 * — it's just a conversation.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(contactId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val network = remember { TrustNetwork.get(context) }
    val snapshot by network.snapshot.collectAsState()
    val contact = snapshot.contacts.firstOrNull { it.id == contactId }
    val messages = snapshot.messagesWith(contactId)
    var draft by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) { network.markRead(contactId) }
    LaunchedEffect(messages.size) {
        network.markRead(contactId)
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }
    BackHandler { onBack() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .navigationBarsPadding(),
    ) {
        // header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.common_back),
                    tint = TextSecondary,
                )
            }
            NameBubble(contact?.name ?: "?", sizeDp = 38)
            Spacer(Modifier.width(12.dp))
            Text(contact?.name ?: "", style = MaterialTheme.typography.titleMedium)
        }

        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            if (messages.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.chat_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextTertiary,
                        )
                    }
                }
            }
            items(messages, key = { it.id }) { message ->
                val align = if (message.fromMe) Alignment.CenterEnd else Alignment.CenterStart
                Box(Modifier.fillMaxWidth(), contentAlignment = align) {
                    Column(
                        horizontalAlignment = if (message.fromMe) Alignment.End else Alignment.Start,
                        modifier = Modifier.widthIn(max = 300.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(
                                    RoundedCornerShape(
                                        topStart = 18.dp,
                                        topEnd = 18.dp,
                                        bottomStart = if (message.fromMe) 18.dp else 4.dp,
                                        bottomEnd = if (message.fromMe) 4.dp else 18.dp,
                                    )
                                )
                                .then(
                                    if (message.fromMe) Modifier.background(PactGradient)
                                    else Modifier.background(Surface2)
                                )
                                .combinedClickable(
                                    onClick = {},
                                    onLongClick = { deleting = message.id },
                                )
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        ) {
                            Text(
                                message.text,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (message.fromMe) Ink else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Text(
                            if (message.status == TrustNetwork.MessageStatus.QUEUED) {
                                stringResource(R.string.chat_queued)
                            } else {
                                DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(message.ts))
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = TextTertiary,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(4.dp)) }
        }

        // composer
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text(stringResource(R.string.chat_hint), color = TextTertiary) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(22.dp),
                maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Periwinkle,
                    unfocusedBorderColor = Surface1,
                ),
            )
            Spacer(Modifier.width(10.dp))
            IconButton(
                onClick = {
                    val text = draft.trim()
                    if (text.isNotEmpty()) {
                        network.sendChat(contactId, text)
                        draft = ""
                    }
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(PactGradient),
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.Send,
                    contentDescription = stringResource(R.string.chat_send),
                    tint = Ink,
                )
            }
        }
    }

    deleting?.let { id ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(stringResource(R.string.chat_delete), style = MaterialTheme.typography.headlineSmall) },
            text = {},
            confirmButton = {
                TextButton(onClick = {
                    network.deleteMessageLocally(id)
                    deleting = null
                }) { Text(stringResource(R.string.common_remove), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
}
