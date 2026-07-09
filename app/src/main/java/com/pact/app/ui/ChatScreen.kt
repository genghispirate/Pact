package com.pact.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pact.app.R
import com.pact.app.core.TrustNetwork
import com.pact.app.ui.theme.CardBorder
import com.pact.app.ui.theme.Ink
import com.pact.app.ui.theme.Mint
import com.pact.app.ui.theme.PactGradient
import com.pact.app.ui.theme.Periwinkle
import com.pact.app.ui.theme.Rose
import com.pact.app.ui.theme.Surface1
import com.pact.app.ui.theme.Surface2
import com.pact.app.ui.theme.TextSecondary
import com.pact.app.ui.theme.TextTertiary
import com.pact.app.ui.theme.Violet
import java.text.DateFormat
import java.util.Date

/**
 * The Lockbox: an end-to-end encrypted 1:1 thread that also carries live unlock
 * requests as interactive widgets — so the squad can roast or cheer *before*
 * tapping Grant. Every bubble and request travelled as signed ciphertext; none
 * of that shows here.
 */
@Composable
fun ChatScreen(contactId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val network = remember { TrustNetwork.get(context) }
    val snapshot by network.snapshot.collectAsState()
    val contact = snapshot.contacts.firstOrNull { it.id == contactId }

    val feed = remember(snapshot, contactId) {
        val items = mutableListOf<Feed>()
        snapshot.messagesWith(contactId).forEach { items += Feed.Msg(it) }
        snapshot.incoming.filter { it.fromContactId == contactId }.forEach { items += Feed.In(it) }
        snapshot.requests.filter { contactId in it.approverIds }.forEach { items += Feed.Out(it) }
        items.sortedBy { it.ts }
    }

    var draft by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) { network.markRead(contactId) }
    LaunchedEffect(feed.size) {
        network.markRead(contactId)
        if (feed.isNotEmpty()) listState.animateScrollToItem(feed.size - 1)
    }
    BackHandler { onBack() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .navigationBarsPadding(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.common_back),
                    tint = TextSecondary,
                )
            }
            NameBubble(contact?.name ?: "?", face = contact?.face, sizeDp = 38)
            Spacer(Modifier.width(12.dp))
            Text(contact?.name ?: "", style = MaterialTheme.typography.titleMedium)
        }

        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
        ) {
            if (feed.isEmpty()) {
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
            items(feed, key = { it.key }) { item ->
                when (item) {
                    is Feed.In -> RequestWidgetIncoming(
                        request = item.r,
                        onGrant = { network.respond(item.r.id, approve = true, customMinutes = null, message = null) },
                        onDeny = { network.respond(item.r.id, approve = false, customMinutes = null, message = null) },
                    )
                    is Feed.Out -> RequestWidgetOutgoing(item.r)
                    is Feed.Msg -> ChatBubble(item.m, onLongPress = { deleting = item.m.id })
                }
            }
            item { Spacer(Modifier.height(4.dp)) }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
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
                modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(PactGradient),
            ) {
                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = stringResource(R.string.chat_send), tint = com.pact.app.ui.theme.OnAccent)
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
                TextButton(onClick = { network.deleteMessageLocally(id); deleting = null }) {
                    Text(stringResource(R.string.common_remove), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
}

/** One thread carries three kinds of item; this keeps them time-ordered. */
private sealed interface Feed {
    val ts: Long
    val key: String
    data class Msg(val m: TrustNetwork.ChatMessage) : Feed {
        override val ts get() = m.ts
        override val key get() = "m_${m.id}"
    }
    data class In(val r: TrustNetwork.IncomingRequest) : Feed {
        override val ts get() = r.receivedAt
        override val key get() = "i_${r.id}"
    }
    data class Out(val r: TrustNetwork.ApprovalRequest) : Feed {
        override val ts get() = r.createdAt
        override val key get() = "o_${r.id}"
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatBubble(message: TrustNetwork.ChatMessage, onLongPress: () -> Unit) {
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
                    .combinedClickable(onClick = {}, onLongClick = onLongPress)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(
                    message.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (message.fromMe) Ink else MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                if (message.status == TrustNetwork.MessageStatus.QUEUED) stringResource(R.string.chat_queued)
                else DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(message.ts)),
                style = MaterialTheme.typography.labelMedium,
                color = TextTertiary,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
    }
}

/** A request I received: decide it right here, after the roast. Flips green/coral once decided. */
@Composable
private fun RequestWidgetIncoming(
    request: TrustNetwork.IncomingRequest,
    onGrant: () -> Unit,
    onDeny: () -> Unit,
) {
    val flip by animateFloatAsState(if (request.decided) 360f else 0f, tween(500), label = "flip")
    val accent = when {
        !request.decided -> Violet
        request.myDecision == true -> Mint
        else -> Rose
    }
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { rotationY = flip }
            .clip(shape)
            .background(accent.copy(alpha = 0.10f))
            .border(1.5.dp, accent.copy(alpha = 0.7f), shape)
            .padding(16.dp),
    ) {
        Text(
            if (request.kind == TrustNetwork.RequestKind.UNLOCK)
                stringResource(R.string.widget_wants, request.label, request.minutes)
            else request.label,
            style = MaterialTheme.typography.titleSmall,
        )
        request.reason?.let {
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.incoming_reason, it), style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
        Spacer(Modifier.height(12.dp))
        if (!request.decided) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PactButton(stringResource(R.string.widget_grant), onClick = onGrant, modifier = Modifier.weight(1f))
                PactButton(stringResource(R.string.widget_deny), onClick = onDeny, tonal = true, modifier = Modifier.weight(1f))
            }
        } else {
            Text(
                stringResource(if (request.myDecision == true) R.string.widget_granted else R.string.widget_denied),
                style = MaterialTheme.typography.titleSmall,
                color = accent,
            )
        }
    }
}

/** My own request, shown in the thread with its live status. */
@Composable
private fun RequestWidgetOutgoing(request: TrustNetwork.ApprovalRequest) {
    val accent = when (request.state) {
        TrustNetwork.RequestState.APPROVED -> Mint
        TrustNetwork.RequestState.DENIED -> Rose
        TrustNetwork.RequestState.EXPIRED -> TextTertiary
        else -> Violet
    }
    val shape = RoundedCornerShape(18.dp)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(shape)
                .background(accent.copy(alpha = 0.10f))
                .border(1.5.dp, accent.copy(alpha = 0.6f), shape)
                .padding(16.dp),
        ) {
            Text(
                if (request.kind == TrustNetwork.RequestKind.UNLOCK)
                    stringResource(R.string.widget_you_asked, request.minutes, request.label)
                else request.label,
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(
                    when (request.state) {
                        TrustNetwork.RequestState.APPROVED -> R.string.widget_status_granted
                        TrustNetwork.RequestState.DENIED -> R.string.widget_status_denied
                        TrustNetwork.RequestState.EXPIRED -> R.string.widget_status_expired
                        else -> R.string.widget_status_waiting
                    }
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = accent,
            )
        }
    }
}
