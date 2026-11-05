<!-- citra_id: bd9a596c-cafa-4eb3-ad81-9a88d9b57b97 -->
> Documento de referência para desenvolver a atualização V1.2. Tudo o que está aqui entra nesta versão, **exceto a gamificação das finanças**, que fica fora do escopo.

**Plataforma:** Android · **Armazenamento local:** offline primeiro · **Backup:** nuvem (conta Google)

---

## 📌 Resumo das decisões

| Decisão | Definição |
| --- | --- |
| 🔌 **Módulo desligado por padrão** | A aba de Finanças **não aparece** até o usuário ligá-la em **Configurações → Finanças** |
| ☁️ **Backup na nuvem** | **Todos** os dados de finanças vão para a nuvem **a cada backup** |
| 🔗 **Vínculo com notas** | Extração diária, semanal, mensal ou anual para tabelas formatadas, **ignorando os parágrafos manuais** |

---

## 1. 🔌 Ativação do módulo

### 1.1 Estado padrão

- Em instalações novas e em atualizações, **Finanças vem desabilitado**.
- Desabilitado significa que **nada do módulo aparece ou corre**:
- sem aba ou ícone de Finanças na navegação;
- sem widgets de finanças disponíveis;
- sem extrações agendadas, e os vínculos ficam em pausa;
- sem opções de finanças nos menus das notas.

### 1.2 Como ligar

1. **Configurações → Finanças**.
2. Ligar o interruptor **"Ativar Finanças"**.
3. Assistente rápido de primeira utilização:
- moeda padrão (**Kwanza (Kz)** em primeiro lugar na lista);
- saldo atual inicial (opcional, dá para definir depois);
- bloqueio por PIN ou biometria (opcional).
4. A aba de Finanças aparece e os widgets ficam disponíveis.

### 1.3 Como desligar

- O interruptor volta a **desligado**: a aba some, os widgets deixam de funcionar e os vínculos ficam em pausa.
- **Nenhum dado é apagado.** Ao religar, tudo volta como estava.
- As notas já geradas **mantêm o último conteúdo extraído**, mas deixam de ser atualizadas.
- Apagar os dados de finanças é uma ação **separada** ("Apagar dados de finanças"), com confirmação dupla.

### 1.4 Opções dentro de Configurações → Finanças

| Opção | Padrão |
| --- | --- |
| Ativar Finanças | **Desligado** |
| Moeda padrão | Kwanza (Kz) |
| Bloqueio por PIN ou biometria | Desligado |
| Ocultar valores na tela | Desligado |
| Backup dos dados de finanças | **Sempre incluído** no backup (sem opção de excluir) |
| Apagar dados de finanças | Ação manual |

---

## 2. 💰 Sistema de Finanças

### 2.1 Princípios

- Duas visões: **Despesas** e **Rendas**.
- **Várias moedas**, com o **Kwanza angolano (Kz)** como primeira da lista.
- Funciona **offline**, e os dados sincronizam com a nuvem no backup (secção 5).

### 2.2 Anatomia da tela

```text
┌──────────────────────────────────────────┐
│  Saldo atual:  7.000 Kz  [✎]             │
│  [ DESPESAS ]  [ rendas ]        Kz ▾    │
├──────────────────────────────────────────┤
│  ◀  Novembro 2026  ▶                     │
│  Dia | Semana | Mês | Ano | Período      │
├──────────────────────────────────────────┤
│          ( gráfico de pizza )            │
├──────────────────────────────────────────┤
│  [ + Adicionar despesa ]                 │
├──────────────────────────────────────────┤
│  [img]  Arroz                    23%  ⋮  │
│         3 × 900 Kz                       │
│  [img]  Óleo                     21%  ⋮  │
│         1 litro · marca X                │
└──────────────────────────────────────────┘
```

| Zona | Comportamento |
| --- | --- |
| **Saldo no topo** | Mostra o dinheiro que o usuário tem. Ao tocar no número (ícone ✎) ele define o **valor atual** |
| **Seletor de tipo** | Alterna entre **Despesas** e **Rendas** |
| **Calendário pequeno** | Períodos **diário, semanal, mensal, anual** ou **período específico**. Dá para voltar atrás e rever períodos antigos. Cada dia mostra o seu total |
| **Gráfico de pizza** | Aparece quando há valores no período, com uma fatia por categoria. Sem valores, mostra um estado vazio |
| **Botão +** | Adiciona uma despesa ou uma renda |
| **Lista de elementos** | Imagem da categoria à esquerda, nome ao lado e **comentário abaixo do nome em letra menor**. À direita ficam a **percentagem** do total do período e o menu **⋮** (editar, duplicar, apagar) |

### 2.3 Novo lançamento

| Campo | Obrigatório | Regras |
| --- | --- | --- |
| Tipo | Sim | Despesa ou renda |
| **Nome** | **Sim** | Texto curto |
| Valor | Sim | Se houver quantidade e unitário, é calculado |
| Categoria | Sim | Uma das categorias (secção 2.4) |
| Comentário | Não | Aparece abaixo do nome, em letra menor |
| Data | Sim | Hoje por padrão |
| Moeda | Sim | Moeda padrão por padrão |
| Carteira | Sim | Carteira padrão por padrão |
| Quantidade e unidade | Não | Unidades: un, kg, L, monte, garrafa, pacote, corte e outras, com texto livre |
| Valor unitário | Não | **Valor total = quantidade × valor unitário** |
| Prioridade | Não | **Obrigatória** (padrão) ou **Opcional** |
| Etiquetas | Não | Várias, além da categoria |
| Anexo | Não | Foto do talão ou recibo |

> Se o usuário digitar só o valor total, o Citra assume quantidade 1 e deixa o unitário vazio.

### 2.4 Categorias

**Padrão (6 de despesas e 6 de rendas, nomes a confirmar):**

| 🔴 Despesas | 🟢 Rendas |
| --- | --- |
| 🛒 Alimentação | 💼 Salário |
| 🚌 Transporte | 🧾 Negócio / Freelance |
| 🏠 Casa e contas | 🎁 Presente |
| 💊 Saúde e cuidados | 🤝 Dívida recebida |
| 🎓 Educação | 📈 Investimentos |
| 🎮 Lazer | ➕ Outras |

- A **categoria define o tipo** do elemento (salário, presente, dívida…).
- O usuário pode **criar, editar e arquivar categorias próprias**, escolhendo nome, ícone e cor.

### 2.5 Regras do saldo

- O usuário define o **valor atual** tocando no número do topo.
- Cada **despesa subtrai** do saldo, que pode ficar **zerado ou negativo** (negativo em vermelho).
- Cada **renda soma** ao saldo.
- Com várias carteiras, o saldo do topo é a **soma das carteiras** e cada carteira tem o seu próprio.

### 2.6 Funcionalidades adicionais

| Funcionalidade | Comportamento |
| --- | --- |
| **Planeado vs. real** | A lista de compras tem preço previsto. Ao marcar ✔, vira lançamento real e mostra a diferença (ex.: Óleo previsto 2.500 Kz, pago 1.550 Kz) |
| **Orçamento por categoria** | Limite mensal por categoria, com alerta aos **80%** e aos **100%** |
| **Lançamentos recorrentes** | Salário, renda de casa, internet. Entram sozinhos na data, com lembrete antes |
| **Carteiras / contas** | Dinheiro vivo, conta bancária, carteira móvel. Saldo próprio e **transferências** entre elas |
| **Multi-moeda com câmbio manual** | O usuário define a taxa e pode manter histórico de taxas. Totais convertidos para a moeda padrão |
| **Lançamento por voz e por foto** | Ditar "gastei 500 em cebola" ou fotografar o talão, aproveitando os recursos de voz e fotos do Citra |
| **Dívidas e empréstimos** | Quem deve a quem, valor, prazo, pagamentos parciais e lembrete |
| **Metas de poupança** | Objetivo, valor alvo, prazo e barra de progresso |
| **Insights** | Comparação com o período anterior, dia da semana em que mais gasta, top categorias, projeção do saldo até ao fim do mês |
| **Pesquisa e filtros** | Por nome, categoria, valor, etiqueta, carteira e período |
| **Privacidade** | Bloqueio por PIN ou biometria e modo "ocultar valores" |
| **Widgets** | "+ despesa" rápido e saldo no ecrã inicial (só disponíveis com o módulo ligado) |
| **Exportar** | CSV, Excel e PDF dos lançamentos |
| **Lixeira** | Lançamentos apagados ficam recuperáveis por 30 dias |

---

## 3. 🔗 Vínculo com Notas

### 3.1 A ideia

O usuário liga as finanças a uma **nota** (existente ou nova). O Citra **extrai os dados regularmente** e escreve, dentro da nota, **tabelas já formatadas** com nome, quantidade, valor unitário e valor total, somando tudo no fim. O resultado fica como `.md` aberto em `Documentos/Citra Docs`.

> O vínculo só pode ser criado com o módulo de Finanças **ligado**.

### 3.2 Criar um vínculo

1. Em **Finanças**, tocar em **⋮ → Vincular a nota**, ou dentro da nota em **Inserir bloco de finanças**.
2. Escolher o destino: **Nova nota** (a partir de um modelo) ou **Nota existente**, indicando onde inserir (fim da nota, posição do cursor ou bloco já existente).
3. Escolher o que extrair: despesas, rendas ou ambas, com filtros opcionais.
4. Escolher a **frequência**: diária, semanal, mensal ou anual.
5. Ver a **pré-visualização** (diferenças destacadas) e confirmar.

Uma nota pode ter **vários vínculos**, cada um com os seus filtros (por exemplo, um só para Alimentação).

### 3.3 Opções do vínculo

| Opção | Valores | Padrão |
| --- | --- | --- |
| **Fonte** | Despesas · Rendas · Ambas | Ambas |
| **Frequência** | **Diária** · **Semanal** · **Mensal** · **Anual** · Manual | Mensal |
| **Quando corre** | Hora do dia, dia da semana, dia do mês ou 31 de dezembro | Fim do período |
| **Período extraído** | Acompanha a frequência (dia, semana, mês ou ano) | Automático |
| **Como escreve** | Substituir o bloco · Acrescentar nova secção por período · Criar nota nova por período | Substituir o bloco |
| **Agrupar por** | Prioridade · Categoria · Sem grupos | Prioridade |
| **Juntar itens iguais** | Soma as quantidades de itens com o mesmo nome | Desligado |
| **Colunas extra** | Categoria · Data · Comentário | Nenhuma |
| **Resumo de saldo** | Total, saldo e saldo final | Ligado |
| **Resumo automático** | Maior despesa, % por categoria, comparação com o período anterior | Desligado |
| **Gráfico** | Pizza ou barras em Mermaid | Desligado |
| **Filtros** | Categorias, prioridade, carteira, etiquetas, moeda | Sem filtros |
| **Notificar ao atualizar** | Aviso "Despesas Mensais atualizada: 23 lançamentos" | Desligado |

Para "Criar nota nova por período", o nome segue um modelo editável, como `Despesas Mensais · {mês} {ano}`.

### 3.4 Modelos prontos

| Modelo | Conteúdo |
| --- | --- |
| **Despesas Mensais** | Obrigatórias, opcionais, saldo final e simulação |
| **Rendas do Mês** | Tabela de rendas por categoria e total |
| **Balanço Completo** | Rendas, despesas, saldo e resumo automático |
| **Lista de Compras** | Itens com preço previsto, ligados ao planeado vs. real |
| **Relatório Semanal** | Resumo dos últimos 7 dias |
| **Relatório Anual** | Soma das notas mensais (relatório encadeado) |

### 3.5 Regra de ouro: os parágrafos manuais são ignorados

O Citra só escreve **dentro de blocos geridos**, delimitados por marcadores invisíveis:

```markdown
<!-- citra:fin:inicio id=fin_7f3a -->
(tabelas geradas pelo Citra)
<!-- citra:fin:fim id=fin_7f3a -->
```

- ✅ **Dentro** dos marcadores: reescrito a cada extração.
- 🔒 **Fora** dos marcadores (títulos, checklists, cálculos à mão, o "Resumo"): **nunca é lido nem alterado**.
- Os marcadores são comentários HTML, **invisíveis no Obsidian** e em qualquer leitor Markdown.
- A nota guarda um **id interno** no cabeçalho, e por isso o vínculo continua se o ficheiro for renomeado ou movido.

### 3.6 O que as tabelas contêm

- Colunas: **Item · Quantidade · Valor Unit. · Valor Total** (mais as colunas extra escolhidas).
- **Valor Total da linha** = quantidade × valor unitário.
- Itens **sem preço** aparecem com "—" e ⚠️ **Pendente**, ficam fora da soma e têm preenchimento rápido a partir da nota.
- **Obrigatórias** entram no saldo final. **Opcionais** têm **total próprio**, não entram, e o Citra mostra a **simulação** "saldo final se incluir os opcionais".
- **Saldo final = saldo atual − total das despesas obrigatórias.**
- **Alerta de inconsistência:** avisa quando quantidade × unitário ≠ total.

### 3.7 Exemplo de resultado (nota "Despesas Mensais")

```markdown
---
citra_id: nota_8c21
pasta: Logística
data: 2026-11-04
---
# Despesas Mensais

## Análise de Preços
- [x] ~~Rever em lojas por melhores opções de preçário~~

**Saldo Positivo: 7.000 Kz**

## Despesas Obrigatórias
<!-- citra:fin:inicio id=fin_7f3a tipo=despesas prioridade=obrigatoria periodo=mensal -->
| Item | Quantidade | Valor Unit. | Valor Total |
|---|---|---|---|
| **Arroz** | 3 | 900 Kz | 2.700 Kz |
| **Tomate** | 10 montes | 100 Kz | 1.000 Kz |
| **Cebola** | 5 montes | 100 Kz | 500 Kz |
| **Óleo** | 1 litro | 2.500 Kz | 2.500 Kz |
| Vinagre | 1 garrafa | 350 Kz | 350 Kz |
| **Açúcar** | 2 quilos | 1.000 Kz | 2.000 Kz |
| **Soja** | 1 quilo | — | ⚠️ Pendente |
| Batata | 5 montes | 200 Kz | 1.000 Kz |
| Massa | 3 | 350 Kz | 1.050 Kz |
| Peixe (Seco) | — | — | ⚠️ Pendente |
| **Corte de Cabelo** | 1 corte | 800 Kz | 800 Kz |

| Total das despesas | Saldo | Saldo final |
|---|---|---|
| 11.900 Kz | 7.000 Kz | 7.000 − 11.900 = **−4.900 Kz** |
<!-- citra:fin:fim id=fin_7f3a -->

## Despesas Opcionais
Estas despesas não serão calculadas no saldo final.

<!-- citra:fin:inicio id=fin_7f3b tipo=despesas prioridade=opcional periodo=mensal -->
| Item | Quantidade | Valor Unit. | Valor Total |
|---|---|---|---|
| Folhas | 4 | 50 Kz | 200 Kz |
| Palha de Aço | 1 pacote | 200 Kz | 200 Kz |
| Fósforos | 2 | 50 Kz | 100 Kz |
| Pasta de Dentes | 1 | 1.200 Kz | 1.200 Kz |

| Total dos opcionais | Saldo final se incluir os opcionais |
|---|---|
| 1.700 Kz | 7.000 − 13.600 = **−6.600 Kz** |
<!-- citra:fin:fim id=fin_7f3b -->

## Lista de Tarefas
- [x] ~~Cortou o cabelo: 800 Kz~~
- [x] ~~Comprou o Arroz: 2.700 Kz~~
- [ ] Comprou a Batata

## Resumo
Por conta do arroz estar acabando, precisei comprar pelo menos 3 quilos novos…
```

Tudo o que está **fora** dos blocos `citra:fin` é do usuário e fica intacto.

### 3.8 Recursos extra do vínculo

| Recurso | Comportamento |
| --- | --- |
| **Pré-visualização e desfazer** | Mostra o que vai mudar antes de aplicar. **Desfazer a última atualização** restaura o bloco anterior |
| **Atualizar agora** | Botão manual e indicador "Última extração: hoje, 23:59" na nota |
| **Variáveis em linha** | O usuário escreve `{{saldo_final}}`, `{{total_despesas}}` ou `{{total_rendas}}` num parágrafo seu. O Citra grava o valor resolvido entre marcadores invisíveis, assim o `.md` continua legível e o valor se mantém atualizado |
| **Checklist ligada aos lançamentos** *(beta)* | Marcar "Comprou o Arroz: 2.700 Kz" cria o lançamento real e atualiza a tabela |
| **Edição bidirecional** *(beta)* | Corrigir um valor na tabela da nota atualiza o lançamento correspondente |
| **Exportação** | PDF e Word com as tabelas formatadas e totais em destaque, usando a exportação já existente |

### 3.9 Situações especiais

| Situação | Comportamento |
| --- | --- |
| Nota **renomeada ou movida** | O vínculo continua, graças ao id interno |
| Nota **apagada** | Pausa o vínculo, avisa e oferece criar uma nota nova |
| Marcadores **apagados** | Pausa o vínculo, avisa e oferece recriar o bloco no fim da nota |
| O usuário **edita dentro do bloco** | A tabela mostra "bloco gerido, edições aqui serão substituídas", com a opção de **converter em texto normal** |
| Telefone **desligado** à hora marcada | A extração em falta corre quando o app abrir |
| Nota **aberta no editor** | A atualização espera a nota ser fechada |
| **Sem lançamentos** no período | Escreve "Sem lançamentos neste período" (ou não escreve nada, à escolha) |
| **Módulo desligado** | Os vínculos ficam em pausa e a nota mantém o último conteúdo |

---

## 4. 📆 Fluxo no dia a dia

- **Manhã:** o usuário abre as Finanças, confere o **saldo no topo** e os lançamentos recorrentes que entraram.
- **Durante o dia:** regista as compras por toque, voz ou foto do talão.
- **Noite:** o gráfico mostra para onde foi o dinheiro e a **extração diária** atualiza a nota do dia.
- **Fim do mês:** a nota **Despesas Mensais** tem as tabelas prontas, com total e saldo final, e os parágrafos de Resumo continuam como o usuário os escreveu.

---

## 5. ☁️ Backup na nuvem

### 5.1 Regra

**Todos os dados do sistema de finanças são guardados na nuvem a cada backup.** Não existe opção para excluí-los do backup.

### 5.2 O que entra no backup

| Dados | Incluído |
| --- | --- |
| Lançamentos (despesas e rendas) e anexos (fotos de talões) | ✅ |
| Categorias (padrão e próprias) e etiquetas | ✅ |
| Carteiras, saldos e transferências | ✅ |
| Moedas, taxas de câmbio e histórico | ✅ |
| Orçamentos, metas, dívidas e recorrências | ✅ |
| Configurações de Finanças (incluindo se o módulo está ligado) | ✅ |
| **Vínculos** (configuração, frequência, ids dos blocos) e o registo das execuções | ✅ |
| Lixeira (30 dias) | ✅ |
| Notas vinculadas (`.md`) | ✅, pelo backup de notas já existente |

### 5.3 Como funciona

- Requer **conta Google**. Sem conta, os dados ficam **só no aparelho** e o app mostra um aviso a oferecer a ligação da conta.
- **Backup automático** sempre que houver ligação, mais o botão **"Fazer backup agora"**.
- **Incremental:** só os registos alterados desde o último backup são enviados.
- Cada registo tem **id único (UUID)**, **data de atualização** e **marca de apagado** (`deletedAt`), o que permite fundir alterações entre aparelhos.
- Conflito entre aparelhos: **vence a alteração mais recente** de cada registo. Nada é perdido por sobrescrita de uma lista inteira.
- **Restauro** num aparelho novo: o Citra recupera os dados de finanças, o estado do interruptor e reativa os vínculos.
- Dados **criptografados em trânsito e em repouso**.
- Se o bloqueio por PIN estiver ligado, ele também é exigido depois de um restauro.

### 5.4 Comportamento de atualização

- Quem atualiza para a V1.2 **não vê nenhuma mudança** até ligar Finanças.
- Ao ligar com um backup de finanças já existente na conta, o Citra pergunta: **"Restaurar os dados da nuvem?"**

---

## 6. 🛠️ Notas técnicas

### 6.1 Modelo de dados (resumo)

| Entidade | Campos principais |
| --- | --- |
| **Lançamento** | id, tipo, nome, comentário, categoriaId, carteiraId, moeda, data, quantidade, unidade, valorUnitário (inteiro), valorTotal (inteiro), prioridade, etiquetas[], anexos[], recorrênciaId?, planeado?, atualizadoEm, apagadoEm |
| **Categoria** | id, tipo, nome, ícone, cor, padrão, arquivada |
| **Carteira** | id, nome, moeda, saldoInicial |
| **Taxa de câmbio** | id, de, para, taxa, data |
| **Orçamento** | id, categoriaId, limite, período |
| **Recorrência** | id, modelo de lançamento, regra de repetição, lembrete |
| **Dívida** | id, pessoa, sentido (devo ou devem-me), valor, prazo, pagamentos[] |
| **Meta** | id, nome, alvo, prazo, valorAtual |
| **Vínculo** | id, notaId, blocoId, fonte, frequência, agendamento, opções, filtros, estado (ativo, pausado), últimaExecução |
| **Execução** | id, vínculoId, quando, nº de lançamentos, resultado, versão anterior do bloco |

### 6.2 Regras de implementação

- **Dinheiro como inteiro:** guardar valores na menor unidade da moeda, para evitar erros de arredondamento.
- **Agendamento:** tarefas periódicas do Android (WorkManager), com recuperação das extrações em falta ao abrir o app.
- **Escrita segura nas notas:** gravar em ficheiro temporário e só depois substituir o original.
- **Isolamento do módulo:** com o interruptor desligado, não registar rotas, widgets nem tarefas agendadas de finanças.
- **Extração em sincronização:** com a mesma conta em dois aparelhos, só um deles executa cada extração agendada (evita blocos duplicados).

### 6.3 Ordem de construção sugerida

| Etapa | Entrega |
| --- | --- |
| **1** | Interruptor em Configurações, módulo isolado e desligado por padrão |
| **2** | Lançamentos, categorias, saldo, calendário, pizza e lista (secções 2.1 a 2.5) |
| **3** | Backup na nuvem dos dados de finanças e restauro (secção 5) |
| **4** | Vínculo com notas: blocos geridos, tabelas e frequências (secções 3.1 a 3.7) |
| **5** | Carteiras, multi-moeda, recorrentes, orçamentos, planeado vs. real |
| **6** | Dívidas, metas, insights, voz e foto, widgets, exportação |
| **7** | Recursos extra do vínculo, incluindo os beta (secção 3.8) |

---

## ✅ Critérios de aceite

- [ ] Em instalação nova e em atualização, **Finanças está desligado** e não há rasto do módulo na interface.
- [ ] Ligar em **Configurações → Finanças** mostra a aba, e desligar esconde-a **sem apagar dados**.
- [ ] É possível registar despesas e r