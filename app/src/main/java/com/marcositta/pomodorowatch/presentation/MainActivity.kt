package com.marcositta.pomodorowatch.presentation

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
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.marcositta.pomodorowatch.R

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { PomodoroApp() } }
    }
}

enum class EstadoRelogio { PARADO_INICIO, RODANDO, VIBRANDO, ESPERANDO_PROXIMO }

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

    val minhaFonte = FontFamily(Font(R.font.fonte_relogio))
    val CorTextoNormal = Color(0xFFF0F0F0)
    val CorTextoDestaque = Color(0xFFFFB7B2)
    val CorTextoSecundario = Color(0xFFAAAAAA)

    // --- TEMPOS ---
    val TEMPO_FOCO = 1500f        // 25 min
    val TEMPO_PAUSA_CURTA = 300f  // 5 min
    val TEMPO_PAUSA_LONGA = 1200f // 20 min (Prêmio do 4º ciclo)

    // --- ESTADOS ---
    var estadoAtual by remember { mutableStateOf(EstadoRelogio.PARADO_INICIO) }
    var tipoFase by remember { mutableStateOf("TRABALHO") }
    var tempoTotal by remember { mutableStateOf(TEMPO_FOCO) }
    var tempoAtual by remember { mutableStateOf(TEMPO_FOCO) }

    // NOVO: Contador de Ciclos
    var ciclosConcluidos by remember { mutableIntStateOf(0) }

    // --- MOTOR DO TEMPO ---
    LaunchedEffect(key1 = estadoAtual, key2 = tempoAtual) {
        if (estadoAtual == EstadoRelogio.RODANDO && tempoAtual > 0) {
            delay(1000L)
            tempoAtual -= 1
        } else if (tempoAtual <= 0f && estadoAtual == EstadoRelogio.RODANDO) {
            // Se acabou um foco, conta +1 ciclo
            if (tipoFase == "TRABALHO") {
                ciclosConcluidos += 1
            }

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

        EstadoRelogio.RODANDO ->
            if (tipoFase == "TRABALHO") "Foco" else "Pausa"

        EstadoRelogio.VIBRANDO ->
            if (tipoFase == "TRABALHO") "Ciclo Concluído!" else "Retome\no foco!"

        EstadoRelogio.ESPERANDO_PROXIMO ->
            if (tipoFase == "TRABALHO") {
                // Lógica visual: Avisa se a próxima pausa é a Longa
                if (ciclosConcluidos % 4 == 0 && ciclosConcluidos > 0) "Toque para iniciar\nPAUSA LONGA"
                else "Toque para iniciar\no período de pausa"
            } else {
                "Toque para\nretomar o foco"
            }
    }

    val tamanhoFonte = if (estadoAtual == EstadoRelogio.ESPERANDO_PROXIMO) 16.sp else 26.sp
    val corTextoAtual = if (estadoAtual == EstadoRelogio.VIBRANDO) CorTextoDestaque else CorTextoNormal

    Box(
        modifier = Modifier.fillMaxSize().clickable {
            when (estadoAtual) {
                EstadoRelogio.PARADO_INICIO -> {
                    tipoFase = "TRABALHO"
                    tempoTotal = TEMPO_FOCO
                    tempoAtual = TEMPO_FOCO
                    estadoAtual = EstadoRelogio.RODANDO
                    ciclosConcluidos = 0 // Garante que começa zerado
                }
                EstadoRelogio.RODANDO -> { }
                EstadoRelogio.VIBRANDO -> {
                    vibrador.cancel()
                    estadoAtual = EstadoRelogio.ESPERANDO_PROXIMO
                }
                EstadoRelogio.ESPERANDO_PROXIMO -> {
                    // --- LÓGICA INTELIGENTE DOS 4 CICLOS ---
                    if (tipoFase == "TRABALHO") {
                        // Acabou o trabalho, vai para pausa
                        tipoFase = "DESCANSO"

                        // Verifica se é múltiplo de 4 (4, 8, 12...)
                        if (ciclosConcluidos % 4 == 0) {
                            tempoTotal = TEMPO_PAUSA_LONGA
                            tempoAtual = TEMPO_PAUSA_LONGA
                        } else {
                            tempoTotal = TEMPO_PAUSA_CURTA
                            tempoAtual = TEMPO_PAUSA_CURTA
                        }
                    } else {
                        // Acabou a pausa, volta para trabalho
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
                fontFamily = minhaFonte,
                fontSize = tamanhoFonte,
                textAlign = TextAlign.Center,
                color = corTextoAtual
            )

            if (estadoAtual == EstadoRelogio.VIBRANDO) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Toque para\nparar de vibrar",
                    fontFamily = minhaFonte,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    color = CorTextoSecundario
                )
            }

            // Debug visual (Opcional): Mostra qual ciclo estamos
            // Você pode remover isso depois se achar que polui
            if (estadoAtual == EstadoRelogio.RODANDO && tipoFase == "TRABALHO") {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Ciclo #${ciclosConcluidos + 1}",
                    fontFamily = minhaFonte,
                    fontSize = 10.sp,
                    color = Color.DarkGray
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
        for (i in 0 until numTracos) {
            val anguloRad = Math.toRadians((i * (360.0 / numTracos.toDouble())) - 90.0)
            val inicio = Offset(x = centro.x + (raio - 10) * cos(anguloRad).toFloat(), y = centro.y + (raio - 10) * sin(anguloRad).toFloat())
            val fim = Offset(x = centro.x + raio * cos(anguloRad).toFloat(), y = centro.y + raio * sin(anguloRad).toFloat())
            drawLine(color = Color(0xFF444444), start = inicio, end = fim, strokeWidth = 3f)
        }
        if (estado == EstadoRelogio.RODANDO) {
            val segundosPassados = (60 - (atual % 60)) % 60
            val anguloSegundos = (segundosPassados * 6) - 90
            val radSegundos = Math.toRadians(anguloSegundos.toDouble())
            val pontaSegX = centro.x + (raio - 15) * cos(radSegundos).toFloat()
            val pontaSegY = centro.y + (raio - 15) * sin(radSegundos).toFloat()
            drawLine(color = Color(0xFF888888), start = centro, end = Offset(pontaSegX, pontaSegY), strokeWidth = 2f, alpha = 0.3f)
        }
        val progresso = if (estado == EstadoRelogio.RODANDO) (1 - (atual / total)) else 0f
        val graus = (progresso * 360) - 90
        val radianos = Math.toRadians(graus.toDouble())
        val tomateCentroX = centro.x + (raio / 2) * cos(radianos).toFloat()
        val tomateCentroY = centro.y + (raio / 2) * sin(radianos).toFloat()
        val tamanhoTomatePx = 20.dp.toPx()
        val tamanhoInt = tamanhoTomatePx.toInt()
        withTransform({ rotate(degrees = graus + 90f, pivot = Offset(tomateCentroX, tomateCentroY)) }) {
            drawImage(image = imagemTomate, dstOffset = IntOffset((tomateCentroX - (tamanhoTomatePx / 2)).toInt(), (tomateCentroY - (tamanhoTomatePx / 2)).toInt()), dstSize = IntSize(tamanhoInt, tamanhoInt))
        }
    }
}