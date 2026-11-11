package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import org.json.JSONObject

@Composable
fun BackupDialog(
    onExportBackup: () -> String,
    onRestoreBackup: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isRestoreMode by remember { mutableStateOf(false) }
    var jsonInput by remember { mutableStateOf("") }
    var showCloudDialogFromBackup by remember { mutableStateOf(false) }
    var showErrorDialog by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val content = context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader(Charsets.UTF_8).readText()
                }
                if (content != null && isValidCitraBackup(content)) {
                    onRestoreBackup(content)
                    Toast.makeText(context, "Backup restaurado com sucesso!", Toast.LENGTH_SHORT).show()
                    onDismiss()
                } else {
                    showErrorDialog = true
                }
            } catch (_: Exception) {
                showErrorDialog = true
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (isRestoreMode) "Restaurar Backup Local" else "Backup Local do App",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!isRestoreMode) {
                    Text(
                        text = "Exporte todos os seus dados (Tarefas, Hábitos, Sessões Arancia, Categorias e Conquistas) em um arquivo JSON.",
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

                    // Button 1: Carregar Arquivo de Backup do Armazenamento (.txt / .json)
                    Button(
                        onClick = {
                            filePickerLauncher.launch(arrayOf("text/plain", "application/json", "*/*"))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Carregar Arquivo de Backup (.txt / .json)")
                    }

                    // Button 2: Exportar e Compartilhar
                    OutlinedButton(
                        onClick = {
                            val json = onExportBackup()
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/json"
                                putExtra(Intent.EXTRA_SUBJECT, "Citra_Backup.json")
                                putExtra(Intent.EXTRA_TEXT, json)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Compartilhar / Salvar Backup JSON"))
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Exportar e Compartilhar Backup")
                    }

                    // Button 3: Copiar JSON
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

                    // Button 4: Colar JSON manualmente
                    OutlinedButton(
                        onClick = { isRestoreMode = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Colar Texto JSON Manualmente")
                    }

                    // Button 5: Ver Dados na Nuvem
                    OutlinedButton(
                        onClick = { showCloudDialogFromBackup = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CloudQueue, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Ver Dados Salvos na Nuvem")
                    }
                } else {
                    Text(
                        text = "Você pode abrir um arquivo de backup do seu armazenamento ou colar o conteúdo JSON abaixo:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = {
                            filePickerLauncher.launch(arrayOf("text/plain", "application/json", "*/*"))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Procurar Arquivo (.txt / .json)")
                    }

                    OutlinedTextField(
                        value = jsonInput,
                        onValueChange = { jsonInput = it },
                        placeholder = { Text("{\"version\": 1, \"tasks\": ...}") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        },
        confirmButton = {
            if (isRestoreMode) {
                Button(
                    onClick = {
                        if (isValidCitraBackup(jsonInput)) {
                            onRestoreBackup(jsonInput)
                            onDismiss()
                        } else {
                            showErrorDialog = true
                        }
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

    if (showErrorDialog) {
        AlertDialog(
            onDismissRequest = { showErrorDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = RedAction,
                        modifier = Modifier.size(24.dp)
                    )
                    Text("Backup Não Reconhecido", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(
                    text = "Os dados do arquivo selecionado não foram reconhecidos pelo Citra. Certifique-se de escolher um arquivo .txt ou .json válido gerado pelo backup do aplicativo.",
                    fontSize = 13.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { showErrorDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = RedAction)
                ) {
                    Text("Entendi")
                }
            }
        )
    }

    if (showCloudDialogFromBackup) {
        val authRepo = remember { com.example.data.auth.AuthRepository.getInstance(context) }
        CloudSyncedDataDialog(
            authRepository = authRepo,
            onDismiss = { showCloudDialogFromBackup = false }
        )
    }
}

/**
 * Validates whether the given string represents a valid Citra backup format.
 */
private fun isValidCitraBackup(content: String): Boolean {
    val trimmed = content.trim()
    if (trimmed.isBlank() || (!trimmed.startsWith("{") && !trimmed.startsWith("["))) {
        return false
    }
    return try {
        if (trimmed.startsWith("{")) {
            val root = JSONObject(trimmed)
            root.has("version") || root.has("tasks") || root.has("habits") ||
                root.has("notes") || root.has("focusSessions") || root.has("categories") ||
                root.has("financeEntries")
        } else {
            // Might be an array of notes or entities
            val arr = org.json.JSONArray(trimmed)
            arr.length() > 0
        }
    } catch (_: Exception) {
        false
    }
}
