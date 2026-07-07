package com.pact.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.ResultPoint
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.BarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import com.pact.app.R
import com.pact.app.ui.theme.Ink
import com.pact.app.ui.theme.Periwinkle
import com.pact.app.ui.theme.Surface1
import com.pact.app.ui.theme.TextSecondary

/**
 * In-app portrait QR scanner: embedded ZXing camera view under a Compose
 * overlay (rounded viewfinder, torch toggle). Replaces the stock landscape
 * CaptureActivity. Fully offline.
 */
@Composable
fun ScanScreen(
    title: String,
    onResult: (String) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionDenied by remember { mutableStateOf(false) }
    var torchOn by remember { mutableStateOf(false) }
    var delivered by remember { mutableStateOf(false) }
    val barcodeView = remember { mutableStateOf<BarcodeView?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        permissionDenied = !granted
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    BackHandler { onClose() }

    DisposableEffect(Unit) {
        onDispose { barcodeView.value?.pause() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (hasPermission) {
            AndroidView(
                factory = { ctx ->
                    BarcodeView(ctx).apply {
                        decoderFactory = DefaultDecoderFactory(listOf(BarcodeFormat.QR_CODE))
                        decodeContinuous(object : BarcodeCallback {
                            override fun barcodeResult(result: BarcodeResult) {
                                if (!delivered) {
                                    delivered = true
                                    pause()
                                    onResult(result.text)
                                }
                            }

                            override fun possibleResultPoints(resultPoints: MutableList<ResultPoint>) = Unit
                        })
                        resume()
                        barcodeView.value = this
                    }
                },
                update = { view -> view.setTorch(torchOn) },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // viewfinder frame
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(260.dp)
                .clip(RoundedCornerShape(32.dp))
                .border(3.dp, Periwinkle, RoundedCornerShape(32.dp))
        )

        // top bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Ink.copy(alpha = 0.55f)),
            ) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.common_close),
                    tint = Color.White,
                )
            }
            Spacer(Modifier.size(12.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
        }

        // bottom hint + torch
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 32.dp),
        ) {
            if (permissionDenied) {
                Text(
                    stringResource(R.string.scan_perm),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                )
            } else {
                Text(
                    stringResource(R.string.scan_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center,
                )
            }
            IconButton(
                onClick = { torchOn = !torchOn },
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(if (torchOn) Periwinkle else Surface1.copy(alpha = 0.8f)),
            ) {
                Icon(
                    if (torchOn) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff,
                    contentDescription = stringResource(R.string.cd_torch),
                    tint = if (torchOn) Ink else Color.White,
                )
            }
        }
    }
}
