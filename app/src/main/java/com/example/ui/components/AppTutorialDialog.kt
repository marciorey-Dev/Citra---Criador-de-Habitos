package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.OrangeContainer
import com.example.ui.theme.OrangePrimary
import com.example.ui.theme.RedAction

data class TutorialStep(
    val iconEmoji: String,
    val title: String,
    val description: String,
    val tip: String
)

@Composable
fun AppTutorialDialog(
    onDismiss: () -> Unit
) {
    val steps = listOf(
        TutorialStep(
            iconEmoji = "🍊",
            title = "Bem-vindo ao Citra!",
            description = "Seu novo espaço minimalista e acolhedor para gerenciar tempo, tarefas, hábitos diários e anotações.",
            tip = "Vamos fazer um rápido tour para você dominar todas as ferramentas!"
        ),
        TutorialStep(
            iconEmoji = "📋",
            title = "1. Criando e Gerenciando Tarefas",
            description = "Na aba Organização > Tarefas, toque no '+' para adicionar tarefas com prazos e prioridades (Alta, Média ou Baixa).",
            tip = "Cada tarefa tem um botão 'Play' para iniciar um Pomodoro já focado nela!"
        ),
        TutorialStep(
            iconEmoji = "✨",
            title = "2. Cultivando Hábitos Diários",
            description = "Em Hábitos, defina rotinas com horário fixo diário. Você receberá uma notificação sonora com 10 minutos de antecedência.",
            tip = "Faça o check-in diário para manter sua chama de sequência (Streak 🔥) crescendo!"
        ),
        TutorialStep(
            iconEmoji = "📝",
            title = "3. Bloco de Notas com Markdown & Lixeira",
            description = "Escreva resumos e ideias em Markdown com atalhos para negrito, listas e links. Filtre por categorias.",
            tip = "Notas apagadas vão para a Lixeira e podem ser restauradas em até 30 dias!"
        ),
        TutorialStep(
            iconEmoji = "📅",
            title = "4. Navegando pelo Calendário",
            description = "Na aba Calendário, veja quais dias possuem tarefas e notas agendadas com marcadores visuais e ícones.",
            tip = "Toque em qualquer dia para criar uma tarefa ou nota diretamente para aquela data."
        ),
        TutorialStep(
            iconEmoji = "📊",
            title = "5. Estatísticas: Semanal, Mensal & Anual",
            description = "Acompanhe o tempo de foco em gráficos de barra e no Gráfico de Pizza por categoria. Alterne entre períodos e compare semanas, meses e anos.",
            tip = "Veja seu dia mais produtivo e o histórico de cumprimento de hábitos."
        ),
        TutorialStep(
            iconEmoji = "⏱️",
            title = "6. Pomodoro Cítrico Flexível",
            description = "O temporizador se parece com uma fatia de laranja! As sementes vão sumindo como um relógio analógico à medida que o tempo passa.",
            tip = "Ajuste o foco de 15 a 120 min, a pausa de 5 a 25 min, e defina quantos ciclos deseja repetir."
        ),
        TutorialStep(
            iconEmoji = "🎵",
            title = "7. Playlist com Suas Músicas Locais",
            description = "Monte sua playlist adicionando faixas de áudio direto do seu aparelho para ouvir durante os estudos.",
            tip = "Controle o volume e as faixas sem sair do aplicativo."
        ),
        TutorialStep(
            iconEmoji = "🏆",
            title = "8. Conquistas, Notificações & Backup",
            description = "No menu superior de três pontos (⋮), acerte os sons de notificação, veja suas conquistas desbloqueadas e faça backup local em JSON.",
            tip = "Você está pronto para ser mais produtivo com Citra!"
        )
    )

    var currentStepIndex by remember { mutableStateOf(0) }
    val step = steps[currentStepIndex]

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Step Indicator & Skip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Passo ${currentStepIndex + 1} de ${steps.size}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = OrangePrimary
                    )

                    TextButton(onClick = onDismiss) {
                        Text("Pular Tutorial", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Icon Box
                Box(
                    modifier = Modifier
                        .size(70.dp)
                        .background(OrangeContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = step.iconEmoji, fontSize = 36.sp)
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = step.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = step.description,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lightbulb,
                            contentDescription = null,
                            tint = OrangePrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = step.tip,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Navigation Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentStepIndex > 0) {
                        OutlinedButton(
                            onClick = { currentStepIndex-- },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Voltar")
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    Button(
                        onClick = {
                            if (currentStepIndex < steps.size - 1) {
                                currentStepIndex++
                            } else {
                                onDismiss()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RedAction),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (currentStepIndex == steps.size - 1) "Começar a Usar!" else "Próximo",
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
