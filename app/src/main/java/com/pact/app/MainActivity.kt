package com.pact.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pact.app.core.PactState
import com.pact.app.ui.AppPickerList
import com.pact.app.ui.ChallengesScreen
import com.pact.app.ui.ChatScreen
import com.pact.app.ui.CircleScreen
import com.pact.app.ui.HomeScreen
import com.pact.app.ui.OnboardingFlow
import com.pact.app.ui.PactButton
import com.pact.app.ui.ReceiptsScreen
import com.pact.app.ui.SettingsScreen
import com.pact.app.ui.StatsScreen
import com.pact.app.ui.TrustedHome
import com.pact.app.ui.theme.PactTheme
import com.pact.app.ui.theme.TextSecondary

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(
            runCatching { com.pact.app.core.LocaleHelper.wrap(newBase) }.getOrDefault(newBase)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // If the last run crashed, show the trace in a plain view (no Compose,
        // no theme — so it can't crash the same way) with a copy button.
        val crashFile = java.io.File(filesDir, "last_crash.txt")
        if (crashFile.exists() && crashFile.length() > 0) {
            val trace = runCatching { crashFile.readText() }.getOrDefault("(unreadable)")
            crashFile.delete()
            showCrashReport(trace)
            return
        }

        try {
            enableEdgeToEdge()
            val state = PactState.get(this)
            setContent {
                PactTheme {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        if (!com.pact.app.core.LocaleHelper.hasChosen(this@MainActivity)) {
                            com.pact.app.ui.LanguageScreen(onPick = { tag ->
                                com.pact.app.core.LocaleHelper.set(this@MainActivity, tag)
                                recreate()
                            })
                        } else {
                            PactApp(state)
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            runCatching {
                java.io.File(filesDir, "last_crash.txt").writeText(
                    "MainActivity start failed:\n\n" + android.util.Log.getStackTraceString(t)
                )
            }
            showCrashReport("MainActivity start failed:\n\n" + android.util.Log.getStackTraceString(t))
        }
    }

    /** Dependency-free crash screen so a startup crash is visible, not silent. */
    private fun showCrashReport(trace: String) {
        val scroll = android.widget.ScrollView(this)
        val col = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundColor(0xFF0A0A0F.toInt())
            setPadding(40, 120, 40, 60)
        }
        val title = android.widget.TextView(this).apply {
            text = "Pact hit an error on launch"
            setTextColor(0xFFF4F4F8.toInt()); textSize = 20f
            setPadding(0, 0, 0, 24)
        }
        val copy = android.widget.Button(this).apply {
            text = "Copy details"
            setOnClickListener {
                val cm = getSystemService(android.content.ClipboardManager::class.java)
                cm.setPrimaryClip(android.content.ClipData.newPlainText("Pact crash", trace))
                android.widget.Toast.makeText(this@MainActivity, "Copied", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        val body = android.widget.TextView(this).apply {
            text = trace
            setTextColor(0xFF9A9AB0.toInt()); textSize = 12f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(0, 24, 0, 0)
        }
        col.addView(title); col.addView(copy); col.addView(body)
        scroll.addView(col)
        setContentView(scroll)
    }

    // Fast in-app sync only while a screen is visible.
    override fun onStart() {
        super.onStart()
        (application as? PactApp)?.acquireLiveSync()
        // Starting from a foreground (visible) context is always allowed; the
        // service then keeps itself (and the shield's process) alive via
        // START_STICKY. Only the person locking their own apps needs it.
        val snap = PactState.get(this).snapshot.value
        if (snap.setupComplete && snap.role == PactState.Role.USER) {
            com.pact.app.service.ShieldService.start(this)
        }
    }

    override fun onStop() {
        super.onStop()
        (application as? PactApp)?.releaseLiveSync()
    }
}

private enum class Screen { Home, AddApps, Settings, Stats, Circle, Chat, Challenges, Receipts, Farm }

@Composable
private fun PactApp(state: PactState) {
    val snapshot by state.snapshot.collectAsState()
    var screen by rememberSaveable { mutableStateOf(Screen.Home) }
    var chatContactId by rememberSaveable { mutableStateOf<String?>(null) }

    if (snapshot.role == PactState.Role.SPONSOR) {
        // Trusted-person device: their circle and chats.
        when (screen) {
            Screen.Chat -> chatContactId?.let { id ->
                ChatScreen(contactId = id, onBack = { screen = Screen.Home })
            } ?: run { screen = Screen.Home }
            else -> TrustedHome(
                state = state,
                onOpenChat = { id -> chatContactId = id; screen = Screen.Chat },
            )
        }
        return
    }
    if (!snapshot.setupComplete) {
        OnboardingFlow(state = state, onDone = { screen = Screen.Home })
        return
    }

    val topLevel = screen in setOf(Screen.Home, Screen.Stats, Screen.Circle, Screen.Farm, Screen.Settings)
    // On a top-level tab, back returns to Home (except Home itself, which exits).
    if (topLevel && screen != Screen.Home) BackHandler { screen = Screen.Home }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            when (screen) {
                Screen.Home -> HomeScreen(
                    state = state,
                    onAddApps = { screen = Screen.AddApps },
                    onOpenSettings = { screen = Screen.Settings },
                    onOpenStats = { screen = Screen.Stats },
                    onOpenCircle = { screen = Screen.Circle },
                    onOpenChallenges = { screen = Screen.Challenges },
                    onOpenReceipts = { screen = Screen.Receipts },
                    onOpenFarm = { screen = Screen.Farm },
                )
                Screen.Farm -> com.pact.app.ui.FarmScreen(onBack = { screen = Screen.Home })
                Screen.Challenges -> {
                    BackHandler { screen = Screen.Home }
                    ChallengesScreen(state = state, onBack = { screen = Screen.Home })
                }
                Screen.Receipts -> {
                    BackHandler { screen = Screen.Home }
                    ReceiptsScreen(state = state, onBack = { screen = Screen.Home })
                }
                Screen.AddApps -> AddAppsScreen(
                    state = state,
                    alreadyBlocked = snapshot.blocked,
                    onBack = { screen = Screen.Home },
                )
                Screen.Settings -> SettingsScreen(state = state, onBack = { screen = Screen.Home })
                Screen.Stats -> StatsScreen(state = state, onBack = { screen = Screen.Home })
                Screen.Circle -> CircleScreen(
                    state = state,
                    onBack = { screen = Screen.Home },
                    onOpenChat = { id -> chatContactId = id; screen = Screen.Chat },
                )
                Screen.Chat -> chatContactId?.let { id ->
                    ChatScreen(contactId = id, onBack = { screen = Screen.Circle })
                } ?: run { screen = Screen.Home }
            }
        }
        if (topLevel) {
            com.pact.app.ui.PactBottomBar(
                current = when (screen) {
                    Screen.Stats -> com.pact.app.ui.BottomDest.STATS
                    Screen.Farm -> com.pact.app.ui.BottomDest.FARM
                    Screen.Circle -> com.pact.app.ui.BottomDest.CIRCLE
                    Screen.Settings -> com.pact.app.ui.BottomDest.SETTINGS
                    else -> com.pact.app.ui.BottomDest.HOME
                },
                onSelect = { dest ->
                    screen = when (dest) {
                        com.pact.app.ui.BottomDest.HOME -> Screen.Home
                        com.pact.app.ui.BottomDest.STATS -> Screen.Stats
                        com.pact.app.ui.BottomDest.FARM -> Screen.Farm
                        com.pact.app.ui.BottomDest.CIRCLE -> Screen.Circle
                        com.pact.app.ui.BottomDest.SETTINGS -> Screen.Settings
                    }
                },
            )
        }
    }
}

/** Adding apps to the Pact is always free — friction only guards the way out. */
@Composable
private fun AddAppsScreen(
    state: PactState,
    alreadyBlocked: Set<String>,
    onBack: () -> Unit,
) {
    var selected by remember { mutableStateOf(setOf<String>()) }
    BackHandler { onBack() }
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
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
            Text(stringResource(R.string.home_add_apps), style = MaterialTheme.typography.headlineSmall)
        }
        Box(Modifier.weight(1f)) {
            AppPickerList(
                selected = selected,
                onSelectedChange = { selected = it },
                excluded = alreadyBlocked,
            )
        }
        PactButton(
            text = if (selected.isEmpty()) stringResource(R.string.pick_min_one)
            else pluralStringResource(R.plurals.lock_n_apps, selected.size, selected.size),
            onClick = {
                state.addBlocked(selected)
                onBack()
            },
            enabled = selected.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
        )
    }
}
