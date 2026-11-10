package com.commutealarm.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

// Material 3 Expressive Deep Midnight Palette
private val DeepCharcoalBg = Color(0xFF080A10)
private val MidnightBlueStart = Color(0xFF131C2E)
private val MidnightBlueEnd = Color(0xFF1D2B45)
private val MidnightBorder = Color(0xFF31476E)

private val WarmAmberCardBg = Color(0xFF28190D)
private val WarmAmberBorder = Color(0xFF57371B)
private val WarmAmberAccent = Color(0xFFF59E0B)
private val WarmAmberText = Color(0xFFFEF08A)

private val DarkSlateCardBg = Color(0xFF121722)
private val DarkSlateBorder = Color(0xFF252F45)

private val EmeraldBadgeBg = Color(0xFF064E3B)
private val EmeraldBright = Color(0xFF10B981)
private val CoralRedBadgeBg = Color(0xFF451A1A)
private val CoralRedBright = Color(0xFFEF4444)

private val MutedAxisColor = Color(0xFF64748B)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = DeepCharcoalBg,
                    surface = MidnightBlueStart,
                    primary = Color(0xFF38BDF8)
                )
            ) {
                val vm: CommuteViewModel = viewModel()
                val uiState by vm.uiState.collectAsStateWithLifecycle()
                val context = LocalContext.current

                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { }

                LaunchedEffect(Unit) {
                    val needed = mutableListOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        needed.add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    val missing = needed.filter {
                        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                    }
                    if (missing.isNotEmpty()) {
                        permissionLauncher.launch(missing.toTypedArray())
                    }
                }

                DynamicCommuteTimelineScreen(
                    uiState = uiState,
                    onToggleAlarm = vm::toggleAlarmEnabled,
                    onRefreshTraffic = vm::refreshTrafficNow,
                    onUpdateArrival = vm::updateTargetArrival,
                    onUpdatePrepAndBaseline = vm::updatePrepAndBaseline,
                    onUpdateDestinationAndApi = vm::updateLocationsAndApiKey,
                    onEnableOpenSourceFallback = vm::enableOpenSourceRollbackAndRetry,
                    onLaunchMaps = {
                        MapsLauncher.launchTurnByTurnNavigation(
                            context,
                            uiState.config.destinationAddress
                        )
                    },
                    onTestRingScreen = vm::triggerRingPreview
                )
            }
        }
    }
}

@Composable
fun ExpressiveMorphCard(
    baseCorner: Dp = 24.dp,
    pressedCorner: Dp = 16.dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (RoundedCornerShape) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "expressiveScale"
    )

    val cornerRadius by animateDpAsState(
        targetValue = if (isPressed) pressedCorner else baseCorner,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "expressiveCorner"
    )

    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .scale(scale)
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        content(shape)
    }
}

private enum class TimelineStepType { WAKE, PREP, DRIVE, ARRIVAL }

@Composable
fun DynamicCommuteTimelineScreen(
    uiState: CommuteUiState,
    onToggleAlarm: (Boolean) -> Unit,
    onRefreshTraffic: () -> Unit,
    onUpdateArrival: (Int, Int) -> Unit,
    onUpdatePrepAndBaseline: (Int, Int) -> Unit,
    onUpdateDestinationAndApi: (String, Boolean, String, String, String, String, Boolean, Boolean) -> Unit,
    onEnableOpenSourceFallback: () -> Unit,
    onLaunchMaps: () -> Unit,
    onTestRingScreen: () -> Unit
) {
    val config = uiState.config
    var showTimePickerModal by remember { mutableStateOf(false) }
    var showPrepEditModal by remember { mutableStateOf(false) }
    var showDestinationModal by remember { mutableStateOf(false) }

    fun formatHourShort(hour24: Int): String {
        val suffix = if (hour24 < 12) "AM" else "PM"
        val h12 = when {
            hour24 == 0 -> 12
            hour24 > 12 -> hour24 - 12
            else -> hour24
        }
        return "$h12 $suffix"
    }

    val wakeAxisHour = formatHourShort(config.dynamicWakeTime.hour)
    val leaveAxisHour = formatHourShort(config.leaveByTime.hour)
    val arrivalAxisHour = formatHourShort(config.targetArrivalTime.hour)

    Scaffold(
        containerColor = DeepCharcoalBg,
        topBar = {
            Surface(
                color = Color(0xFF0F172A),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        ) {
                            Text(
                                text = "Commute Alarm",
                                color = Color.White,
                                fontSize = 21.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-0.5).sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(if (config.isEnabled) EmeraldBright else WarmAmberAccent)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (config.isEnabled) "Alarm Armed" else "Alarm Off",
                                    color = if (config.isEnabled) EmeraldBright else WarmAmberAccent,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = onTestRingScreen,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF1A2234))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Notifications,
                                    contentDescription = "Preview Lock Screen Alarm",
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            IconButton(
                                onClick = { showDestinationModal = true },
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF1A2234))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Settings & API Keys",
                                    tint = Color(0xFFCBD5E1),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Switch(
                                checked = config.isEnabled,
                                onCheckedChange = onToggleAlarm,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFF052E16),
                                    checkedTrackColor = EmeraldBright,
                                    uncheckedThumbColor = MutedAxisColor,
                                    uncheckedTrackColor = DarkSlateCardBg
                                )
                            )
                        }
                    }
                    HorizontalDivider(color = Color(0xFF1E293B))
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (config.needsOpenSourcePrompt && !config.allowOpenSourceFallback) {
                OpenSourceRollbackBanner(
                    onAllowClick = onEnableOpenSourceFallback
                )
            }

            TimelineStepRow(
                timeLabel = wakeAxisHour,
                stepType = TimelineStepType.WAKE,
                isAlarmEnabled = config.isEnabled,
                isFirst = true,
                isLast = false
            ) {
                PrimaryWakeUpHeaderCard(
                    config = config,
                    isPolling = uiState.isPollingTraffic,
                    onRefreshClick = onRefreshTraffic
                )
            }

            TimelineStepRow(
                timeLabel = leaveAxisHour,
                stepType = TimelineStepType.PREP,
                isAlarmEnabled = config.isEnabled,
                isFirst = false,
                isLast = false
            ) {
                GetReadyBlockCard(
                    prepMinutes = config.prepMinutes,
                    baselineMinutes = config.baselineCommuteMinutes,
                    leaveByText = TimeFormatters.formatDigits(config.leaveByTime),
                    onClick = { showPrepEditModal = true }
                )
            }

            TimelineStepRow(
                timeLabel = "${config.displayedDriveMinutes}m",
                stepType = TimelineStepType.DRIVE,
                isAlarmEnabled = config.isEnabled,
                isFirst = false,
                isLast = false
            ) {
                CommuteDriveBlockCard(
                    config = config,
                    onEditRoute = { showDestinationModal = true },
                    onOpenMaps = onLaunchMaps
                )
            }

            TimelineStepRow(
                timeLabel = arrivalAxisHour,
                stepType = TimelineStepType.ARRIVAL,
                isAlarmEnabled = config.isEnabled,
                isFirst = false,
                isLast = true
            ) {
                TargetArrivalAnchorPill(
                    arrivalTimeText = TimeFormatters.formatFull(config.targetArrivalTime),
                    destinationLabel = config.destinationLabel,
                    onClick = { showTimePickerModal = true }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showTimePickerModal) {
        TargetArrivalTimePickerDialog(
            initialHour = config.targetArrivalHour,
            initialMinute = config.targetArrivalMinute,
            onDismiss = { showTimePickerModal = false },
            onConfirm = { h, m ->
                onUpdateArrival(h, m)
                showTimePickerModal = false
            }
        )
    }

    if (showPrepEditModal) {
        PrepAndBaselineEditDialog(
            initialPrep = config.prepMinutes,
            initialBaseline = config.baselineCommuteMinutes,
            onDismiss = { showPrepEditModal = false },
            onConfirm = { prep, baseline ->
                onUpdatePrepAndBaseline(prep, baseline)
                showPrepEditModal = false
            }
        )
    }

    if (showDestinationModal) {
        DestinationAndApiModal(
            config = config,
            onDismiss = { showDestinationModal = false },
            onSave = { origin, useGps, dest, label, userTomTomKey, googleKey, allowOsrm, letOsrmControl ->
                onUpdateDestinationAndApi(origin, useGps, dest, label, userTomTomKey, googleKey, allowOsrm, letOsrmControl)
                showDestinationModal = false
            }
        )
    }
}

@Composable
private fun OpenSourceRollbackBanner(
    onAllowClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF2E1E0F))
            .border(1.5.dp, WarmAmberAccent, RoundedCornerShape(20.dp))
            .padding(18.dp)
    ) {
        Text(
            text = "Primary Traffic API Unavailable",
            color = WarmAmberText,
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "TomTom / Google Maps could not be reached. Allow rolling back to free OpenStreetMap (OSRM) routing?",
            color = Color(0xFFFDE68A).copy(alpha = 0.9f),
            fontSize = 12.sp,
            lineHeight = 17.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = onAllowClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = WarmAmberAccent,
                contentColor = Color(0xFF1C1208)
            ),
            shape = RoundedCornerShape(50)
        ) {
            Text(
                text = "Enable Open-Source Rollback",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun TimelineStepRow(
    timeLabel: String,
    stepType: TimelineStepType,
    isAlarmEnabled: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    content: @Composable () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "bellPulse")
    val badgeScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = if (isAlarmEnabled && (stepType == TimelineStepType.WAKE)) 1.08f else 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bellScale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .width(46.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.TopCenter
        ) {
            Canvas(modifier = Modifier.fillMaxHeight().width(2.dp)) {
                val startY = if (isFirst) 20.dp.toPx() else 0f
                val endY = if (isLast) 24.dp.toPx() else size.height
                drawLine(
                    color = Color(0xFF1E293B),
                    start = Offset(size.width / 2, startY),
                    end = Offset(size.width / 2, endY),
                    strokeWidth = 2.dp.toPx()
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 10.dp)
            ) {
                when (stepType) {
                    TimelineStepType.WAKE -> {
                        Box(
                            modifier = Modifier
                                .scale(badgeScale)
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isAlarmEnabled) Brush.linearGradient(listOf(Color(0xFF1D3557), Color(0xFF2563EB)))
                                    else Brush.linearGradient(listOf(Color(0xFF1E2433), Color(0xFF1E2433)))
                                )
                                .border(1.dp, if (isAlarmEnabled) Color(0xFF60A5FA) else Color(0xFF334155), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "Alarm",
                                tint = if (isAlarmEnabled) Color.White else MutedAxisColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    TimelineStepType.PREP -> {
                        Surface(
                            color = Color(0xFF28190D),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.border(1.dp, WarmAmberBorder, RoundedCornerShape(8.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = "Prep",
                                tint = WarmAmberAccent,
                                modifier = Modifier
                                    .padding(5.dp)
                                    .size(14.dp)
                            )
                        }
                    }
                    TimelineStepType.DRIVE -> {
                        Surface(
                            color = Color(0xFF121722),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.border(1.dp, Color(0xFF252F45), RoundedCornerShape(8.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Place,
                                contentDescription = "Drive",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier
                                    .padding(5.dp)
                                    .size(14.dp)
                            )
                        }
                    }
                    TimelineStepType.ARRIVAL -> {
                        Surface(
                            color = Color(0xFF0F172A),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.border(1.dp, Color(0xFF38BDF8), RoundedCornerShape(8.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = "Arrival",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier
                                    .padding(5.dp)
                                    .size(14.dp)
                            )
                        }
                    }
                }

                if (timeLabel.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = timeLabel,
                        color = Color(0xFF94A3B8),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        Box(modifier = Modifier.weight(1f)) {
            content()
        }
    }
}

@Composable
private fun PrimaryWakeUpHeaderCard(
    config: AlarmConfig,
    isPolling: Boolean,
    onRefreshClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "expressiveHeader")
    val haloScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "haloScale"
    )

    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spinAngle"
    )

    ExpressiveMorphCard(
        baseCorner = 24.dp,
        pressedCorner = 16.dp,
        onClick = onRefreshClick
    ) { morphShape ->
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(MidnightBlueStart, MidnightBlueEnd)
                    )
                )
                .border(1.5.dp, MidnightBorder, morphShape)
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val auraColor = when {
                    !config.isEnabled -> MutedAxisColor
                    config.usesBaselineForSchedule -> WarmAmberAccent
                    config.sleepDeltaMinutes >= 0 -> EmeraldBright
                    else -> CoralRedBright
                }
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(auraColor.copy(alpha = 0.15f), Color.Transparent),
                        center = Offset(size.width * 0.35f, size.height * 0.55f),
                        radius = size.width * 0.55f * haloScale
                    ),
                    center = Offset(size.width * 0.35f, size.height * 0.55f),
                    radius = size.width * 0.55f * haloScale
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = Color(0xFF0B111E).copy(alpha = 0.85f),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .border(1.dp, Color(0xFF283754), RoundedCornerShape(50))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (config.usesBaselineForSchedule) WarmAmberAccent else EmeraldBright)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isPolling) {
                                    "Syncing traffic..."
                                } else {
                                    "${config.activeProvider} • ${TimeFormatters.formatEpochTime(config.lastPollEpochMillis)}"
                                },
                                color = Color(0xFFCBD5E1),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Surface(
                        color = Color(0xFF253552),
                        shape = CircleShape,
                        modifier = Modifier.size(34.dp)
                    ) {
                        IconButton(
                            onClick = onRefreshClick,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Poll Traffic Now",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(17.dp)
                                    .graphicsLayer {
                                        rotationZ = if (isPolling) spinAngle else 0f
                                    }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AnimatedContent(
                        targetState = config.dynamicWakeTime,
                        transitionSpec = {
                            (slideInVertically { h -> h / 2 } + fadeIn(tween(280)))
                                .togetherWith(slideOutVertically { h -> -h / 2 } + fadeOut(tween(200)))
                                .using(SizeTransform(clip = false))
                        },
                        modifier = Modifier.weight(1f, fill = false),
                        label = "ExpressiveWakeTime"
                    ) { targetWake ->
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = TimeFormatters.formatDigits(targetWake),
                                color = if (config.isEnabled) Color.White else Color.White.copy(alpha = 0.6f),
                                fontSize = 44.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = (-1.5).sp,
                                maxLines = 1,
                                softWrap = false
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = TimeFormatters.formatAmPm(targetWake),
                                color = Color(0xFF94A3B8),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(bottom = 6.dp),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    DeltaPillBadge(
                        deltaMinutes = config.sleepDeltaMinutes,
                        isBaselineLocked = config.usesBaselineForSchedule
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = config.statusNote,
                    color = Color(0xFFE2E8F0),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 19.sp
                )
            }
        }
    }
}

@Composable
fun DeltaPillBadge(
    deltaMinutes: Int,
    modifier: Modifier = Modifier,
    isBaselineLocked: Boolean = false
) {
    val isGain = deltaMinutes >= 0
    val bgColor = when {
        isBaselineLocked -> Color(0xFF3B2510)
        isGain -> EmeraldBadgeBg
        else -> CoralRedBadgeBg
    }
    val accentColor = when {
        isBaselineLocked -> WarmAmberAccent
        isGain -> EmeraldBright
        else -> CoralRedBright
    }
    val label = when {
        isBaselineLocked -> "Baseline"
        deltaMinutes > 0 -> "+$deltaMinutes min"
        else -> "$deltaMinutes min"
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.border(1.dp, accentColor.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
    ) {
        Text(
            text = label,
            color = accentColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun GetReadyBlockCard(
    prepMinutes: Int,
    baselineMinutes: Int,
    leaveByText: String,
    onClick: () -> Unit
) {
    ExpressiveMorphCard(
        baseCorner = 24.dp,
        pressedCorner = 16.dp,
        onClick = onClick
    ) { morphShape ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(WarmAmberCardBg, Color(0xFF38220F))
                    )
                )
                .border(1.5.dp, WarmAmberBorder, morphShape)
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF4D2F14))
                    .border(1.dp, WarmAmberAccent.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = "Get Ready",
                    tint = WarmAmberAccent,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Get ready",
                        color = WarmAmberText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        softWrap = false
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Surface(
                        color = Color(0xFF4D2F14),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.border(1.dp, WarmAmberAccent.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                    ) {
                        Text(
                            text = "Leave by $leaveByText",
                            color = WarmAmberText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "$prepMinutes min prep • $baselineMinutes min drive • Tap to edit",
                    color = WarmAmberAccent.copy(alpha = 0.9f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun CommuteDriveBlockCard(
    config: AlarmConfig,
    onEditRoute: () -> Unit,
    onOpenMaps: () -> Unit
) {
    ExpressiveMorphCard(
        baseCorner = 24.dp,
        pressedCorner = 16.dp,
        onClick = onEditRoute
    ) { morphShape ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkSlateCardBg)
                .border(1.5.dp, DarkSlateBorder, morphShape)
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1E2638))
                            .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = "Drive",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Drive",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                softWrap = false
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = when (config.trafficCondition) {
                                    TrafficCondition.CLEAR -> EmeraldBadgeBg
                                    TrafficCondition.MODERATE -> Color(0xFF452B12)
                                    TrafficCondition.HEAVY -> CoralRedBadgeBg
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "${config.displayedDriveMinutes} min",
                                    color = when (config.trafficCondition) {
                                        TrafficCondition.CLEAR -> EmeraldBright
                                        TrafficCondition.MODERATE -> WarmAmberAccent
                                        TrafficCondition.HEAVY -> CoralRedBright
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = config.destinationAddress,
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                FilledTonalIconButton(
                    onClick = onOpenMaps,
                    shape = RoundedCornerShape(12.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = Color(0xFF1D3557),
                        contentColor = Color(0xFF38BDF8)
                    ),
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Navigate in Google Maps",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            AnimatedTrafficRouteBar(
                condition = config.trafficCondition,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
            )
        }
    }
}

@Composable
fun AnimatedTrafficRouteBar(
    condition: TrafficCondition,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "expressiveWaveFlow")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    val dashPhase by infiniteTransition.animateFloat(
        initialValue = 48f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dashPhase"
    )

    Canvas(modifier = modifier) {
        val centerY = size.height / 2f
        val startX = 14f
        val endX = size.width - 14f
        val totalWidth = endX - startX
        val stroke = 11f

        drawLine(
            color = Color(0xFF22293A),
            start = Offset(startX, centerY),
            end = Offset(endX, centerY),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )

        fun drawExpressiveWave(fromX: Float, toX: Float, color: Color, amplitude: Float) {
            val path = Path()
            val step = 6f
            var x = fromX
            var first = true
            while (x <= toX) {
                val normalized = (x - startX) / 28f
                val y = centerY + sin(normalized + wavePhase) * amplitude
                if (first) {
                    path.moveTo(x, y)
                    first = false
                } else {
                    path.lineTo(x, y)
                }
                x += step
            }
            drawPath(
                path = path,
                color = color,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }

        when (condition) {
            TrafficCondition.CLEAR -> {
                drawExpressiveWave(
                    fromX = startX,
                    toX = endX,
                    color = EmeraldBright,
                    amplitude = 3.2f
                )
            }

            TrafficCondition.MODERATE -> {
                val seg1 = startX + totalWidth * 0.5f
                val seg2 = startX + totalWidth * 0.82f
                drawExpressiveWave(startX, seg1, EmeraldBright, 3.0f)
                drawExpressiveWave(seg1, seg2, WarmAmberAccent, 4.0f)
                drawLine(
                    color = CoralRedBright,
                    start = Offset(seg2 + 6f, centerY),
                    end = Offset(endX, centerY),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 14f), dashPhase)
                )
            }

            TrafficCondition.HEAVY -> {
                val seg1 = startX + totalWidth * 0.25f
                val seg2 = startX + totalWidth * 0.52f
                drawExpressiveWave(startX, seg1, EmeraldBright, 2.5f)
                drawExpressiveWave(seg1, seg2, WarmAmberAccent, 4.2f)
                drawLine(
                    color = CoralRedBright,
                    start = Offset(seg2 + 6f, centerY),
                    end = Offset(endX, centerY),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 14f), dashPhase)
                )
            }
        }

        drawCircle(
            color = EmeraldBright.copy(alpha = 0.35f),
            radius = 12f,
            center = Offset(startX, centerY)
        )
        drawCircle(
            color = Color.White,
            radius = 6.5f,
            center = Offset(startX, centerY)
        )

        val endGlow = when (condition) {
            TrafficCondition.CLEAR -> EmeraldBright
            TrafficCondition.MODERATE -> WarmAmberAccent
            TrafficCondition.HEAVY -> CoralRedBright
        }
        drawCircle(
            color = endGlow.copy(alpha = 0.4f),
            radius = 12f,
            center = Offset(endX, centerY)
        )
        drawCircle(
            color = Color.White,
            radius = 6.5f,
            center = Offset(endX, centerY)
        )
    }
}

@Composable
private fun TargetArrivalAnchorPill(
    arrivalTimeText: String,
    destinationLabel: String,
    onClick: () -> Unit
) {
    ExpressiveMorphCard(
        baseCorner = 24.dp,
        pressedCorner = 16.dp,
        onClick = onClick
    ) { morphShape ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkSlateCardBg)
                .border(1.5.dp, DarkSlateBorder, morphShape)
                .padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1E293B)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Arrive by $arrivalTimeText",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        softWrap = false
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = destinationLabel,
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Surface(
                color = Color(0xFF1D3557),
                shape = RoundedCornerShape(50)
            ) {
                Text(
                    text = "Edit arrival",
                    color = Color(0xFF7DD3FC),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TargetArrivalTimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    val pickerState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = false
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF161E2E),
        shape = RoundedCornerShape(32.dp),
        title = {
            Text(
                text = "Target Arrival Time",
                color = Color.White,
                fontWeight = FontWeight.ExtraBold
            )
        },
        text = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                TimePicker(state = pickerState)
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(pickerState.hour, pickerState.minute) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldBright,
                    contentColor = Color(0xFF052E16)
                ),
                shape = RoundedCornerShape(50)
            ) {
                Text("Save Time", fontWeight = FontWeight.ExtraBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF94A3B8))
            }
        }
    )
}

@Composable
private fun PrepAndBaselineEditDialog(
    initialPrep: Int,
    initialBaseline: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    var prepMinutes by remember { mutableIntStateOf(initialPrep) }
    var prepText by remember { mutableStateOf(initialPrep.toString()) }

    var baselineMinutes by remember { mutableIntStateOf(initialBaseline) }
    var baselineText by remember { mutableStateOf(initialBaseline.toString()) }

    fun updatePrep(newVal: Int) {
        val coerced = newVal.coerceIn(1, 300)
        prepMinutes = coerced
        prepText = coerced.toString()
    }

    fun updateBaseline(newVal: Int) {
        val coerced = newVal.coerceIn(1, 360)
        baselineMinutes = coerced
        baselineText = coerced.toString()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF161E2E),
        shape = RoundedCornerShape(32.dp),
        title = {
            Text("Exact Routine & Failsafe Times", color = Color.White, fontWeight = FontWeight.ExtraBold)
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                Surface(
                    color = Color(0xFF0F1624),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.border(1.dp, WarmAmberBorder, RoundedCornerShape(20.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Get Ready / Prep Time (Minutes)",
                            color = WarmAmberText,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = prepText,
                                onValueChange = { raw ->
                                    val digits = raw.filter { it.isDigit() }.take(3)
                                    prepText = digits
                                    digits.toIntOrNull()?.let { prepMinutes = it.coerceIn(1, 300) }
                                },
                                label = { Text("Exact min") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.width(105.dp)
                            )

                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(-5, -1, +1, +5).forEach { step ->
                                    Surface(
                                        color = Color(0xFF28190D),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .border(1.dp, WarmAmberBorder, RoundedCornerShape(10.dp))
                                            .clickable { updatePrep(prepMinutes + step) }
                                    ) {
                                        Text(
                                            text = if (step > 0) "+$step" else step.toString(),
                                            color = WarmAmberText,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(vertical = 10.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Slider(
                            value = prepMinutes.coerceIn(1, 180).toFloat(),
                            onValueChange = { updatePrep(it.roundToInt()) },
                            valueRange = 1f..180f,
                            colors = SliderDefaults.colors(
                                thumbColor = WarmAmberAccent,
                                activeTrackColor = WarmAmberAccent
                            )
                        )
                    }
                }

                Surface(
                    color = Color(0xFF0F1624),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.border(1.dp, Color(0xFF253552), RoundedCornerShape(20.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Failsafe Baseline Drive (Minutes)",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "If TomTom is unavailable at the 20-min check (and Open-Source alarm control is off), your alarm fires at Baseline Drive + Get Ready time.",
                            color = MutedAxisColor,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = baselineText,
                                onValueChange = { raw ->
                                    val digits = raw.filter { it.isDigit() }.take(3)
                                    baselineText = digits
                                    digits.toIntOrNull()?.let { baselineMinutes = it.coerceIn(1, 360) }
                                },
                                label = { Text("Exact min") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.width(105.dp)
                            )

                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(-5, -1, +1, +5).forEach { step ->
                                    Surface(
                                        color = Color(0xFF1A2436),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .border(1.dp, Color(0xFF2E4060), RoundedCornerShape(10.dp))
                                            .clickable { updateBaseline(baselineMinutes + step) }
                                    ) {
                                        Text(
                                            text = if (step > 0) "+$step" else step.toString(),
                                            color = EmeraldBright,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(vertical = 10.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Slider(
                            value = baselineMinutes.coerceIn(1, 180).toFloat(),
                            onValueChange = { updateBaseline(it.roundToInt()) },
                            valueRange = 1f..180f,
                            colors = SliderDefaults.colors(
                                thumbColor = EmeraldBright,
                                activeTrackColor = EmeraldBright
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalPrep = prepText.toIntOrNull()?.coerceIn(1, 300) ?: prepMinutes
                    val finalBase = baselineText.toIntOrNull()?.coerceIn(1, 360) ?: baselineMinutes
                    onConfirm(finalPrep, finalBase)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldBright,
                    contentColor = Color(0xFF052E16)
                ),
                shape = RoundedCornerShape(50)
            ) {
                Text("Save Times", fontWeight = FontWeight.ExtraBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF94A3B8))
            }
        }
    )
}

@Composable
private fun DestinationAndApiModal(
    config: AlarmConfig,
    onDismiss: () -> Unit,
    onSave: (String, Boolean, String, String, String, String, Boolean, Boolean) -> Unit
) {
    var useGps by remember { mutableStateOf(config.useGpsOrigin) }
    var originAddress by remember { mutableStateOf(config.originAddress) }
    var destAddress by remember { mutableStateOf(config.destinationAddress) }
    var destLabel by remember { mutableStateOf(config.destinationLabel) }
    var userTomTomKey by remember { mutableStateOf(config.userTomTomApiKey) }
    var googleApiKey by remember { mutableStateOf(config.apiKey) }
    var allowOsrm by remember { mutableStateOf(config.allowOpenSourceFallback) }
    var letOsrmControl by remember { mutableStateOf(config.letOsrmControlAlarm) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF161E2E),
        shape = RoundedCornerShape(32.dp),
        title = {
            Text("Route & API Key Settings", color = Color.White, fontWeight = FontWeight.ExtraBold)
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    color = Color(0xFF0B111E),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.border(1.dp, Color(0xFF283754), RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = if (userTomTomKey.isNotBlank() || googleApiKey.isNotBlank()) {
                                "Custom API Key Active: Unlimited Requests"
                            } else {
                                "Built-in TomTom Key: ${config.builtInCallsLastHour}/5 requests used this hour"
                            },
                            color = EmeraldBright,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Hardware-bound 5/hr rate limit (reserves 1 slot for the 20-min pre-alarm check). Add your own TomTom or Google key below for unlimited polling.",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Use Device GPS for Origin", color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Switch(checked = useGps, onCheckedChange = { useGps = it })
                }

                if (!useGps) {
                    OutlinedTextField(
                        value = originAddress,
                        onValueChange = { originAddress = it },
                        label = { Text("Home / Origin Address or Lat,Lng") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = destLabel,
                    onValueChange = { destLabel = it },
                    label = { Text("Destination Label (e.g., Work, Campus)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = destAddress,
                    onValueChange = { destAddress = it },
                    label = { Text("Destination Address or Lat,Lng") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = userTomTomKey,
                    onValueChange = { userTomTomKey = it },
                    label = { Text("Custom TomTom API Key (Unlimited)") },
                    placeholder = { Text("Overrides built-in 5/hr limited key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = googleApiKey,
                    onValueChange = { googleApiKey = it },
                    label = { Text("Custom Google Maps API Key (Unlimited)") },
                    placeholder = { Text("Optional Google Routes API override") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider(color = Color(0xFF26334D))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                        Text("Allow Open-Source Rollback (OSRM)", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Query free OpenStreetMap routing if TomTom/Google is unavailable",
                            color = MutedAxisColor,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                    Switch(
                        checked = allowOsrm,
                        onCheckedChange = {
                            allowOsrm = it
                            if (!it) letOsrmControl = false
                        }
                    )
                }

                AnimatedVisibility(visible = allowOsrm) {
                    Surface(
                        color = Color(0xFF0F172A),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.border(1.dp, Color(0xFF283754), RoundedCornerShape(16.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                                Text(
                                    text = "Let Open-Source Adjust Alarm Time",
                                    color = WarmAmberText,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Off by default: If TomTom is unreachable at the 20-min mark, alarm fires at Failsafe Baseline + Get Ready time.",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }
                            Switch(
                                checked = letOsrmControl,
                                onCheckedChange = { letOsrmControl = it }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(originAddress, useGps, destAddress, destLabel, userTomTomKey, googleApiKey, allowOsrm, letOsrmControl)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldBright,
                    contentColor = Color(0xFF052E16)
                ),
                shape = RoundedCornerShape(50)
            ) {
                Text("Save Route", fontWeight = FontWeight.ExtraBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF94A3B8))
            }
        }
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF080A10)
@Composable
fun DynamicCommuteTimelineScreenPreview() {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = DeepCharcoalBg,
            surface = MidnightBlueStart,
            primary = Color(0xFF38BDF8)
        )
    ) {
        DynamicCommuteTimelineScreen(
            uiState = CommuteUiState(
                config = AlarmConfig(
                    isEnabled = true,
                    targetArrivalHour = 8,
                    targetArrivalMinute = 30,
                    prepMinutes = 30,
                    baselineCommuteMinutes = 10,
                    liveCommuteMinutes = 5,
                    destinationAddress = "Binghamton University Admin",
                    destinationLabel = "Work"
                ),
                isPollingTraffic = false
            ),
            onToggleAlarm = {},
            onRefreshTraffic = {},
            onUpdateArrival = { _, _ -> },
            onUpdatePrepAndBaseline = { _, _ -> },
            onUpdateDestinationAndApi = { _, _, _, _, _, _, _, _ -> },
            onEnableOpenSourceFallback = {},
            onLaunchMaps = {},
            onTestRingScreen = {}
        )
    }
}
