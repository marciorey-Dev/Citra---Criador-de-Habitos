package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.Achievement
import com.example.ui.theme.GreenAccent
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AchievementsDialog(
    achievements: List<Achievement>,
    onDismiss: () -> Unit
) {
    val totalCount = achievements.size.coerceAtLeast(1)
    val unlockedCount = achievements.count { it.isUnlocked }
    val remainingCount = (achievements.size - unlockedCount).coerceAtLeast(0)
    val pct = (unlockedCount * 100) / totalCount

    val pomarLevel = when {
        pct >= 100 -> "Mestre"
        pct >= 75 -> "Ouro"
        pct >= 50 -> "Prata"
        pct >= 25 -> "Bronze"
        pct >= 10 -> "Broto"
        else -> "Semente"
    }

    val categories = remember(achievements) {
        listOf("Todas") + achievements.map { it.category }.distinct()
    }
    var selectedCategory by remember { mutableStateOf("Todas") }

    val filteredAchievements = remember(achievements, selectedCategory) {
        if (selectedCategory == "Todas") achievements
        else achievements.filter { it.category == selectedCategory }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.background,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                // Top Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .clickable { onDismiss() }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Voltar",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Conquistas",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Text(
                            text = "$unlockedCount/${achievements.size}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 12.dp)
                ) {
                    // 1. Hero Trophy Summary Card
                    item {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Circular Trophy Icon with Lightning Badge
                                    Box(modifier = Modifier.size(54.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .size(50.dp)
                                                .align(Alignment.Center)
                                                .background(
                                                    MaterialTheme.colorScheme.primaryContainer,
                                                    CircleShape
                                                )
                                                .border(
                                                    1.dp,
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                                    CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.EmojiEvents,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(26.dp)
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .align(Alignment.BottomEnd)
                                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                                                .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Bolt,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "Conquistas",
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .size(7.dp)
                                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                                            )
                                        }
                                        Text(
                                            text = "$unlockedCount de ${achievements.size} desbloqueadas",
                                            fontSize = 12.5.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainer,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.StarOutline,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Column {
                                                Text(
                                                    text = "$pct%",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    lineHeight = 13.sp
                                                )
                                                Text(
                                                    text = "Completo",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    lineHeight = 11.sp
                                                )
                                            }
                                        }
                                    }
                                }

                                LinearProgressIndicator(
                                    progress = { unlockedCount.toFloat() / totalCount.toFloat() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Nível Pomar $pomarLevel",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (remainingCount > 0) "$remainingCount para o próximo troféu" else "Pomar Completo!",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                // Motivational Banner
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .background(
                                                    MaterialTheme.colorScheme.primaryContainer,
                                                    CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Park,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = "Continue no seu ritmo solar! Cada pausa consciente e tarefa focada nutre seu pomar pessoal.",
                                            fontSize = 12.sp,
                                            lineHeight = 16.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 2. Category Filter Pills
                    item {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                        ) {
                            items(categories) { cat ->
                                val isSelected = selectedCategory == cat
                                val count = if (cat == "Todas") achievements.size else achievements.count { it.category == cat }
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                    border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .clickable { selectedCategory = cat }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = cat,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    color = if (isSelected) {
                                                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.22f)
                                                    } else {
                                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                                    },
                                                    shape = CircleShape
                                                )
                                                .padding(horizontal = 7.dp, vertical = 1.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = count.toString(),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 3. Achievement Cards
                    items(filteredAchievements, key = { it.id }) { item ->
                        AchievementItemCard(item)
                    }

                    // 4. Bottom Remaining Medals Pill
                    if (remainingCount > 0) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Eco,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = "Mais $remainingCount medalhas esperando por você",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AchievementItemCard(item: Achievement) {
    val dateText = item.unlockedAt?.let {
        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(it))
    }

    val borderColor = if (item.isUnlocked) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("achievement_${item.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Circular Icon with optional Lock badge
            Box(modifier = Modifier.size(50.dp)) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .align(Alignment.Center)
                        .background(
                            color = if (item.isUnlocked) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            shape = CircleShape
                        )
                        .border(
                            1.dp,
                            if (item.isUnlocked) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                            else MaterialTheme.colorScheme.outlineVariant,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = item.iconEmoji,
                        fontSize = 22.sp,
                        color = if (item.isUnlocked) Color.Unspecified else Color.Unspecified.copy(alpha = 0.55f)
                    )
                }

                if (!item.isUnlocked) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .align(Alignment.BottomEnd)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape)
                            .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Bloqueado",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(10.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (item.isUnlocked) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Text(
                            text = item.category,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = item.description,
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))

                if (item.isUnlocked && dateText != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircleOutline,
                            contentDescription = null,
                            tint = GreenAccent,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Conquistado em $dateText",
                            fontSize = 11.5.sp,
                            color = GreenAccent,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else if (!item.isUnlocked && item.maxProgress > 1) {
                    val progPct = ((item.currentProgress * 100) / item.maxProgress.coerceAtLeast(1)).coerceIn(0, 100)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = { (item.currentProgress.toFloat() / item.maxProgress.toFloat()).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                        Text(
                            text = "${item.currentProgress}/${item.maxProgress} ($progPct%)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (!item.isUnlocked) {
                    Text(
                        text = "Bloqueado",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
