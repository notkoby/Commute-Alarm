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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.PI
import kotlin.math.sin

// Material 3 Expressive Rich Dark Palette
private val DeepCharcoalBg = Color(0xFF090A0F)
private val MidnightBlueStart = Color(0xFF141D2E)
private val MidnightBlueEnd = Color(0xFF1E2C46)
private val MidnightBorder = Color(0xFF334970)

private val WarmAmberCardBg = Color(0xFF2B1B0E)
private val WarmAmberBorder = Color(0xFF59381B)
private val WarmAmberAccent = Color(0xFFF59E0B)
private val WarmAmberText = Color(0xFFFEF08A)

private val DarkSlateCardBg = Color(0xFF131722)
private val DarkSlateBorder = Color(0xFF262E42)

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

/**
 * Material 3 Expressive Interactive Card Wrapper:
 * Morphs corner radius and applies a bouncy spring scale when touched.
 */
@Composable
fun ExpressiveMorphCard(
    baseCorner: Dp = 32.dp,
    pressedCorner: Dp = 18.dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (RoundedCornerShape) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.965f else 1f,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DynamicCommuteTimelineScreen(
    uiState: CommuteUiState,
    onToggleAlarm: (Boolean) -> Unit,
    onRefreshTraffic: () -> Unit,
    onUpdateArrival: (Int, Int) -> Unit,
    onUpdatePrepAndBaseline: (Int, Int) -> Unit,
    onUpdateDestinationAndApi: (String, Boolean, String, String, String, String, Boolean) -> Unit,
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
                color = Color(0xFF111520),
                shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = Color(0xFF1F293D),
                        shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
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
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.5).sp
                        )
                        Text(
                            text = if (config.isEnabled) {
                                "Hourly + 20m pre-alarm • ${config.activeProvider}"
                            } else {
                                "Schedule paused"
                            },
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = onTestRingScreen,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background( Color(0xFF1C2333) )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "Preview Lock Screen Alarm",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        IconButton(
                            onClick = { showDestinationModal = true },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background( Color(0xFF1C2333) )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings & API Keys",
                                tint = Color(0xFFCBD5E1),
                                modifier = Modifier.size(19.dp)
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
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (config.needsOpenSourcePrompt && !config.allowOpenSourceFallback) {
                OpenSourceRollbackBanner(
                    onAllowClick = onEnableOpenSourceFallback
                )
            }

            TimelineStepRow(
                timeLabel = wakeAxisHour,
                showBellBadge = true
            ) {
                PrimaryWakeUpHeaderCard(
                    config = config,
                    isPolling = uiState.isPollingTraffic,
                    onRefreshClick = onRefreshTraffic
                )
            }

            TimelineStepRow(
                timeLabel = leaveAxisHour,
                showBellBadge = false
            ) {
                GetReadyBlockCard(
                    prepMinutes = config.prepMinutes,
                    leaveByText = TimeFormatters.formatDigits(config.leaveByTime),
                    onClick = { showPrepEditModal = true }
                )
            }

            TimelineStepRow(
                timeLabel = "",
                showBellBadge = false
            ) {
                CommuteDriveBlockCard(
                    config = config,
                    onEditRoute = { showDestinationModal = true },
                    onOpenMaps = onLaunchMaps
                )
            }

            TimelineStepRow(
                timeLabel = arrivalAxisHour,
                showBellBadge = false
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
            onSave = { origin, useGps, dest, label, userTomTomKey, googleKey, allowOsrm ->
                onUpdateDestinationAndApi(origin, useGps, dest, label, userTomTomKey, googleKey, allowOsrm)
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
            .clip(RoundedCornerShape(26.dp))
            .background(Color(0xFF2E1E0F))
            .border(1.5.dp, WarmAmberAccent, RoundedCornerShape(26.dp))
            .padding(18.dp)
    ) {
        Text(
            text = "Primary Traffic API Unavailable",
            color = WarmAmberText,
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "TomTom / Google Maps could not be reached. Allow rolling back to the free OpenStreetMap (OSRM) routing engine?",
            color = Color(0xFFFDE68A).copy(alpha = 0.9f),
            fontSize = 13.sp,
            lineHeight = 18.sp
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
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun TimelineStepRow(
    timeLabel: String,
    showBellBadge: Boolean,
    content: @Composable () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "bellPulse")
    val badgeScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bellScale"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.width(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (timeLabel.isNotEmpty()) {
                Surface(
                    color = Color(0xFF141926),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = timeLabel,
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }
            }
            if (showBellBadge) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .scale(badgeScale)
                        .size(30.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF1D3557), Color(0xFF2563EB))
                            )
                        )
                        .border(1.dp, Color(0xFF60A5FA), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
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
        baseCorner = 34.dp,
        pressedCorner = 22.dp,
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
            // Expressive Breathing Radial Aura behind the Wake-Up Time
            Canvas(
                modifier = Modifier
                    .matchParentSize()
            ) {
                val auraColor = if (config.sleepDeltaMinutes >= 0) EmeraldBright else WarmAmberAccent
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            auraColor.copy(alpha = 0.16f),
                            Color.Transparent
                        ),
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
                    .padding(22.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = Color(0xFF0B111E).copy(alpha = 0.82f),
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
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (config.isFallbackActive) WarmAmberAccent else EmeraldBright
                                    )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isPolling) {
                                    "Syncing live traffic..."
                                } else {
                                    "${config.activeProvider} • ${TimeFormatters.formatEpochTime(config.lastPollEpochMillis)}"
                                },
                                color = Color(0xFFCBD5E1),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
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
                                    .size(18.dp)
                                    .graphicsLayer {
                                        rotationZ = if (isPolling) spinAngle else 0f
                                    }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

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
                        modifier = Modifier.weight(1f),
                        label = "ExpressiveWakeTime"
                    ) { targetWake ->
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = TimeFormatters.formatDigits(targetWake),
                                color = Color.White,
                                fontSize = 52.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = (-2).sp,
                                maxLines = 1,
                                softWrap = false
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = TimeFormatters.formatAmPm(targetWake),
                                color = Color(0xFF94A3B8),
                                fontSize = 19.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(bottom = 7.dp),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    DeltaPillBadge(
                        deltaMinutes = config.sleepDeltaMinutes
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = config.statusNote,
                    color = Color(0xFFE2E8F0),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@Composable
fun DeltaPillBadge(
    deltaMinutes: Int,
    modifier: Modifier = Modifier
) {
    val isGain = deltaMinutes >= 0
    val bgColor = if (isGain) EmeraldBadgeBg else CoralRedBadgeBg
    val accentColor = if (isGain) EmeraldBright else CoralRedBright
    val label = if (deltaMinutes > 0) "+$deltaMinutes min" else "$deltaMinutes min"

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.border(1.5.dp, accentColor.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
    ) {
        Text(
            text = label,
            color = accentColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun GetReadyBlockCard(
    prepMinutes: Int,
    leaveByText: String,
    onClick: () -> Unit
) {
    ExpressiveMorphCard(
        baseCorner = 28.dp,
        pressedCorner = 16.dp,
        onClick = onClick
    ) { morphShape ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(WarmAmberCardBg, Color(0xFF3B2410))
                    )
                )
                .border(1.5.dp, WarmAmberBorder, morphShape)
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF4D2F14))
                    .border(1.dp, WarmAmberAccent.copy(alpha = 0.4f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = "Get Ready",
                    tint = WarmAmberAccent,
                    modifier = Modifier.size(21.dp)
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
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold
                    )

                    Surface(
                        color = Color(0xFF4D2F14),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.border(1.dp, WarmAmberAccent.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                    ) {
                        Text(
                            text = "Leave by $leaveByText",
                            color = WarmAmberText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(5.dp))

                Text(
                    text = "$prepMinutes min routine • Tap to customize",
                    color = WarmAmberAccent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
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
        baseCorner = 28.dp,
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
                        .padding(end = 10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF1E2638))
                            .border(1.dp, Color(0xFF334155), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = "Drive",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Drive",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold
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
                                    text = "${config.effectiveCommuteMinutes} min",
                                    color = when (config.trafficCondition) {
                                        TrafficCondition.CLEAR -> EmeraldBright
                                        TrafficCondition.MODERATE -> WarmAmberAccent
                                        TrafficCondition.HEAVY -> CoralRedBright
                                    },
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = config.destinationAddress,
                            color = Color(0xFF94A3B8),
                            fontSize = 13.sp,
                            lineHeight = 17.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                FilledTonalIconButton(
                    onClick = onOpenMaps,
                    shape = RoundedCornerShape(16.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = Color(0xFF1D3557),
                        contentColor = Color(0xFF38BDF8)
                    ),
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Navigate in Google Maps",
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Material 3 Expressive Wavy Animated Traffic Route Progress Bar
            AnimatedTrafficRouteBar(
                condition = config.trafficCondition,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(22.dp)
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

        // Subtle background track
        drawLine(
            color = Color(0xFF22293A),
            start = Offset(startX, centerY),
            end = Offset(endX, centerY),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )

        // Helper to draw an expressive M3 sine-wave segment
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

        // Expressive Glowing Endpoint Nodes
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
        pressedCorner = 14.dp,
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
                        .size(34.dp)
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
                Text(
                    text = "$arrivalTimeText • $destinationLabel",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Surface(
                color = Color(0xFF1D3557),
                shape = RoundedCornerShape(50)
            ) {
                Text(
                    text = "Edit arrival",
                    color = Color(0xFF7DD3FC),
                    fontSize = 12.sp,
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
    var prepSlider by remember { mutableFloatStateOf(initialPrep.toFloat()) }
    var baselineSlider by remember { mutableFloatStateOf(initialBaseline.toFloat()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF161E2E),
        shape = RoundedCornerShape(32.dp),
        title = {
            Text("Routine & Baseline Commute", color = Color.White, fontWeight = FontWeight.ExtraBold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column {
                    Text(
                        text = "Get Ready / Prep Time: ${prepSlider.toInt()} min",
                        color = WarmAmberText,
                        fontWeight = FontWeight.Bold
                    )
                    Slider(
                        value = prepSlider,
                        onValueChange = { prepSlider = it },
                        valueRange = 5f..120f,
                        steps = 22,
                        colors = SliderDefaults.colors(
                            thumbColor = WarmAmberAccent,
                            activeTrackColor = WarmAmberAccent
                        )
                    )
                }

                Column {
                    Text(
                        text = "Failsafe Baseline Drive: ${baselineSlider.toInt()} min",
                        color = Color(0xFFCBD5E1),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Used to calculate sleep gained and serves as your offline failsafe alarm.",
                        color = MutedAxisColor,
                        fontSize = 12.sp
                    )
                    Slider(
                        value = baselineSlider,
                        onValueChange = { baselineSlider = it },
                        valueRange = 5f..120f,
                        steps = 22,
                        colors = SliderDefaults.colors(
                            thumbColor = EmeraldBright,
                            activeTrackColor = EmeraldBright
                        )
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(prepSlider.toInt(), baselineSlider.toInt()) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldBright,
                    contentColor = Color(0xFF052E16)
                ),
                shape = RoundedCornerShape(50)
            ) {
                Text("Apply", fontWeight = FontWeight.ExtraBold)
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
    onSave: (String, Boolean, String, String, String, String, Boolean) -> Unit
) {
    var useGps by remember { mutableStateOf(config.useGpsOrigin) }
    var originAddress by remember { mutableStateOf(config.originAddress) }
    var destAddress by remember { mutableStateOf(config.destinationAddress) }
    var destLabel by remember { mutableStateOf(config.destinationLabel) }
    var userTomTomKey by remember { mutableStateOf(config.userTomTomApiKey) }
    var googleApiKey by remember { mutableStateOf(config.apiKey) }
    var allowOsrm by remember { mutableStateOf(config.allowOpenSourceFallback) }

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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    color = Color(0xFF0B111E),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.border(1.dp, Color(0xFF283754), RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
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
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Default TomTom key caps at 5 polls/hr. Add your own TomTom or Google key below for unlimited polling.",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            lineHeight = 15.sp
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text("Allow Open-Source Rollback (OSRM)", color = Color.White, fontSize = 14.sp)
                        Text(
                            "Automatically use free OpenStreetMap routing if rate-limited or offline",
                            color = MutedAxisColor,
                            fontSize = 11.sp
                        )
                    }
                    Switch(checked = allowOsrm, onCheckedChange = { allowOsrm = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(originAddress, useGps, destAddress, destLabel, userTomTomKey, googleApiKey, allowOsrm)
                } ,
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