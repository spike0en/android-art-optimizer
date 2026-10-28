package io.github.daermond.artoptimizer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

private val AmoledDarkColorScheme = darkColorScheme(
    primary = ComposeColor(0xFFD0BCFF),
    onPrimary = ComposeColor(0xFF381E72),
    primaryContainer = ComposeColor(0xFF4F378B),
    onPrimaryContainer = ComposeColor(0xFFEADDFF),
    secondary = ComposeColor(0xFFCCC2DC),
    onSecondary = ComposeColor(0xFF332D41),
    background = ComposeColor(0xFF000000),
    onBackground = ComposeColor(0xFFE6E1E5),
    surface = ComposeColor(0xFF000000),
    onSurface = ComposeColor(0xFFE6E1E5),
    surfaceVariant = ComposeColor(0xFF141414),
    onSurfaceVariant = ComposeColor(0xFFCAC4D0),
    surfaceContainer = ComposeColor(0xFF101010),
    surfaceContainerHigh = ComposeColor(0xFF1A1A1A),
    surfaceContainerHighest = ComposeColor(0xFF222222),
    outline = ComposeColor(0xFF444444),
    outlineVariant = ComposeColor(0xFF282828),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { OptimizerApp() }
    }
}

@Composable
private fun OptimizerApp(model: OptimizerViewModel = viewModel()) {
    val state by model.state.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val isTv = LocalConfiguration.current.uiMode and Configuration.UI_MODE_TYPE_MASK ==
        Configuration.UI_MODE_TYPE_TELEVISION
    val tabFocus = remember { List(4) { FocusRequester() } }
    LaunchedEffect(isTv, tab) { if (isTv) tabFocus[tab].requestFocus() }
    val systemDark = isSystemInDarkTheme() || isTv
    val isDark = when (state.darkMode) {
        true -> true
        false -> false
        null -> systemDark
    }

    val context = LocalContext.current
    val supportsDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme = when {
        supportsDynamic && isDark -> dynamicDarkColorScheme(context).copy(
            background = ComposeColor(0xFF000000),
            surface = ComposeColor(0xFF000000),
            surfaceContainer = ComposeColor(0xFF121212),
            surfaceContainerHigh = ComposeColor(0xFF1A1A1A),
            surfaceContainerHighest = ComposeColor(0xFF222222),
        )
        supportsDynamic && !isDark -> dynamicLightColorScheme(context)
        isDark -> AmoledDarkColorScheme
        else -> lightColorScheme()
    }

    MaterialTheme(colorScheme = colorScheme) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(if (isTv) 32.dp else 16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Android ART Optimizer", style = MaterialTheme.typography.headlineSmall)
                    RemoteIconButton(
                        onClick = { model.toggleTheme(isDark) },
                    ) {
                        ThemeToggleIcon(isDark = isDark)
                    }
                }

                val connected = state.connection == ConnectionPhase.CONNECTED && state.endpoint != null
                val searching = state.connection in setOf(ConnectionPhase.SEARCHING_CONNECT, ConnectionPhase.CONNECTING)
                val statusDot = when {
                    connected -> ComposeColor(0xFF4CAF50)
                    searching -> ComposeColor(0xFFFFB300)
                    else -> MaterialTheme.colorScheme.outline
                }
                val statusText = when {
                    connected -> "Connected over ${LocalConnectionPolicy.connectionLabel(state.endpoint!!, state.network)}"
                    searching -> "Searching for Wireless Debugging"
                    state.connection == ConnectionPhase.NOT_PAIRED -> "Not paired"
                    state.connection == ConnectionPhase.UNAVAILABLE -> "Disconnected"
                    else -> state.connection.name.replace('_', ' ').lowercase()
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.padding(vertical = 4.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(statusDot))
                        Text(statusText, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                }

                Spacer(Modifier.height(4.dp))

                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val tabPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    listOf("Device", "Apps", "Diagnostics", "Console").forEachIndexed { index, label ->
                        val focusModifier = Modifier.focusRequester(tabFocus[index])
                        if (index == tab) {
                            RemoteButton(
                                onClick = { tab = index },
                                modifier = focusModifier,
                                contentPadding = tabPadding,
                            ) { Text(label) }
                        } else {
                            RemoteOutlinedButton(
                                onClick = { tab = index },
                                modifier = focusModifier,
                                contentPadding = tabPadding,
                            ) { Text(label) }
                        }
                    }
                }

                HorizontalDivider(Modifier.padding(vertical = 8.dp))

                when (tab) {
                    0 -> DevicePage(state, model, isTv)
                    1 -> AppsPage(state, model, isDark)
                    2 -> DiagnosticsPage(state, model)
                    else -> ConsolePage(state, model)
                }
            }
        }
    }
}

/** Material buttons accept D-pad focus; this ring makes the active target obvious on a TV. */
@Composable
private fun remoteFocusModifier(): Modifier {
    var focused by remember { mutableStateOf(false) }
    return Modifier
        .onFocusChanged { focused = it.isFocused }
        .border(if (focused) 3.dp else 0.dp,
            if (focused) ComposeColor(0xFFFFD54F) else ComposeColor.Transparent,
            RoundedCornerShape(28.dp))
        .defaultMinSize(minHeight = 48.dp)
}

@Composable
private fun RemoteButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) = Button(
    onClick = onClick,
    modifier = modifier.then(remoteFocusModifier()),
    enabled = enabled,
    contentPadding = contentPadding,
    content = content,
)

@Composable
private fun RemoteOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) = OutlinedButton(
    onClick = onClick,
    modifier = modifier.then(remoteFocusModifier()),
    enabled = enabled,
    contentPadding = contentPadding,
    content = content,
)

@Composable
private fun RemoteIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    IconButton(
        onClick = onClick,
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .border(
                if (focused) 3.dp else 0.dp,
                if (focused) ComposeColor(0xFFFFD54F) else ComposeColor.Transparent,
                CircleShape
            )
            .size(44.dp),
        enabled = enabled,
        content = content
    )
}

@Composable
private fun ThemeToggleIcon(isDark: Boolean, tint: ComposeColor = MaterialTheme.colorScheme.onSurface) {
    Canvas(modifier = Modifier.size(24.dp)) {
        val size = this.size.minDimension
        val center = Offset(size / 2f, size / 2f)
        if (isDark) {
            val r = size * 0.22f
            drawCircle(color = tint, radius = r, center = center)
            for (i in 0 until 8) {
                val angle = (i * Math.PI / 4.0).toFloat()
                val cos = kotlin.math.cos(angle)
                val sin = kotlin.math.sin(angle)
                val start = Offset(center.x + cos * size * 0.34f, center.y + sin * size * 0.34f)
                val end = Offset(center.x + cos * size * 0.46f, center.y + sin * size * 0.46f)
                drawLine(
                    color = tint,
                    start = start,
                    end = end,
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        } else {
            val mainCircle = Path().apply {
                addOval(Rect(center = center, radius = size * 0.42f))
            }
            val cutCircle = Path().apply {
                addOval(Rect(center = Offset(center.x + size * 0.22f, center.y - size * 0.18f), radius = size * 0.36f))
            }
            val moon = Path().apply {
                op(mainCircle, cutCircle, PathOperation.Difference)
            }
            drawPath(moon, color = tint)
        }
    }
}

/** On TV, text entry is an explicit Select action instead of a stop in the D-pad path. */
@Composable
private fun AppTextInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    minLines: Int = 1,
) {
    val isTv = LocalConfiguration.current.uiMode and Configuration.UI_MODE_TYPE_MASK ==
        Configuration.UI_MODE_TYPE_TELEVISION
    if (!isTv) {
        OutlinedTextField(value = value, onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(), label = { Text(label) }, minLines = minLines)
        return
    }
    var editing by rememberSaveable { mutableStateOf(false) }
    var opened by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf(value) }
    val buttonFocus = remember { FocusRequester() }
    val fieldFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(editing) {
        if (editing) fieldFocus.requestFocus()
        else if (opened) buttonFocus.requestFocus()
    }
    RemoteOutlinedButton(onClick = { draft = value; opened = true; editing = true },
        modifier = Modifier.focusRequester(buttonFocus)) {
        Text(if (value.isBlank()) "Edit $label" else "$label: $value",
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    if (editing) {
        fun finish(save: Boolean) {
            if (save) onValueChange(draft)
            keyboard?.hide()
            editing = false
        }
        AlertDialog(
            onDismissRequest = { finish(false) },
            title = { Text(label) },
            text = {
                OutlinedTextField(value = draft, onValueChange = { draft = it },
                    modifier = Modifier.fillMaxWidth().focusRequester(fieldFocus),
                    label = { Text(label) }, minLines = minLines,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { finish(true) }))
            },
            confirmButton = { RemoteButton(onClick = { finish(true) }) { Text("Done") } },
            dismissButton = { RemoteOutlinedButton(onClick = { finish(false) }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Page(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
}

@Composable
private fun DevicePage(state: UiState, model: OptimizerViewModel, isTv: Boolean) = Page {
    val context = LocalContext.current
    var showPairingOptions by rememberSaveable { mutableStateOf(false) }
    var settingsUnavailable by rememberSaveable { mutableStateOf(false) }
    Text("Device and connection", style = MaterialTheme.typography.titleLarge)

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(state.profile?.displayName ?: "Android Device", style = MaterialTheme.typography.titleMedium)
            Text("Network: ${state.network.label}")
            state.endpoint?.let { Text("ADB connection: ${it.kind.label}") }
            Text(state.detail.ifBlank { "Enable Wireless Debugging in Developer Options." })
            if (state.networkNotice.isNotBlank()) Text(state.networkNotice)
            if (state.network.hasEthernet) {
                Text("Ethernet is detected automatically. On supported TVs, enable USB debugging in Developer Options and accept Android's authorization prompt for this app.")
            }
        }
    }

    if (state.connection == ConnectionPhase.CONNECTED) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RemoteOutlinedButton(onClick = model::retry) { Text("Reconnect") }
            RemoteOutlinedButton(onClick = model::disconnectAndTurnOffWirelessDebugging) { Text("Disconnect & Turn off ADB") }
            RemoteOutlinedButton(onClick = model::pairAgain,
                enabled = state.pairingSession.status !in setOf(PairingStatus.STARTING,
                    PairingStatus.READY, PairingStatus.PAIRING)) { Text("Pair Again") }
        }
    } else if (state.isPaired) {
        if (state.connection == ConnectionPhase.SEARCHING_CONNECT || state.connection == ConnectionPhase.CONNECTING) {
            CircularProgressIndicator()
            Text("Reconnecting to Wireless Debugging on this device...")
        } else {
            Text("Wireless Debugging is disconnected or turned off. Android automatically disables it after device sleep or network changes.")
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RemoteButton(onClick = model::retry) { Text("Retry") }
            RemoteOutlinedButton(onClick = {
                settingsUnavailable = runCatching {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                }.isFailure
            }) { Text("Open Developer Options") }
            RemoteOutlinedButton(onClick = { showPairingOptions = !showPairingOptions }) {
                Text(if (showPairingOptions) "Hide pairing" else "Pair with new code")
            }
        }
        if (settingsUnavailable) Text("Open Developer Options from Android Settings on this device.")
        if (showPairingOptions || state.pairingSession.status != PairingStatus.IDLE) {
            HorizontalDivider()
            PairingChoices(state, model, isTv)
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RemoteOutlinedButton(onClick = model::retry) { Text("Retry") }
        }
        PairingChoices(state, model, isTv)
    }
}

@Composable
private fun PairingChoices(state: UiState, model: OptimizerViewModel, isTv: Boolean) {
    val context = LocalContext.current
    var notificationDenied by rememberSaveable { mutableStateOf(false) }
    var settingsUnavailable by rememberSaveable { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationDenied = !granted
        if (granted) model.startPairing(PairingMode.NOTIFICATION)
    }
    val session = state.pairingSession
    val webUrl = session.url?.takeIf { session.mode == PairingMode.WEB &&
        session.status in setOf(PairingStatus.READY, PairingStatus.PAIRING, PairingStatus.FAILED) }
    @Composable fun Actions() {
        Text("Enable Developer Options and Wireless Debugging. Choose a method below first, then open Wireless Debugging → Pair device with pairing code in Android settings. Leave that dialog open while entering its code.")
        RemoteButton(onClick = {
            if (Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else model.startPairing(PairingMode.NOTIFICATION)
        }) { Text("Enter code in notification") }
        Text("Keep the Settings pairing dialog open. Pull down the notification shade and reply with its six-digit code.")
        if (notificationDenied) Text("Notification permission is needed for this method. You can use the web page instead.")
        RemoteOutlinedButton(onClick = { model.startPairing(PairingMode.WEB) }) {
            Text("Enter code on another device")
        }
        Text("Scan the QR code with another phone, tablet, or computer on the same local network, including an iPhone.")
        RemoteOutlinedButton(onClick = {
            settingsUnavailable = runCatching {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
            }.isFailure
        }) { Text("Open Developer Options") }
        if (settingsUnavailable) Text("Open Developer Options from Android Settings on this device.")
        if (session.status != PairingStatus.IDLE) {
            HorizontalDivider()
            Text("${session.mode?.name?.lowercase()?.replaceFirstChar(Char::uppercase) ?: "Pairing"}: ${session.detail}")
            if (session.status in setOf(PairingStatus.STARTING, PairingStatus.READY,
                    PairingStatus.PAIRING) || (session.status == PairingStatus.FAILED && session.url != null)) {
                RemoteOutlinedButton(onClick = model::stopPairing) { Text("Cancel pairing") }
            }
        }
    }
    @Composable fun WebDetails(url: String) {
        PairingQr(url)
        SelectionContainer { Text(url) }
        Text("This local HTTP page is temporary. Use it only on a trusted home network. The other device needs to reach this device directly.")
    }
    if (isTv) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) { Actions() }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (webUrl != null) WebDetails(webUrl)
            }
        }
    } else {
        Actions()
        if (webUrl != null) WebDetails(webUrl)
    }
}

@Composable
private fun PairingQr(url: String) {
    val bitmap = remember(url) {
        val matrix = QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 320, 320,
            mapOf(EncodeHintType.MARGIN to 2))
        Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888).apply {
            val pixels = IntArray(matrix.width * matrix.height) { index ->
                if (matrix[index % matrix.width, index / matrix.width]) Color.BLACK else Color.WHITE
            }
            setPixels(pixels, 0, matrix.width, 0, 0, matrix.width, matrix.height)
        }.asImageBitmap()
    }
    Image(bitmap = bitmap, contentDescription = "QR code for the temporary pairing page",
        modifier = Modifier.size(240.dp))
}

@Composable
private fun AppsPage(state: UiState, model: OptimizerViewModel, isDark: Boolean) = Page {
    val connected = state.connection == ConnectionPhase.CONNECTED
    val context = LocalContext.current
    var entry by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("") }
    var showDiscovered by rememberSaveable { mutableStateOf(false) }

    val needed = remember(state.configured, state.installed, state.records, state.versions) {
        pendingPackages(state.configured, state.installed, state.versions, state.records)
    }
    val installedConfigured = remember(state.configured, state.installed) {
        state.configured.filter { it in state.installed }
    }
    val unconfigured = remember(state.installed, state.configured, filter) {
        state.installed.filter { it !in state.configured && it.value.contains(filter, ignoreCase = true) }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        FilterChip(
            selected = !showDiscovered,
            onClick = { showDiscovered = false },
            label = { Text("Configured (${state.configured.size})") },
        )
        FilterChip(
            selected = showDiscovered,
            onClick = { showDiscovered = true },
            label = { Text("Discovered (${state.installed.count { it !in state.configured }})") },
        )
    }

    if (!showDiscovered) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RemoteButton(
                onClick = model::optimizeNeeded,
                enabled = connected && state.compileAvailable && !state.running && !state.scanningArtState && needed.isNotEmpty()
            ) {
                if (state.scanningArtState) {
                    val (done, total) = state.scanProgress ?: (0 to state.configured.size)
                    Text("Checking status ($done/$total)")
                } else {
                    Text("Optimize needed (${needed.size})")
                }
            }
            RemoteOutlinedButton(
                onClick = { model.optimize(installedConfigured) },
                enabled = connected && state.compileAvailable && !state.running && !state.scanningArtState && installedConfigured.isNotEmpty()
            ) {
                Text("Optimize all (${installedConfigured.size})")
            }
            RemoteOutlinedButton(
                onClick = model::refreshApps,
                enabled = connected && !state.running && !state.scanningArtState
            ) {
                Text(if (state.scanningArtState) "Scanning..." else "Refresh")
            }
            RemoteOutlinedButton(
                onClick = model::clearConfigured,
                enabled = state.configured.isNotEmpty() && !state.running && !state.scanningArtState
            ) {
                Text("Remove all (${state.configured.size})")
            }
        }
        if (state.scanningArtState) {
            val (done, total) = state.scanProgress ?: (0 to 1)
            val progress = if (total > 0) done.toFloat() / total.toFloat() else 0f
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = entry,
                onValueChange = { entry = it },
                label = { Text("Add package ID or CSV") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            RemoteButton(
                onClick = { model.addPackages(entry); entry = "" },
                enabled = entry.isNotBlank(),
            ) {
                Text("Add")
            }
        }
        if (state.entryFeedback.isNotBlank()) Text(state.entryFeedback, style = MaterialTheme.typography.bodySmall)

        state.activeEvent?.let { event ->
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${event.completed}/${event.total} completed • ${event.packageId.value}", style = MaterialTheme.typography.titleMedium)
                    if (event.message.isNotBlank()) Text(event.message, style = MaterialTheme.typography.bodyMedium)
                    if (event.phase == OptimizationPhase.COMPILING && state.running) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        ElapsedText(event.atMillis, true)
                    }
                }
            }
        }

        if (state.configured.isEmpty()) {
            Text("No packages configured. Switch to the Discovered tab to add apps installed on this device.")
        }

        state.configured.forEach { id ->
            val installed = state.versions[id]
            val record = state.records[id]
            val label = remember(id) {
                runCatching {
                    val info = context.packageManager.getApplicationInfo(id.value, 0)
                    context.packageManager.getApplicationLabel(info).toString()
                }.getOrNull()?.takeUnless { it == id.value }
            }
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = if (isDark) ComposeColor(0xFF121212) else MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            if (label != null) Text(label, style = MaterialTheme.typography.titleMedium)
                            Text(id.value,
                                style = if (label != null) MaterialTheme.typography.bodySmall else MaterialTheme.typography.titleMedium,
                                color = if (label != null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                        }
                        val liveFilter = installed?.compilerFilter
                        if (id !in state.installed) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.errorContainer,
                            ) {
                                Text("Not installed", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        } else if (liveFilter != null) {
                            val isSpeed = liveFilter == "speed"
                            val isProfile = liveFilter.contains("profile")
                            val badgeBg = when {
                                isSpeed -> MaterialTheme.colorScheme.primaryContainer
                                isProfile -> MaterialTheme.colorScheme.tertiaryContainer
                                else -> MaterialTheme.colorScheme.surfaceContainerHighest
                            }
                            val badgeFg = when {
                                isSpeed -> MaterialTheme.colorScheme.onPrimaryContainer
                                isProfile -> MaterialTheme.colorScheme.onTertiaryContainer
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Surface(shape = RoundedCornerShape(8.dp), color = badgeBg) {
                                Text(
                                    if (isSpeed) "✓ speed (optimized)" else liveFilter,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = badgeFg
                                )
                            }
                        } else if (state.scanningArtState) {
                            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                                Text(
                                    "Scanning...",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Text(
                        if (id in state.installed) "Installed • v${installed?.versionName ?: "unknown"}"
                        else "Missing from device",
                        style = MaterialTheme.typography.bodySmall
                    )

                    if (record != null) {
                        Text(
                            "Last: ${record.result} • " +
                                DateFormat.getDateTimeInstance().format(Date(record.timestampMillis)),
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (record.result == "Success") {
                            val validationDesc = when (record.validationLevel) {
                                ValidationLevel.ART_STATE -> "Verified via ART runtime status"
                                ValidationLevel.COMMAND -> "Verified via package compiler exit status"
                            }
                            Text("Validation: $validationDesc", style = MaterialTheme.typography.bodySmall)
                        }
                        if (updatedSinceOptimization(installed?.versionCode, record)) {
                            Text("App updated since optimization — run it again",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RemoteButton(
                            onClick = { model.optimize(listOf(id)) },
                            enabled = connected && state.compileAvailable && !state.running && id in state.installed
                        ) {
                            Text("Optimize")
                        }
                        RemoteOutlinedButton(onClick = { model.removePackage(id) }) { Text("Remove") }
                    }
                }
            }
        }
    } else {
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Installed User Apps", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Found ${unconfigured.size} unconfigured user apps on this device.",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (unconfigured.isNotEmpty()) {
                    RemoteButton(
                        onClick = { model.addPackages(unconfigured.joinToString(",") { it.value }) },
                    ) {
                        Text("Add all ${if (filter.isNotBlank()) "filtered" else "user apps"} (${unconfigured.size})")
                    }
                }
            }
        }

        AppTextInput(value = filter, onValueChange = { filter = it }, label = "Search user apps")
        unconfigured.take(100).forEach { id ->
                val label = remember(id) {
                    runCatching {
                        val info = context.packageManager.getApplicationInfo(id.value, 0)
                        context.packageManager.getApplicationLabel(info).toString()
                    }.getOrNull()?.takeUnless { it == id.value }
                }
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = if (isDark) ComposeColor(0xFF121212) else MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            if (label != null) Text(label, style = MaterialTheme.typography.titleMedium)
                            Text(id.value, style = if (label != null) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium)
                        }
                        RemoteOutlinedButton(onClick = { model.addPackages(id.value) }) {
                            Text("Add")
                        }
                    }
                }
            }
            if (unconfigured.size > 100) {
                Text("Showing first 100 results. Use the filter to search.")
            }
        }
    }

@Composable
private fun ElapsedText(startedAt: Long, active: Boolean) {
    var seconds by remember(startedAt) { mutableIntStateOf(0) }
    LaunchedEffect(startedAt, active) {
        while (active) {
            seconds = ((System.currentTimeMillis() - startedAt) / 1_000).coerceAtLeast(0).toInt()
            delay(1_000)
        }
    }
    Text("Elapsed: ${seconds}s")
}

@Composable
private fun DiagnosticsPage(state: UiState, model: OptimizerViewModel) = Page {
    Text("Diagnostics", style = MaterialTheme.typography.titleLarge)
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Connection & Transport", style = MaterialTheme.typography.titleMedium)
            Text("Status: ${state.connection}")
            Text("Network: ${state.network.label}")
            Text("ADB path: ${state.endpoint?.kind?.label ?: "not connected"}")
            Text("Discovery: ${state.discoveryStatus}" +
                if (state.connection == ConnectionPhase.CONNECTED) " (runs in background)" else "")
            Text("Endpoints: ${state.connectEndpoints.size} connect, ${state.pairingEndpoints.size} pair")
            Text("Shell UID: ${state.shellUid.ifBlank { "unavailable" }}")
            Text("Auto-enable Wireless ADB: " +
                if (state.secureSettingsGranted) "Granted (automatic background enable active)"
                else "Not granted (connect once via Wireless Debugging to activate)")
        }
    }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("ART Capabilities", style = MaterialTheme.typography.titleMedium)
            state.profile?.let {
                Text("Device: ${it.displayName} (SDK ${it.sdk})")
                Text(if (it.strongIdentity) "Identity: stable shell identifier verified"
                    else "Identity: model/SDK fingerprint")
            }
            Text("Compile command: ${if (state.compileAvailable) "Available (cmd package compile)" else "Unavailable"}")
            Text("ART inspection: ${if (state.artAvailable) "Available (pm art dump)" else "Unavailable (fallback to exit status)"}")
        }
    }
    RemoteOutlinedButton(onClick = model::retry) { Text("Retry connection") }
}

@Composable
private fun ConsolePage(state: UiState, model: OptimizerViewModel) = Page {
    Text("Advanced • ADB Console", style = MaterialTheme.typography.titleLarge)
    if (!state.consoleWarningAccepted) {
        Text("Expert feature: shell commands can change device settings or packages. Run only commands you understand.")
        RemoteButton(onClick = model::acceptConsoleWarning) { Text("I understand") }
        return@Page
    }
    var command by rememberSaveable { mutableStateOf("") }
    AppTextInput(value = command, onValueChange = { command = it },
        label = "Command after adb shell", minLines = 3)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RemoteButton(onClick = { model.runConsole(command) },
            enabled = !state.consoleRunning && !state.running && state.connection == ConnectionPhase.CONNECTED) { Text("Run") }
        RemoteOutlinedButton(onClick = model::cancelConsole, enabled = state.consoleRunning) { Text("Cancel") }
        RemoteOutlinedButton(onClick = model::clearConsoleOutput) { Text("Clear output") }
    }
    if (state.consoleRunning) { CircularProgressIndicator(); ElapsedText(state.consoleStartedAt, true) }
    Text(state.consoleResult)
    SelectionContainer { Text(state.consoleOutput.ifEmpty { "No output" }, fontFamily = FontFamily.Monospace) }
    HorizontalDivider()
    Text("Local history (up to 20 commands)")
    RemoteOutlinedButton(onClick = model::clearConsoleHistory) { Text("Clear history") }
    state.consoleHistory.forEach { previous ->
        RemoteOutlinedButton(onClick = { command = previous }) { Text(previous.take(80), fontFamily = FontFamily.Monospace) }
    }
}
