package com.example.ui.screens.welcome

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.AlertStyleChoice
import com.example.data.local.FinanceStyleChoice
import com.example.data.local.FocusStyleChoice
import com.example.data.local.GuidanceStyleChoice
import com.example.data.local.MainGoalChoice
import com.example.data.local.NotesStyleChoice
import com.example.data.local.OnboardingProfileAnswers
import com.example.data.local.ProfileDefaultsResolver
import com.example.data.local.ThemeBrightnessChoice
import com.example.data.local.VisualMoodChoice
import com.example.data.model.FinanceCurrency
import com.example.ui.components.CitraMascote
import com.example.ui.components.MascotHumor
import com.example.ui.screens.finances.SearchableCurrencyPickerDialog
import com.example.ui.theme.ThemeAccent

private const val TOTAL_QUESTION_STEPS = 6

data class MascotReactionFeedback(
    val bubbleText: String,
    val humor: MascotHumor,
    val jump: Boolean = false
)

@Composable
fun ProfileQuestionnaireCard(
    displayName: String,
    answers: OnboardingProfileAnswers,
    currentStep: Int, // 1..6 for questions, 7 for final summary
    onAnswersChange: (OnboardingProfileAnswers) -> Unit,
    onStepChange: (Int) -> Unit,
    onBackFromFirstStep: () -> Unit,
    onSkipToDefault: () -> Unit,
    onFinishQuiz: (OnboardingProfileAnswers) -> Unit,
    onMascotReaction: (MascotReactionFeedback) -> Unit,
    finishButtonLabel: String = "Entrar no meu Citra",
    showSkipButton: Boolean = true
) {
    var showCurrencyPicker by remember { mutableStateOf(false) }

    BackHandler(enabled = currentStep >= 1) {
        if (currentStep > 1) {
            onStepChange(currentStep - 1)
        } else {
            onBackFromFirstStep()
        }
    }

    LaunchedEffect(currentStep) {
        val defaultReaction = when (currentStep) {
            1 -> MascotReactionFeedback("Me conta o que é mais importante pra você agora!", MascotHumor.FELIZ)
            2 -> MascotReactionFeedback("Como prefere receber seus avisos e alarmes?", MascotHumor.NEUTRO)
            3 -> MascotReactionFeedback("Qual ritmo de concentração funciona melhor?", MascotHumor.FELIZ)
            4 -> MascotReactionFeedback("Quer acompanhar seus gastos por aqui também?", MascotHumor.NEUTRO)
            5 -> MascotReactionFeedback("Como você lida mais com anotações, PDFs e Word?", MascotHumor.DISCRETO)
            6 -> MascotReactionFeedback("Escolha as cores e se quer dicas nas telas!", MascotHumor.FELIZ)
            else -> MascotReactionFeedback(
                "Tudo pronto, ${displayName.ifBlank { "amigo" }}! Montei o Citra pra você 🍊",
                MascotHumor.FELIZ,
                jump = true
            )
        }
        onMascotReaction(defaultReaction)
    }

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 10.dp,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            // Top Progress Bar & Skip
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (currentStep <= TOTAL_QUESTION_STEPS) {
                        "Passo $currentStep de $TOTAL_QUESTION_STEPS"
                    } else {
                        "Seu perfil pronto"
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                if (showSkipButton && currentStep <= TOTAL_QUESTION_STEPS) {
                    TextButton(
                        onClick = onSkipToDefault,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("quiz_skip_button")
                    ) {
                        Text(
                            text = "Pular",
                            fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 6 Segmented Progress Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (step in 1..TOTAL_QUESTION_STEPS) {
                    val active = currentStep >= step
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(
                                if (active) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    fadeIn(tween(170)) togetherWith fadeOut(tween(130))
                },
                label = "quiz_step_content"
            ) { step ->
                when (step) {
                    1 -> Step1MainGoal(
                        selected = answers.mainGoal,
                        onSelect = { choice ->
                            onAnswersChange(answers.copy(mainGoal = choice))
                            val bubble = when (choice) {
                                MainGoalChoice.FOCUS_STUDY -> "Boa! Vamos abrir direto no foco 🍅"
                                MainGoalChoice.TASKS_HABITS -> "Perfeito, suas tarefas vão estar sempre à mão!"
                                MainGoalChoice.NOTES_DOCS -> "Ótimo! Seu caderno e leitores prontos logo de cara 📄"
                                MainGoalChoice.CALENDAR -> "Combinado! Compromissos sempre em destaque 📅"
                                MainGoalChoice.OVERVIEW -> "Visão equilibrada para acompanhar tudo no dia!"
                            }
                            onMascotReaction(MascotReactionFeedback(bubble, MascotHumor.FELIZ, jump = true))
                        }
                    )

                    2 -> Step2AlertStyle(
                        selected = answers.alertStyle,
                        onSelect = { choice ->
                            onAnswersChange(answers.copy(alertStyle = choice))
                            val reaction = when (choice) {
                                AlertStyleChoice.DISCREET -> MascotReactionFeedback(
                                    "Combinado! Avisos gentis sem atrapalhar seu celular 🔕",
                                    MascotHumor.DISCRETO
                                )
                                AlertStyleChoice.NORMAL_ALARM -> MascotReactionFeedback(
                                    "Legal! Alarmes clássicos até você dar uma olhada ⏰",
                                    MascotHumor.FELIZ
                                )
                                AlertStyleChoice.DISRUPTIVE -> MascotReactionFeedback(
                                    "Modo firme ativado! Não vamos deixar nada passar 🚨",
                                    MascotHumor.SURPRESO,
                                    jump = true
                                )
                            }
                            onMascotReaction(reaction)
                        }
                    )

                    3 -> Step3FocusStyle(
                        selected = answers.focusStyle,
                        onSelect = { choice ->
                            onAnswersChange(answers.copy(focusStyle = choice))
                            val reaction = when (choice) {
                                FocusStyleChoice.LIGHT_SPRINTS -> MascotReactionFeedback(
                                    "15 minutinhos leves são ótimos para pegar ritmo!",
                                    MascotHumor.FELIZ
                                )
                                FocusStyleChoice.CLASSIC_FLOW -> MascotReactionFeedback(
                                    "25 min contínuos com música calma: clássico infalível 🎵",
                                    MascotHumor.FELIZ,
                                    jump = true
                                )
                                FocusStyleChoice.DEEP_IMMERSION -> MascotReactionFeedback(
                                    "45 min blindado contra distrações! Foco máximo 🛡️",
                                    MascotHumor.SURPRESO
                                )
                            }
                            onMascotReaction(reaction)
                        }
                    )

                    4 -> Step4Finances(
                        selected = answers.financeStyle,
                        hideValues = answers.financeHideValues,
                        currencyCode = answers.financeCurrencyCode,
                        onSelectStyle = { choice ->
                            onAnswersChange(answers.copy(financeStyle = choice))
                            val bubble = if (choice == FinanceStyleChoice.ENABLED) {
                                "Show! Sua carteira e metas financeiras já vão estar ativas 💰"
                            } else {
                                "Certo! Deixo a aba limpa só com produtividade 👍"
                            }
                            onMascotReaction(MascotReactionFeedback(bubble, MascotHumor.FELIZ))
                        },
                        onToggleHideValues = { hide ->
                            onAnswersChange(answers.copy(financeHideValues = hide))
                        },
                        onSelectCurrency = { code ->
                            onAnswersChange(answers.copy(financeCurrencyCode = code))
                        },
                        onOpenAllCurrencies = { showCurrencyPicker = true }
                    )

                    5 -> Step5NotesStyle(
                        selected = answers.notesStyle,
                        onSelect = { choice ->
                            onAnswersChange(answers.copy(notesStyle = choice))
                            val bubble = when (choice) {
                                NotesStyleChoice.READ_FOLDERS -> "Notas e PDFs abrem limpos para leitura sem poluição visual 📖"
                                NotesStyleChoice.EDIT_CARDS -> "Abriu a nota, já sai escrevendo! Simples e direto ✍️"
                                NotesStyleChoice.COMPACT_LIST -> "Tudo organizado em lista enxuta para achar rápido!"
                            }
                            onMascotReaction(MascotReactionFeedback(bubble, MascotHumor.FELIZ))
                        }
                    )

                    6 -> Step6VisualAndGuidance(
                        answers = answers,
                        onUpdate = { updated ->
                            onAnswersChange(updated)
                            val bubble = if (updated.visualMood == VisualMoodChoice.SURPRISE_DAILY) {
                                "Todo dia à meia-noite o Citra ganha uma cor nova! 🎨"
                            } else if (updated.guidanceStyle == GuidanceStyleChoice.SELF_SUFFICIENT) {
                                "Sem rodeios! Já liberamos o caminho pra você 🚀"
                            } else {
                                "Visual do seu jeito com dicas rápidas onde precisar ✨"
                            }
                            onMascotReaction(MascotReactionFeedback(bubble, MascotHumor.FELIZ))
                        }
                    )

                    else -> Step7Summary(
                        displayName = displayName,
                        answers = answers
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Footer Navigation Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        if (currentStep > 1) {
                            onStepChange(currentStep - 1)
                        } else {
                            onBackFromFirstStep()
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .heightIn(min = 50.dp)
                        .testTag("quiz_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Voltar",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Voltar", fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = {
                        if (currentStep <= TOTAL_QUESTION_STEPS) {
                            onStepChange(currentStep + 1)
                        } else {
                            onFinishQuiz(answers)
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 50.dp)
                        .testTag(
                            if (currentStep <= TOTAL_QUESTION_STEPS) "quiz_next_button"
                            else "quiz_finish_button"
                        )
                ) {
                    Text(
                        text = when {
                            currentStep < TOTAL_QUESTION_STEPS -> "Próximo"
                            currentStep == TOTAL_QUESTION_STEPS -> "Ver resumo"
                            else -> finishButtonLabel
                        },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }

    if (showCurrencyPicker) {
        SearchableCurrencyPickerDialog(
            selectedCurrency = FinanceCurrency.fromCode(answers.financeCurrencyCode),
            onDismiss = { showCurrencyPicker = false },
            onSelectCurrency = { picked ->
                onAnswersChange(answers.copy(financeCurrencyCode = picked.code))
                showCurrencyPicker = false
            }
        )
    }
}

@Composable
private fun StepHeader(
    question: String,
    helper: String
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = question,
            fontSize = 18.5.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = helper,
            fontSize = 12.5.sp,
            lineHeight = 17.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(14.dp))
    }
}

@Composable
private fun QuestionnaireOptionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    testTag: String,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
) {
    val bgColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.52f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f)
    }
    val borderColor = if (selected) accentColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = bgColor,
        border = BorderStroke(if (selected) 2.dp else 1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = if (selected) accentColor else MaterialTheme.colorScheme.surfaceVariant,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.onPrimary else accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (selected) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selecionado",
                    tint = accentColor,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun Step1MainGoal(
    selected: MainGoalChoice,
    onSelect: (MainGoalChoice) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StepHeader(
            question = "O que você mais quer colocar nos trilhos primeiro?",
            helper = "Isso define qual área do Citra abre para você logo ao entrar."
        )
        MainGoalChoice.entries.forEach { choice ->
            val icon = when (choice) {
                MainGoalChoice.FOCUS_STUDY -> Icons.Default.Timer
                MainGoalChoice.TASKS_HABITS -> Icons.Default.Checklist
                MainGoalChoice.NOTES_DOCS -> Icons.Default.Description
                MainGoalChoice.CALENDAR -> Icons.Default.CalendarMonth
                MainGoalChoice.OVERVIEW -> Icons.Default.Home
            }
            QuestionnaireOptionCard(
                icon = icon,
                title = choice.title,
                subtitle = choice.subtitle,
                selected = selected == choice,
                testTag = "quiz_step1_${choice.name}",
                onClick = { onSelect(choice) }
            )
        }
    }
}

@Composable
private fun Step2AlertStyle(
    selected: AlertStyleChoice,
    onSelect: (AlertStyleChoice) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StepHeader(
            question = "Quando chega a hora de uma tarefa ou fim de foco, como o Citra deve te avisar?",
            helper = "Você pode ajustar tarefa por tarefa depois, mas definiremos esse como seu padrão."
        )
        AlertStyleChoice.entries.forEach { choice ->
            val icon = when (choice) {
                AlertStyleChoice.DISCREET -> Icons.Default.NotificationsNone
                AlertStyleChoice.NORMAL_ALARM -> Icons.Default.Alarm
                AlertStyleChoice.DISRUPTIVE -> Icons.Default.WarningAmber
            }
            QuestionnaireOptionCard(
                icon = icon,
                title = choice.title,
                subtitle = choice.subtitle,
                selected = selected == choice,
                testTag = "quiz_step2_${choice.name}",
                onClick = { onSelect(choice) }
            )
        }
    }
}

@Composable
private fun Step3FocusStyle(
    selected: FocusStyleChoice,
    onSelect: (FocusStyleChoice) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StepHeader(
            question = "Como o tempo e as distrações funcionam melhor quando você precisa se concentrar?",
            helper = "Prepara o temporizador Arancia e o nível de proteção contra distrações."
        )
        FocusStyleChoice.entries.forEach { choice ->
            val icon = when (choice) {
                FocusStyleChoice.LIGHT_SPRINTS -> Icons.Default.Schedule
                FocusStyleChoice.CLASSIC_FLOW -> Icons.Default.SelfImprovement
                FocusStyleChoice.DEEP_IMMERSION -> Icons.Default.Shield
            }
            QuestionnaireOptionCard(
                icon = icon,
                title = choice.title,
                subtitle = choice.subtitle,
                selected = selected == choice,
                testTag = "quiz_step3_${choice.name}",
                onClick = { onSelect(choice) }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Step4Finances(
    selected: FinanceStyleChoice,
    hideValues: Boolean,
    currencyCode: String,
    onSelectStyle: (FinanceStyleChoice) -> Unit,
    onToggleHideValues: (Boolean) -> Unit,
    onSelectCurrency: (String) -> Unit,
    onOpenAllCurrencies: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StepHeader(
            question = "Você também quer usar o Citra para organizar seu dinheiro e gastos?",
            helper = "O controle financeiro é opcional e fica oculto para quem prefere só produtividade."
        )
        FinanceStyleChoice.entries.forEach { choice ->
            val icon = when (choice) {
                FinanceStyleChoice.DISABLED -> Icons.Default.DoNotDisturbOn
                FinanceStyleChoice.ENABLED -> Icons.Default.AccountBalanceWallet
            }
            QuestionnaireOptionCard(
                icon = icon,
                title = choice.title,
                subtitle = choice.subtitle,
                selected = selected == choice,
                testTag = "quiz_step4_${choice.name}",
                onClick = { onSelectStyle(choice) }
            )
        }

        AnimatedVisibility(visible = selected == FinanceStyleChoice.ENABLED) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Moeda principal",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val quickCurrencies = listOf("AOA" to "Kz (AOA)", "BRL" to "R$ (BRL)", "USD" to "$ (USD)", "EUR" to "€ (EUR)")
                        quickCurrencies.forEach { (code, label) ->
                            val isSel = currencyCode == code
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                ),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onSelectCurrency(code) }
                                    .testTag("quiz_currency_$code")
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }

                        val isCustomCurrency = currencyCode !in listOf("AOA", "BRL", "USD", "EUR")
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isCustomCurrency) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                1.dp,
                                if (isCustomCurrency) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(onClick = onOpenAllCurrencies)
                                .testTag("quiz_currency_more")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Escolher outra moeda",
                                    tint = if (isCustomCurrency) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isCustomCurrency) currencyCode else "Outra",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isCustomCurrency) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onToggleHideValues(!hideValues) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VisibilityOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Esconder valores por privacidade",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Mostra ••• até você tocar para ver",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = hideValues,
                            onCheckedChange = onToggleHideValues,
                            modifier = Modifier.testTag("quiz_finance_hide_switch")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Step5NotesStyle(
    selected: NotesStyleChoice,
    onSelect: (NotesStyleChoice) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StepHeader(
            question = "Qual é o seu jeito natural de lidar com notas, PDFs e textos?",
            helper = "Evita exibir símbolos técnicos quando você só quer ler, ou agiliza sua escrita."
        )
        NotesStyleChoice.entries.forEach { choice ->
            val icon = when (choice) {
                NotesStyleChoice.READ_FOLDERS -> Icons.Default.FolderCopy
                NotesStyleChoice.EDIT_CARDS -> Icons.Default.EditNote
                NotesStyleChoice.COMPACT_LIST -> Icons.AutoMirrored.Filled.FormatListBulleted
            }
            QuestionnaireOptionCard(
                icon = icon,
                title = choice.title,
                subtitle = choice.subtitle,
                selected = selected == choice,
                testTag = "quiz_step5_${choice.name}",
                onClick = { onSelect(choice) }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Step6VisualAndGuidance(
    answers: OnboardingProfileAnswers,
    onUpdate: (OnboardingProfileAnswers) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StepHeader(
            question = "Para fechar: qual visual e nível de ajuda combinam com você?",
            helper = "Deixe o app com a sua cara e escolha se quer dicas nas primeiras telas."
        )

        // Brightness 3-way pills
        Text(
            text = "Aparência da tela",
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ThemeBrightnessChoice.entries.forEach { mode ->
                val isSel = answers.themeBrightness == mode
                val icon = when (mode) {
                    ThemeBrightnessChoice.LIGHT -> Icons.Default.LightMode
                    ThemeBrightnessChoice.DARK -> Icons.Default.DarkMode
                    ThemeBrightnessChoice.SYSTEM -> Icons.Default.BrightnessAuto
                }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onUpdate(answers.copy(themeBrightness = mode)) }
                        .testTag("quiz_brightness_${mode.name}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = mode.label,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Visual Mood / Color Palette
        Text(
            text = "Clima de cores",
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            VisualMoodChoice.entries.forEach { mood ->
                val isSel = answers.visualMood == mood
                val dotColor = when (mood) {
                    VisualMoodChoice.TANGERINA -> ThemeAccent.TANGERINA.primary
                    VisualMoodChoice.CALMA -> ThemeAccent.VERDE_MENTA.primary
                    VisualMoodChoice.FOCO -> ThemeAccent.AZUL_OCEANO.primary
                    VisualMoodChoice.CRIATIVO -> ThemeAccent.ROXO_COSMICO.primary
                    VisualMoodChoice.SURPRISE_DAILY -> MaterialTheme.colorScheme.primary
                }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    border = BorderStroke(
                        width = if (isSel) 1.8.dp else 1.dp,
                        color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                    ),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onUpdate(answers.copy(visualMood = mood)) }
                        .testTag("quiz_mood_${mood.name}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (mood == VisualMoodChoice.SURPRISE_DAILY) {
                            Icon(
                                imageVector = Icons.Default.Casino,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .background(dotColor, CircleShape)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = mood.label,
                            fontSize = 12.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Guidance Preference
        Text(
            text = "Acompanhamento inicial",
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        GuidanceStyleChoice.entries.forEach { g ->
            val icon = if (g == GuidanceStyleChoice.GUIDED) Icons.Default.School else Icons.Default.Explore
            QuestionnaireOptionCard(
                icon = icon,
                title = g.title,
                subtitle = g.subtitle,
                selected = answers.guidanceStyle == g,
                testTag = "quiz_guidance_${g.name}",
                onClick = { onUpdate(answers.copy(guidanceStyle = g)) }
            )
        }

        // Daily welcome quote toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable { onUpdate(answers.copy(welcomePhrasesEnabled = !answers.welcomePhrasesEnabled)) }
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Mostrar uma frase inspiradora por dia no Início",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Switch(
                checked = answers.welcomePhrasesEnabled,
                onCheckedChange = { onUpdate(answers.copy(welcomePhrasesEnabled = it)) },
                modifier = Modifier.testTag("quiz_phrases_switch")
            )
        }
    }
}

@Composable
private fun Step7Summary(
    displayName: String,
    answers: OnboardingProfileAnswers
) {
    val badges = remember(answers) { ProfileDefaultsResolver.summaryBadges(answers) }
    val cleanName = displayName.trim().ifBlank { "Viajante Citra" }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Seu Citra está pronto, $cleanName!",
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Ajustamos cada detalhe para combinar com o seu ritmo. Você pode mudar qualquer item depois em Configurações.",
            fontSize = 12.5.sp,
            lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(2.dp))

        badges.forEach { badge ->
            val icon = when (badge.key) {
                "start" -> Icons.Default.NotificationsActive
                "focus" -> Icons.Default.Bolt
                else -> Icons.Default.AccountBalanceWallet
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = badge.label,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = badge.detail,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileQuestionnaireDialog(
    displayName: String,
    initialAnswers: OnboardingProfileAnswers,
    onDismiss: () -> Unit,
    onComplete: (OnboardingProfileAnswers) -> Unit
) {
    var answers by remember(initialAnswers) { mutableStateOf(initialAnswers) }
    var currentStep by remember { mutableIntStateOf(1) }
    var mascotBubble by remember { mutableStateOf("Vamos recalibrar o Citra do seu jeito!") }
    var mascotHumor by remember { mutableStateOf(MascotHumor.FELIZ) }
    var jumpTrigger by remember { mutableIntStateOf(0) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFFFFA53B), Color(0xFFFF8B1F), Color(0xFFE65A00))
                    )
                )
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth()
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Fechar questionário",
                            tint = Color.White
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color.White.copy(alpha = 0.22f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = mascotBubble,
                                color = Color.White,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        CitraMascote(
                            humor = mascotHumor,
                            olharX = 0f,
                            olharY = 0.2f,
                            tamanho = 84.dp,
                            folha = true,
                            piscar = true,
                            jumpTrigger = jumpTrigger
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        ProfileQuestionnaireCard(
                            displayName = displayName,
                            answers = answers,
                            currentStep = currentStep,
                            onAnswersChange = { answers = it },
                            onStepChange = { currentStep = it },
                            onBackFromFirstStep = onDismiss,
                            onSkipToDefault = onDismiss,
                            onFinishQuiz = { finalAnswers ->
                                onComplete(finalAnswers)
                                onDismiss()
                            },
                            onMascotReaction = { feedback ->
                                mascotBubble = feedback.bubbleText
                                mascotHumor = feedback.humor
                                if (feedback.jump) jumpTrigger++
                            },
                            finishButtonLabel = "Aplicar no meu Citra",
                            showSkipButton = false
                        )
                    }
                }
            }
        }
    }
}
