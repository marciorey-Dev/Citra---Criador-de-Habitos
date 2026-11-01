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
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.R
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
                Image(
                    painter = painterResource(id = R.drawable.citra_logo),
                    contentDescription = "Logo Citra",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(18.dp))
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Citra Versão 0.10.3",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "Novo Design de Ícone com Bordas Arredondadas, Sons Aprimorados & Barra Flutuante",
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
                        icon = "📄",
                        title = "Exportação Word (.docx) com Markdown Completo",
                        description = "Exporte suas anotações para o formato Microsoft Word (.docx) preservando títulos, listas, tabelas, caixas de destaque, códigos e imagens incorporadas."
                    )
                    FeatureHighlightItem(
                        icon = "🎨",
                        title = "Fidelidade Cromática ao Tema do App",
                        description = "Em qualquer formato de exportação (PDF, DOCX, HTML ou Imagem), os elementos, bordas, cabeçalhos e divisores respeitam a cor do tema ativo no aplicativo."
                    )
                    FeatureHighlightItem(
                        icon = "🖼️",
                        title = "Imagens Preservadas e Convertidas na Exportação",
                        description = "Imagens anexadas às suas notas agora são convertidas e embutidas como parte integral do ficheiro em PDF, DOCX, HTML (base64) e Imagem."
                    )
                    FeatureHighlightItem(
                        icon = "🎵",
                        title = "HTML Avançado com Player de Áudio Arte Interna",
                        description = "Ao exportar em HTML, gravações de áudio são convertidas em arte multimídia interativa interna com player responsivo completo e visualizador embutido."
                    )
                    FeatureHighlightItem(
                        icon = "🏷️",
                        title = "Categorias Logo Após o Título",
                        description = "A seleção de categoria foi movida para logo abaixo do título da nota, em local direto, acessível e sem obstrução da barra de formatação."
                    )
                    FeatureHighlightItem(
                        icon = "💾",
                        title = "Salvamento Fluido sem Fechar & Auto-Save",
                        description = "O botão de salvar salva a nota instantaneamente sem fechar o editor. E sair do app ou alternar entre janelas salva a anotação automaticamente."
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
