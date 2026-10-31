package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.GreenAccent

@Composable
fun WhatsNewDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f)
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Icon
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "🚀", fontSize = 28.sp)
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Citra Versão 0.7.9",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "Organização Otimizada, Modos de Notas, Alarmes em Loop e Gestão de Dados",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Scrollable list of highlights
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FeatureHighlightItem(
                        icon = "🗂️",
                        title = "Novos Modos de Visualização e Ordenação",
                        description = "Alterne entre Grade (2 colunas), Grade Pequena (3 colunas), Detalhes (com primeiro parágrafo) e Lista compacta. Ordene por data, categoria, tamanho ou A-Z com notas fixadas sempre no topo."
                    )
                    FeatureHighlightItem(
                        icon = "📌",
                        title = "Novo Visual para Notas Fixadas",
                        description = "Bordas marcadas na cor da categoria e ícone de pin colorido, eliminando o fundo cinza para um visual leve e elegante."
                    )
                    FeatureHighlightItem(
                        icon = "🎯",
                        title = "Auto-Centralização ao Tocar na Nota",
                        description = "Ao posicionar o cursor no Live Preview ou tocar em blocos na leitura, a tela rola suavemente para centralizar o texto focado."
                    )
                    FeatureHighlightItem(
                        icon = "⚠️",
                        title = "Alerta de Hábito Não Cumprido",
                        description = "Hábitos não realizados após o horário ficam destacados em vermelho e enviam um aviso 5 minutos depois (configurável)."
                    )
                    FeatureHighlightItem(
                        icon = "🔁",
                        title = "Toque em Loop Contínuo",
                        description = "Agora você pode ativar o alarme contínuo em tarefas ou hábitos prioritários, tocando repetidamente até ser atendido."
                    )
                    FeatureHighlightItem(
                        icon = "💾",
                        title = "Backup Local e Limpeza de Dados no Perfil",
                        description = "Crie ou restaure backups JSON diretamente na aba Dados de 'Eu'. Recursos para resetar tarefas, hábitos, notas ou todos os dados com confirmação segura."
                    )
                    FeatureHighlightItem(
                        icon = "📁",
                        title = "Transparência do Armazenamento de Notas",
                        description = "Suas notas continuam como arquivos .md puros em 'Documentos/Citra Docs/', livres de bancos de dados fechados."
                    )
                    FeatureHighlightItem(
                        icon = "📐",
                        title = "Interface Limpa e Sem Títulos Desnecessários",
                        description = "Mais espaço vertical útil no menu de Tarefas, removendo cabeçalhos redundantes e subindo os elementos."
                    )
                    FeatureHighlightItem(
                        icon = "📁",
                        title = "Músicas do seu Aparelho",
                        description = "Você pode selecionar qualquer arquivo de música (MP3, WAV) direto do seu celular para ser o toque do app dali em diante."
                    )
                    FeatureHighlightItem(
                        icon = "🔄",
                        title = "Resistência a Reinicializações (BootReceiver)",
                        description = "Se o seu aparelho descarregar ou reiniciar, o Citra restaura automaticamente todos os seus alarmes ativos com seus respectivos toques."
                    )
                    FeatureHighlightItem(
                        icon = "📊",
                        title = "Tabelas Excel Integradas e Editáveis",
                        description = "Tabelas com estilo planilha, linhas e colunas customizáveis, rolagem lateral suave e edição direta no modo leitura."
                    )
                    FeatureHighlightItem(
                        icon = "🎙️",
                        title = "Gravação de Áudio Direto na Nota",
                        description = "Grave notas de voz diretamente no editor de notas e salve automaticamente os arquivos na pasta 'CitraRecord' em Músicas."
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // GitHub developer contact card
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Repositório & Feedback no GitHub",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Acompanhe o desenvolvimento do Citra, consulte novidades ou envie sugestões no perfil do criador Márcio Rey.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    val url = "https://github.com/marciorey-dev"
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    try { context.startActivity(intent) } catch (_: Exception) {}
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Visitar GitHub: marciorey-dev", fontSize = 12.sp, color = Color.White)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Dismiss Button
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Começar a Usar!",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun FeatureHighlightItem(
    icon: String,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                RoundedCornerShape(14.dp)
            )
            .padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(text = icon, fontSize = 20.sp)
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
