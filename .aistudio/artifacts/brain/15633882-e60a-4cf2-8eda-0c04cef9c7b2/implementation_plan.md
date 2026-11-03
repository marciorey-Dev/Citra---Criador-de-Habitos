# Sistema de Tutoriais Interativos com Spotlight & Balão Cítrico no Citra

Sistema de onboarding contextual e interativo para o Citra. Apresenta as principais funcionalidades diretamente na superfície do app através de uma camada de foco escurecida com recorte retangular arredondado, transição fluida e um balão de diálogo temático cítrico (máximo de 1 parágrafo e 5 linhas por passo, até 4 passos por área).

---

## Decisões Críticas & Preferências Confirmadas

> [!IMPORTANT]
> As seguintes decisões foram confirmadas na fase de alinhamento:
> - **Recorte do Spotlight**: Retângulo arredondado dinâmico, ajustado com precisão geométrica ao elemento destacado na tela.
> - **Transição entre Passos**: Interpolação suave e animada deslizando as coordenadas do foco e do balão ao tocar em *Próximo*.
> - **Botões por Passo**: Três controles distintos — *Próximo* (avança no compartimento atual), *Pular* (pula somente a área atual, mantendo as demais ativas ao navegar) e *Pular Tudo* (conclui e oculta todos os tutoriais globalmente).
> - **Configurações**: Adicionada opção nas Configurações para reiniciar e rever todos os tutoriais quando o usuário desejar.

---

## 1. Visão Geral & Conceito

O objetivo é substituir o antigo diálogo estático em lista por um **guia visual interativo de superfície** (*Spotlight Tour*). O sistema não bloqueia o app com textos longos; ao contrário, ele ilumina os componentes reais da interface um a um, com um balão temático em tons cítricos suaves, explicando a funcionalidade em no máximo 5 linhas diretas.

### Áreas Cobertas (Compartimentos)
1. **Início (Home)**:
   - *Passo 1*: Card do Pomodoro Cítrico (início rápido de foco).
   - *Passo 2*: Atalhos de Tarefas de Hoje (check-in instantâneo).
   - *Passo 3*: Seção de Hábitos Diários e Sequência (Streaks).
   - *Passo 4*: Barra de Navegação Inferior (como alternar entre as ferramentas).
2. **Tarefas (Organização > Tarefas)**:
   - *Passo 1*: Botão de Adicionar Tarefa (+).
   - *Passo 2*: Filtros por Prioridade (Alta, Média, Baixa) e Categorias.
   - *Passo 3*: Botão de Play na Tarefa (iniciar Pomodoro vinculado).
   - *Passo 4*: Checkbox com som e feedback háptico.
3. **Hábitos (Organização > Hábitos)**:
   - *Passo 1*: Criação de Hábitos com horário de lembrete diário.
   - *Passo 2*: Botão de Check-in diário e contador de chamas (Streak).
   - *Passo 3*: Filtro de frequência (Todos os dias ou dias da semana).
4. **Notas (Organização > Notas)**:
   - *Passo 1*: Botão de Nova Nota e Pesquisa rápida.
   - *Passo 2*: Pastas e Categorias de organização.
   - *Passo 3*: Ações na Nota (Fixar no topo, exportar e lixeira).
5. **Dados (Estatísticas & Produtividade)**:
   - *Passo 1*: Seletor de período (Semana, Mês, Ano).
   - *Passo 2*: Gráfico de Horas de Foco e Gráfico de Pizza por categoria.
   - *Passo 3*: Resumo de taxas de conclusão de hábitos e tarefas.
6. **Menu de Três Pontinhos (Menu de Opções Superior)**:
   - *Passo 1*: Conquistas e Marcos desbloqueáveis.
   - *Passo 2*: Backup local em arquivo JSON.
   - *Passo 3*: Configurações de som, notificações e temas.

---

## 2. Experiência do Usuário & Design Visual

```
┌────────────────────────────────────────────────────────┐
│                   Tela Escurecida                      │
│                                                        │
│            ┌───────────────────────────┐               │
│            │   [ ÁREA ILUMINADA ]      │ ◄── Recorte   │
│            │   Elemento da Interface   │     arredondado
│            └─────────────┬─────────────┘               │
│                          │                             │
│                  ┌───────┴───────┐                     │
│                  │  ▲ Seta Guia  │                     │
│    ┌─────────────┴───────────────┴────────────────┐    │
│    │ 🍊 CITRA GUIA                      (1 de 3)  │    │
│    │                                              │    │
│    │ Toque no play para iniciar um ciclo Pomodoro │    │
│    │ focado diretamente nesta tarefa. As sementes │    │
│    │ do relógio marcarão o tempo.                 │    │
│    │                                              │    │
│    │ [Pular Tudo]         [Pular]      [Próximo] │    │
│    └──────────────────────────────────────────────┘    │
└────────────────────────────────────────────────────────┘
```

### Regras do Balão e Destaque
- **Máximo de 1 parágrafo e 5 linhas**: Texto limpo, sem prolixidade, direto ao benefício prático.
- **Identidade Cítrica**: Balão estilizado com bordas arredondadas (18.dp), tonalidade acolhedora com badge laranja cítrico (`#FF8C00`), tipografia nítida e botão de ação primário destacado.
- **Posicionamento Inteligente**: O balão detecta se o elemento focado está na metade superior ou inferior da tela, posicionando-se automaticamente no lado oposto para nunca cobrir o recorte iluminado.
- **Comportamento dos 3 Botões**:
  - `Próximo`: Incrementa o passo da área atual com animação fluida de transição de coordenadas.
  - `Pular`: Encerra o tutorial daquela área específica e grava que essa área já foi vista. Ao abrir outra área pela primeira vez, o tutorial daquela nova área funcionará normalmente.
  - `Pular Tudo`: Grava flag global `allTutorialsSkipped = true`, encerrando imediatamente qualquer tutorial no app.

---

## 3. Arquitetura Técnica & Gerenciamento de Estado

```
┌───────────────────────────────────────────────────────────────┐
│                    PreferencesManager                         │
│  - completedTutorialAreas: Set<String>                        │
│  - allTutorialsSkipped: Boolean                               │
│  - resetTutorials(): restaura todas as áreas                  │
└───────────────────────────────┬───────────────────────────────┘
                                │
                                ▼
┌───────────────────────────────────────────────────────────────┐
│                      MainViewModel                            │
│  - activeTutorial: StateFlow<TutorialState?>                  │
│  - triggerTutorial(area: TutorialArea)                        │
│  - nextStep() / skipArea() / skipAll()                        │
└───────────────────────────────┬───────────────────────────────┘
                                │
                                ▼
┌───────────────────────────────────────────────────────────────┐
│                   CitraSpotlightOverlay                       │
│  - Canvas com BlendMode.Clear (recorte transparente)          │
│  - Animatable Rect (bounds suavizados entre transições)       │
│  - Balão de Diálogo Cítrico com botões de ação                │
└───────────────────────────────────────────────────────────────┘
```

### Captura de Posição de Elementos (Spotlight Targets)
- Usaremos um modificador reutilizável `Modifier.spotlightTarget(area, stepIndex, state)` ou coordenadas calculadas por `onGloballyPositioned`, permitindo que o overlay descubra os limites exatos (`Rect`) do componente no espaço de coordenadas da janela.
- Caso um elemento ainda não tenha sido medido (ex: tela ainda renderizando), o spotlight faz um fallback elegante centralizado com animação de expansão.

---

## 4. Plano de Execução por Etapas

1. **Estado de Persistência (`PreferencesManager.kt`)**:
   - Salvar áreas concluídas (`Set<String>`) e flag `all_tutorials_skipped`.
   - Adicionar método `resetAllTutorials()` para a opção nas Configurações.
2. **Componente Visual (`CitraSpotlightOverlay.kt`)**:
   - Fundo escurecido semi-transparente (`Color.Black.copy(alpha = 0.72f)`).
   - Recorte vazado com `Path` e `DrawScope.drawPath` com `BlendMode.Clear` ou `clipPath(PathOperation.Difference)`.
   - Balão temático com contador de passos (ex: `1/4`), texto de até 5 linhas e botões *Pular Tudo*, *Pular* e *Próximo*.
3. **Integração nas Telas**:
   - `HomeScreen.kt`: Tutorial ao abrir a tela inicial pela 1ª vez.
   - `OrganizationScreen.kt` (Tarefas, Hábitos, Notas): Disparo ao abrir cada sub-aba.
   - `StatsScreen.kt`: Disparo ao abrir a tela de dados.
   - Menu superior de 3 pontos: Disparo ao abrir o menu pela 1ª vez.
4. **Configurações (`SettingsDialog.kt`)**:
   - Adicionar opção "Rever Tutoriais do Aplicativo" na seção de Ajuda/Sobre, permitindo redefinir os tutoriais a qualquer momento.
5. **Verificação & Compilação**:
   - Validação completa com `compile_applet`.
