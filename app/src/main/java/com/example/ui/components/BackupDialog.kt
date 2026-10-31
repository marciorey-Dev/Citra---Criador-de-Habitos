package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.OrangePrimary
import com.example.ui.theme.RedAction

@Composable
fun BackupDialog(
    onExportBackup: () -> String,
    onRestoreBackup: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isRestoreMode by remember { mutableStateOf(false) }
    var jsonInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isRestoreMode) "Restaurar Backup Local" else "Backup Local do App", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!isRestoreMode) {
                    Text(
                        text = "Exporte todos os seus dados (Tarefas, Hábitos, Sessões Pomodoro, Categorias e Conquistas) em um arquivo JSON.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("📁", fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Onde ficam salvas as suas notas?",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "As anotações são arquivos Markdown (.md) independentes gravados em 'Documentos/Citra Docs/' no armazenamento do seu aparelho. Elas não dependem do banco de dados e nunca são perdidas.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f),
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }

                    Button(
                        onClick = {
                            val json = onExportBackup()
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/json"
                                putExtra(Intent.EXTRA_SUBJECT, "Citra_Backup.json")
                                putExtra(Intent.EXTRA_TEXT, json)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Compartilhar / Salvar Backup JSON"))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Exportar e Compartilhar Backup")
                    }

                    OutlinedButton(
                        onClick = {
                            val json = onExportBackup()
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Citra Backup", json))
                            Toast.makeText(context, "Backup copiado para a área de transferência!", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Copiar JSON do Backup")
                    }

                    OutlinedButton(
                        onClick = { isRestoreMode = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Restaurar a partir de JSON")
                    }
                } else {
                    Text(
                        text = "Cole o conteúdo do backup JSON abaixo para restaurar seus dados:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = jsonInput,
                        onValueChange = { jsonInput = it },
                        placeholder = { Text("{\"version\": 1, \"tasks\": ...}") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        },
        confirmButton = {
            if (isRestoreMode) {
                Button(
                    onClick = {
                        onRestoreBackup(jsonInput)
                        onDismiss()
                    },
                    enabled = jsonInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = RedAction)
                ) {
                    Text("Restaurar Dados")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = {
                if (isRestoreMode) isRestoreMode = false else onDismiss()
            }) {
                Text(if (isRestoreMode) "Voltar" else "Fechar")
            }
        }
    )
}
