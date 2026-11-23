package com.example.ui.screens.finances

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.FinanceCurrency
import com.example.data.model.FinanceEntry
import com.example.data.model.formatFinanceAmount
import com.example.ui.theme.GreenAccent

@Composable
fun FinanceToolsTab(
    walletsCount: Int,
    plannedCount: Int,
    budgetsCount: Int,
    activeRecurringCount: Int,
    noteLinksCount: Int,
    exchangeRatesCount: Int,
    activeCurrency: FinanceCurrency,
    onOpenWallets: () -> Unit,
    onOpenPlannedList: () -> Unit,
    onOpenBudgets: () -> Unit,
    onOpenRecurring: () -> Unit,
    onOpenNoteLinksManager: () -> Unit,
    onOpenExchangeRates: () -> Unit,
    onOpenFinanceSettings: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("finances_tools_tab"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
                Text(
                    text = "Recursos Avançados",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Ferramentas de controle separadas do seu resumo diário.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            FinanceToolCard(
                icon = Icons.Default.AccountBalanceWallet,
                accentColor = MaterialTheme.colorScheme.primary,
                title = "Carteiras & Contas",
                subtitle = "Saldos por conta (dinheiro, banco, móvel) e transferências",
                counterBadge = if (walletsCount == 0) "Sem carteiras" else "$walletsCount carteira(s)",
                testTag = "tool_card_wallets",
                onClick = onOpenWallets
            )
        }

        item {
            FinanceToolCard(
                icon = Icons.Default.Checklist,
                accentColor = GreenAccent,
                title = "Planeados & Lista de Compras",
                subtitle = "Itens previstos para marcar como pagos sem sujar o histórico",
                counterBadge = if (plannedCount == 0) "Lista vazia" else "$plannedCount pendente(s)",
                testTag = "tool_card_planned",
                onClick = onOpenPlannedList
            )
        }

        item {
            FinanceToolCard(
                icon = Icons.Default.PieChart,
                accentColor = Color(0xFF9C27B0),
                title = "Orçamentos por Categoria",
                subtitle = "Defina tetos mensais com barras de progresso e alertas",
                counterBadge = if (budgetsCount == 0) "Sem limites" else "$budgetsCount orçamento(s)",
                testTag = "tool_card_budgets",
                onClick = onOpenBudgets
            )
        }

        item {
            FinanceToolCard(
                icon = Icons.Default.Repeat,
                accentColor = Color(0xFF2196F3),
                title = "Lançamentos Recorrentes",
                subtitle = "Contas fixas e salários automáticos com lembretes",
                counterBadge = if (activeRecurringCount == 0) "Nenhuma ativa" else "$activeRecurringCount ativa(s)",
                testTag = "tool_card_recurring",
                onClick = onOpenRecurring
            )
        }

        item {
            FinanceToolCard(
                icon = Icons.Default.Link,
                accentColor = Color(0xFF009688),
                title = "Vínculos com Notas (Citra Docs)",
                subtitle = "Gere tabelas e relatórios automáticos dentro das suas notas",
                counterBadge = if (noteLinksCount == 0) "Nenhum vínculo" else "$noteLinksCount vínculo(s)",
                testTag = "tool_card_note_links",
                onClick = onOpenNoteLinksManager
            )
        }

        item {
            FinanceToolCard(
                icon = Icons.Default.CurrencyExchange,
                accentColor = Color(0xFFFFB300),
                title = "Câmbio & Moedas",
                subtitle = "Taxas de conversão manuais e moeda padrão (${activeCurrency.code})",
                counterBadge = if (exchangeRatesCount == 0) "Só padrão" else "$exchangeRatesCount taxa(s)",
                testTag = "tool_card_exchange",
                onClick = onOpenExchangeRates
            )
        }

        item {
            FinanceToolCard(
                icon = Icons.Default.Settings,
                accentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                title = "Configurações & Bloqueio por PIN",
                subtitle = "Ajuste moeda padrão, PIN de acesso e limpeza de dados",
                counterBadge = "Preferências",
                testTag = "tool_card_settings",
                onClick = onOpenFinanceSettings
            )
        }
    }
}

@Composable
private fun FinanceToolCard(
    icon: ImageVector,
    accentColor: Color,
    title: String,
    subtitle: String,
    counterBadge: String,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(accentColor.copy(alpha = 0.14f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = title,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = accentColor.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = counterBadge,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun FinancePlannedListDialog(
    plannedEntries: List<FinanceEntry>,
    hideValues: Boolean,
    onDismiss: () -> Unit,
    onAddPlannedItem: () -> Unit,
    onEditEntry: (FinanceEntry) -> Unit,
    onMarkAsPaid: (FinanceEntry) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.80f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Planeados & Lista de Compras",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Marque como ✔ Pago para lançar no saldo real.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar")
                    }
                }

                if (plannedEntries.isEmpty()) {
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
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .background(MaterialTheme.colorScheme.surfaceContainer, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Checklist,
                                    contentDescription = null,
                                    tint = GreenAccent,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Text(
                                text = "Nenhum item planeado",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "Adicione compras ou gastos futuros para controlar antes de pagar.",
                                fontSize = 12.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(plannedEntries, key = { it.id }) { pEntry ->
                            val curr = FinanceCurrency.fromCode(pEntry.currencyCode)
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { onEditEntry(pEntry) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(pEntry.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text(
                                            "Previsto: ${formatFinanceAmount(pEntry.totalAmountMinor, curr, hideValues)}",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    FilledTonalButton(
                                        onClick = { onMarkAsPaid(pEntry) },
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier.heightIn(min = 44.dp)
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Pago", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = onAddPlannedItem,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Adicionar item planeado", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
