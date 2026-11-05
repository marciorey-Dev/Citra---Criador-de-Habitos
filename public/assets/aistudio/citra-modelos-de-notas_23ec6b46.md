# 🍊 Citra: Modelos de Notas

> Rascunhos prontos para começar uma nota em segundos. Cada modelo é Markdown puro (`.md`) e funciona também no Obsidian.

**Preenchimento automático (ideia):** `{{data}}`, `{{hora}}` e `{{titulo}}` são trocados pelo Citra ao criar a nota.

---

## 🗂️ Índice

| # | Modelo | Categoria |
|---|---|---|
| 1 | Anotações de Aula (Cornell) | 📚 Estudo |
| 2 | Resumo de Leitura | 📚 Estudo |
| 3 | Plano de Estudo para Prova | 📚 Estudo |
| 4 | Perguntas e Respostas | 📚 Estudo |
| 5 | Exercícios e Fórmulas | 📚 Estudo |
| 6 | Trabalho Escolar | 📝 Trabalhos |
| 7 | Relatório de Laboratório | 📝 Trabalhos |
| 8 | Apresentação / Seminário | 📝 Trabalhos |
| 9 | Lista de Tarefas do Dia | ✅ Produtividade |
| 10 | Plano de Rotina Semanal | ✅ Produtividade |
| 11 | Revisão Semanal | ✅ Produtividade |
| 12 | Meta (SMART) | ✅ Produtividade |
| 13 | Planejamento de Projeto | ✅ Produtividade |
| 14 | Caixa de Entrada (ideias rápidas) | ✅ Produtividade |
| 15 | Ata de Reunião | 💼 Trabalho |
| 16 | Brainstorming | 💼 Trabalho |
| 17 | Relatório Diário de Trabalho | 💼 Trabalho |
| 18 | Nota de Programação | 💻 Programação |
| 19 | Registro de Bug | 💻 Programação |
| 20 | Especificação de Funcionalidade | 💻 Programação |
| 21 | Checklist de Lançamento | 💻 Programação |
| 22 | Diário Noturno | 🌱 Vida pessoal |
| 23 | Gratidão e Conquistas | 🌱 Vida pessoal |
| 24 | Treino e Saúde | 🌱 Vida pessoal |
| 25 | Lista de Compras | 🌱 Vida pessoal |
| 26 | Orçamento do Mês (Finanças) | 💰 Finanças |
| 27 | Viagem | 🎉 Ocasiões |
| 28 | Receita | 🎉 Ocasiões |
| 29 | Evento / Festa | 🎉 Ocasiões |
| 30 | Para Ver, Ler e Jogar | 🎉 Ocasiões |
| 31 | Nota Permanente (Segundo Cérebro) | 🧠 Conhecimento |

---

# 📚 Estudo

## 1. Anotações de Aula (Cornell)

*Para aulas e palestras. Aproveita fotos do quadro e a gravação do áudio.*

````markdown
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
````

## 2. Resumo de Leitura

````markdown
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
````

## 3. Plano de Estudo para Prova

````markdown
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
````

## 4. Perguntas e Respostas

*Para estudar com flashcards.*

````markdown
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
````

## 5. Exercícios e Fórmulas

````markdown
# 🧮 {{titulo}}
📅 {{data}} · #exatas

## Fórmulas
- Equação de 2.º grau: $x = \frac{-b \pm \sqrt{b^2 - 4ac}}{2a}$
- Área do círculo: $A = \pi r^2$
- 

## Exercício 1
**Enunciado:** 

**Resolução:**
$$
 
$$

**Resposta:** 

## Erros para não repetir
- 
````

---

# 📝 Trabalhos

## 6. Trabalho Escolar

````markdown
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
````

## 7. Relatório de Laboratório

````markdown
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
$$
 
$$

## Discussão
## Conclusão
## Referências
````

## 8. Apresentação / Seminário

````markdown
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
````

---

# ✅ Produtividade

## 9. Lista de Tarefas do Dia

````markdown
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
````

## 10. Plano de Rotina Semanal

````markdown
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
````

## 11. Revisão Semanal

````markdown
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
````

## 12. Meta (SMART)

````markdown
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
````

## 13. Planejamento de Projeto

````markdown
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
````

## 14. Caixa de Entrada (ideias rápidas)

````markdown
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
````

---

# 💼 Trabalho

## 15. Ata de Reunião

````markdown
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
````

## 16. Brainstorming

````markdown
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
````

## 17. Relatório Diário de Trabalho

````markdown
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
````

---

# 💻 Programação

## 18. Nota de Programação

````markdown
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
````

## 19. Registro de Bug

````markdown
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
````

## 20. Especificação de Funcionalidade

````markdown
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
````

## 21. Checklist de Lançamento

````markdown
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
````

---

# 🌱 Vida pessoal

## 22. Diário Noturno

````markdown
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
````

## 23. Gratidão e Conquistas

````markdown
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
````

## 24. Treino e Saúde

````markdown
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
````

## 25. Lista de Compras

````markdown
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

**Orçamento previsto:** Kz
````

---

# 💰 Finanças

*Requer o módulo de Finanças ligado. Os blocos entre os comentários são atualizados pelo Citra; o restante da nota é seu.*

## 26. Orçamento do Mês

````markdown
# 💰 Orçamento de {{titulo}}
📅 {{data}} · #financas

## Análise de preços
- [ ] 

## Rendas
<!-- citra:fin:inicio id=fin_novo1 tipo=rendas periodo=mensal -->
(o Citra preenche aqui)
<!-- citra:fin:fim id=fin_novo1 -->

## Despesas Obrigatórias
<!-- citra:fin:inicio id=fin_novo2 tipo=despesas prioridade=obrigatoria periodo=mensal -->
(o Citra preenche aqui)
<!-- citra:fin:fim id=fin_novo2 -->

## Despesas Opcionais
<!-- citra:fin:inicio id=fin_novo3 tipo=despesas prioridade=opcional periodo=mensal -->
(o Citra preenche aqui)
<!-- citra:fin:fim id=fin_novo3 -->

## Lista de compras do mês
- [ ] 

## Resumo (escreva à vontade)
Texto livre. O Citra não altera nada fora dos blocos.
````

---

# 🎉 Ocasiões

## 27. Viagem

````markdown
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
````

## 28. Receita

````markdown
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
````

## 29. Evento / Festa

````markdown
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
````

## 30. Para Ver, Ler e Jogar

````markdown
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
````

---

# 🧠 Conhecimento

## 31. Nota Permanente (Segundo Cérebro)

````markdown
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
````

---

## ➕ Ideias para mais modelos

| Ocasião | Para quê |
|---|---|
| 🧑‍💼 Entrevista de emprego | Perguntas, respostas preparadas e feedback |
| 🏠 Mudança de casa | Checklist por cômodo e contas a transferir |
| 🛍️ Pesquisa de compra | Tabela comparando produtos, preços e lojas |
| 📄 Currículo | Experiências, cursos e habilidades |
| 🌐 Vocabulário de idioma | Palavra, tradução, frase de exemplo |
| 🌌 Diário de sonhos | Registrar sonhos logo ao acordar |
| 🌿 Plantas e jardim | Rega, adubo e fotos de crescimento |
| 🐶 Cuidados com o pet | Vacinas, consultas e rações |
| 🔧 Manutenção da casa | O que consertar e quando |
| 📞 Contactos importantes | Pessoas, funções e telefones |
| 💼 Plano de negócio | Ideia, cliente, custos e primeiros passos |
| ✉️ Cartas e e-mails | Rascunhos formais e informais |
| 🎵 Playlists | Músicas para estudar, treinar e relaxar |
| 🏆 Lições aprendidas | O que funcionou e o que mudar |
