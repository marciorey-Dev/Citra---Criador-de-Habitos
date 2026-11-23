package com.example.ui.tutorial

import androidx.compose.ui.Alignment

enum class CitraTutorialArea(val id: String, val title: String) {
    HOME("HOME", "Início"),
    TIMER("TIMER", "Timer Arancia"),
    TASKS("TASKS", "Tarefas"),
    HABITS("HABITS", "Hábitos"),
    NOTES("NOTES", "Notas"),
    STATS("STATS", "Dados"),
    FINANCES("FINANCES", "Finanças"),
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
                title = "Sessão Arancia",
                text = "Inicie sessões de foco diretamente do Início. O relógio cítrico marca o tempo e as pausas de forma fluida e sem distrações.",
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
                text = "Cultive sua rotina e faça check-in diário para manter seus dias consecutivos sempre em alta.",
                targetKey = "home_habits_shortcut",
                preferBelow = false
            ),
            CitraTutorialStep(
                title = "Navegação Rápida",
                text = "Alterne entre Início, Timer, Organização, Notas, Finanças (quando ativadas) e Dados através da barra inferior.",
                targetKey = "bottom_navigation_bar",
                preferBelow = false
            )
        ),

        CitraTutorialArea.TIMER to listOf(
            CitraTutorialStep(
                title = "Modo Arancia & Cronômetro",
                text = "Use o botão ao lado do Play para alternar entre o Timer Arancia (ciclos de foco) e o Cronômetro livre.",
                targetKey = "timer_mode_toggle",
                preferBelow = true
            ),
            CitraTutorialStep(
                title = "Ajustes Opcionais",
                text = "Toque no ícone de ajustes para personalizar a duração da sessão, pausa e repetições quando desejar.",
                targetKey = "timer_settings_btn",
                preferBelow = true
            ),
            CitraTutorialStep(
                title = "Mascotes Citra Interativos",
                text = "Cada sessão concluída no Timer gera um mascote Citra proporcional ao tempo focado. Incline o celular para vê-los rolar e dê um toque duplo em um Citra para fazê-lo explodir!",
                targetKey = "timer_circle_display",
                preferBelow = false
            )
        ),

        CitraTutorialArea.TASKS to listOf(
            CitraTutorialStep(
                title = "Criar Nova Tarefa",
                text = "Toque no botão '+' para adicionar tarefas com prazos, prioridades (Alta, Média ou Baixa), alarme e categorias.",
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
                text = "Toque no ícone de play ao lado de uma tarefa para iniciar uma sessão Arancia focada especialmente nela.",
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
                title = "Dias Consecutivos",
                text = "Cada dia cumprido reforça sua constância. Mantenha o ritmo firme!",
                targetKey = "habits_streak_counter",
                preferBelow = true
            )
        ),

        CitraTutorialArea.NOTES to listOf(
            CitraTutorialStep(
                title = "Criar Nova Anotação",
                text = "Crie notas com Markdown, tabelas, fórmulas matemáticas, callouts, checklists e gravações de áudio. A barra de formatação fica fixada na parte inferior e pode ser desafixada no menu avançado.",
                targetKey = "notes_add_button",
                preferBelow = true
            ),
            CitraTutorialStep(
                title = "Rascunho Rápido & Modelos",
                text = "Use o botão de rascunho rápido para escolher modelos prontos (Diário, Reunião, Estudos, Projeto) e começar a escrever na hora.",
                targetKey = "notes_search_input",
                preferBelow = true
            ),
            CitraTutorialStep(
                title = "Pastas, Organização e Lixeira",
                text = "Alterne entre visualização em grade ou lista, filtre por categorias e acesse a Lixeira para restaurar todas ou esvaziar com um toque.",
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

        CitraTutorialArea.FINANCES to listOf(
            CitraTutorialStep(
                title = "1. Resumo do Seu Dinheiro",
                text = "Veja seu saldo consolidado, quanto entrou vs. saiu no período e o gráfico das principais categorias em um só lugar.",
                targetKey = "finances_summary_card",
                preferBelow = true
            ),
            CitraTutorialStep(
                title = "2. Lançamento em Segundos",
                text = "Toque em '+ Novo lançamento' para registrar despesas ou rendas só com valor, nome e categoria — com 'Mais opções' quando precisar.",
                targetKey = "finances_add_button",
                preferBelow = false
            ),
            CitraTutorialStep(
                title = "3. Movimentos & Ferramentas",
                text = "Use 'Movimentos' para buscar no histórico por data e 'Ferramentas' para Carteiras, Planeados, Orçamentos, Recorrências, Câmbio e Vínculos com Notas.",
                targetKey = "finances_tools_tab",
                preferBelow = true
            )
        ),

        CitraTutorialArea.MENU to listOf(
            CitraTutorialStep(
                title = "Configurações Gerais",
                text = "Ajuste toques de alarme, imagem de fundo, reset dos mascotes do Timer e preferências das Finanças.",
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
