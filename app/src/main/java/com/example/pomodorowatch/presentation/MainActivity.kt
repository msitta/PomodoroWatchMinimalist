package com.example.pomodorowatch.presentation

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp // Importante para tamanho de fonte manual se precisar
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

// Imports gráficos
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.example.pomodorowatch.R

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                PomodoroApp()
            }
        }
    }
}

enum class EstadoRelogio {
    PARADO_INICIO, RODANDO, VIBRANDO, ESPERANDO_PROXIMO
}

@Composable
fun PomodoroApp() {
    val context = LocalContext.current
    val vibrador = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    // --- CARREGANDO A FONTE PERSONALIZADA ---
    // Certifique-se de ter o arquivo 'fonte_relogio.ttf' na pasta res/font
    // Se der erro aqui, é porque o arquivo não está lá ou tem nome diferente.
    val minhaFonte = FontFamily(Font(R.font.fonte_relogio))

    // --- PALETA DE CORES ELEGANTES ---
    val CorTextoNormal = Color(0xFFF0F0F0) // Off-White (Quase branco)
    val CorTextoDestaque = Color(0xFFFFB7B2) // Coral Pastel (Suave)
    val CorTextoSecundario = Color(0xFFAAAAAA) // Cinza Médio

    // --- TEMPOS ---
    val TEMPO_FOCO = 1500f
    val TEMPO_PAUSA = 300f

    // --- ESTADOS ---
    var estadoAtual by remember { mutableStateOf(EstadoRelogio.PARADO_INICIO) }
    var tipoFase by remember { mutableStateOf("TRABALHO") }
    var tempoTotal by remember { mutableStateOf(TEMPO_FOCO) }
    var tempoAtual by remember { mutableStateOf(TEMPO_FOCO) }

    // --- MOTOR ---
    LaunchedEffect(key1 = estadoAtual, key2 = tempoAtual) {
        if (estadoAtual == EstadoRelogio.RODANDO && tempoAtual > 0) {
            delay(1000L)
            tempoAtual -= 1
        } else if (tempoAtual <= 0f && estadoAtual == EstadoRelogio.RODANDO) {
            estadoAtual = EstadoRelogio.VIBRANDO
            val timings = longArrayOf(0, 500, 500)
            val amplitudes = intArrayOf(0, 255, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrador.vibrate(VibrationEffect.createWaveform(timings, amplitudes, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrador.vibrate(timings, 0)
            }
        }
    }

    // --- TEXTOS ---
    val textoCentral = when (estadoAtual) {
        EstadoRelogio.PARADO_INICIO -> "Iniciar"
        EstadoRelogio.RODANDO -> if (tipoFase == "TRABALHO") "Foco" else "Pausa"
        EstadoRelogio.VIBRANDO -> if (tipoFase == "TRABALHO") "Faça uma\npausa..." else "Retome\no foco!"
        EstadoRelogio.ESPERANDO_PROXIMO -> if (tipoFase == "TRABALHO") "Toque para iniciar\no período de pausa" else "Toque para\nretomar o foco"
    }

    // Define o tamanho da fonte baseado no tamanho do texto
    val tamanhoFonte = if (estadoAtual == EstadoRelogio.ESPERANDO_PROXIMO) 16.sp else 26.sp

    // Define a cor baseada no estado
    val corTextoAtual = if (estadoAtual == EstadoRelogio.VIBRANDO) CorTextoDestaque else CorTextoNormal

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable {
                when (estadoAtual) {
                    EstadoRelogio.PARADO_INICIO -> {
                        tipoFase = "TRABALHO"
                        tempoTotal = TEMPO_FOCO
                        tempoAtual = TEMPO_FOCO
                        estadoAtual = EstadoRelogio.RODANDO
                    }
                    EstadoRelogio.RODANDO -> { }
                    EstadoRelogio.VIBRANDO -> {
                        vibrador.cancel()
                        estadoAtual = EstadoRelogio.ESPERANDO_PROXIMO
                    }
                    EstadoRelogio.ESPERANDO_PROXIMO -> {
                        if (tipoFase == "TRABALHO") {
                            tipoFase = "DESCANSO"
                            tempoTotal = TEMPO_PAUSA
                            tempoAtual = TEMPO_PAUSA
                        } else {
                            tipoFase = "TRABALHO"
                            tempoTotal = TEMPO_FOCO
                            tempoAtual = TEMPO_FOCO
                        }
                        estadoAtual = EstadoRelogio.RODANDO
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        RelogioVisual(tempoTotal, tempoAtual, estadoAtual)

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = textoCentral,
                fontFamily = minhaFonte, // APLICANDO A FONTE NOVA AQUI
                fontSize = tamanhoFonte,
                textAlign = TextAlign.Center,
                color = corTextoAtual
            )

            if (estadoAtual == EstadoRelogio.VIBRANDO) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Toque para\nparar de vibrar",
                    fontFamily = minhaFonte, // APLICANDO AQUI TAMBÉM
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    color = CorTextoSecundario
                )
            }
        }
    }
}

@Composable
fun RelogioVisual(total: Float, atual: Float, estado: EstadoRelogio) {
    val imagemTomate = ImageBitmap.imageResource(id = R.drawable.tomate_cartoon)

    Canvas(modifier = Modifier.fillMaxSize().padding(10.dp)) {
        val centro = center
        val raio = size.minDimension / 2
        val numTracos = if (total > 300) 25 else 5

        // Tracinhos mais sutis (Cinza mais escuro)
        for (i in 0 until numTracos) {
            val anguloRad = Math.toRadians((i * (360.0 / numTracos.toDouble())) - 90.0)
            val inicio = Offset(
                x = centro.x + (raio - 10) * cos(anguloRad).toFloat(),
                y = centro.y + (raio - 10) * sin(anguloRad).toFloat()
            )
            val fim = Offset(
                x = centro.x + raio * cos(anguloRad).toFloat(),
                y = centro.y + raio * sin(anguloRad).toFloat()
            )
            // Mudei a cor dos traços para ficar mais sutil
            drawLine(color = Color(0xFF444444), start = inicio, end = fim, strokeWidth = 3f)
        }

        if (estado == EstadoRelogio.RODANDO) {
            val segundosPassados = (60 - (atual % 60)) % 60
            val anguloSegundos = (segundosPassados * 6) - 90
            val radSegundos = Math.toRadians(anguloSegundos.toDouble())

            val pontaSegX = centro.x + (raio - 15) * cos(radSegundos).toFloat()
            val pontaSegY = centro.y + (raio - 15) * sin(radSegundos).toFloat()

            // Ponteiro mais sutil ainda
            drawLine(
                color = Color(0xFF888888),
                start = centro,
                end = Offset(pontaSegX, pontaSegY),
                strokeWidth = 2f,
                alpha = 0.3f // Mais transparente
            )
        }

        val progresso = if (estado == EstadoRelogio.RODANDO) (1 - (atual / total)) else 0f
        val graus = (progresso * 360) - 90
        val radianos = Math.toRadians(graus.toDouble())
        val tomateCentroX = centro.x + (raio / 2) * cos(radianos).toFloat()
        val tomateCentroY = centro.y + (raio / 2) * sin(radianos).toFloat()
        val tamanhoTomatePx = 20.dp.toPx()
        val tamanhoInt = tamanhoTomatePx.toInt()

        withTransform({
            rotate(degrees = graus + 90f, pivot = Offset(tomateCentroX, tomateCentroY))
        }) {
            drawImage(
                image = imagemTomate,
                dstOffset = IntOffset(
                    (tomateCentroX - (tamanhoTomatePx / 2)).toInt(),
                    (tomateCentroY - (tamanhoTomatePx / 2)).toInt()
                ),
                dstSize = IntSize(tamanhoInt, tamanhoInt)
            )
        }
    }
}