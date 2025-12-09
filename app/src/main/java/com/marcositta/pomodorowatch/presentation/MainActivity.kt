package com.marcositta.pomodorowatch.presentation

import android.content.Context
import androidx.compose.foundation.shape.CircleShape
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
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

// ADICIONADO O ESTADO 'PAUSADO'
enum class EstadoRelogio { PARADO_INICIO, RODANDO, PAUSADO, VIBRANDO, ESPERANDO_PROXIMO }

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

    // --- VARIÁVEIS DE CONFIGURAÇÃO ---
    var tempoFocoConfigurado by remember { mutableFloatStateOf(1500f) }
    val TEMPO_PAUSA_CURTA = 300f
    val TEMPO_PAUSA_LONGA = 1200f

    var editandoConfig by remember { mutableStateOf(false) }

    var estadoAtual by remember { mutableStateOf(EstadoRelogio.PARADO_INICIO) }
    var tipoFase by remember { mutableStateOf("TRABALHO") }
    var tempoTotal by remember { mutableStateOf(tempoFocoConfigurado) }
    var tempoAtual by remember { mutableStateOf(tempoFocoConfigurado) }
    var ciclosConcluidos by remember { mutableIntStateOf(0) }

    // --- MOTOR DO TEMPO ---
    LaunchedEffect(key1 = estadoAtual, key2 = tempoAtual) {
        // Só roda se o estado for RODANDO. Se for PAUSADO, ele fica quieto aqui.
        if (estadoAtual == EstadoRelogio.RODANDO && tempoAtual > 0) {
            delay(1000L)
            tempoAtual -= 1
        } else if (tempoAtual <= 0f && estadoAtual == EstadoRelogio.RODANDO) {
            if (tipoFase == "TRABALHO") ciclosConcluidos += 1
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

    if (editandoConfig) {
        TelaConfiguracao(
            fonte = minhaFonte,
            tempoAtualSegundos = tempoFocoConfigurado,
            onSalvar = { novoTempo ->
                tempoFocoConfigurado = novoTempo
                tempoTotal = novoTempo
                tempoAtual = novoTempo
                estadoAtual = EstadoRelogio.PARADO_INICIO
                editandoConfig = false
            }
        )
    } else {
        TelaRelogio(
            fonte = minhaFonte,
            estadoAtual = estadoAtual,
            tipoFase = tipoFase,
            tempoTotal = tempoTotal,
            tempoAtual = tempoAtual,
            ciclosConcluidos = ciclosConcluidos,
            vibrador = vibrador,
            onClicarCentro = {
                when (estadoAtual) {
                    EstadoRelogio.PARADO_INICIO -> {
                        tipoFase = "TRABALHO"
                        tempoTotal = tempoFocoConfigurado
                        tempoAtual = tempoFocoConfigurado
                        estadoAtual = EstadoRelogio.RODANDO
                        ciclosConcluidos = 0
                    }

                    // --- MUDANÇA: PAUSA INTELIGENTE ---
                    EstadoRelogio.RODANDO -> {
                        // Ao clicar enquanto roda, ele apenas PAUSA
                        estadoAtual = EstadoRelogio.PAUSADO
                    }

                    EstadoRelogio.PAUSADO -> {
                        // Ao clicar no centro enquanto pausado, ele RETOMA
                        estadoAtual = EstadoRelogio.RODANDO
                    }
                    // ----------------------------------

                    EstadoRelogio.VIBRANDO -> {
                        vibrador.cancel()
                        estadoAtual = EstadoRelogio.ESPERANDO_PROXIMO
                    }
                    EstadoRelogio.ESPERANDO_PROXIMO -> {
                        if (tipoFase == "TRABALHO") {
                            tipoFase = "DESCANSO"
                            if (ciclosConcluidos % 4 == 0) {
                                tempoTotal = TEMPO_PAUSA_LONGA
                                tempoAtual = TEMPO_PAUSA_LONGA
                            } else {
                                tempoTotal = TEMPO_PAUSA_CURTA
                                tempoAtual = TEMPO_PAUSA_CURTA
                            }
                        } else {
                            tipoFase = "TRABALHO"
                            tempoTotal = tempoFocoConfigurado
                            tempoAtual = tempoFocoConfigurado
                        }
                        estadoAtual = EstadoRelogio.RODANDO
                    }
                }
            },
            onAbrirConfig = {
                if (estadoAtual == EstadoRelogio.PARADO_INICIO) {
                    editandoConfig = true
                }
            },
            onResetar = {
                // Função específica para o botão de reset
                estadoAtual = EstadoRelogio.PARADO_INICIO
                tempoAtual = tempoTotal // Reseta visualmente
            }
        )
    }
}

@Composable
fun TelaRelogio(
    fonte: FontFamily,
    estadoAtual: EstadoRelogio,
    tipoFase: String,
    tempoTotal: Float,
    tempoAtual: Float,
    ciclosConcluidos: Int,
    vibrador: Vibrator,
    onClicarCentro: () -> Unit,
    onAbrirConfig: () -> Unit,
    onResetar: () -> Unit
) {
    val CorTextoNormal = Color(0xFFF0F0F0)
    val CorTextoDestaque = Color(0xFFFFB7B2)
    val CorTextoSecundario = Color(0xFFAAAAAA)

    val textoCentral = when (estadoAtual) {
        EstadoRelogio.PARADO_INICIO -> "Iniciar"
        EstadoRelogio.RODANDO -> if (tipoFase == "TRABALHO") "Foco" else "Pausa"
        EstadoRelogio.PAUSADO -> "Pausado" // Texto novo para o estado pausado
        EstadoRelogio.VIBRANDO -> if (tipoFase == "TRABALHO") "Ciclo Concluído!" else "Retome\no foco!"
        EstadoRelogio.ESPERANDO_PROXIMO ->
            if (tipoFase == "TRABALHO") {
                if (ciclosConcluidos % 4 == 0 && ciclosConcluidos > 0) "Toque para iniciar\nPAUSA LONGA"
                else "Toque para iniciar\no período de pausa"
            } else {
                "Toque para\nretomar o foco"
            }
    }

    val tamanhoFonte = if (estadoAtual == EstadoRelogio.ESPERANDO_PROXIMO) 16.sp else 26.sp
    val corTextoAtual = if (estadoAtual == EstadoRelogio.VIBRANDO) CorTextoDestaque else CorTextoNormal

    Box(
        modifier = Modifier.fillMaxSize().clickable { onClicarCentro() },
        contentAlignment = Alignment.Center
    ) {
        RelogioVisual(tempoTotal, tempoAtual, estadoAtual)

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = textoCentral, fontFamily = fonte, fontSize = tamanhoFonte, textAlign = TextAlign.Center, color = corTextoAtual)

            if (estadoAtual == EstadoRelogio.PAUSADO) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "Toque na tela\npara retomar", fontFamily = fonte, fontSize = 12.sp, textAlign = TextAlign.Center, color = CorTextoSecundario)
            }

            if (estadoAtual == EstadoRelogio.VIBRANDO) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = "Toque para\nparar de vibrar", fontFamily = fonte, fontSize = 12.sp, textAlign = TextAlign.Center, color = CorTextoSecundario)
            }
        }

        // --- BOTÃO DE CONFIGURAÇÃO (Só no início) ---
        if (estadoAtual == EstadoRelogio.PARADO_INICIO) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp)
                    .size(40.dp)
                    .clickable { onAbrirConfig() },
                contentAlignment = Alignment.Center
            ) {
                Text(text = "⚙️", fontSize = 24.sp)
            }
        }

        // --- BOTÃO DE RESET (Só aparece quando PAUSADO) ---
        if (estadoAtual == EstadoRelogio.PAUSADO) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp)
                    .size(40.dp)
                    .background(Color.DarkGray, CircleShape) // Fundo redondinho para destacar
                    .clickable { onResetar() }, // Esse clique chama o RESET, não o centro
                contentAlignment = Alignment.Center
            ) {
                // Ícone de "Stop" (Quadrado) ou "Reset"
                Text(text = "⏹", fontSize = 18.sp, color = Color.White)
            }
        }
    }
}

@Composable
fun TelaConfiguracao(
    fonte: FontFamily,
    tempoAtualSegundos: Float,
    onSalvar: (Float) -> Unit
) {
    var minutos by remember { mutableFloatStateOf(tempoAtualSegundos / 60) }

    Column(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Tempo de Foco", fontFamily = fonte, color = Color.Gray, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Button(
                onClick = { if (minutos > 5) minutos -= 5 },
                colors = ButtonDefaults.buttonColors(backgroundColor = Color.DarkGray),
                modifier = Modifier.size(40.dp)
            ) {
                Text("-", color = Color.White, fontSize = 20.sp)
            }

            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = "${minutos.toInt()} min",
                fontFamily = fonte,
                color = Color(0xFFFFB7B2),
                fontSize = 24.sp
            )

            Spacer(modifier = Modifier.width(10.dp))

            Button(
                onClick = { if (minutos < 60) minutos += 5 },
                colors = ButtonDefaults.buttonColors(backgroundColor = Color.DarkGray),
                modifier = Modifier.size(40.dp)
            ) {
                Text("+", color = Color.White, fontSize = 20.sp)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = { onSalvar(minutos * 60) },
            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF4CAF50)),
            modifier = Modifier.height(40.dp)
        ) {
            Text("OK", color = Color.White, fontFamily = fonte)
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

        // --- MOSTRA PONTEIRO SE ESTIVER RODANDO OU PAUSADO ---
        if (estado == EstadoRelogio.RODANDO || estado == EstadoRelogio.PAUSADO) {
            val segundosPassados = (60 - (atual % 60)) % 60
            val anguloSegundos = (segundosPassados * 6) - 90
            val radSegundos = Math.toRadians(anguloSegundos.toDouble())
            val pontaSegX = centro.x + (raio - 15) * cos(radSegundos).toFloat()
            val pontaSegY = centro.y + (raio - 15) * sin(radSegundos).toFloat()

            // Se estiver pausado, deixa o ponteiro um pouco mais transparente
            val alphaPonteiro = if (estado == EstadoRelogio.PAUSADO) 0.1f else 0.3f

            drawLine(color = Color(0xFF888888), start = centro, end = Offset(pontaSegX, pontaSegY), strokeWidth = 2f, alpha = alphaPonteiro)
        }

        // --- TOMATE (Mantém a posição se estiver pausado) ---
        // Agora calcula progresso se for RODANDO OU PAUSADO
        val progresso = if (estado == EstadoRelogio.RODANDO || estado == EstadoRelogio.PAUSADO) (1 - (atual / total)) else 0f

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