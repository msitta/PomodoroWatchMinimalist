package com.marcositta.pomodorowatch.presentation

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.wear.ambient.AmbientModeSupport
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.marcositta.pomodorowatch.R
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : FragmentActivity(), AmbientModeSupport.AmbientCallbackProvider {

    private val isAmbient = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AmbientModeSupport.attach(this)
        setContent {
            MaterialTheme {
                PomodoroApp(isAmbientState = isAmbient)
            }
        }
    }

    override fun getAmbientCallback(): AmbientModeSupport.AmbientCallback =
        object : AmbientModeSupport.AmbientCallback() {
            override fun onEnterAmbient(ambientDetails: Bundle?) { isAmbient.value = true }
            override fun onExitAmbient() { isAmbient.value = false }
            override fun onUpdateAmbient() { super.onUpdateAmbient() }
        }
}

enum class WatchState { IDLE, RUNNING, PAUSED, VIBRATING, WAITING_NEXT }
enum class PhaseType { WORK, BREAK }

@Composable
fun PomodoroApp(isAmbientState: State<Boolean>) {
    val context = LocalContext.current
    val isAmbient by isAmbientState

    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    val customFont = FontFamily(Font(R.font.fonte_relogio))

    // Configurações
    var focusTimeConfig by remember { mutableFloatStateOf(1500f) }
    var shortBreakConfig by remember { mutableFloatStateOf(300f) }
    var longBreakConfig by remember { mutableFloatStateOf(1200f) }
    var cyclesBeforeLongBreakConfig by remember { mutableIntStateOf(4) }

    var isEditingConfig by remember { mutableStateOf(false) }

    // Estados
    var currentState by remember { mutableStateOf(WatchState.IDLE) }
    var currentPhase by remember { mutableStateOf(PhaseType.WORK) }
    var totalTime by remember { mutableFloatStateOf(focusTimeConfig) }
    var currentTime by remember { mutableFloatStateOf(focusTimeConfig) }
    var completedCycles by remember { mutableIntStateOf(0) }

    // Timer Logic
    LaunchedEffect(key1 = currentState, key2 = currentTime, key3 = isAmbient) {
        if (!isAmbient && currentState == WatchState.RUNNING && currentTime > 0) {
            delay(1000L)
            currentTime -= 1
        } else if (currentTime <= 0f && currentState == WatchState.RUNNING) {
            if (currentPhase == PhaseType.WORK) completedCycles += 1
            currentState = WatchState.VIBRATING
            if(!isAmbient) {
                val timings = longArrayOf(0, 500, 500)
                val amplitudes = intArrayOf(0, 255, 0)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, 0))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(timings, 0)
                }
            }
        }
    }

    if (isEditingConfig && !isAmbient) {
        ConfigScreen(
            font = customFont,
            initialFocus = focusTimeConfig,
            initialShortBreak = shortBreakConfig,
            initialLongBreak = longBreakConfig,
            initialCycles = cyclesBeforeLongBreakConfig,
            onSave = { newFocus, newShort, newLong, newCycles ->
                focusTimeConfig = newFocus
                shortBreakConfig = newShort
                longBreakConfig = newLong
                cyclesBeforeLongBreakConfig = newCycles
                totalTime = newFocus
                currentTime = newFocus
                currentState = WatchState.IDLE
                completedCycles = 0
                isEditingConfig = false
            }
        )
    } else {
        WatchScreen(
            font = customFont,
            state = currentState,
            phase = currentPhase,
            totalTime = totalTime,
            currentTime = currentTime,
            completedCycles = completedCycles,
            cyclesTarget = cyclesBeforeLongBreakConfig,
            vibrator = vibrator,
            isAmbient = isAmbient,
            onCenterClick = {
                if (!isAmbient) {
                    when (currentState) {
                        WatchState.IDLE -> {
                            currentPhase = PhaseType.WORK
                            totalTime = focusTimeConfig
                            currentTime = focusTimeConfig
                            currentState = WatchState.RUNNING
                            completedCycles = 0
                        }
                        WatchState.RUNNING -> currentState = WatchState.PAUSED
                        WatchState.PAUSED -> currentState = WatchState.RUNNING
                        WatchState.VIBRATING -> {
                            vibrator.cancel()
                            currentState = WatchState.WAITING_NEXT
                        }
                        WatchState.WAITING_NEXT -> {
                            if (currentPhase == PhaseType.WORK) {
                                currentPhase = PhaseType.BREAK
                                if (completedCycles > 0 && completedCycles % cyclesBeforeLongBreakConfig == 0) {
                                    totalTime = longBreakConfig
                                    currentTime = longBreakConfig
                                } else {
                                    totalTime = shortBreakConfig
                                    currentTime = shortBreakConfig
                                }
                            } else {
                                currentPhase = PhaseType.WORK
                                totalTime = focusTimeConfig
                                currentTime = focusTimeConfig
                            }
                            currentState = WatchState.RUNNING
                        }
                    }
                }
            },
            onOpenConfig = { if (currentState == WatchState.IDLE && !isAmbient) isEditingConfig = true },
            onReset = {
                if (!isAmbient) {
                    vibrator.cancel()
                    currentState = WatchState.IDLE
                    totalTime = focusTimeConfig
                    currentTime = focusTimeConfig
                    currentPhase = PhaseType.WORK
                    completedCycles = 0
                }
            }
        )
    }
}

@Composable
fun WatchScreen(
    font: FontFamily,
    state: WatchState,
    phase: PhaseType,
    totalTime: Float,
    currentTime: Float,
    completedCycles: Int,
    cyclesTarget: Int,
    vibrator: Vibrator,
    isAmbient: Boolean,
    onCenterClick: () -> Unit,
    onOpenConfig: () -> Unit,
    onReset: () -> Unit
) {
    // --- PALETA MONOCHROME ---
    val grayMain = Color(0xFFDDDDDD)      // Texto principal
    val graySecondary = Color(0xFF777777) // Instruções
    val grayAmbient = Color(0xFF555555)   // Ambient mode

    // Relógio Digital Ambient
    var timeNow by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(isAmbient) {
        if (isAmbient) {
            while(true) {
                timeNow = LocalTime.now()
                delay(1000L * 60)
            }
        }
    }

    val centerText = if (isAmbient) {
        timeNow.format(DateTimeFormatter.ofPattern("HH:mm"))
    } else {
        // TUDO EM CAIXA BAIXA
        when (state) {
            WatchState.IDLE -> "iniciar"
            WatchState.RUNNING -> if (phase == PhaseType.WORK) "foco" else "pausa"
            WatchState.PAUSED -> "pausado"
            WatchState.VIBRATING -> if (phase == PhaseType.WORK) "ciclo\nconcluído" else "voltar\nao foco"
            WatchState.WAITING_NEXT ->
                if (phase == PhaseType.WORK) {
                    if (completedCycles > 0 && completedCycles % cyclesTarget == 0) "relaxe..."
                    else "iniciar\npausa"
                } else {
                    "iniciar\nfoco"
                }
        }
    }

    val fontSize = if (isAmbient) 28.sp else 20.sp
    val fontColor = if (isAmbient) grayAmbient else grayMain

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(enabled = !isAmbient) { onCenterClick() },
        contentAlignment = Alignment.Center
    ) {
        VisualClock(totalTime, currentTime, state, isAmbient)

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = centerText,
                fontFamily = font,
                fontSize = fontSize,
                textAlign = TextAlign.Center,
                color = fontColor
            )

            if (!isAmbient) {
                if (state == WatchState.PAUSED) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "toque para retomar", // lower
                        fontFamily = font,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        color = graySecondary
                    )
                }

                if (state == WatchState.VIBRATING) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "toque para parar", // lower
                        fontFamily = font,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        color = graySecondary
                    )
                }
            }
        }

        if (!isAmbient) {
            if (state == WatchState.IDLE) {
                Box(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp).size(40.dp).clickable { onOpenConfig() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "⚙️", fontSize = 20.sp, color = Color.Gray)
                }
            }

            if (state == WatchState.PAUSED) {
                Box(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp).size(40.dp).background(Color(0xFF222222), CircleShape).clickable { onReset() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "⏹", fontSize = 14.sp, color = Color.Gray)
                }
            }
        }
    }
}

@Composable
fun ConfigScreen(
    font: FontFamily,
    initialFocus: Float,
    initialShortBreak: Float,
    initialLongBreak: Float,
    initialCycles: Int,
    onSave: (Float, Float, Float, Int) -> Unit
) {
    var focus by remember { mutableFloatStateOf(initialFocus / 60) }
    var shortBreak by remember { mutableFloatStateOf(initialShortBreak / 60) }
    var longBreak by remember { mutableFloatStateOf(initialLongBreak / 60) }
    var cycles by remember { mutableIntStateOf(initialCycles) }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier.fillMaxSize().background(Color.Black).verticalScroll(scrollState).padding(vertical = 40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // lower
        Text("configurações", fontFamily = font, color = Color.DarkGray, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(20.dp))

        // lower
        ConfigItem(title = "foco", value = focus, suffix = "m", font = font,
            onMinus = { if(focus > 1) focus -= 1 }, onPlus = { if(focus < 60) focus += 1 })
        Spacer(modifier = Modifier.height(15.dp))

        // lower
        ConfigItem(title = "pausa curta", value = shortBreak, suffix = "m", font = font,
            onMinus = { if(shortBreak > 1) shortBreak -= 1 }, onPlus = { if(shortBreak < 15) shortBreak += 1 })
        Spacer(modifier = Modifier.height(15.dp))

        // lower
        ConfigItem(title = "pausa longa", value = longBreak, suffix = "m", font = font,
            onMinus = { if(longBreak > 1) longBreak -= 1 }, onPlus = { if(longBreak < 60) longBreak += 1 })
        Spacer(modifier = Modifier.height(15.dp))

        // lower
        ConfigItem(title = "ciclos p/ longa", value = cycles.toFloat(), suffix = "x", font = font,
            onMinus = { if(cycles > 2) cycles -= 1 }, onPlus = { if(cycles < 10) cycles += 1 })

        Spacer(modifier = Modifier.height(30.dp))

        Button(
            onClick = { onSave(focus * 60, shortBreak * 60, longBreak * 60, cycles) },
            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF333333)),
            modifier = Modifier.height(40.dp).fillMaxWidth(0.7f)
        ) {
            // lower
            Text("salvar", color = Color.LightGray, fontFamily = font)
        }
    }
}

@Composable
fun ConfigItem(title: String, value: Float, suffix: String, font: FontFamily, onMinus: () -> Unit, onPlus: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, fontFamily = font, color = Color.Gray, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Button(onClick = onMinus, colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF222222)), modifier = Modifier.size(32.dp)) {
                Text("-", color = Color.Gray, fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(text = "${value.toInt()} $suffix", fontFamily = font, color = Color.LightGray, fontSize = 18.sp)
            Spacer(modifier = Modifier.width(10.dp))
            Button(onClick = onPlus, colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF222222)), modifier = Modifier.size(32.dp)) {
                Text("+", color = Color.Gray, fontSize = 16.sp)
            }
        }
    }
}

@Composable
fun VisualClock(total: Float, current: Float, state: WatchState, isAmbient: Boolean) {
    val tomatoImage = ImageBitmap.imageResource(id = R.drawable.tomate_cartoon)

    Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        val centerPoint = center
        val radius = size.minDimension / 2

        if (!isAmbient) {
            drawCircle(color = Color(0xFF222222), radius = radius, style = Stroke(width = 2f))
        }

        if (!isAmbient) {
            val totalMinutes = (total / 60).toInt().coerceAtLeast(1)
            val numTicks = totalMinutes
            for (i in 0 until numTicks) {
                val angleRad = Math.toRadians((i * (360.0 / numTicks.toDouble())) - 90.0)
                val start = Offset(x = centerPoint.x + (radius - 15) * cos(angleRad).toFloat(), y = centerPoint.y + (radius - 15) * sin(angleRad).toFloat())
                val end = Offset(x = centerPoint.x + radius * cos(angleRad).toFloat(), y = centerPoint.y + radius * sin(angleRad).toFloat())
                drawLine(color = Color(0xFF333333), start = start, end = end, strokeWidth = 2f)
            }
        }

        if (!isAmbient && (state == WatchState.RUNNING || state == WatchState.PAUSED)) {
            val secondsPassed = (60 - (current % 60)) % 60
            val secondsAngle = (secondsPassed * 6) - 90
            val secondsRad = Math.toRadians(secondsAngle.toDouble())
            val tipX = centerPoint.x + (radius - 20) * cos(secondsRad).toFloat()
            val tipY = centerPoint.y + (radius - 20) * sin(secondsRad).toFloat()
            val pointerAlpha = if (state == WatchState.PAUSED) 0.1f else 0.2f
            drawLine(color = Color(0xFFAAAAAA), start = centerPoint, end = Offset(tipX, tipY), strokeWidth = 2f, alpha = pointerAlpha)
            drawCircle(Color(0xFFAAAAAA), radius = 4f, alpha = pointerAlpha, center = centerPoint)
        }

        val progress = if (total > 0) (1 - (current / total)) else 0f
        val degrees = (progress * 360) - 90
        val radians = Math.toRadians(degrees.toDouble())

        val tomatoSizePx = 24.dp.toPx()
        val tomatoRadius = radius - (tomatoSizePx / 2)
        val tomatoCenterX = centerPoint.x + tomatoRadius * cos(radians).toFloat()
        val tomatoCenterY = centerPoint.y + tomatoRadius * sin(radians).toFloat()
        val sizeInt = tomatoSizePx.toInt()

        val tomatoAlpha = if (isAmbient) 0.3f else 1.0f

        withTransform({
            rotate(degrees = degrees + 90f, pivot = Offset(tomatoCenterX, tomatoCenterY))
        }) {
            drawImage(
                image = tomatoImage,
                dstOffset = IntOffset((tomatoCenterX - (tomatoSizePx / 2)).toInt(), (tomatoCenterY - (tomatoSizePx / 2)).toInt()),
                dstSize = IntSize(sizeInt, sizeInt),
                alpha = tomatoAlpha
            )
        }
    }
}