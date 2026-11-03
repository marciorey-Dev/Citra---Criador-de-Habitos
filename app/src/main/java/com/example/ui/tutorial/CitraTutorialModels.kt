package com.example.ui.tutorial

import androidx.compose.ui.Alignment

enum class CitraTutorialArea(val id: String, val title: String) {
    HOME("HOME", "Início"),
    TASKS("TASKS", "Tarefas"),
    HABITS("HABITS", "Hábitos"),
    NOTES("NOTES", "Notas"),
    STATS("STATS", "Dados"),
    MENU("MENU", "Menu de Opções")
}

data class CitraTutorialStep(
    val title: String,
    val text: String,
    val targetKey: String,
    val preferBelow: Boolean = true
)

object CitraTutorialData {
    val tutorialsByArea: Map<CitraTutorialArea, List<CitraTutorialStep>> = mapOf(
        CitraTutorialArea.HOME to listOf(
            CitraTutorialStep(
                title = "Foco Pomodoro",
                text = "Inicie sessões de foco diretamente do Início. O relógio cítrico marca o tempo e as pausas de forma fluida.",
                targetKey = "home_pomodoro_card",
                preferBelow = true
            ),
            CitraTutorialStep(
                title = "Tarefas de Hoje",
                text = "Acompanhe suas tarefas prioritárias do dia e marque como concluídas com um toque rápido.",
                targetKey = "home_tasks_shortcut",
                preferBelow = true
            ),
            CitraTutorialStep(
                title = "Hábitos Diários",
                text = "Cultive sua rotina e faça check-in diário para manter sua sequência de chamas (Streaks) sempre ativa.",
                targetKey = "home_habits_shortcut",
                preferBelow = false
            ),
            CitraTutorialStep(
                title = "Navegação Rápida",
                text = "Alterne entre o Timer, suas Tarefas, Notas e painel de Dados através desta barra inferior.",
                targetKey = "bottom_navigation_bar",
                preferBelow = false
            )
        ),

        CitraTutorialArea.TASKS to listOf(
            CitraTutorialStep(
                title = "Criar Nova Tarefa",
                text = "Toque no botão '+' para adicionar tarefas com prazos, prioridades (Alta, Média ou Baixa) e categorias.",
                targetKey = "tasks_add_button",
                preferBelow = true
            ),
            CitraTutorialStep(
                title = "Filtros e Prioridades",
                text = "Organize sua visão filtrando tarefas por status, prioridade ou por categorias personalizadas.",
                targetKey = "tasks_filter_row",
                preferBelow = true
            ),
            CitraTutorialStep(
                title = "Foco Vinculado",
                text = "Toque no ícone de play ao lado de uma tarefa para iniciar um Pomodoro focado especialmente nela.",
                targetKey = "tasks_list_first_item",
                preferBelow = true
            ),
            CitraTutorialStep(
                title = "Conclusão e Háptica",
                text = "Marque a caixa para concluir a tarefa com som agradável e vibração satisfatória.",
                targetKey = "tasks_checkbox_area",
                preferBelow = true
            )
        ),

        CitraTutorialArea.HABITS to listOf(
            CitraTutorialStep(
                title = "Adicionar Hábito",
                text = "Crie hábitos com horário fixo e escolha os dias da semana para receber lembretes sonoros no horário certo.",
                targetKey = "habits_add_button",
                preferBelow = true
            ),
            CitraTutorialStep(
                title = "Check-in Diário",
                text = "Toque para confirmar o cumprimento do hábito hoje e ver sua sequência de dias consecutivos aumentar.",
                targetKey = "habits_checkin_first_item",
                preferBelow = true
            ),
            CitraTutorialStep(
                title = "Chama de Sequência",
                text = "A chama indica quantos dias seguidos você cumpriu o hábito. Não deixe o fogo se apagar!",
                targetKey = "habits_streak_counter",
                preferBelow = true
            )
        ),

        CitraTutorialArea.NOTES to listOf(
            CitraTutorialStep(
                title = "Criar Nova Anotação",
                text = "Crie notas com suporte a Markdown, tabelas, fórmulas matemáticas, checklists e gravações de áudio.",
                targetKey = "notes_add_button",
                preferBelow = true
            ),
            CitraTutorialStep(
                title = "Pesquisa Instantânea",
                text = "Encontre qualquer anotação ou trecho de texto rapidamente usando o campo de busca no topo.",
                targetKey = "notes_search_input",
                preferBelow = true
            ),
            CitraTutorialStep(
                title = "Pastas e Categorias",
                text = "Filtre suas anotações por categorias ou acesse a lixeira para restaurar notas apagadas em até 30 dias.",
                targetKey = "notes_category_bar",
                preferBelow = true
            )
        ),

        CitraTutorialArea.STATS to listOf(
            CitraTutorialStep(
                title = "Filtro de Período",
                text = "Alterne a visualização das suas métricas entre Semanal, Mensal ou Anual para acompanhar sua evolução.",
                targetKey = "stats_period_selector",
                preferBelow = true
            ),
            CitraTutorialStep(
                title = "Tempo de Foco",
                text = "Visualize suas horas de dedicação em gráficos de barras e descubra seus dias mais produtivos.",
                targetKey = "stats_focus_chart",
                preferBelow = true
            ),
            CitraTutorialStep(
                title = "Distribuição por Categoria",
                text = "O gráfico de pizza mostra para quais áreas da sua vida você está dedicando maior foco e atenção.",
                targetKey = "stats_category_pie",
                preferBelow = false
            )
        ),

        CitraTutorialArea.MENU to listOf(
            CitraTutorialStep(
                title = "Configurações Gerais",
                text = "Ajuste toques de alarme personalizados, temas de cores, modo escuro e comportamento no modo silencioso.",
                targetKey = "top_menu_settings",
                preferBelow = true
            ),
            CitraTutorialStep(
                title = "Conquistas",
                text = "Veja todos os marcos e troféus desbloqueados conforme você mantém sua consistência e produtividade.",
                targetKey = "top_menu_achievements",
                preferBelow = true
            ),
            CitraTutorialStep(
                title = "Backup & Restauração",
                text = "Exporte ou importe seus dados em arquivo JSON para garantir a segurança das suas informações.",
                targetKey = "top_menu_backup",
                preferBelow = true
            )
        )
    )
}

data class ActiveTutorialState(
    val area: CitraTutorialArea,
    val stepIndex: Int = 0
) {
    val steps: List<CitraTutorialStep> get() = CitraTutorialData.tutorialsByArea[area] ?: emptyList()
    val currentStep: CitraTutorialStep? get() = steps.getOrNull(stepIndex)
    val isLastStep: Boolean get() = stepIndex >= steps.size - 1
    val totalSteps: Int get() = steps.size
}
