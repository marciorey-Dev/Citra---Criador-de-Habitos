package com.example.data.local

import com.example.ui.theme.ThemeAccent
import org.json.JSONObject

enum class MainGoalChoice(val title: String, val subtitle: String) {
    FOCUS_STUDY(
        title = "Manter o foco e estudar sem distrações",
        subtitle = "Abre direto no temporizador Arancia para começar rápido"
    ),
    TASKS_HABITS(
        title = "Não esquecer tarefas e firmar novos hábitos",
        subtitle = "Abre na central de Organização com suas rotinas do dia"
    ),
    NOTES_DOCS(
        title = "Organizar ideias, resumos, PDFs e documentos",
        subtitle = "Abre direto no seu caderno de notas e leitura"
    ),
    CALENDAR(
        title = "Planejar compromissos e prazos no calendário",
        subtitle = "Abre na visão mensal de prazos e eventos"
    ),
    OVERVIEW(
        title = "Ter uma visão geral equilibrada do meu dia",
        subtitle = "Abre no Início reunindo foco, tarefas e atalhos"
    )
}

enum class AlertStyleChoice(val title: String, val subtitle: String) {
    DISCREET(
        title = "Só uma notificação discreta",
        subtitle = "Aviso gentil na barra sem som contínuo nem tela cheia"
    ),
    NORMAL_ALARM(
        title = "Alarme normal até eu ver",
        subtitle = "Toca como alarme repetido nas suas tarefas marcadas"
    ),
    DISRUPTIVE(
        title = "Impossível de ignorar — para tudo na tela!",
        subtitle = "Tarefas urgentes e fim de foco abrem em tela cheia e tocam mesmo no silencioso"
    )
}

enum class FocusStyleChoice(val title: String, val subtitle: String) {
    LIGHT_SPRINTS(
        title = "Blocos curtos e leves (15 min)",
        subtitle = "Você avança no seu ritmo entre foco e pausa"
    ),
    CLASSIC_FLOW(
        title = "Clássico contínuo (25 min) com som suave",
        subtitle = "Avança os ciclos sozinho com áudio ambiente relaxante"
    ),
    DEEP_IMMERSION(
        title = "Imersão total (45 min) sem distrações",
        subtitle = "Bloqueia distrações e evita saídas acidentais durante o foco"
    )
}

enum class FinanceStyleChoice(val title: String, val subtitle: String) {
    DISABLED(
        title = "Agora não, prefiro só produtividade",
        subtitle = "Mantém a interface mais enxuta (pode ativar quando quiser)"
    ),
    ENABLED(
        title = "Sim! Quero cuidar do dinheiro e gastos",
        subtitle = "Ativa carteira, orçamentos e vínculo com notas desde já"
    )
}

enum class NotesStyleChoice(val title: String, val subtitle: String) {
    READ_FOLDERS(
        title = "Leio muitos PDFs, Word e resumos prontos",
        subtitle = "Abre notas em Modo Leitura limpo e organiza em pastas"
    ),
    EDIT_CARDS(
        title = "Anoto ideias rápido e edito na hora",
        subtitle = "Abre direto para escrever com visualização em cartões"
    ),
    COMPACT_LIST(
        title = "Prefiro uma lista compacta e direta",
        subtitle = "Abre para edição rápida em uma lista fácil de varrer"
    )
}

enum class VisualMoodChoice(val label: String) {
    TANGERINA("Tangerina"),
    CALMA("Calma"),
    FOCO("Foco Azul"),
    CRIATIVO("Criativo"),
    SURPRISE_DAILY("Cor nova todo dia")
}

enum class ThemeBrightnessChoice(val label: String) {
    LIGHT("Claro"),
    DARK("Escuro"),
    SYSTEM("Automático")
}

enum class GuidanceStyleChoice(val title: String, val subtitle: String) {
    GUIDED(
        title = "Quero dicas rápidas nas telas novas",
        subtitle = "Exibe pequenos guias interativos na primeira visita e frase do dia"
    ),
    SELF_SUFFICIENT(
        title = "Já pego o jeito sozinho",
        subtitle = "Vai direto ao ponto sem pop-ups de tutorial"
    )
}

data class OnboardingProfileAnswers(
    val mainGoal: MainGoalChoice = MainGoalChoice.OVERVIEW,
    val alertStyle: AlertStyleChoice = AlertStyleChoice.NORMAL_ALARM,
    val focusStyle: FocusStyleChoice = FocusStyleChoice.CLASSIC_FLOW,
    val financeStyle: FinanceStyleChoice = FinanceStyleChoice.DISABLED,
    val financeHideValues: Boolean = false,
    val financeCurrencyCode: String = "AOA",
    val notesStyle: NotesStyleChoice = NotesStyleChoice.EDIT_CARDS,
    val themeBrightness: ThemeBrightnessChoice = ThemeBrightnessChoice.SYSTEM,
    val visualMood: VisualMoodChoice = VisualMoodChoice.TANGERINA,
    val guidanceStyle: GuidanceStyleChoice = GuidanceStyleChoice.GUIDED,
    val welcomePhrasesEnabled: Boolean = true
) {
    fun toJsonString(): String {
        return JSONObject().apply {
            put("mainGoal", mainGoal.name)
            put("alertStyle", alertStyle.name)
            put("focusStyle", focusStyle.name)
            put("financeStyle", financeStyle.name)
            put("financeHideValues", financeHideValues)
            put("financeCurrencyCode", financeCurrencyCode)
            put("notesStyle", notesStyle.name)
            put("themeBrightness", themeBrightness.name)
            put("visualMood", visualMood.name)
            put("guidanceStyle", guidanceStyle.name)
            put("welcomePhrasesEnabled", welcomePhrasesEnabled)
        }.toString()
    }

    companion object {
        fun fromJsonString(raw: String?): OnboardingProfileAnswers {
            if (raw.isNullOrBlank()) return OnboardingProfileAnswers()
            return try {
                val obj = JSONObject(raw)
                OnboardingProfileAnswers(
                    mainGoal = parseEnum(obj.optString("mainGoal"), MainGoalChoice.OVERVIEW),
                    alertStyle = parseEnum(obj.optString("alertStyle"), AlertStyleChoice.NORMAL_ALARM),
                    focusStyle = parseEnum(obj.optString("focusStyle"), FocusStyleChoice.CLASSIC_FLOW),
                    financeStyle = parseEnum(obj.optString("financeStyle"), FinanceStyleChoice.DISABLED),
                    financeHideValues = obj.optBoolean("financeHideValues", false),
                    financeCurrencyCode = obj.optString("financeCurrencyCode", "AOA").ifBlank { "AOA" },
                    notesStyle = parseEnum(obj.optString("notesStyle"), NotesStyleChoice.EDIT_CARDS),
                    themeBrightness = parseEnum(obj.optString("themeBrightness"), ThemeBrightnessChoice.SYSTEM),
                    visualMood = parseEnum(obj.optString("visualMood"), VisualMoodChoice.TANGERINA),
                    guidanceStyle = parseEnum(obj.optString("guidanceStyle"), GuidanceStyleChoice.GUIDED),
                    welcomePhrasesEnabled = obj.optBoolean("welcomePhrasesEnabled", true)
                )
            } catch (_: Exception) {
                OnboardingProfileAnswers()
            }
        }

        private inline fun <reified T : Enum<T>> parseEnum(value: String?, fallback: T): T {
            if (value.isNullOrBlank()) return fallback
            return try {
                enumValueOf<T>(value)
            } catch (_: Exception) {
                fallback
            }
        }
    }
}

data class ResolvedOnboardingDefaults(
    val defaultStartTab: String,
    val defaultTaskAlertMode: String,
    val pomodoroDisruptiveAlarm: Boolean,
    val overrideSilentMode: Boolean,
    val headsUpBanner: Boolean,
    val vibrate: Boolean,
    val focusMinutes: Int,
    val timerContinuousMode: Boolean,
    val timerAllowSwipe: Boolean,
    val keepScreenOn: Boolean,
    val focusDistractionFreeEnabled: Boolean,
    val ambientBreakMusicInApp: Boolean,
    val financesEnabled: Boolean,
    val financeHideValues: Boolean,
    val financeCurrencyCode: String,
    val defaultNoteOpenMode: String,
    val notesLayoutMode: String,
    val themeMode: Boolean?,
    val themeAccent: ThemeAccent,
    val dailyRandomColor: Boolean,
    val allTutorialsSkipped: Boolean,
    val welcomePhrasesEnabled: Boolean
)

data class ProfileSummaryBadge(
    val key: String,
    val label: String,
    val detail: String
)

object ProfileDefaultsResolver {
    const val TASK_ALERT_NOTIFICATION = "NOTIFICATION"
    const val TASK_ALERT_ALARM = "ALARM"
    const val TASK_ALERT_DISRUPTIVE = "DISRUPTIVE"

    fun resolve(answers: OnboardingProfileAnswers): ResolvedOnboardingDefaults {
        val startTab = when (answers.mainGoal) {
            MainGoalChoice.FOCUS_STUDY -> "TIMER"
            MainGoalChoice.TASKS_HABITS -> "ORGANIZATION"
            MainGoalChoice.NOTES_DOCS -> "NOTES"
            MainGoalChoice.CALENDAR -> "CALENDAR"
            MainGoalChoice.OVERVIEW -> "HOME"
        }

        val (taskAlertMode, pomodoroDisruptive, overrideSilent) = when (answers.alertStyle) {
            AlertStyleChoice.DISCREET -> Triple(TASK_ALERT_NOTIFICATION, false, false)
            AlertStyleChoice.NORMAL_ALARM -> Triple(TASK_ALERT_ALARM, false, false)
            AlertStyleChoice.DISRUPTIVE -> Triple(TASK_ALERT_DISRUPTIVE, true, true)
        }

        val (focusMins, continuous, allowSwipe, distractionFree, ambientMusic) = when (answers.focusStyle) {
            FocusStyleChoice.LIGHT_SPRINTS -> Quintuple(15, false, true, false, false)
            FocusStyleChoice.CLASSIC_FLOW -> Quintuple(25, true, true, false, true)
            FocusStyleChoice.DEEP_IMMERSION -> Quintuple(45, true, false, true, false)
        }

        val (noteOpenMode, noteLayout) = when (answers.notesStyle) {
            NotesStyleChoice.READ_FOLDERS -> "READING" to "STACKED_CATEGORIES"
            NotesStyleChoice.EDIT_CARDS -> "EDIT" to "GRID_2"
            NotesStyleChoice.COMPACT_LIST -> "EDIT" to "LIST"
        }

        val themeMode: Boolean? = when (answers.themeBrightness) {
            ThemeBrightnessChoice.LIGHT -> false
            ThemeBrightnessChoice.DARK -> true
            ThemeBrightnessChoice.SYSTEM -> null
        }

        val (accent, dailyRandom) = when (answers.visualMood) {
            VisualMoodChoice.TANGERINA -> ThemeAccent.TANGERINA to false
            VisualMoodChoice.CALMA -> ThemeAccent.VERDE_MENTA to false
            VisualMoodChoice.FOCO -> ThemeAccent.AZUL_OCEANO to false
            VisualMoodChoice.CRIATIVO -> ThemeAccent.ROXO_COSMICO to false
            VisualMoodChoice.SURPRISE_DAILY -> ThemeAccent.TANGERINA to true
        }

        val skipTutorials = answers.guidanceStyle == GuidanceStyleChoice.SELF_SUFFICIENT
        val financesOn = answers.financeStyle == FinanceStyleChoice.ENABLED

        return ResolvedOnboardingDefaults(
            defaultStartTab = startTab,
            defaultTaskAlertMode = taskAlertMode,
            pomodoroDisruptiveAlarm = pomodoroDisruptive,
            overrideSilentMode = overrideSilent,
            headsUpBanner = true,
            vibrate = true,
            focusMinutes = focusMins,
            timerContinuousMode = continuous,
            timerAllowSwipe = allowSwipe,
            keepScreenOn = true,
            focusDistractionFreeEnabled = distractionFree,
            ambientBreakMusicInApp = ambientMusic,
            financesEnabled = financesOn,
            financeHideValues = financesOn && answers.financeHideValues,
            financeCurrencyCode = answers.financeCurrencyCode.ifBlank { "AOA" },
            defaultNoteOpenMode = noteOpenMode,
            notesLayoutMode = noteLayout,
            themeMode = themeMode,
            themeAccent = accent,
            dailyRandomColor = dailyRandom,
            allTutorialsSkipped = skipTutorials,
            welcomePhrasesEnabled = answers.welcomePhrasesEnabled
        )
    }

    fun summaryBadges(answers: OnboardingProfileAnswers): List<ProfileSummaryBadge> {
        val resolved = resolve(answers)
        val tabName = when (resolved.defaultStartTab) {
            "TIMER" -> "Timer (Arancia)"
            "ORGANIZATION" -> "Tarefas & Hábitos"
            "NOTES" -> "Notas & Leitura"
            "CALENDAR" -> "Calendário"
            else -> "Visão Geral (Início)"
        }
        val alertDetail = when (answers.alertStyle) {
            AlertStyleChoice.DISCREET -> "Avisos discretos sem interromper"
            AlertStyleChoice.NORMAL_ALARM -> "Alarme normal até dispensar"
            AlertStyleChoice.DISRUPTIVE -> "Tela cheia e som prioritário"
        }
        val focusDetail = when (answers.focusStyle) {
            FocusStyleChoice.LIGHT_SPRINTS -> "15 min no seu ritmo"
            FocusStyleChoice.CLASSIC_FLOW -> "25 min contínuo + som suave"
            FocusStyleChoice.DEEP_IMMERSION -> "45 min sem distrações"
        }
        val financeDetail = if (resolved.financesEnabled) {
            if (resolved.financeHideValues) "Ativo em ${resolved.financeCurrencyCode} (valores ocultos)"
            else "Ativo em ${resolved.financeCurrencyCode}"
        } else {
            "Desligado (foco em produtividade)"
        }
        val notesDetail = when (answers.notesStyle) {
            NotesStyleChoice.READ_FOLDERS -> "Modo Leitura + pastas empilhadas"
            NotesStyleChoice.EDIT_CARDS -> "Edição direta em cartões"
            NotesStyleChoice.COMPACT_LIST -> "Edição direta em lista compacta"
        }
        val lookDetail = buildString {
            append(answers.themeBrightness.label)
            append(" • ")
            append(answers.visualMood.label)
            append(if (resolved.allTutorialsSkipped) " • Direto ao ponto" else " • Guiado")
        }

        return listOf(
            ProfileSummaryBadge("start", "Tela inicial: $tabName", alertDetail),
            ProfileSummaryBadge("focus", "Foco: $focusDetail", notesDetail),
            ProfileSummaryBadge("finance", "Finanças: $financeDetail", lookDetail)
        )
    }

    private data class Quintuple<A, B, C, D, E>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D,
        val fifth: E
    )
}
