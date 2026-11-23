**implementation\_plan Finanças**

**\[ Categoria: Geral \]**  • Exportado pelo Citra em 23/11/2026 13:48

# **Simplificação da Experiência de Finanças no Citra**

Reorganização completa da área de Finanças para eliminar a sobrecarga visual e cognitiva, separando o acompanhamento diário em três abas claras (**Resumo**, **Movimentos** e **Ferramentas**), transformando o cadastro de lançamentos em um fluxo rápido em duas camadas e reduzindo a curva de aprendizado sem perder nenhum recurso existente.

### **Decisões Confirmadas e Pontos de Atenção**

**IMPORTANT:** 

*As decisões principais abaixo foram confirmadas na etapa de alinhamento e orientam toda a reestruturação.*

* **Arquitetura em 3 Abas Limpas**: Separação da tela principal em **Resumo**, **Movimentos** e **Ferramentas**, seguindo o mesmo padrão visual de pílula deslizante já usado em Organização.

* **Cadastro Rápido \+ Mais Opções**: O formulário de lançamento passa a exigir apenas **Valor**, **Nome** e **Categoria** à primeira vista (com seletor simples de Despesa ou Renda), recolhendo campos avançados (Quantidade × Preço Unitário, Carteira, Moeda diferente da padrão, Prioridade, Etiquetas, Anexo e Item Planeado) dentro de um painel expansível **Mais opções**.

* **Ferramentas Avançadas Preservadas e Separadas**: **Carteiras**, **Orçamentos**, **Recorrências**, **Lista Planeada**, **Câmbio** e **Vínculo com Notas** saem da tela principal e passam a morar na aba **Ferramentas**, preservando 100% dos dados e fluxos existentes.

* **Padrão Recomendado para Tutorial e Limpeza**: Redução do tutorial guiado de Finanças de 8 passos longos para **3 passos objetivos** (um por aba) e remoção de controles duplicados (hoje o campo Carteira e os seletores de Moeda aparecem repetidos dentro do mesmo formulário de lançamento).

### **1\. Visão Geral & Conceito Central**

* **O que entrega**: Uma experiência financeira progressiva onde 90% das ações cotidianas (ver quanto dinheiro tem, ver quanto entrou vs. saiu no período e registrar um gasto em segundos) acontecem em telas limpas e diretas, enquanto recursos de controle avançado ficam organizados como utilitários sob demanda.

* **Público-alvo**: Usuários do Citra que querem registrar despesas e receitas no dia a dia sem precisar aprender conceitos complexos (como blocos Markdown, taxas de câmbio manuais ou itens planeados) antes do primeiro uso.

* **Valor Principal**: Redução drástica do atrito no primeiro contato e no registro diário ("3 segundos para lançar um gasto"), eliminando a sensação de "tudo misturado na mesma tela".

### **2\. Experiência do Usuário & Design Visual**

#### **Fluxos Principais do Usuário**

**1\. Primeiro Contato & Aba "Resumo" (Visão Geral Limpa)**

* **Cartão de Saldo Único**: Exibe o saldo atual consolidado, seletor rápido da moeda padrão, botão de ocultar valores (privacidade) e atalho discreto para configurações/bloqueio por PIN.

* **Placar "Entrou vs. Saiu" do Período**: Substitui a alternância escondida entre Despesas e Rendas no topo por um resumo lado a lado mostrando **Rendas (+)**, **Despesas (−)** e o **Balanço do período** no navegador de período (Dia, Semana, Mês, Ano).

* **Gráfico de Distribuição Limpo**: Gráfico de rosca focado nas principais categorias de despesa (ou renda, ao tocar no filtro), acompanhado de no máximo 4–5 fatias principais e um estado vazio amigável com dica de ação direta.

* **Botão de Ação Principal Único**: CTA proeminente e fixo/acessível (\+ Novo lançamento) para abrir o cadastro rápido de qualquer aba de uso diário.

**2\. Aba "Movimentos" (Histórico e Busca Sem Ruído)**

* **Filtros Objetivos**: Pílula simples para alternar entre **Todos**, **Despesas** e **Rendas**, acompanhada do seletor de período (Mês/Semana/Dia/Personalizado) e barra de busca rápida por nome ou categoria.

* **Lista Agrupada por Data**: Movimentos organizados cronologicamente com cartões enxutos (ícone da categoria, nome, categoria/carteira, valor colorido e menu de ação rápida: Editar, Duplicar, Apagar).

* **Zero Mistura**: Itens planeados (lista de compras) e convites para vincular notas não interrompem o histórico real de movimentações.

**3\. Aba "Ferramentas" (Recursos Avançados Organizados)**

* Grade ou lista limpa de cartões explicativos com contadores em tempo real (ex.: *"2 carteiras"*, *"3 orçamentos"*, *"1 lista ativa"*):

* **Carteiras & Contas**: Saldos por conta e transferências rápidas.

* **Planeados & Lista de Compras**: Itens previstos com botão de confirmar pagamento (✔ Pago) para converter em despesa real.

* **Orçamentos por Categoria**: Limites mensais com barras de progresso e alertas (80% / 100%).

* **Lançamentos Recorrentes**: Contas e salários automáticos com lembretes.

* **Vínculos com Notas (Citra Docs)**: Criação simplificada e gestão de notas Markdown vinculadas.

* **Câmbio & Moedas**: Taxas manuais para quem opera com múltiplas moedas.

**4\. Fluxo de "Cadastro Rápido" de Lançamento**

* **Camada Essencial (Visível imediatamente)**:

**1\.** Alternador **Despesa / Renda**.

**2\.** Campo **Valor** em destaque numérico grande (com símbolo da moeda padrão).

**3\.** Campo **Nome** (ex.: *Almoço*, *Transporte*, *Salário*).

**4\.** Chips visuais das **Categorias** (com botão discreto para criar/gerir).

* **Camada Expansível ("Mais opções ▾")**: Recolhida por padrão (abre automaticamente ao editar um item que já possua esses dados preenchidos):

* Data do lançamento (padrão: hoje).

* Quantidade × Preço unitário.

* Carteira de origem/destino (sem campo de texto duplicado).

* Moeda diferente da padrão.

* Prioridade (Obrigatória vs. Opcional).

* Comentário, Etiquetas e Foto de comprovante.

* Marcar como "Planeado (Lista de compras)".

#### **Identidade Visual & Tema**

* **Direção Estética**: Continuidade fiel ao sistema visual quente e tátil do Citra, priorizando espaço em branco, bordas sutis (outlineVariant) e hierarquia numérica clara em vez de densidade informacional.

* **Paleta & Tokens**: Mantém integralmente os tokens do tema atual (CreamBackground / DarkBackground, surface, surfaceContainerLow), usando o primary dinâmico do usuário (ThemeAccent) para ações principais, GreenAccent exclusivamente para entradas/rendas/saldos positivos e RedAction exclusivamente para alertas de limite, exclusões e saldos negativos.

* **Tipografia & Hierarquia**: Mantém a família Plus Jakarta Sans configurada no projeto, reforçando contraste entre rótulos curtos (11–12 sp), títulos de seção (15–16 sp) e valores financeiros (22–28 sp ExtraBold) para leitura instantânea.

* **Componentes & Espaçamento**: Barra superior segmentada com pílula deslizante animada (CircleShape) para as 3 abas (idêntica à de Organização), cartões arredondados (20–24 dp) com espaçamento consistente de 12–16 dp na grade de 8 dp e alvos de toque mínimos de 48 dp.

* **Feedback Interativo & Movimento**: Transições suaves entre abas e abertura animada (AnimatedVisibility) da seção "Mais opções" no editor de lançamento, além de validação inline imediata ao tentar salvar sem nome ou valor.

### **3\. Decisões de Produto & Trade-offs**

* **Decisão 1: Navegação Interna em 3 Sub-abas vs. Menu Overflow \+ Tela Única Longa**

* *Abordagem Escolhida*: Substituir o menu de três pontinhos lotado e a rolagem única longa por uma barra segmentada fixa de 3 abas (**Resumo**, **Movimentos**, **Ferramentas**).

* *Por quê*: Os usuários não precisam decorar onde está cada função escondida nos três pontinhos nem rolar por gráficos, lista de compras e links de notas ao mesmo tempo. Tudo ganha um lugar lógico e previsível.

* *Alternativas Consideradas*: Manter tela única recolhendo cards em accordions — descartada porque ainda manteria o menu overflow complexo e misturaria o resumo com o histórico detalhado.

* **Decisão 2: Divulgação Progressiva no Editor de Lançamento**

* *Abordagem Escolhida*: Exibir somente Tipo, Valor, Nome e Categoria por padrão, mantendo todos os 8 campos secundários dentro de uma seção expansível **"Mais opções"** (auto-expandida ao editar registros antigos que usem campos avançados).

* *Por quê*: Reduz o tempo percebido de registro em mais de 60% para o caso mais comum (lançar um gasto simples), enquanto evita remover recursos úteis para usuários avançados. Além disso, corrige a duplicação atual de campos de Carteira e Moeda no mesmo formulário.

* *Alternativas Consideradas*: Wizard em 2 etapas (Avançar → Detalhes) — descartada porque adiciona um toque extra obrigatório antes de salvar um gasto simples.

* **Decisão 3: Preservação Total da Camada de Dados \+ Simplificação do Tutorial**

* *Abordagem Escolhida*: Manter intactas todas as entidades e tabelas locais existentes (FinanceEntry, FinanceCategory, FinanceWallet, FinanceBudget, FinanceRecurringRule, FinanceExchangeRate, FinanceNoteLink), alterando apenas a camada de apresentação e reduzindo o tutorial de Finanças de 8 para 3 passos.

* *Por quê*: Zero risco de perda de dados ou quebra de backups existentes (exportFinancesJson / restoreFinancesJson) e redução imediata da barreira inicial de aprendizagem.

* *Alternativas Consideradas*: Desativar ou excluir tabelas de Câmbio/Recorrências — descartada para respeitar o histórico e o investimento de usuários avançados.

### **4\. Arquitetura Técnica & Estratégia de Dados *(Referência Técnica)***

#### **Diagrama de Arquitetura e Fluxo**

┌──────────────────────────────────────────────────────────────────────┐  
│                       Camada de Dados (Existente)                    │  
│  Room DAOs (Entradas, Categorias, Carteiras, Orçamentos,             │  
│  Recorrências, Câmbio, Vínculos) \+ Preferências \+ MainViewModel      │  
└───────────────────────────────┬──────────────────────────────────────┘  
                                │ StateFlow (saldo, entradas, filtros)  
                                ▼  
┌──────────────────────────────────────────────────────────────────────┐  
│                   Tela Principal de Finanças (Shell)                 │  
│  \[Bloqueio PIN se ativo\] ──▶ Barra Segmentada:                       │  
│     (0) Resumo      │     (1) Movimentos      │     (2) Ferramentas  │  
└──────────┬──────────┴─────────────┬───────────┴────────────┬─────────┘  
           │                        │                        │  
           ▼                        ▼                        ▼  
┌────────────────────┐  ┌──────────────────────┐  ┌────────────────────┐  
│  Sub-aba Resumo    │  │  Sub-aba Movimentos  │  │ Sub-aba Ferramentas│  
│ • Saldo & Moeda    │  │ • Busca \+ Filtro     │  │ • Carteiras        │  
│ • Entrou vs Saiu   │  │   (Todos/Desp/Renda) │  │ • Planeados (Pago) │  
│ • Gráfico Donut    │  │ • Lista por Período  │  │ • Orçamentos       │  
│ • CTA \+ Lançamento │  │ • Ações Rápidas      │  │ • Recorrências     │  
└─────────┬──────────┘  └──────────┬───────────┘  │ • Vincular Notas   │  
          │                        │              │ • Câmbio           │  
          └────────────┬───────────┘              └─────────┬──────────┘  
                       ▼                                    │  
         ┌───────────────────────────┐                      ▼  
         │  Editor de Lançamento     │           \[Diálogos Especializados  
         │ • Básico: Tipo, Valor,    │            já existentes abertos  
         │   Nome, Categoria         │            sob demanda com  
         │ • \[▾ Mais opções\]: Data,  │            contadores ao vivo\]  
         │   Qtd×Unit, Carteira,     │  
         │   Moeda, Prioridade,      │  
         │   Tags, Anexo, Planeado   │  
         └───────────────────────────┘

#### **Modelo de Dados & Estado**

* **Persistência Inalterada**: Toda a estrutura Room e de preferências (financesEnabled, financeDefaultCurrency, financeBaseBalanceMinor, financeHideValues, financePinCode, financeEntries, financeCategories, financeWallets, financeBudgets, financeRecurringRules, financeExchangeRates, financeNoteLinks) permanece intacta, garantindo compatibilidade total com backups e vínculos automáticos de notas em segundo plano.

* **Estado Local de Navegação**: Controle reativo da sub-aba ativa (Resumo \= 0, Movimentos \= 1, Ferramentas \= 2), filtro de tipo na aba Movimentos (Todos, Despesas, Rendas), termo de busca e seletor de período compartilhado entre Resumo e Movimentos.

* **Auto-expansão Inteligente no Editor**: Ao editar um lançamento existente, a seção "Mais opções" verifica automaticamente se há campos avançados preenchidos (comentário, quantidade unitária, tags, anexo, item planeado ou moeda diferente da padrão) para abrir já expandida apenas quando necessário.

#### **Mapeamento de Componentes Interativos e Estados**

* **Seletor de Sub-abas & Tutorial**: Trocar entre Resumo, Movimentos e Ferramentas atualiza a área de conteúdo de forma fluida; o tutorial interativo aponta apenas para os 3 alvos centrais (Resumo de Saldo/Fluxo, Botão de Novo Lançamento e Aba Ferramentas).

* **Ações na Aba Movimentos**: Filtragem em memória (por período, tipo e texto) sem consultas extras ao banco; toque no cartão abre o editor, e menu de contexto oferece Editar, Duplicar e Apagar com atualização imediata de todos os saldos na aba Resumo.

* **Hub de Ferramentas**: Cada cartão exibe um resumo dinâmico em tempo real (quantidade de carteiras, itens pendentes na lista planeada, orçamentos ativos, regras recorrentes, vínculos e taxas) e abre o respectivo fluxo modal já existente, além de permitir acesso direto às Configurações de Finanças e Bloqueio por PIN.

