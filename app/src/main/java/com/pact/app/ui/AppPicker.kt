package com.pact.app.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pact.app.R
import com.pact.app.core.Apps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.pact.app.ui.theme.CardBorder
import com.pact.app.ui.theme.Ink
import com.pact.app.ui.theme.Periwinkle
import com.pact.app.ui.theme.Surface1
import com.pact.app.ui.theme.Surface2
import com.pact.app.ui.theme.TextTertiary

/**
 * Searchable list of launchable apps with multi-select. Apps people most
 * commonly want to lock (social, video) sort to the top.
 */
@Composable
fun AppPickerList(
    selected: Set<String>,
    onSelectedChange: (Set<String>) -> Unit,
    excluded: Set<String> = emptySet(),
) {
    val context = LocalContext.current
    // Loading the app list touches PackageManager for every installed app —
    // do it off the main thread so the screen never stutters.
    val allApps by produceState(initialValue = emptyList<com.pact.app.core.AppInfo>(), excluded) {
        value = withContext(Dispatchers.IO) {
            Apps.installedApps(context).filter { it.pkg !in excluded }
        }
    }
    var query by remember { mutableStateOf("") }
    val visible = remember(query, allApps) {
        if (query.isBlank()) allApps
        else allApps.filter { it.label.contains(query.trim(), ignoreCase = true) }
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.search_apps), color = TextTertiary) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = TextTertiary) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Periwinkle,
                unfocusedBorderColor = Surface2,
            ),
        )
        if (allApps.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Periwinkle)
            }
            return@Column
        }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(visible, key = { it.pkg }) { app ->
                val isSelected = app.pkg in selected
                val rowShape = RoundedCornerShape(16.dp)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(rowShape)
                        .background(if (isSelected) Surface2 else Surface1)
                        .border(
                            width = 1.dp,
                            color = if (isSelected) Periwinkle.copy(alpha = 0.5f) else CardBorder,
                            shape = rowShape,
                        )
                        .clickable {
                            onSelectedChange(
                                if (isSelected) selected - app.pkg else selected + app.pkg
                            )
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    AppIconImage(
                        drawable = remember(app.pkg) { Apps.icon(context, app.pkg) },
                        sizeDp = 40,
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(app.label, style = MaterialTheme.typography.titleSmall)
                        if (app.pkg in Apps.SUGGESTED) {
                            Text(
                                stringResource(R.string.often_locked),
                                style = MaterialTheme.typography.labelMedium,
                                color = TextTertiary,
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) Periwinkle else Surface2)
                            .border(
                                width = 1.5.dp,
                                color = if (isSelected) Periwinkle else MaterialTheme.colorScheme.outline,
                                shape = CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isSelected) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = null,
                                tint = Ink,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}
