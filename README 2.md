# 🍊 Citra — Guia Completo e Documentação Estendida

![Citra Banner](https://via.placeholder.com/1200x400/FF6F00/FFFFFF?text=Citra+%E2%80%94+Gamified+Productivity+%26+Focus+Ecosystem)

> **Citra** é um ecossistema completo de produtividade gamificada para Android construído nativamente com **Kotlin** e **Jetpack Compose**. Combinando uma estética cítrica acolhedora, princípios de **Material Design 3**, arquitetura offline-first robusta e sincronização em nuvem, o Citra unifica gerenciamento de hábitos, tarefas com alarmes de alta precisão, cronômetro Pomodoro imersivo com paisagens sonoras, calendário unificado e um editor de notas estilo Obsidian de última geração com suporte a fórmulas LaTeX matemáticas e químicas.

---

## 📑 Sumário

1. [Visão Geral e Filosofia](#-visão-geral-e-filosofia)
2. [Funcionalidades em Destaque](#-funcionalidades-em-destaque)
   - [Cronômetro Pomodoro & Paisagens Sonoras](#1-cronômetro-pomodoro--paisagens-sonoras)
   - [Tela de Alarme Dedicada](#2-tela-de-alarme-dedicada-alarmringingscreen)
   - [Gerenciamento de Tarefas Inteligentes](#3-gerenciamento-de-tarefas-inteligentes)
   - [Rastreador de Hábitos & Gamificação](#4-rastreador-de-hábitos--gamificação)
   - [Editor de Notas Smooth Markdown (Estilo Obsidian)](#5-editor-de-notas-smooth-markdown-estilo-obsidian)
   - [Fórmulas Matemáticas e Químicas em LaTeX](#6-fórmulas-matemáticas-e-químicas-em-latex)
   - [Tabelas Dinâmicas Harmonizadas](#7-tabelas-dinâmicas-harmonizadas)
   - [Exportação Completa de Notas](#8-exportação-completa-de-notas)
   - [Calendário Unificado & Timeline](#9-calendário-unificado--timeline)
   - [Estatísticas, Métricas & Perfil](#10-estatísticas-métricas--perfil)
   - [Backup, Nuvem e Persistência](#11-backup-nuvem-e-persistência)
3. [Arquitetura e Tecnologias](#-arquitetura-e-tecnologias)
4. [Estrutura do Projeto](#-estrutura-do-projeto)
5. [Guia de Utilização das Fórmulas e Markdown](#-guia-de-utilização-das-fórmulas-e-markdown)
6. [Autoria e Créditos](#-autoria-e-créditos)

---

## 💡 Visão Geral e Filosofia

O Citra foi desenvolvido para resolver a fragmentação comum em ferramentas de produtividade. Em vez de exigir múltiplos aplicativos para focar, listar tarefas, manter hábitos e redigir anotações ricas, o Citra reúne tudo em um único fluxo de trabalho harmonioso:

- **Estética Orgânica e Cítrica:** Interface moderna baseada em tons quentes de laranja e âmbar, suporte nativo a temas Claro e Escuro (Material 3).
- **Sem Fricção:** Ações rápidas para criar tarefas, registrar check-ins de hábitos e iniciar blocos de foco.
- **Offline-First com Nuvem Opcional:** Todos os dados pertencem primeiramente ao dispositivo (Room SQLite), com sincronização em tempo real via Firebase Firestore quando conectado.
- **Gamificação Construtiva:** Sistema de níveis, experiência (XP) e conquistas que premiam consistência real e não apenas volume de cliques.

---

## 🚀 Funcionalidades em Destaque

### 1. Cronômetro Pomodoro & Paisagens Sonoras

- **Ciclos Dinâmicos de Foco:** Configuração de intervalos de 10 a 120 minutos (em passos de 5 minutos), com controles específicos para pausas curtas e longas.
- **Visualizador Cítrico Interativo:** Mostrador geométrico inspirado nos gomos de uma laranja, com animações suaves e indicação visual de progresso.
- **Persistência em Segundo Plano:** O timer sobrevive ao fechamento do app, pausas do sistema e mudanças de orientação, mantendo contagem real via timestamp.
- **Paisagens Sonoras Integradas (`SoundEffectsManager` & `AmbientBreakMusicManager`):**
  - Sons relaxantes e analógicos: *Analog Warm*, *Deep Resonant*, *Light Chimes*, *Upbeat* e *Celebration*.
  - Música ambiente de fundo (*Fundo - By Dyven Clade*).
  - Suporte para reprodução de áudios locais do dispositivo durante pausas ou sessões de foco.
- **Classificação por Categorias:** Associação de cada bloco de foco a uma categoria colorida com emoji representativo.

---

### 2. Tela de Alarme Dedicada (`AlarmRingingScreen`)

Desenvolvida especialmente para tarefas e hábitos configurados para toque em loop:
- **Ativação Universal:** A tela abre automaticamente ao tocar na notificação do sistema Android ou ao abrir o aplicativo enquanto o alarme estiver soando.
- **Design Ultra-Focado:** Fundo preto minimalista com relógio digital monospace `"00:00"` pulsando no centro.
- **Apenas Dois Controles Essenciais:**
  - 💤 **Adiar 10 minutos:** Para imediatamente o áudio e reagenda o alarme com precisão para dali a 10 minutos via `AlarmManager.setExactAndAllowWhileIdle`.
  - ⏹️ **Desligar:** Encerra instantaneamente o loop sonoro, desativa a vibração e cancela a notificação correspondente.

---

### 3. Gerenciamento de Tarefas Inteligentes

- **Criação Instantânea:** Formulário simplificado que exige apenas o nome da tarefa e a data, permitindo adicionar prioridades (Baixa, Média, Alta) e lembretes de forma opcional.
- **Motor de Alarmes Confiável (`NotificationHelper`):**
  - Uso de permissões de alarme exato (`SCHEDULE_EXACT_ALARM` e `USE_EXACT_ALARM`).
  - Recuperação automática de alarmes programados após reinicialização do dispositivo (`BootReceiver`).
  - Suporte a alarmes sonoros em repetição (loop) e som único.
- **Classificação e Filtros:** Separação entre pendentes, concluídas, por categoria ou por data de entrega.

---

### 4. Rastreador de Hábitos & Gamificação

- **Sequências e Streaks:** Acompanhamento do progresso diário, semanal e cálculo de sequências consecutivas (fogo/streak).
- **Feedback Sonoro de Conquista:** Efeito sonoro gratificante e tátil ao registrar o check-in do hábito.
- **Sistema de Conquistas (`AchievementsDialog`):**
  - Diversos troféus desbloqueáveis conforme o uso contínuo do app (ex.: primeiro hábito, 7 dias de streak, 10 horas de foco).
  - Notificações de conquista com efeito sonoro especial de celebração.
- **Níveis e Pontos de Experiência (XP):** Cada tarefa finalizada, sessão de foco completada e hábito realizado concede pontos de experiência para evolução de perfil.

---

### 5. Editor de Notas Smooth Markdown (Estilo Obsidian)

- **Modo Visual (WYSIWYG Moderno):** Oculte a poluição de marcadores como `###` ou `**` durante a leitura, exibindo tipografia rica diretamente na tela.
- **Elementos Ricos Suportados:**
  - Títulos hierárquicos (H1 até H5) com dimensionamento adaptativo.
  - Caixas de verificação (Checklists interativas).
  - Blocos de Destaque (*Callouts* de informação, atenção, sucesso e erro).
  - Blocos Recolhíveis (*Toggles* / seções sanfona).
  - Blocos de Código com destaque de sintaxe sintático.
  - Links Bidirecionais no estilo Obsidian (`[[Nome da Nota|Apelido]]`) que permitem navegar entre anotações.
- **Gravação e Reprodução de Áudio Local:** Grave notas de voz diretamente no corpo do texto e ouça com o mini-reprodutor integrado.
- **Inserção de Imagens e Mídia:** Anexe imagens locais da galeria com visualização em alta definição dentro da nota.
- **Lixeira com Recuperação de 30 Dias:** Proteção contra exclusão acidental.

---

### 6. Fórmulas Matemáticas e Químicas em LaTeX

O Citra conta com o `LatexFormulaRenderer`, um analisador e renderizador nativo de expressões científicas:
- **Fórmulas Inline (`$x$`):**
  - Integradas ao fluxo natural do parágrafo com tamanho de fonte proporcional.
  - Exemplo: `$E=mc^2$`, `$a^2 + b^2 = c^2$`.
- **Fórmulas em Bloco / Display (`$$x$$`):**
  - Centralizadas, em tamanho maior e com destaque visual sutil.
  - Exemplo: `$$\frac{-b \pm \sqrt{b^2 - 4ac}}{2a}$$`.
- **Fórmulas Químicas com `\ce{...}`:**
  - Formatação precisa de elementos químicos, estados físicos, cargas e reações.
  - Exemplos: `$\ce{H2O}$`, `$\ce{2H2 + O2 -> 2H2O}$`, `$\ce{H2SO4}$`.
- **Prevenção de Falhas:** Em caso de sintaxe incorreta, o app exibe uma etiqueta de aviso amigável sem travar a nota ou o leitor.
- **Renderização Idêntica:** As fórmulas funcionam tanto no modo de leitura dentro do aplicativo quanto nos arquivos exportados em imagem e PDF.

---

### 7. Tabelas Dinâmicas Harmonizadas

- **Integração Total ao Tema (Light / Dark):**
  - Eliminação de blocos sólidos ou fundos que quebram o contraste.
  - Linhas e bordas finas com tons de `outlineVariant`.
  - Cabeçalhos suavemente destacados com tonalidade sutil (`primaryContainer`/`surfaceVariant`).
  - Fundo translúcido ou neutro para perfeita leitura em temas escuros e claros.
- **Largura de Colunas Auto-Ajustável:**
  - A largura de cada coluna calcula dinamicamente o maior texto nela contido, otimizando o espaço da tela em celulares e tablets.
  - Quebra de linha automática (sem cortar ou truncar palavras).
- **Formatação Rica Dentro de Células:**
  - Cada célula aceita **negrito** (`**`), *itálico* (`*`), `código inline` (`` ` ``), tachado (`~~`), <u>sublinhado</u> (`<u>`), ==destaque== (`==`) e fórmulas matemáticas (`$...$`).
  - Barra de ferramentas rápida para formatação na edição de tabelas.
  - Persistência garantida: a formatação é mantida ao salvar, alternar telas e ao exportar.

---

### 8. Exportação Completa de Notas

- **Exportação em Imagem (`exportAsImage`):**
  - **Captura Integral:** Converte a nota do início ao fim em imagem de alta resolução (PNG), sem limite artificial de altura.
  - **Formato Vertical:** Proporção perfeita para leitura e compartilhamento em mensageiros e redes sociais.
  - **Quebra Inteligente:** Textos longos e tabelas respeitam as margens laterais.
  - **Cores & Mídia:** Mantém o tema cromático da nota e renderiza imagens embutidas.
  - **Zero Espaço Vazio:** O corte inferior é calculado exatamente onde o conteúdo termina, descartando qualquer sobra em branco.
- **Outros Formatos Suportados:**
  - Arquivo Markdown puro (`.md`)
  - Documento de Texto (`.txt`)
  - Documento Web (`.html`)
  - Documento para Impressão (`.pdf`)

---

### 9. Calendário Unificado & Timeline

- **Visão Centralizada:** Apresenta na mesma grade diária as tarefas agendadas, os hábitos a cumprir e as notas criadas na respectiva data.
- **Filtros Visuais Rápidos:** Botões de alternância para exibir ou ocultar tarefas, hábitos ou notas, mantendo o calendário limpo conforme a necessidade do momento.
- **Navegação Temporal:** Deslocamento ágil entre meses e anos com indicador do dia atual.

---

### 10. Estatísticas, Métricas & Perfil

- **Relatórios de Produtividade (`StatsScreen`):**
  - Tempo total de foco diário, semanal e mensal.
  - Gráficos de distribuição por categorias.
  - Histórico de taxas de conclusão de hábitos e tarefas.
- **Perfil do Usuário (`ProfileScreen`):**
  - Foto de avatar e biografia customizável.
  - Exibição de nível atual, barra de progresso para o próximo nível e total de XP acumulado.
  - Conexão e compartilhamento de perfil com amigos via código alfanumérico único de 9 caracteres ou leitor/gerador de **QR Code** integrado.
  - Modo público ou privado para controle de visibilidade.

---

### 11. Backup, Nuvem e Persistência

- **Arquitetura 100% Local-First:**
  - Banco de dados relacional **Room (SQLite)** integrado, garantindo velocidade instantânea e funcionamento sem internet.
- **Exportação e Importação Manual (JSON):**
  - Permite criar backups locais completos em arquivo `.json` e restaurá-los em qualquer dispositivo com um toque.
- **Armazenamento Automático do Dispositivo (`AutoDeviceStorageManager`):**
  - Salvaguarda automática e organizada no armazenamento interno do aparelho.
- **Sincronização com Firebase Firestore:**
  - Login seguro via Google Sign-In (`CredentialManager`).
  - Sincronização em nuvem das tarefas, hábitos, notas e histórico de estatísticas.

---

## 🛠️ Arquitetura e Tecnologias

| Componente | Tecnologia Utilizada | Finalidade |
| :--- | :--- | :--- |
| **Linguagem** | Kotlin 2.x | Código conciso, seguro contra nulos e moderno |
| **Interface (UI)** | Jetpack Compose + Material 3 | Layouts reativos, fluidos e adaptativos |
| **Banco Local** | Room Database (SQLite) + KSP | Persistência local estruturada e reativa com `Flow` |
| **Estado & Concorrência** | ViewModel, StateFlow, Coroutines | Padrão MVVM desacoplado e performático |
| **Áudio & Efeitos** | Android MediaPlayer / SoundPool | Paisagens sonoras, sinos e alarmes em loop |
| **Alarmes Exatos** | `AlarmManager` + `BroadcastReceiver` | Disparo pontual mesmo sob economia de bateria e após reboot |
| **Backend & Nuvem** | Firebase Firestore, Firebase Auth | Sincronização multi-dispositivo e perfil social |
| **Renderização Científica**| `LatexFormulaRenderer` nativo | Parser e renderizador de fórmulas LaTeX e química `\ce` |
| **Compartilhamento** | `QrCodeGenerator` | Geração rápida de QR Code para amizades e notas |

---

## 📁 Estrutura do Projeto

```
app/src/main/java/com/example/
├── MainActivity.kt                       # Ponto de entrada, navegação e interceptação de alarmes
├── audio/
│   └── LocalMusicPlayer.kt               # Reprodutor de áudios locais e pausas
├── data/
│   ├── auth/AuthRepository.kt            # Autenticação e credenciais Google/Firebase
│   ├── backup/                           # Gerenciadores de exportação e restauração JSON
│   ├── local/                            # Entidades, DAOs e banco Room (AppDatabase)
│   ├── model/                            # Modelos de dados (Task, Habit, Note, Achievement, etc.)
│   └── repository/CitraRepository.kt     # Repositório central que une dados locais e nuvem
├── notifications/
│   ├── BootReceiver.kt                   # Restaura alarmes após reinicialização do Android
│   ├── CitraNotificationReceiver.kt     # Recebe broadcasts e aciona toque de notificações
│   └── NotificationHelper.kt             # Criação de canais, alarmes exatos e controle de loop
├── ui/
│   ├── MainViewModel.kt                  # Estado global da aplicação
│   ├── components/                       # Diálogos de tutorial, conquistas, backup e novas funções
│   ├── navigation/Screen.kt              # Rotas e navegação da interface
│   ├── screens/
│   │   ├── alarm/AlarmRingingScreen.kt   # Tela dedicada de alarme com toque em loop
│   │   ├── auth/AuthScreen.kt            # Tela de autenticação e boas-vindas
│   │   ├── calendar/CalendarScreen.kt    # Calendário unificado
│   │   ├── home/HomeScreen.kt            # Painel principal
│   │   ├── organization/                 # Abas de Hábitos, Tarefas e Notas
│   │   │   └── notes/                    # Editor Obsidian, componentes e renderização
│   │   ├── profile/ProfileScreen.kt      # Perfil do usuário, QR Code e amigos
│   │   ├── stats/StatsScreen.kt          # Estatísticas, gráficos e tempo de foco
│   │   └── timer/TimerScreen.kt          # Cronômetro Pomodoro cítrico
│   └── theme/                            # Definições de cores, tipografia e temas M3
└── util/
    ├── LatexFormulaRenderer.kt           # Renderizador de fórmulas LaTeX e química
    ├── InlineMarkdownHelper.kt           # Analisador de formatação rica inline
    ├── NoteExportHelper.kt               # Exportador para imagem, PDF, HTML e Markdown
    ├── QrCodeGenerator.kt                # Gerador nativo de imagens QR Code
    └── SoundEffectsManager.kt            # Gerenciador de efeitos sonoros e sinos
```

---

## 📖 Guia de Utilização das Fórmulas e Markdown

### Fórmulas Matemáticas
- **Inline:** `$f(x) = x^2 + 2x + 1$`
- **Fração:** `$$\frac{a}{b}$$`
- **Raiz Quadrada:** `$$\sqrt{x^2 + y^2}$$`
- **Equação Célebre:** `$E = mc^2$`
- **Limites e Somas:** `$$\sum_{i=1}^{n} i$$`

### Fórmulas Químicas
Utilize o comando `\ce{...}` dentro dos delimitadores de fórmula:
- **Água:** `$\ce{H2O}$`
- **Glicose:** `$\ce{C6H12O6}$`
- **Ácido Sulfúrico:** `$\ce{H2SO4}$`
- **Reação Química:** `$\ce{2H2 + O2 -> 2H2O}$`

### Tabelas com Formatação
Basta utilizar as barras verticais e a sintaxe markdown convencional. As colunas se auto-ajustarão:

```markdown
| Disciplina | Conteúdo | Status | Expressão |
| :--- | :--- | :--- | :--- |
| Física | Termodinâmica | **Concluído** | $PV = nRT$ |
| Química | Soluções | *Revisando* | $\ce{NaCl}$ em $\ce{H2O}$ |
| Matemática | Geometria Analítica | ==Prioritário== | $x^2 + y^2 = r^2$ |
```

---

## 📦 Ficheiro APK

Para instalação e testes diretos no dispositivo, o arquivo compilado está disponível na raiz do repositório:
- **Ficheiro:** `Citra 0.7.10.1.apk` (ou `Citra 0.7.10.1`)

---

## 👤 Autoria e Créditos

- **Desenvolvedor:** Márcio Rey
- **E-mail:** marcioreyakm@gmail.com
- **WhatsApp:** +244 932 918 030
- **Processo:** Desenvolvimento assistido e refinado com Google AI Studio.

---

*Citra — O seu companheiro diário para foco, hábitos e anotações sem distrações.*
