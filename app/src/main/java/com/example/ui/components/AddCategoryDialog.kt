package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.OrangeContainer
import com.example.ui.theme.RedAction

@Composable
fun AddCategoryDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedEmoji by remember { mutableStateOf("🎯") }

    val suggestedEmojis = listOf(
        "📚", "💼", "📖", "🎨", "🏃", "🎯", "💻", "🎓",
        "🧘", "🎵", "✍️", "🧪", "🚀", "💡", "🌿", "☕"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nova Categoria", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome da Categoria") },
                    placeholder = { Text("Ex: Programação, Faculdade...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Escolha ou digite um Emoji:", fontSize = 12.sp, fontWeight = FontWeight.Bold)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .background(OrangeContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(selectedEmoji, fontSize = 22.sp)
                    }
                    OutlinedTextField(
                        value = selectedEmoji,
                        onValueChange = { if (it.length <= 4) selectedEmoji = it },
                        label = { Text("Emoji") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Grid of suggested emojis
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    suggestedEmojis.chunked(8).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            row.forEach { emoji ->
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .background(
                                            color = if (selectedEmoji == emoji) OrangeContainer else MaterialTheme.colorScheme.surfaceVariant,
                                            shape = CircleShape
                                        )
                                        .clickable { selectedEmoji = emoji },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(emoji, fontSize = 16.sp)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(name, selectedEmoji)
                    }
                },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = RedAction)
            ) {
                Text("Adicionar", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
