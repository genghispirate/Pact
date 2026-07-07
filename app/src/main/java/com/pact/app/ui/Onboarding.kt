package com.pact.app.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pact.app.R
import com.pact.app.core.PactState
import com.pact.app.core.Qr
import com.pact.app.core.TrustNetwork
import com.pact.app.service.BlockerService
import com.pact.app.ui.theme.Amber
import com.pact.app.ui.theme.Ink
import com.pact.app.ui.theme.Mint
import com.pact.app.ui.theme.PactGradient
import com.pact.app.ui.theme.Periwinkle
import com.pact.app.ui.theme.Surface1
import com.pact.app.ui.theme.Surface2
import com.pact.app.ui.theme.TextSecondary
import com.pact.app.ui.theme.TextTertiary
import com.pact.app.ui.theme.Violet
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Onboarding: introduction → role → guided setup. The user never sees a key,
 * a code, or a protocol — just people. Pairing is one QR scan; the circle
 * fills in live as trusted people join.
 */

private enum class Phase { Intro, RoleSelect, UserSetup, TrustedSetup }

@Composable
fun OnboardingFlow(state: PactState, onDone: () -> Unit) {
    var phase by remember { mutableStateOf(Phase.Intro) }
    BackHandler(enabled = phase == Phase.RoleSelect) { phase = Phase.Intro }
    when (phase) {
        Phase.Intro -> IntroPager(onFinished = { phase = Phase.RoleSelect })
        Phase.RoleSelect -> RoleSelect(
            onUser = { phase = Phase.UserSetup },
            onTrusted = { phase = Phase.TrustedSetup },
        )
        Phase.UserSetup -> UserSetupFlow(state = state, onDone = onDone)
        Phase.TrustedSetup -> TrustedSetupFlow(
            state = state,
            onBack = { phase = Phase.RoleSelect },
            onDone = onDone,
        )
    }
}

// ------------------------------------------------------------------- brand

/** The in-app logo mark: gradient rounded square with a shield. */
@Composable
fun PactLogo(sizeDp: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(sizeDp.dp)
            .clip(RoundedCornerShape((sizeDp * 30 / 100).dp))
            .background(PactGradient),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.Shield,
            contentDescription = null,
            tint = Color(0xFFF4F6FF),
            modifier = Modifier.size((sizeDp * 52 / 100).dp),
        )
        Icon(
            Icons.Rounded.Key,
            contentDescription = null,
            tint = com.pact.app.ui.theme.VioletDeep,
            modifier = Modifier.size((sizeDp * 22 / 100).dp),
        )
    }
}

// -------------------------------------------------------------------- intro

private data class IntroPage(
    val art: @Composable () -> Unit,
    val title: Int,
    val body: Int,
)

@Composable
private fun IntroPager(onFinished: () -> Unit) {
    val pages = listOf(
        IntroPage({ ArtLockedPhone() }, R.string.intro_page1_title, R.string.intro_page1_body),
        IntroPage({ ArtCircle() }, R.string.intro_page2_title, R.string.intro_page2_body),
        IntroPage({ ArtEncryptedShield() }, R.string.intro_page3_title, R.string.intro_page3_body),
    )
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize()) {
        // ambient brand wash behind everything
        Box(
            Modifier
                .fillMaxWidth()
                .height(420.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Violet.copy(alpha = 0.14f), Color.Transparent)
                    )
                )
        )
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 20.dp, start = 28.dp, end = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PactLogo(34)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                if (pagerState.currentPage < pages.size - 1) {
                    TextButton(onClick = onFinished) {
                        Text(stringResource(R.string.common_skip), color = TextTertiary)
                    }
                }
            }

            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                val p = pages[page]
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    p.art()
                    Spacer(Modifier.height(20.dp))
                    Text(
                        stringResource(p.title),
                        style = MaterialTheme.typography.displaySmall,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        stringResource(p.body),
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(pages.size) { i ->
                    val active = i == pagerState.currentPage
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (active) 26.dp else 8.dp, 8.dp)
                            .clip(CircleShape)
                            .then(
                                if (active) Modifier.background(PactGradient)
                                else Modifier.background(Surface2)
                            )
                    )
                }
            }
            PactButton(
                text = stringResource(
                    if (pagerState.currentPage == pages.size - 1) R.string.intro_get_started
                    else R.string.common_next
                ),
                onClick = {
                    if (pagerState.currentPage == pages.size - 1) onFinished()
                    else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 28.dp, vertical = 20.dp),
            )
        }
    }
}

// -------------------------------------------------------------- role select

@Composable
private fun RoleSelect(onUser: () -> Unit, onTrusted: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            stringResource(R.string.role_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.role_body),
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(36.dp))

        RoleCard(
            icon = Icons.Rounded.Lock,
            iconOnGradient = true,
            title = stringResource(R.string.role_user_title),
            body = stringResource(R.string.role_user_body),
            onClick = onUser,
        )
        Spacer(Modifier.height(14.dp))
        RoleCard(
            icon = Icons.Rounded.Key,
            iconOnGradient = false,
            title = stringResource(R.string.role_trusted_title),
            body = stringResource(R.string.role_trusted_body),
            onClick = onTrusted,
        )
    }
}

@Composable
private fun RoleCard(
    icon: ImageVector,
    iconOnGradient: Boolean,
    title: String,
    body: String,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Surface1)
            .border(1.dp, com.pact.app.ui.theme.CardBorder, shape)
            .clickable(onClick = onClick)
            .padding(20.dp),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (iconOnGradient) PactGradient else androidx.compose.ui.graphics.SolidColor(Surface2)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (iconOnGradient) Ink else Periwinkle,
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(2.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
    }
}

// ----------------------------------------------------------- user setup flow

@Composable
private fun UserSetupFlow(state: PactState, onDone: () -> Unit) {
    val context = LocalContext.current
    val network = remember { TrustNetwork.get(context) }
    var step by remember { mutableIntStateOf(0) }
    var myName by remember { mutableStateOf(network.myName) }
    var avatar by remember { mutableStateOf(network.myAvatar) }
    var selectedApps by remember { mutableStateOf(setOf<String>()) }
    var limits by remember { mutableStateOf(mapOf<String, Int>()) }

    BackHandler(enabled = step > 0) { step -= 1 }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 56.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            repeat(6) { i ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (i == step) 22.dp else 7.dp, 7.dp)
                        .clip(CircleShape)
                        .background(if (i <= step) Periwinkle else Surface2)
                )
            }
        }

        Box(Modifier.weight(1f)) {
            when (step) {
                0 -> MyNameStep(
                    name = myName,
                    onNameChange = { myName = it },
                    avatar = avatar,
                    onAvatarChange = { avatar = it },
                    onNext = {
                        network.myName = myName
                        network.myAvatar = avatar
                        step = 1
                    },
                )
                1 -> CircleStep(network = network, onNext = { step = 2 })
                2 -> PermissionStep(onNext = { step = 3 })
                3 -> PickAppsStep(
                    selected = selectedApps,
                    onSelectedChange = { selectedApps = it },
                    onNext = {
                        // default every newly picked app to a sensible daily allowance
                        limits = selectedApps.associateWith {
                            limits[it] ?: PactState.DEFAULT_LIMIT_MINUTES
                        }
                        step = 4
                    },
                )
                4 -> SetLimitsStep(
                    apps = selectedApps.toList(),
                    limits = limits,
                    onLimitChange = { pkg, m -> limits = limits + (pkg to m) },
                    onNext = { step = 5 },
                )
                5 -> SealStep(
                    circleCount = network.snapshot.collectAsState().value.supporters().size,
                    onFinish = {
                        state.completeSetup(myName, selectedApps, limits)
                        onDone()
                    },
                )
            }
        }
    }
}

/** Set each locked app's daily allowance. The heart of the new model: not off forever, just budgeted. */
@Composable
private fun SetLimitsStep(
    apps: List<String>,
    limits: Map<String, Int>,
    onLimitChange: (String, Int) -> Unit,
    onNext: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Text(
            stringResource(R.string.limits_step_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.limits_step_body),
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(apps, key = { it }) { pkg ->
                val shape = RoundedCornerShape(20.dp)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(Surface1)
                        .border(1.dp, com.pact.app.ui.theme.CardBorder, shape)
                        .padding(14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppIconImage(remember(pkg) { com.pact.app.core.Apps.icon(context, pkg) }, sizeDp = 40)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            com.pact.app.core.Apps.label(context, pkg),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        val m = limits[pkg] ?: PactState.DEFAULT_LIMIT_MINUTES
                        Text(
                            if (m == 0) stringResource(R.string.limit_off)
                            else if (m % 60 == 0) stringResource(R.string.limit_hours, m / 60)
                            else if (m > 60) stringResource(R.string.limit_h_m, m / 60, m % 60)
                            else stringResource(R.string.limit_minutes, m),
                            style = MaterialTheme.typography.titleSmall,
                            color = Periwinkle,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    LimitSlider(
                        value = limits[pkg] ?: PactState.DEFAULT_LIMIT_MINUTES,
                        onChange = { onLimitChange(pkg, it) },
                    )
                }
            }
        }
        PactButton(
            stringResource(R.string.common_continue),
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        )
    }
}

private val LIMIT_STEPS = listOf(0, 5, 10, 15, 20, 30, 45, 60, 90, 120)

@Composable
private fun LimitSlider(value: Int, onChange: (Int) -> Unit) {
    val index = LIMIT_STEPS.indexOf(value).let { if (it < 0) 5 else it }
    androidx.compose.material3.Slider(
        value = index.toFloat(),
        onValueChange = { onChange(LIMIT_STEPS[it.toInt().coerceIn(0, LIMIT_STEPS.lastIndex)]) },
        valueRange = 0f..(LIMIT_STEPS.lastIndex.toFloat()),
        steps = LIMIT_STEPS.size - 2,
        colors = androidx.compose.material3.SliderDefaults.colors(
            thumbColor = Periwinkle,
            activeTrackColor = Violet,
            inactiveTrackColor = Surface2,
        ),
    )
}

@Composable
private fun StepScaffold(
    title: String,
    subtitle: String? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        if (subtitle != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(28.dp))
        content()
    }
}

@Composable
private fun MyNameStep(
    name: String,
    onNameChange: (String) -> Unit,
    avatar: String,
    onAvatarChange: (String) -> Unit,
    onNext: () -> Unit,
) {
    StepScaffold(
        title = stringResource(R.string.circle_your_name_title),
        subtitle = stringResource(R.string.circle_your_name_body),
    ) {
        // The chosen face, big and glossy.
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(Surface1)
                .border(2.dp, Periwinkle, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(avatar, fontSize = 52.sp)
        }
        Spacer(Modifier.height(18.dp))
        // Avatar palette.
        AvatarGrid(selected = avatar, onSelect = onAvatarChange)
        Spacer(Modifier.height(22.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { onNameChange(it.take(16)) },
            label = { Text(stringResource(R.string.my_name_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                if (name.trim().length >= 2) onNext()
            }),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Periwinkle,
                unfocusedBorderColor = Surface2,
            ),
        )
        Spacer(Modifier.height(24.dp))
        PactButton(
            stringResource(R.string.common_continue),
            onClick = onNext,
            enabled = name.trim().length >= 2,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AvatarGrid(selected: String, onSelect: (String) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        TrustNetwork.AVATARS.forEach { emoji ->
            val on = emoji == selected
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (on) Periwinkle.copy(alpha = 0.16f) else Surface1)
                    .border(if (on) 2.dp else 1.dp, if (on) Periwinkle else com.pact.app.ui.theme.CardBorder, RoundedCornerShape(14.dp))
                    .clickable { onSelect(emoji) },
                contentAlignment = Alignment.Center,
            ) {
                Text(emoji, fontSize = 28.sp)
            }
        }
    }
}

/** The pairing step: show the QR (in person) or share a code (remote), watch the circle fill in live. */
@Composable
fun CircleStep(network: TrustNetwork, onNext: () -> Unit) {
    val qrContent = remember { network.pairingQrContent() }
    val qr = remember(qrContent) { Qr.encode(qrContent).asImageBitmap() }
    val snapshot by network.snapshot.collectAsState()
    val supporters = snapshot.supporters()

    StepScaffold(
        title = stringResource(R.string.circle_step_title),
        subtitle = stringResource(R.string.circle_step_body),
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White)
                .padding(16.dp),
        ) {
            Image(
                bitmap = qr,
                contentDescription = stringResource(R.string.pair_qr_desc),
                modifier = Modifier.size(220.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        InviteByCode(network)
        Spacer(Modifier.height(20.dp))
        if (supporters.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.CircularProgressIndicator(
                    color = Periwinkle,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.circle_waiting),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextTertiary,
                )
            }
        } else {
            supporters.forEach { contact ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 4.dp),
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = Mint)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.circle_joined, contact.name),
                        style = MaterialTheme.typography.titleSmall,
                        color = Mint,
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        if (supporters.isNotEmpty()) {
            PactButton(
                stringResource(R.string.common_continue),
                onClick = onNext,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            TextButton(onClick = onNext) {
                Text(stringResource(R.string.perm_later), color = TextTertiary)
            }
        }
    }
}

/**
 * Remote pairing: generate a short code and share it over any messenger. The
 * friend doesn't need to be in the room — they type it into their Pact.
 */
@Composable
fun InviteByCode(network: TrustNetwork) {
    val context = LocalContext.current
    var code by remember { mutableStateOf<String?>(null) }

    if (code == null) {
        androidx.compose.material3.TextButton(onClick = { code = network.createPairingCode() }) {
            Text(stringResource(R.string.pair_code_reveal), color = Periwinkle)
        }
    } else {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Surface1)
                .border(1.dp, com.pact.app.ui.theme.CardBorder, RoundedCornerShape(20.dp))
                .padding(18.dp),
        ) {
            Text(
                stringResource(R.string.pair_code_title),
                style = MaterialTheme.typography.labelMedium,
                color = TextTertiary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                code!!,
                style = MaterialTheme.typography.headlineMedium,
                color = Periwinkle,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.pair_code_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = TextTertiary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(14.dp))
            PactButton(
                stringResource(R.string.pair_code_share),
                onClick = {
                    val msg = context.getString(R.string.pair_share_message, code)
                    context.startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).setType("text/plain")
                                .putExtra(Intent.EXTRA_TEXT, msg),
                            context.getString(R.string.pair_code_share),
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PermissionStep(onNext: () -> Unit) {
    val context = LocalContext.current
    val serviceOn by produceState(initialValue = BlockerService.isEnabled(context)) {
        while (true) {
            value = BlockerService.isEnabled(context)
            delay(700L)
        }
    }
    StepScaffold(
        title = stringResource(R.string.perm_title),
        subtitle = stringResource(R.string.perm_body),
    ) {
        PactCard {
            NumberedStep(1, stringResource(R.string.perm_step1))
            Spacer(Modifier.height(12.dp))
            NumberedStep(2, stringResource(R.string.perm_step2))
            Spacer(Modifier.height(12.dp))
            NumberedStep(3, stringResource(R.string.perm_step3))
            Spacer(Modifier.height(12.dp))
            NumberedStep(4, stringResource(R.string.perm_step4))
            Spacer(Modifier.height(12.dp))
            NumberedStep(5, stringResource(R.string.perm_step5))
        }
        Spacer(Modifier.height(16.dp))
        PactCard(background = if (serviceOn) Surface2 else Surface1) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (serviceOn) Icons.Rounded.Check else Icons.Rounded.Shield,
                    contentDescription = null,
                    tint = if (serviceOn) Mint else Amber,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(
                        if (serviceOn) R.string.perm_status_on else R.string.perm_status_waiting
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (serviceOn) Mint else TextSecondary,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        if (serviceOn) {
            PactButton(
                stringResource(R.string.common_continue),
                onClick = onNext,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            PactButton(
                stringResource(R.string.perm_open_settings),
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onNext) {
                Text(stringResource(R.string.perm_later), color = TextTertiary)
            }
        }
    }
}

@Composable
private fun PickAppsStep(
    selected: Set<String>,
    onSelectedChange: (Set<String>) -> Unit,
    onNext: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Text(
            stringResource(R.string.pick_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        )
        Box(Modifier.weight(1f)) {
            AppPickerList(selected = selected, onSelectedChange = onSelectedChange)
        }
        PactButton(
            if (selected.isEmpty()) stringResource(R.string.pick_min_one)
            else pluralStringResource(R.plurals.lock_n_apps, selected.size, selected.size),
            onClick = onNext,
            enabled = selected.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        )
    }
}

@Composable
private fun SealStep(circleCount: Int, onFinish: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
    StepScaffold(
        title = stringResource(R.string.seal_title),
        subtitle = if (circleCount > 0) {
            stringResource(R.string.circle_members, circleCount)
        } else {
            stringResource(R.string.circle_empty)
        },
    ) {
        Box(
            modifier = Modifier
                .size(104.dp)
                .clip(CircleShape)
                .background(PactGradient),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = Ink,
                modifier = Modifier.size(52.dp),
            )
        }
        Spacer(Modifier.height(40.dp))
        PactButton(
            stringResource(R.string.seal_begin),
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Confetti(Modifier.fillMaxSize())
    }
}
