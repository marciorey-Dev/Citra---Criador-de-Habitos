package com.example.ui.screens.organization.notes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.RecentExternalDocEntry
import com.example.data.model.Note
import com.example.data.model.NoteDocumentType
import com.example.util.DeviceDocumentScanner
import com.example.util.DiscoveredExternalDocument

/**
 * 2-column visual Device Document Explorer for PDF and DOCX files.
 *
 * Single tap opens the file immediately in transient read-only mode without creating a vault link.
 * Users can search, browse page-1 previews (red PDF / blue DOCX sheets), link custom SAF folders,
 * open the system multi-picker, or multi-select documents to save into the Citra vault.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DocumentExplorerDialog(
    docType: NoteDocumentType,
    recentDocs: List<RecentExternalDocEntry>,
    authorizedTreeUris: Set<String>,
    existingNotes: List<Note>,
    onImportFromDevice: () -> Unit,
    onLinkFolder: () -> Unit,
    onOpenTransient: (DiscoveredExternalDocument) -> Unit,
    onSaveSelectedToVault: (List<DiscoveredExternalDocument>) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var search by remember { mutableStateOf("") }
    var selectedUris by remember { mutableStateOf<Set<String>>(emptySet()) }
    var discoveredDocs by remember { mutableStateOf<List<DiscoveredExternalDocument>>(emptyList()) }
    var isScanning by remember { mutableStateOf(true) }

    val isPdf = docType == NoteDocumentType.PDF
    val accentColor = if (isPdf) PdfSheetAccentColor else DocxSheetAccentColor
    val titleText = if (isPdf) "Explorar PDFs" else "Explorar Word (.docx)"

    LaunchedEffect(docType, recentDocs, authorizedTreeUris, existingNotes) {
        isScanning = true
        discoveredDocs = DeviceDocumentScanner.scanDocuments(
            context = context,
            targetType = docType,
            recentDocs = recentDocs,
            authorizedTreeUris = authorizedTreeUris,
            existingNotes = existingNotes
        )
        isScanning = false
    }

    val filteredDocs = remember(discoveredDocs, search) {
        val q = search.trim()
        if (q.isBlank()) discoveredDocs else {
            discoveredDocs.filter {
                it.title.contains(q, ignoreCase = true) ||
                    it.sourceLabel.contains(q, ignoreCase = true)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.90f)
                .padding(horizontal = 12.dp, vertical = 14.dp)
                .testTag("recent_external_docs_picker_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(accentColor.copy(alpha = 0.14f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPdf) Icons.Default.PictureAsPdf else Icons.Default.Article,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = titleText,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Toque para ler • Segure para guardar no cofre",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar")
                    }
                }

                // System Picker & SAF Folder Link row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onImportFromDevice,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accentColor,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("recent_picker_import_device_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isPdf) "Escolher PDFs" else "Escolher .docx",
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    OutlinedButton(
                        onClick = onLinkFolder,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("explorer_link_folder_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CreateNewFolder,
                            contentDescription = "Vincular pasta do aparelho",
                            tint = accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Pasta",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Search Bar
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = {
                        Text(
                            text = if (isPdf) "Pesquisar PDFs no aparelho..." else "Pesquisar documentos .docx..."
                        )
                    },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = if (search.isNotBlank()) {
                        {
                            IconButton(onClick = { search = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Limpar busca")
                            }
                        }
                    } else null,
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("recent_picker_search_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (isScanning) "Procurando documentos..." else "Encontrados (${filteredDocs.size})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (selectedUris.isNotEmpty()) {
                        Text(
                            text = "${selectedUris.size} selecionado(s)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = accentColor
                        )
                    }
                }

                if (isScanning) {
                    LinearProgressIndicator(
                        color = accentColor,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (!isScanning && filteredDocs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(
                                imageVector = if (isPdf) Icons.Default.PictureAsPdf else Icons.Default.Article,
                                contentDescription = null,
                                tint = accentColor.copy(alpha = 0.4f),
                                modifier = Modifier.size(44.dp)
                            )
                            Text(
                                text = if (search.isNotBlank()) {
                                    "Nenhum documento encontrado para \"$search\"."
                                } else {
                                    "Nenhum arquivo encontrado automaticamente.\nToque em 'Escolher' ou 'Pasta' para acessar mais diretórios."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .testTag("document_explorer_grid"),
                        contentPadding = PaddingValues(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredDocs, key = { it.uri }) { entry ->
                            val isSelected = entry.uri in selectedUris
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (isSelected) {
                                    accentColor.copy(alpha = 0.14f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerLow
                                },
                                border = BorderStroke(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) accentColor else accentColor.copy(alpha = 0.25f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .combinedClickable(
                                        onClick = {
                                            if (selectedUris.isNotEmpty()) {
                                                selectedUris = if (isSelected) {
                                                    selectedUris - entry.uri
                                                } else {
                                                    selectedUris + entry.uri
                                                }
                                            } else {
                                                onOpenTransient(entry)
                                            }
                                        },
                                        onLongClick = {
                                            selectedUris = if (isSelected) {
                                                selectedUris - entry.uri
                                            } else {
                                                selectedUris + entry.uri
                                            }
                                        }
                                    )
                                    .testTag("explorer_doc_card_${entry.uri.hashCode()}")
                            ) {
                                Column(
                                    modifier = Modifier.padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(0.82f)
                                    ) {
                                        if (isPdf) {
                                            PdfPageOnePreviewBox(
                                                externalUri = entry.uri,
                                                compact = false,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            DocxPageOnePreviewBox(
                                                externalUri = entry.uri,
                                                compact = false,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }

                                        if (entry.isInVault) {
                                            Surface(
                                                shape = RoundedCornerShape(bottomStart = 8.dp),
                                                color = Color(0xFF16A34A),
                                                modifier = Modifier.align(Alignment.TopEnd)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.CheckCircle,
                                                        contentDescription = "No cofre",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(10.dp)
                                                    )
                                                    Text(
                                                        text = "Cofre",
                                                        fontSize = 8.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White
                                                    )
                                                }
                                            }
                                        }

                                        if (selectedUris.isNotEmpty() || isSelected) {
                                            Checkbox(
                                                checked = isSelected,
                                                onCheckedChange = { checked ->
                                                    selectedUris = if (checked) {
                                                        selectedUris + entry.uri
                                                    } else {
                                                        selectedUris - entry.uri
                                                    }
                                                },
                                                colors = CheckboxDefaults.colors(checkedColor = accentColor),
                                                modifier = Modifier
                                                    .align(Alignment.BottomEnd)
                                                    .background(
                                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                                        CircleShape
                                                    )
                                            )
                                        }
                                    }

                                    Text(
                                        text = entry.title.ifBlank { "Documento" },
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Text(
                                        text = entry.sourceLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                if (selectedUris.isNotEmpty()) {
                    val toSave = filteredDocs.filter { it.uri in selectedUris }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { selectedUris = emptySet() },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("Limpar")
                        }
                        Button(
                            onClick = { onSaveSelectedToVault(toSave) },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accentColor,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("recent_picker_load_selected_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.LibraryAdd,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Guardar no Cofre (${toSave.size})",
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
