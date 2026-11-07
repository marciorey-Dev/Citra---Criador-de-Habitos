package com.example.ui.screens.organization.notes

import com.example.data.model.Note
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class NoteTemplate(
    val id: String,
    val title: String,
    val category: String,
    val categoryEmoji: String,
    val summary: String,
    val defaultNoteTitle: String,
    val rawMarkdown: String
)

object NoteTemplatesCatalog {
    val allTemplates: List<NoteTemplate> = listOf(
        // 📚 Estudo
        NoteTemplate(
            id = "cornell",
            title = "Anotações de Aula (Cornell)",
            category = "Estudo",
            categoryEmoji = "📚",
            summary = "Perguntas-chave, anotações, espaço para fotos do quadro, áudio, resumo em 3 linhas e revisão.",
            defaultNoteTitle = "Aula de {{data}}",
            rawMarkdown = """
# 📚 {{titulo}}
📅 {{data}} · 🎓 Disciplina: · 👤 Professor: · #aula

## ❓ Perguntas e palavras-chave
- 

## 📝 Anotações da aula
- 

## 🖼️ Quadro e fotos
(anexar fotos)

## 🎙️ Gravação
(anexar o áudio da explicação)

## 🧾 Resumo em 3 linhas
> 

## ✅ Depois da aula
- [ ] Rever amanhã
- [ ] Fazer os exercícios
- [ ] Tirar dúvidas com o professor
            """.trimIndent()
        ),
        NoteTemplate(
            id = "resumo_leitura",
            title = "Resumo de Leitura",
            category = "Estudo",
            categoryEmoji = "📚",
            summary = "Ideia central, pontos principais, citações marcantes, aprendizados e avaliação do livro.",
            defaultNoteTitle = "Resumo de Leitura",
            rawMarkdown = """
# 📖 {{titulo}}
✍️ Autor: · 📅 {{data}} · #leitura

## Ideia central
> 

## Pontos principais
1. 
2. 
3. 

## Citações que marcaram
> "" (p. )

## O que aprendi
- 

## Como aplicar
- [ ] 

⭐ Nota: /5
            """.trimIndent()
        ),
        NoteTemplate(
            id = "plano_prova",
            title = "Plano de Estudo para Prova",
            category = "Estudo",
            categoryEmoji = "📚",
            summary = "Tabela de conteúdos e prazos, cronograma semanal, revisões espaçadas (1, 7, 30 dias) e dúvidas.",
            defaultNoteTitle = "Plano de Prova",
            rawMarkdown = """
# 🎯 Prova de {{titulo}}
📅 Data da prova: · #prova

## Conteúdo
| Matéria | Tópicos | Prazo | Status |
|---|---|---|---|
|  |  |  | ⬜ |

## Cronograma da semana
| Dia | Horário | O que estudar |
|---|---|---|
| Seg |  |  |
| Ter |  |  |
| Qua |  |  |
| Qui |  |  |
| Sex |  |  |

## Revisões
- [ ] Revisão em 1 dia
- [ ] Revisão em 7 dias
- [ ] Revisão em 30 dias

## Dúvidas
- 
            """.trimIndent()
        ),
        NoteTemplate(
            id = "flashcards",
            title = "Perguntas e Respostas",
            category = "Estudo",
            categoryEmoji = "📚",
            summary = "Estrutura rápida de perguntas (P:) e respostas (R:) para revisar matéria estilo flashcards.",
            defaultNoteTitle = "Flashcards - {{data}}",
            rawMarkdown = """
# 🃏 {{titulo}}
#flashcards

**P:** 
**R:** 

---

**P:** 
**R:** 

---

**P:** 
**R:** 
            """.trimIndent()
        ),
        NoteTemplate(
            id = "exercicios_formulas",
            title = "Exercícios e Fórmulas",
            category = "Estudo",
            categoryEmoji = "📚",
            summary = "Bloco de fórmulas em LaTeX, enunciado, resolução passo a passo e erros para evitar.",
            defaultNoteTitle = "Exercícios - {{data}}",
            rawMarkdown = """
# 🧮 {{titulo}}
📅 {{data}} · #exatas

## Fórmulas
- Equação de 2.º grau: ${'$'}x = \frac{-b \pm \sqrt{b^2 - 4ac}}{2a}${'$'}
- Área do círculo: ${'$'}A = \pi r^2${'$'}
- 

## Exercício 1
**Enunciado:** 

**Resolução:**
${'$'}${'$'}
 
${'$'}${'$'}

**Resposta:** 

## Erros para não repetir
- 
            """.trimIndent()
        ),

        // 📝 Trabalhos
        NoteTemplate(
            id = "trabalho_escolar",
            title = "Trabalho Escolar",
            category = "Trabalhos",
            categoryEmoji = "📝",
            summary = "Capa, índice, introdução, desenvolvimento em tópicos, conclusão, referências ABNT e checklist.",
            defaultNoteTitle = "Trabalho Escolar",
            rawMarkdown = """
# {{titulo}}

**Aluno:** 
**Turma:** 
**Professor:** 
**Data:** {{data}}

---

## Índice
1. Introdução
2. Desenvolvimento
3. Conclusão
4. Referências

---

## 1. Introdução
- **Tema:** 
- **Contexto:** 
- **Objetivo:** 
- **Pergunta ou problema:** 

## 2. Desenvolvimento

### 2.1 Conceitos principais


### 2.2 Análise


### 2.3 Exemplos e dados


## 3. Conclusão
- Resposta à pergunta inicial:
- Pontos principais:
- Opinião ou reflexão final:

## 4. Referências
- SOBRENOME, Nome. *Título da obra*. Cidade: Editora, ano.
- SOBRENOME, Nome. Título do artigo. Site, ano. Disponível em: . Acesso em: {{data}}.

---

## ✅ Checklist de entrega
- [ ] Introdução com objetivo claro
- [ ] Desenvolvimento dividido em partes
- [ ] Conclusão responde ao objetivo
- [ ] Referências completas
- [ ] Revisão de ortografia
- [ ] Exportar em PDF ou Word
            """.trimIndent()
        ),
        NoteTemplate(
            id = "relatorio_lab",
            title = "Relatório de Laboratório",
            category = "Trabalhos",
            categoryEmoji = "📝",
            summary = "Objetivo, materiais, procedimento, tabela de dados coletados, cálculos LaTeX e conclusão.",
            defaultNoteTitle = "Relatório de Laboratório",
            rawMarkdown = """
# 🔬 {{titulo}}
📅 {{data}} · Grupo: · #laboratorio

## Objetivo
## Materiais
- 

## Procedimento
1. 
2. 

## Dados coletados
| Medida | Valor | Unidade |
|---|---|---|
|  |  |  |

## Cálculos e resultados
${'$'}${'$'}
 
${'$'}${'$'}

## Discussão
## Conclusão
## Referências
            """.trimIndent()
        ),
        NoteTemplate(
            id = "apresentacao",
            title = "Apresentação / Seminário",
            category = "Trabalhos",
            categoryEmoji = "📝",
            summary = "Roteiro de slides com tempo, frase de abertura, perguntas previstas e checklist pré-apresentação.",
            defaultNoteTitle = "Apresentação - {{data}}",
            rawMarkdown = """
# 🎤 {{titulo}}
📅 Data: · ⏱️ Tempo: min · #apresentacao

## Roteiro
| Slide | Conteúdo | Tempo |
|---|---|---|
| 1 | Abertura |  |
| 2 |  |  |
| 3 |  |  |
| Final | Conclusão e perguntas |  |

## Frase de abertura
> 

## Perguntas que podem fazer
- 

## Antes de apresentar
- [ ] Ensaiar em voz alta
- [ ] Testar o equipamento
- [ ] Água e material por perto
            """.trimIndent()
        ),

        // ✅ Produtividade
        NoteTemplate(
            id = "tarefas_dia",
            title = "Lista de Tarefas do Dia",
            category = "Produtividade",
            categoryEmoji = "✅",
            summary = "Prioridade do dia, checklist de tarefas, hábitos diários, sessões Arancia e fechamento noturno.",
            defaultNoteTitle = "Dia {{data}}",
            rawMarkdown = """
# ☀️ {{data}}

## 🎯 Prioridade do dia
> 

## ✅ Tarefas
- [ ] 
- [ ] 
- [ ] 

## 🔥 Hábitos
- [ ] Água
- [ ] Leitura
- [ ] Exercício

## ⏱️ Foco (pomodoros)
- [ ] 🍅 1
- [ ] 🍅 2
- [ ] 🍅 3
- [ ] 🍅 4

## 🌙 Fechamento
- O que correu bem: 
- O que fica para amanhã: 
            """.trimIndent()
        ),
        NoteTemplate(
            id = "rotina_semanal",
            title = "Plano de Rotina Semanal",
            category = "Produtividade",
            categoryEmoji = "✅",
            summary = "Grade de horários de segunda a domingo (06h às 22h), hábitos fixos e regras da semana.",
            defaultNoteTitle = "Rotina Semanal",
            rawMarkdown = """
# 🗓️ Rotina da Semana
📅 A partir de {{data}} · #rotina

| Horário | Seg | Ter | Qua | Qui | Sex | Sáb | Dom |
|---|---|---|---|---|---|---|---|
| 06:00 |  |  |  |  |  |  |  |
| 08:00 |  |  |  |  |  |  |  |
| 10:00 |  |  |  |  |  |  |  |
| 12:00 |  |  |  |  |  |  |  |
| 14:00 |  |  |  |  |  |  |  |
| 16:00 |  |  |  |  |  |  |  |
| 18:00 |  |  |  |  |  |  |  |
| 20:00 |  |  |  |  |  |  |  |
| 22:00 |  |  |  |  |  |  |  |

## Hábitos fixos
- [ ] 
- [ ] 

## Regras da semana
- 
            """.trimIndent()
        ),
        NoteTemplate(
            id = "revisao_semanal",
            title = "Revisão Semanal",
            category = "Produtividade",
            categoryEmoji = "✅",
            summary = "Conquistas, pontos de melhoria, aprendizados, horas de foco e metas para a próxima semana.",
            defaultNoteTitle = "Revisão Semanal - {{data}}",
            rawMarkdown = """
# 🔁 Revisão da Semana
📅 {{data}} · #revisao

## ✨ Conquistas
- 

## 😬 O que não correu bem
- 

## 📚 Aprendizados
- 

## ⏱️ Horas de foco
Total: h · Melhor dia: 

## 🎯 Próxima semana
1. 
2. 
3. 
            """.trimIndent()
        ),
        NoteTemplate(
            id = "meta_smart",
            title = "Meta (SMART)",
            category = "Produtividade",
            categoryEmoji = "✅",
            summary = "Tabela Específica, Mensurável, Atingível, Relevante e Temporal, passos e possíveis obstáculos.",
            defaultNoteTitle = "Meta SMART",
            rawMarkdown = """
# 🎯 {{titulo}}
📅 Início: {{data}} · Prazo: · #meta

| Critério | Resposta |
|---|---|
| **S**pecífica: o que exatamente? |  |
| **M**ensurável: como vou medir? |  |
| **A**tingível: é realista? |  |
| **R**elevante: por que importa? |  |
| **T**emporal: até quando? |  |

## Passos
- [ ] 
- [ ] 
- [ ] 

## Progresso
- 0% ▱▱▱▱▱▱▱▱▱▱

## Possíveis obstáculos
- 
            """.trimIndent()
        ),
        NoteTemplate(
            id = "planejamento_projeto",
            title = "Planejamento de Projeto",
            category = "Produtividade",
            categoryEmoji = "✅",
            summary = "Objetivo, tabela de entregas e responsáveis, riscos, próximos passos e diário do projeto.",
            defaultNoteTitle = "Projeto - {{data}}",
            rawMarkdown = """
# 🚀 {{titulo}}
📅 Início: {{data}} · Entrega: · #projeto

## Objetivo
> 

## Entregas
| Etapa | Responsável | Prazo | Status |
|---|---|---|---|
|  |  |  | ⬜ |

## Riscos
- 

## Próximos passos
- [ ] 
- [ ] 

## Diário do projeto
- {{data}}: 
            """.trimIndent()
        ),
        NoteTemplate(
            id = "caixa_entrada",
            title = "Caixa de Entrada (ideias rápidas)",
            category = "Produtividade",
            categoryEmoji = "✅",
            summary = "Espaço livre para capturar ideias soltas e organizar depois em tarefas ou notas.",
            defaultNoteTitle = "Caixa de Entrada - {{data}}",
            rawMarkdown = """
# 📥 Caixa de Entrada
#inbox

*Escreva primeiro, organize depois.*

- 
- 
- 

---
## Para organizar hoje
- [ ] Virar tarefa
- [ ] Virar nota
- [ ] Apagar
            """.trimIndent()
        ),

        // 💼 Trabalho
        NoteTemplate(
            id = "ata_reuniao",
            title = "Ata de Reunião",
            category = "Trabalho",
            categoryEmoji = "💼",
            summary = "Participantes, pauta, decisões tomadas, tabela de tarefas definidas e próxima reunião.",
            defaultNoteTitle = "Reunião - {{data}}",
            rawMarkdown = """
# 🤝 {{titulo}}
📅 {{data}} · ⏰ {{hora}} · #reuniao

**Participantes:** 
**Pauta:**
1. 
2. 

## Decisões
- 

## Tarefas definidas
| Tarefa | Responsável | Prazo |
|---|---|---|
|  |  |  |

## Pontos em aberto
- 

## Próxima reunião
📅 
            """.trimIndent()
        ),
        NoteTemplate(
            id = "brainstorming",
            title = "Brainstorming",
            category = "Trabalho",
            categoryEmoji = "💼",
            summary = "Definição do desafio, chuva de ideias sem filtro, matriz Impacto x Esforço e primeira ação.",
            defaultNoteTitle = "Brainstorming - {{data}}",
            rawMarkdown = """
# 💡 {{titulo}}
📅 {{data}} · #ideias

## Problema ou desafio
> 

## Ideias (sem julgar)
- 
- 
- 
- 

## Melhores ideias
| Ideia | Impacto | Esforço |
|---|---|---|
|  | Alto / Médio / Baixo | Alto / Médio / Baixo |

## Primeira ação
- [ ] 
            """.trimIndent()
        ),
        NoteTemplate(
            id = "relatorio_diario",
            title = "Relatório Diário de Trabalho",
            category = "Trabalho",
            categoryEmoji = "💼",
            summary = "Resumo do que foi feito hoje, plano para amanhã, bloqueios e horas focadas.",
            defaultNoteTitle = "Relatório {{data}}",
            rawMarkdown = """
# 🧾 Relatório de {{data}}
#diario-trabalho

## ✅ Feito hoje
- 

## ⏭️ Vou fazer amanhã
- 

## 🚧 Bloqueios
- 

## ⏱️ Tempo de foco
Total: h
            """.trimIndent()
        ),

        // 💻 Programação
        NoteTemplate(
            id = "nota_programacao",
            title = "Nota de Programação",
            category = "Programação",
            categoryEmoji = "💻",
            summary = "Conceito, quando usar, bloco de código de exemplo, armadilhas comuns e links úteis.",
            defaultNoteTitle = "Código - {{data}}",
            rawMarkdown = """
# 💻 {{titulo}}
📅 {{data}} · 🏷️ Linguagem: · #codigo

## O que é
> 

## Quando usar
- 

## Exemplo
```python
def exemplo():
    return "olá, Citra"
```

## Armadilhas
- 

## Links úteis
- 
            """.trimIndent()
        ),
        NoteTemplate(
            id = "registro_bug",
            title = "Registro de Bug",
            category = "Programação",
            categoryEmoji = "💻",
            summary = "Comportamento esperado vs atual, passos para reproduzir, log de erro, hipóteses e solução.",
            defaultNoteTitle = "Bug - {{data}}",
            rawMarkdown = """
# 🐞 {{titulo}}
📅 {{data}} · Gravidade: Alta / Média / Baixa · #bug

## Comportamento esperado
## Comportamento atual
## Passos para reproduzir
1. 
2. 

## Erro / log
```text

```

## Hipóteses
- [ ] 

## Causa encontrada
## Solução
## Como evitar no futuro
            """.trimIndent()
        ),
        NoteTemplate(
            id = "spec_funcionalidade",
            title = "Especificação de Funcionalidade",
            category = "Programação",
            categoryEmoji = "💻",
            summary = "Problema, solução proposta, regras, fora do escopo, tarefas e critérios de aceite.",
            defaultNoteTitle = "Spec Funcionalidade",
            rawMarkdown = """
# 🛠️ {{titulo}}
📅 {{data}} · Estado: Ideia / Em dev / Pronto · #spec

## Problema
> 

## Solução proposta
## Regras e comportamento
- 

## Fora do escopo
- 

## Tarefas
- [ ] 
- [ ] 

## Critérios de aceite
- [ ] 
            """.trimIndent()
        ),
        NoteTemplate(
            id = "checklist_lancamento",
            title = "Checklist de Lançamento",
            category = "Programação",
            categoryEmoji = "💻",
            summary = "Verificações antes, durante e depois de publicar uma nova versão.",
            defaultNoteTitle = "Lançamento {{data}}",
            rawMarkdown = """
# 📦 Lançamento {{titulo}}
📅 {{data}} · #release

## Antes
- [ ] Testes passam
- [ ] Versão e changelog atualizados
- [ ] Backup feito

## Lançar
- [ ] Gerar a versão final
- [ ] Publicar

## Depois
- [ ] Verificar erros
- [ ] Ler os primeiros comentários
- [ ] Anotar o que aprender
            """.trimIndent()
        ),

        // 🌱 Vida pessoal
        NoteTemplate(
            id = "diario_noturno",
            title = "Diário Noturno",
            category = "Vida pessoal",
            categoryEmoji = "🌱",
            summary = "Registro de humor, reflexão sobre o dia, 3 coisas boas e intenção para amanhã.",
            defaultNoteTitle = "Diário {{data}}",
            rawMarkdown = """
# 🌙 {{data}}
#diario

**Humor:** 😄 🙂 😐 🙁 😞

## Como foi o dia
> 

## 3 coisas boas
1. 
2. 
3. 

## O que eu faria diferente
- 

## Amanhã
- [ ] 
            """.trimIndent()
        ),
        NoteTemplate(
            id = "gratidao",
            title = "Gratidão e Conquistas",
            category = "Vida pessoal",
            categoryEmoji = "🌱",
            summary = "Lista de gratidão, pequenas vitórias do dia e reconhecimento a quem ajudou.",
            defaultNoteTitle = "Gratidão {{data}}",
            rawMarkdown = """
# 🙏 {{data}}
#gratidao

## Sou grato por
- 
- 
- 

## Pequenas vitórias
- 

## Alguém que me ajudou
- 
            """.trimIndent()
        ),
        NoteTemplate(
            id = "treino_saude",
            title = "Treino e Saúde",
            category = "Vida pessoal",
            categoryEmoji = "🌱",
            summary = "Tabela de exercícios, séries, repetições, carga, energia, sono, água e medidas.",
            defaultNoteTitle = "Treino {{data}}",
            rawMarkdown = """
# 💪 Treino de {{data}}
#saude

| Exercício | Séries | Repetições | Carga |
|---|---|---|---|
|  |  |  |  |

## Como me senti
Energia: /10 · Sono: h · Água: copos

## Medidas
- Peso: kg
- Observações: 

## Próximo treino
- [ ] 
            """.trimIndent()
        ),
        NoteTemplate(
            id = "lista_compras",
            title = "Lista de Compras",
            category = "Vida pessoal",
            categoryEmoji = "🌱",
            summary = "Checklist dividido por alimentação, limpeza, higiene, outros e orçamento previsto.",
            defaultNoteTitle = "Compras {{data}}",
            rawMarkdown = """
# 🛒 Compras de {{data}}
#compras

## Alimentação
- [ ] 
- [ ] 

## Limpeza e casa
- [ ] 

## Higiene
- [ ] 

## Outros
- [ ] 

**Orçamento previsto:** 
            """.trimIndent()
        ),

        // 💰 Finanças
        NoteTemplate(
            id = "orcamento_mes",
            title = "Orçamento do Mês",
            category = "Finanças",
            categoryEmoji = "💰",
            summary = "Análise de preços, blocos de rendas, despesas obrigatórias/opcionais e resumo financeiro.",
            defaultNoteTitle = "Orçamento {{data}}",
            rawMarkdown = """
# 💰 Orçamento de {{titulo}}
📅 {{data}} · #financas

## Análise de preços
- [ ] 

## Rendas
- [ ] 

## Despesas Obrigatórias
- [ ] 

## Despesas Opcionais
- [ ] 

## Lista de compras do mês
- [ ] 

## Resumo
Anotações livres e observações financeiras do mês.
            """.trimIndent()
        ),

        // 🎉 Ocasiões
        NoteTemplate(
            id = "viagem",
            title = "Viagem",
            category = "Ocasiões",
            categoryEmoji = "🎉",
            summary = "Roteiro por dia, checklist de documentos e mala, tabela de orçamento e contatos úteis.",
            defaultNoteTitle = "Viagem",
            rawMarkdown = """
# ✈️ {{titulo}}
📅 De: · Até: · #viagem

## Roteiro
| Dia | Plano | Onde ficar |
|---|---|---|
| 1 |  |  |

## Documentos
- [ ] Bilhetes
- [ ] Reservas
- [ ] Seguro

## O que levar
- [ ] Roupas
- [ ] Carregadores
- [ ] Remédios

## Orçamento
| Item | Valor |
|---|---|
| Transporte |  |
| Hospedagem |  |
| Comida |  |
| Passeios |  |
| **Total** |  |

## Contactos úteis
- 
            """.trimIndent()
        ),
        NoteTemplate(
            id = "receita",
            title = "Receita",
            category = "Ocasiões",
            categoryEmoji = "🎉",
            summary = "Tempo de preparo, porções, ingredientes, modo de preparo passo a passo e dicas.",
            defaultNoteTitle = "Receita",
            rawMarkdown = """
# 🍲 {{titulo}}
⏱️ Preparo: min · 🍽️ Porções: · #receita

## Ingredientes
- 
- 

## Modo de preparo
1. 
2. 

## Dicas
- 

⭐ Nota: /5
            """.trimIndent()
        ),
        NoteTemplate(
            id = "evento_festa",
            title = "Evento / Festa",
            category = "Ocasiões",
            categoryEmoji = "🎉",
            summary = "Lista de convidados, checklist de organização (local, convites, comida, música) e orçamento.",
            defaultNoteTitle = "Evento - {{data}}",
            rawMarkdown = """
# 🎂 {{titulo}}
📅 Data: · 📍 Local: · #evento

## Convidados
| Nome | Confirmou |
|---|---|
|  | ⬜ |

## Organização
- [ ] Local reservado
- [ ] Convites enviados
- [ ] Comida e bebida
- [ ] Decoração
- [ ] Música

## Orçamento
| Item | Valor |
|---|---|
|  |  |
| **Total** |  |
            """.trimIndent()
        ),
        NoteTemplate(
            id = "ver_ler_jogar",
            title = "Para Ver, Ler e Jogar",
            category = "Ocasiões",
            categoryEmoji = "🎉",
            summary = "Listas de filmes, séries, livros, jogos e podcasts + tabela de avaliações.",
            defaultNoteTitle = "Para Ver, Ler e Jogar",
            rawMarkdown = """
# 🍿 Minha Lista
#lista

## 🎬 Filmes e séries
- [ ] 

## 📚 Livros
- [ ] 

## 🎮 Jogos
- [ ] 

## 🎧 Podcasts e músicas
- [ ] 

## Já vi, li ou joguei
| Título | Nota | Comentário |
|---|---|---|
|  | /5 |  |
            """.trimIndent()
        ),

        // 🧠 Conhecimento
        NoteTemplate(
            id = "nota_permanente",
            title = "Nota Permanente (Segundo Cérebro)",
            category = "Conhecimento",
            categoryEmoji = "🧠",
            summary = "Ideia central em uma frase, explicação com suas palavras, conexões [[links]] e fonte.",
            defaultNoteTitle = "Nota Permanente",
            rawMarkdown = """
# {{titulo}}
📅 {{data}} · #permanente

## Ideia (em uma frase)
> 

## Explicação com as minhas palavras
 

## Conexões
- Relaciona-se com: [[ ]]
- Exemplo: 

## Fonte
- 
            """.trimIndent()
        ),

        // 🚚 Logística
        NoteTemplate(
            id = "logistica_rota",
            title = "Planeamento Logístico & Rota",
            category = "Logística",
            categoryEmoji = "🚚",
            summary = "Controlo de itinerários, pontos de paragem, prazos de entrega e contactos.",
            defaultNoteTitle = "Logística {{data}}",
            rawMarkdown = """
# 🚚 Logística & Roteiro
📅 {{data}} · #logistica

## 📍 Rota & Destinos
| Ponto | Destino | Horário Previsto | Status |
|---|---|---|---|
| 01 |  |  | ⏳ Pendente |
| 02 |  |  | ⏳ Pendente |

## 📦 Lista de Carga / Entregas
- [ ] Item 1 - Destinatário: 
- [ ] Item 2 - Destinatário: 

## 📞 Contactos Importantes
- Responsável: 
- Suporte / Operações: 

## 📝 Observações de Campo
- Condições de transporte e ocorrências.
            """.trimIndent()
        ),
        NoteTemplate(
            id = "logistica_estoque",
            title = "Inventário & Suprimentos",
            category = "Logística",
            categoryEmoji = "📦",
            summary = "Contagem física, entradas, saídas, itens em falta e reposições urgentes.",
            defaultNoteTitle = "Inventário {{data}}",
            rawMarkdown = """
# 📦 Inventário & Controlo de Stock
📅 {{data}} · #logistica #estoque

## 📋 Tabela de Suprimentos
| Código | Produto / Item | Qtd Atual | Mínimo | Repor? |
|---|---|---|---|---|
| 001 |  | 0 | 5 | ⚠️ Sim |

## 🚨 Reposições Prioritárias
- [ ] Solicitar cotação para: 

## 🔍 Registo de Entradas & Saídas
- Entrada: 
- Saída: 
            """.trimIndent()
        ),

        // 📝 Rascunhos
        NoteTemplate(
            id = "rascunho_rapido",
            title = "Rascunho Rápido",
            category = "Rascunhos",
            categoryEmoji = "📝",
            summary = "Anotação livre para registar pensamentos imediatos, listas de ideias e pontos soltos.",
            defaultNoteTitle = "Rascunho {{data}}",
            rawMarkdown = """
# 📝 Rascunho
📅 {{data}} · ⏰ {{hora}} · #rascunho

## Ideias & Pensamentos
- 

## Ações Rápidas
- [ ] 
            """.trimIndent()
        )
    )

    fun buildUnsavedNoteFromTemplate(template: NoteTemplate): Note {
        val now = Date()
        val dateStr = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(now)
        val timeStr = SimpleDateFormat("HH:mm", Locale("pt", "BR")).format(now)

        val resolvedTitle = template.defaultNoteTitle
            .replace("{{data}}", dateStr)
            .replace("{{hora}}", timeStr)

        val resolvedContent = template.rawMarkdown
            .replace("{{data}}", dateStr)
            .replace("{{hora}}", timeStr)
            .replace("{{titulo}}", resolvedTitle)

        val mappedCategory = when (template.category) {
            "Estudo" -> "Estudos"
            "Trabalhos" -> "Trabalho"
            else -> template.category
        }

        return Note(
            id = 0L,
            title = resolvedTitle,
            content = resolvedContent,
            category = mappedCategory,
            iconEmoji = template.categoryEmoji
        )
    }
}
