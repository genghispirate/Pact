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
import com.pact.app.ui.SettingsScreen
import com.pact.app.ui.StatsScreen
import com.pact.app.ui.TrustedHome
import com.pact.app.ui.theme.PactTheme
import com.pact.app.ui.theme.TextSecondary

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val state = PactState.get(this)
        setContent {
            PactTheme {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    PactApp(state)
                }
            }
        }
    }

    // Fast in-app sync only while a screen is visible.
    override fun onStart() {
        super.onStart()
        (application as? PactApp)?.acquireLiveSync()
    }

    override fun onStop() {
        super.onStop()
        (application as? PactApp)?.releaseLiveSync()
    }
}

private enum class Screen { Home, AddApps, Settings, Stats, Circle, Chat, Challenges }

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

    when (screen) {
        Screen.Home -> HomeScreen(
            state = state,
            onAddApps = { screen = Screen.AddApps },
            onOpenSettings = { screen = Screen.Settings },
            onOpenStats = { screen = Screen.Stats },
            onOpenCircle = { screen = Screen.Circle },
            onOpenChallenges = { screen = Screen.Challenges },
        )
        Screen.Challenges -> {
            BackHandler { screen = Screen.Home }
            ChallengesScreen(state = state, onBack = { screen = Screen.Home })
        }
        Screen.AddApps -> AddAppsScreen(
            state = state,
            alreadyBlocked = snapshot.blocked,
            onBack = { screen = Screen.Home },
        )
        Screen.Settings -> {
            BackHandler { screen = Screen.Home }
            SettingsScreen(state = state, onBack = { screen = Screen.Home })
        }
        Screen.Stats -> {
            BackHandler { screen = Screen.Home }
            StatsScreen(state = state, onBack = { screen = Screen.Home })
        }
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
