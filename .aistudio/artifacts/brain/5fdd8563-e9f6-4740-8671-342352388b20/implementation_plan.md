# Sistema de Animações Interativas, Feedback Tátil e Efeito Escada

Guia arquitetural e plano de implementação em etapas para transformar as microinterações do Citra com física squash & stretch (comprimir e esticar), transições graduais de cor, feedback tátil háptico, conclusão animada de tarefas/hábitos, abas e seletores deslizantes nas Finanças e animação de entrada em escada no lançamento do app.

---

### Decisões Críticas e Alinhamento

> [!IMPORTANT]
> **Foco Exclusivo em Planejamento**: Conforme solicitado, nenhum código de produção foi modificado nesta fase. Este guia decompõe o escopo completo em etapas claras para validação antes da implementação.

- **Física de Squash & Stretch**: O efeito de comprimir (0.92f–0.94f) ao pressionar e esticar levemente ao soltar antes de retornar a 1.0f será padronizado através de um Modifier de alto desempenho (`Modifier.squashBounceClickable`) suportando `spring(dampingRatio = MediumBouncy, stiffness = StiffnessMedium)`.
- **Efeito Escada (Staggered Cascade)**: Ocorre estritamente na inicialização do aplicativo (cold start) sobre a tela inicial definida pelo usuário (seja Início, Timer, Tarefas/Organização, Notas ou Finanças), sem repetir nas trocas cotidianas de aba durante a sessão.
- **Conclusão de Tarefas com Sequência Coreografada**: Quando o usuário toca para concluir uma tarefa:
  1. O ícone de visto preenche e dispara feedback háptico imediato.
  2. O texto da tarefa recebe animação de risco (strikethrough) e o card transiciona gradualmente para verde suave.
  3. Após um delay perceptivo (~280ms), o item transiciona em fade-out e colapsa sua altura antes de atualizar o estado permanente.

---

## 1. Visão Geral e Conceito Central

O objetivo principal desta atualização é substituir interações estáticas ou de resposta instantânea por um **Design System de Movimento Vivo e Tátil**. No Jetpack Compose, animações de alto padrão não são meros efeitos estéticos: elas comunicam a física do toque, confirmam a execução de comandos e conferem ao Citra uma sensação artesanal e fluida.

```
┌────────────────────────────────────────────────────────────────────────┐
│                   CITRA MOTION & INTERACTION SYSTEM                    │
├───────────────────────────────┬────────────────────────────────────────┤
│     Microinterações Táteis    │         Transições de Estado           │
│  • Squash & Stretch (Pressionar)│  • Despesa ⇄ Renda (Pill Deslizante) │
│  • Resposta Háptica Instantânea│  • Tarefas ⇄ Hábitos (Tab Sliding)   │
│  • Transições de Cor Graduais │  • Menu Flutuante (Fade-In + Slide)    │
├───────────────────────────────┴────────────────────────────────────────┤
│                       Coreografias de Entrada                          │
│  • Conclusão de Tarefa: Check ➔ Risco ➔ Verde ➔ Fade/Desaparecimento   │
│  • Finanças: Gráfico de Pizza em Arco + Lista em Cascata Ascendente    │
│  • Cold Start: Efeito Escada Staggered na Tela Padrão de Abertura      │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Experiência do Usuário e Especificação Visual de Movimento

### 2.1 Efeito de Comprimir e Esticar (Squash & Stretch)
- **Ao Pressionar (`isPressed = true`)**: O elemento reduz suavemente sua escala para 93% no centro (`scaleX = 0.93f`, `scaleY = 0.93f`) acompanhado de um leve impacto tátil (haptic feedback `LongPress` suave).
- **Ao Soltar (`isPressed = false`)**: O elemento recupera sua forma com uma física de mola elástica (`Spring.DampingRatioMediumBouncy` e `Spring.StiffnessMedium`), criando a sensação física de uma superfície tátil de borracha premium.

### 2.2 Transição Gradual de Cor
- Botões que alteram seu estado não mudam bruscamente de cor. O Compose calculará a interpolação suave via `animateColorAsState(animationSpec = tween(280, easing = FastOutSlowInEasing))`.

### 2.3 Tarefas e Hábitos na Tela Inicial (Home)
- **Botão Iniciar Foco**: Recebe feedback de compressão acentuado com brilho sutil no ícone de play.
- **Botões Criar Nota e Criar Tarefa**: Efeito tátil de pressão elástica ao toque.
- **Conclusão de Tarefas na Home**: Ao tocar no checkbox da tarefa atual ou das próximas tarefas:
  - Checkbox anima seu preenchimento.
  - Nome da tarefa é riscado progressivamente com linha animada.
  - O container transiciona gradativamente para uma tonalidade verde suave.
  - O card desliza suavemente para a esquerda ou desaparece em fade-out antes de sumir da lista de pendentes.
- **Hábitos na Home**: Ao tocar num hábito, o card sofre compressão tátil, o indicador circular ou badge se expande e preenche em cor de sucesso, confirmando visualmente a conclusão do dia.

### 2.4 Tela de Tarefas e Organização
- **Botão FAB (Adicionar Tarefa)**: Ao ser clicado, sofre squash & stretch e o modal/menu flutuante surge de baixo para cima com `slideInVertically(initialOffsetY = { it / 3 }) + fadeIn(tween(300))`.
- **Navegação por Abas (Tarefas, Hábitos, Calendário)**: A pílula de seleção desliza horizontalmente de uma aba para a outra (`animateDpAsState` no offset X e na largura), criando uma transição contínua sem saltos.
- **Check na Lista de Tarefas**: O ícone de check se desenha, o texto é riscado, a cor do card pulsa para verde suave, o feedback tátil vibra e a tarefa é removida/arquivada com animação de colapso de linha.

### 2.5 Finanças: Alternância, Gráfico e Lista
- **Seletor Despesa ⇄ Renda**: A pílula de fundo desliza suavemente da esquerda para a direita (ou vice-versa) através de um indicador animado com `animateDpAsState` enquanto sua cor transiciona gradualmente entre a cor primária (Despesa) e o verde esmeralda (Renda).
- **Gráfico de Categorias**: Ao entrar na tela ou alterar o período, o gráfico em arco desenha suas fatias em tempo real do ângulo 0° até o ângulo final (`sweepAngle * progress`, animado de 0f a 1f via `Animatable`), separando visualmente as fatias.
- **Lista de Despesas/Receitas**: Os lançamentos entram em cascata com um slide-up leve e fade-in escalonado por índice conforme a rolagem.

### 2.6 Efeito Escada (Staggered Entrance) na Tela Inicial do App
- Quando o Citra é aberto, verifica-se qual é a tela inicial definida nas configurações (`defaultStartTab`).
- Apenas na primeira montagem desta tela, os blocos estruturais disparam uma animação em cascata (escada):
  - Bloco 0 (Cabeçalho/Data): Delay 0ms, Fade-in + Slide-up de 16.dp.
  - Bloco 1 (Hero Card/Timer): Delay 70ms, Fade-in + Slide-up de 20.dp.
  - Bloco 2 (Atalhos/Ações): Delay 140ms, Fade-in + Slide-up de 24.dp.
  - Bloco 3 (Tarefas/Hábitos): Delay 210ms, Fade-in + Slide-up de 28.dp.
  - Bloco 4 (Gráficos/Resumos): Delay 280ms, Fade-in + Slide-up de 32.dp.

---

## 3. Divisão do Escopo em Etapas de Execução

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    GUIA DE IMPLEMENTAÇÃO EM 6 ETAPAS                       │
├─────────┬──────────────────────────────────────────────────────────────────┤
│ ETAPA 1 │ Fundação: Modifiers Reutilizáveis de Compressão e Feedback Tátil │
├─────────┼──────────────────────────────────────────────────────────────────┤
│ ETAPA 2 │ Animações e Conclusão de Tarefas/Hábitos na Tela Inicial (Home)  │
├─────────┼──────────────────────────────────────────────────────────────────┤
│ ETAPA 3 │ Organização: Pílula Deslizante de Abas, FAB e Animação de Check │
├─────────┼──────────────────────────────────────────────────────────────────┤
│ ETAPA 4 │ Finanças: Seletor Deslizante, Gráfico Vivo e Lista em Cascata    │
├─────────┼──────────────────────────────────────────────────────────────────┤
│ ETAPA 5 │ Sistema de Efeito Escada (Staggered Entrance) no Cold Start      │
├─────────┼──────────────────────────────────────────────────────────────────┤
│ ETAPA 6 │ Validação Geral, Ajuste Fino de Fps e Compilação Final           │
└─────────┴──────────────────────────────────────────────────────────────────┘
```

### Etapa 1: Fundação de Microinterações (Modifiers & Utilitários)
- **Local**: `ui/components/` ou `util/MotionExtensions.kt`.
- Criar o modifier universal `Modifier.squashBounceClickable()` que:
  - Observa `MutableInteractionSource` e `collectIsPressedAsState()`.
  - Anima `scaleX` e `scaleY` com física de amortecimento `spring()`.
  - Dispara `LocalHapticFeedback.current.performHapticFeedback(HapticFeedbackType.LongPress)` com segurança e baixo overhead.
- Criar o wrapper `Modifier.staggeredEntrance(index: Int, isFirstLaunch: Boolean)` para o efeito escada.

### Etapa 2: Animações na Tela Inicial (`HomeScreen.kt`)
- Aplicar o feedback elástico squash & stretch no card/botão de Iniciar Sessão de Foco (`home_start_focus_btn`).
- Aplicar o feedback elástico nos botões rápidos "Nova Nota" e "Nova Tarefa".
- Implementar estado local de conclusão nas tarefas da Home:
  - Quando clicada, a tarefa entra em estado de `isCompleting`: dispara o visto, aciona a animação de risco do texto (`TextDecoration.LineThrough` com cor animada), altera o background para verde claro e aguarda 280ms antes de chamar `viewModel.toggleTask()`.
- Implementar resposta elástica e transição de cor nos cards horizontais de Hábitos de Hoje.

### Etapa 3: Animações de Tarefas, Hábitos e Navegação (`OrganizationScreen.kt` & `TasksSubTab.kt`)
- **Navegação de Abas**: Substituir o `Surface` estático por um indicador deslizante (`TabIndicator` com animação contínua de offset e largura calculada via `animateDpAsState`).
- **FAB de Adicionar Tarefa**: Animação elástica ao clique.
- **Menu/Modal de Criação (`QuickAddTaskDialog`)**: Entrada com `slideInVertically(initialOffsetY = { it / 2 }) + fadeIn()`.
- **Card de Tarefa na Lista (`TaskItemCard`)**: Animação de visto sincronizada com risco do texto, mudança gradativa para cor verde e transição de saída da lista.

### Etapa 4: Animações no Módulo de Finanças (`FinancesScreen.kt`)
- **Seletor Despesa ⇄ Renda**:
  - Transformar o seletor em um container segmentado com pílula de fundo deslizante que se move horizontalmente entre "DESPESAS" e "RENDAS".
  - A cor da pílula transiciona gradualmente via `animateColorAsState` entre a cor primária (laranja/tema atual) e verde esmeralda (`GreenAccent`).
- **Gráfico de Categorias em Pizza/Rosca**:
  - Animar o progresso de abertura do gráfico (`Animatable` de 0f a 1f com `FastOutSlowInEasing`) ao mudar o período ou a tela, desenhando os arcos gradualmente.
- **Lista de Lançamentos**:
  - Cada item de despesa/renda surge de forma escalonada com suave slide vertical e fade-in dinâmico.

### Etapa 5: Animação de Entrada em Escada (Cold Start)
- Criar no `MainViewModel` ou `MainActivity` a flag `hasTriggeredInitialScreenEntrance` que é acionada apenas uma vez na inicialização.
- Identificar qual a tela ativa configurada pelo usuário (`defaultStartTab`).
- Aplicar nas seções verticais dessa tela a animação em cascata (`staggeredEntrance`), escalonando os atrasos de 0ms até 300ms para uma abertura fluida e visualmente sofisticada.

### Etapa 6: Verificação de Performance e Compilação
- Garantir 60/120fps sem alocações desnecessárias no loop de desenho (`remember`, `derivedStateOf`).
- Compilar o applet via `compile_applet` para assegurar integridade completa do build e ausência de erros.

---

## 4. Arquitetura Técnica e Mapeamento de Componentes

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                            FLUXO DE COMPONENTES                             │
├─────────────────────────────────────────────────────────────────────────────┤
│  MotionExtensions.kt                                                        │
│  ├─ Modifier.squashBounceClickable() ──► Usado em todos os botões do app    │
│  ├─ Modifier.staggeredEntrance()     ──► Usado nas seções do Cold Start     │
│  └─ animateColorAsState()            ──► Transições de Despesa ⇄ Renda     │
├─────────────────────────────────────────────────────────────────────────────┤
│  HomeScreen.kt                                                              │
│  ├─ Hero Focus Card                  ──► Squash & Stretch + Haptic Pulse    │
│  ├─ Quick Actions                    ──► Compressão tátil no toque          │
│  ├─ Task Items                       ──► Check ➔ Risco ➔ Verde ➔ Fade Out   │
│  └─ Habit Cards                      ──► Feedback tátil + Sucesso animado   │
├─────────────────────────────────────────────────────────────────────────────┤
│  OrganizationScreen.kt & TasksSubTab.kt                                     │
│  ├─ Segmented Tab Bar                ──► Pílula deslizante com animateDp    │
│  ├─ Add Task FAB                     ──► Squash bounce                      │
│  ├─ QuickAddTaskDialog               ──► Slide-in vertical de baixo + Fade  │
│  └─ TaskItemCard                     ──► Conclusão animada de linha         │
├─────────────────────────────────────────────────────────────────────────────┤
│  FinancesScreen.kt                                                          │
│  ├─ Expense/Income Pill Toggle       ──► Deslizamento horizontal + Cor      │
│  ├─ Canvas Donut Chart               ──► Arco vivo em tempo real (0° ➔ 360°)│
│  └─ Transaction Items                ──► Cascata ascendente dinâmica        │
└─────────────────────────────────────────────────────────────────────────────┘
```

> [!NOTE]
> Este plano cobre 100% dos requisitos solicitados. Ao clicar no botão **Proceed** ou aprovar o plano, iniciaremos a execução ordenada pelas 6 etapas.
