# 🍊 Notas de Atualização — Citra

Bem-vindo às notas de atualização do **Citra**! Aqui pode acompanhar todas as novidades e melhorias adicionadas desde a versão **1.0**.

---

## 🍊 Versão 1.2.3 (Atual)
- **Mascote no Ecrã de Carregamento:** Substituição do logótipo estático pelo mascote animado **Citra** (`CitraMascote`) no ecrã de carregamento inicial.
- **Separação Visual & Contraste Aprimorado (Modo Claro e Escuro):** Refinamento da hierarquia tonal entre o fundo, cartões (`surface`), pílulas, círculos e contornos (`outlineVariant`) mantendo o tom quente característico do Citra.
- **Nova Interface de Conquistas:** Cartão de resumo com troféu, progresso do **Nível Pomar**, barra motivacional, filtros por categoria com contagem e cartões de medalhas com data de desbloqueio.
- **Novas Interfaces de Finanças (Despesas & Rendas):**
  - Cartão de **Saldo Atual** com resumo rápido das carteiras, alternância segmentada **Despesas / Rendas**, navegador de período com faixa de 5 dias e totais diários.
  - Gráfico Donut com total central, legenda por categoria, grade de **Categorias de Renda** e cartão de vinculação rápida a notas.
- **Nova Interface de Carteiras & Contas:** Cartão de **Património Total Consolidado**, cartões de carteiras com barra de participação (`% do total`), **Transferência Rápida** integrada com botão de inversão, resumo de **Câmbio & Multi-Moeda** e lista de **Últimas Movimentações**.

---

## 🍊 Versão 1.2.2
- **Mascotes Citra Interativos no Timer Arancia:**
  - Cada sessão de foco concluída no modo Timer gera um mascote Citra proporcional à duração da sessão (`15` a `120` minutos).
  - Física de bolas em tempo real com gravidade por inclinação do telemóvel (acelerómetro), colisões entre si e toque duplo para explodir e desaparecer.
  - Opção para resetar os mascotes do Timer em **Configurações**.
- **Modo Arancia:** Substituição completa da nomenclatura *Pomodoro* por **Arancia** em toda a aplicação.
- **Editor de Notas Aprimorado:**
  - Barra de formatação fixada na parte inferior por padrão, com opção de desafixar/fixar no menu avançado.
  - Seleção de múltiplos parágrafos transforma todos em tarefas (`- [ ] `) ou lista (`- `), e envolve parágrafos selecionados em blocos de Código, Math, Callout ou Recolhível.
  - Seletor de linguagem no editor de código em ecrã inteiro posicionado acima dos botões de ação para evitar colisões.
  - Remoção do ícone de régua (`📐`) nos blocos matemáticos em modo leitura e exportação.
  - Salvamento automático da nota aberta antes de exibir o ecrã de alarme `00:00`.
- **Organização & Lixeira de Notas:**
  - Opções **Restaurar todas** e **Esvaziar lixeira** na lixeira de notas, e alternância de visualização em grade/lista nos modelos de rascunho rápido.
  - Removida a leitura automática da pasta `Citra Docs` ao iniciar.
- **Alarmes, Fundo, Sincronização & Tutoriais:**
  - Nome da tarefa ou hábito exibido no ecrã de alarme `00:00`.
  - Correção de estabilidade ao definir imagem de fundo e eliminação definitiva ao remover.
  - Opção para ativar ou desativar a sincronização automática na nuvem.
  - Tutorial interativo atualizado para a nova interface (incluindo guia completo de Finanças sem limite de mensagens), reativável em **Dados → Tutorial e Dicas do Citra**, e animação de entrada do mascote sempre ativa no ecrã de login.

---

## 🚀 Versão 1.2.1
- **Carteiras e Contas:** Gerencie múltiplas carteiras (*Dinheiro vivo*, *Conta Bancária*, *Carteira Móvel*), acompanhe o saldo individual de cada uma e faça **transferências** entre elas.
- **Multi-moeda com Câmbio Manual:** Registe lançamentos em diferentes moedas (`Kz`, `$`, `€`, `R$`, `£`), defina taxas de câmbio manuais com **histórico de taxas** e veja os totais convertidos automaticamente para a sua moeda padrão.
- **Orçamentos por Categoria:** Defina limites mensais por categoria com barras de progresso e **alertas automáticos aos 80% e aos 100%**.
- **Lançamentos Recorrentes:** Configure receitas e despesas automáticas (*Salário*, *Renda*, *Internet*, etc.) com frequência diária, semanal, mensal ou anual e **lembretes antecipados**.
- **Planeado vs. Real (Lista de Compras):** Adicione itens planeados com preço previsto e marque **✔ Pago** ao comprar para registar o valor real e ver a diferença poupada ou gasta a mais.
- **Backup Completo na Nuvem:** Carteiras, taxas de câmbio, orçamentos, recorrências e itens planeados incluídos na sincronização incremental por UUID.

---

## 🔗 Versão 1.2
- **Módulo de Finanças (Opcional e Isolado):**
  - Desligado por padrão em **Configurações → Finanças** (desligar oculta a aba sem apagar nenhum dado).
  - Assistente de configuração inicial com escolha de moeda (Kwanza `Kz` em destaque), saldo inicial e bloqueio opcional por **PIN ou Biometria**.
  - Registo rápido de **Despesas e Rendas** (por valor total ou quantidade × preço unitário), categorias personalizáveis, etiquetas, prioridades (*Obrigatória* / *Opcional*), anexos de talões, modo *ocultar valores* e gráfico de pizza por categoria.
- **Vínculo de Finanças com Notas Markdown:**
  - Ligue as suas finanças a notas novas ou existentes em `Documentos/Citra Docs/` com **blocos geridos invisíveis** (`<!-- citra:fin:inicio -->` ... `<!-- citra:fin:fim -->`) que preservam 100% dos seus parágrafos e checklists manuais.
  - **6 Modelos Prontos:** *Despesas Mensais*, *Rendas do Mês*, *Balanço Completo*, *Lista de Compras*, *Relatório Semanal* e *Relatório Anual*.
  - Agendamento automático (*Diária*, *Semanal*, *Mensal*, *Anual*, *Manual*), resumo de saldo, simulação com despesas opcionais, resumo automático, gráfico Mermaid, pré-visualização e **Desfazer última atualização**.
- **Backup na Nuvem e Restauro Inteligente:**
  - Sincronização automática e manual (**"Fazer backup agora"**) na conta Google com fusão por UUID e data de atualização, além de restauro automático ao ativar o módulo num novo aparelho.
- **Mascote Interativo Citra & Identidade Visual:**
  - Novo mascote vetorial animado no ecrã de login e início, com seguimento do olhar ao tocar ou escrever, animações de apresentação e novo ícone adaptativo.

---

## ✨ Versão 1.1
- **Timer & Cronómetro Minimalista:**
  - Modo imersivo em ecrã inteiro durante sessões de foco e cronómetro livre, com definições opcionais recolhidas e transição suave.
- **Frases Diárias & Modelos de Notas:**
  - Mensagens diárias personalizáveis por categoria no ecrã inicial e menu de rascunho rápido com modelos de notas prontos.
- **Perfil e Estatísticas Refinados:**
  - Visualização limpa com gráfico de pizza de distribuição de foco e métricas essenciais.

---

## 🌱 Versão 1.0
- **Lançamento Inicial do Citra:**
  - Gestão completa de **Tarefas**, **Hábitos** com sequências e lembretes, **Timer Arancia**, **Calendário** integrado e **Notas Markdown** abertas em `Documentos/Citra Docs/`.
  - Sincronização na nuvem com conta Google, temas dinâmicos Material 3, conquistas e widgets para o ecrã inicial.
