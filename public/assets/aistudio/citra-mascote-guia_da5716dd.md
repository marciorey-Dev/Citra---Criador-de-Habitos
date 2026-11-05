<!-- citra_id: 1cab8e21-7040-4bf4-8df5-59e868e2fd05 -->
# 🍊 Citra: Guia do Mascote e prompt para o agente

> O app **é** o mascote: uma laranja minimalista com dois olhos. Ele vira o ícone, abre o app e reage ao que o usuário faz.

**Protótipo para ver e testar:** prancheta "Mascote Citra" (conceito, expressões, ícone, paleta e a abertura interativa).
**Ficheiro de apoio:** `citra-mascote.svg` (o mascote neutro, com ids por parte).

---

## 1. 🎨 Conceito

| Item | Decisão |
|---|---|
| **Nome** | Citra (o mesmo do app) |
| **Forma** | Círculo perfeito. Sem boca, sem braços, sem pernas |
| **Identidade** | Dois olhos brancos grandes, pupilas escuras com brilho, e uma folha no topo |
| **Personalidade** | Atento, gentil, um pouco tímido. Tudo se diz com o olhar |
| **Regra de ouro** | Ele **nunca fala nem faz ruído**. Só olha, pisca, acena e pula |
| **Detalhe vivo** | A folha balança quando ele fica feliz |

**Para manter minimalista:** cores chapadas, só 6 cores, formas simples, sem contorno, sem sombras complexas. Expressão vem de **pálpebras**, **pupilas** e **pequenos movimentos do corpo**.

**Evitar:** boca, sobrancelhas, olhos assustadores, ficar parecido com personagens de laranja que já existem, animações longas que atrasem o uso do app.

### Paleta

| Cor | Hex | Uso |
|---|---|---|
| Laranja Citra | `#FF8B1F` | Corpo e fundo do ícone |
| Pêssego | `#FFB866` | Brilho no corpo |
| Folha | `#3FA34D` | Folha (veia `#2F8A3E`) |
| Tinta | `#2B1A12` | Pupilas, textos e botões |
| Creme | `#FFF6EA` | Fundo das telas |
| Bochecha | `#E8451F` | Bochechas (45% de opacidade) |

---

## 2. 📐 Especificação visual (espaço 200 × 212)

Origem `viewBox="0 -12 200 212"`. Todos os valores abaixo estão nesse espaço e escalam com o tamanho.

| Parte | Geometria |
|---|---|
| **Corpo** | Círculo, centro (100, 100), raio 80 |
| **Brilho** | Elipse (64, 52), rx 17, ry 8, rotação −35°, 75% de opacidade |
| **Folha** | Caule e folha no topo. Pivô da animação na base do caule (100, 22) |
| **Olhos** | Elipses brancas em (74, 98) e (126, 98), rx 17, ry 21 |
| **Pupilas** | Círculos r 9 nos mesmos centros, mais um brilho branco r 3 em (+3,5, −4,5) |
| **Bochechas** | Elipses (48, 120) e (152, 120), rx 10, ry 6 (só no humor feliz) |
| **Olhos felizes** | Arcos `M58 104 Q74 84 90 104` e `M110 104 Q126 84 142 104`, traço 6 |

### Parâmetros de expressão

| Humor | Pálpebra (escala Y dos olhos) | Pupila | Extras |
|---|---|---|---|
| `neutro` | 1 | normal | Pisca em ciclo |
| `feliz` | arcos no lugar dos olhos | n/a | Bochechas e folha balançando |
| `discreto` (desvia o olhar) | 0,6 | normal | Olha para cima à esquerda |
| `não` | 0,5 | normal | Balança a cabeça |
| `sonolento` | 0,2 | normal | Sem piscar |
| `surpreso` | 1,14 | escala 0,75 | Opcional |

### Olhar (gaze)

- Entrada: `lookX` e `lookY`, de **−1 a 1**.
- **Pupilas** deslocam `(lookX × 6, lookY × 7)` dentro do olho.
- **Conjunto dos olhos** desloca `(lookX × 5, lookY × 4)` sobre o corpo. Isso dá a sensação de volume.
- Cálculo a partir do toque: `vetor = toque − centro do mascote`, depois `lookX = clamp(dx / 110)` e `lookY = clamp(dy / 110)`, com 110 dp para um mascote de 200 dp de largura. Escala proporcionalmente se o tamanho mudar.
- **Suavização:** 120 a 160 ms com ease-out (ou uma mola leve). Nunca saltar de posição.

### Piscar

Ciclo de **3,2 s**: aberto, pisca (escala Y 0,08) aos 41% e outra vez aos 48%, aberto até o fim. No app real pode ser aleatório (2 a 6 s), com 1 em cada 4 piscadas dupla.

---

## 3. 📱 Ícone do app

> O ícone é o **rosto cheio**: fundo laranja, só os olhos. Sem folha, sem moldura.

**Ícone adaptativo do Android (108 × 108 dp):**

| Camada | Conteúdo |
|---|---|
| **Fundo** | Cor chapada `#FF8B1F` |
| **Primeiro plano** | Os dois olhos, centrados em (54, 54), dentro da zona segura de 66 dp |
| **Monocromático** (Android 13+) | Os olhos em silhueta, com as pupilas recortadas |

Primeiro plano em SVG (`viewBox="0 0 108 108"`):

```svg
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108">
  <ellipse cx="35.8" cy="54" rx="11.9" ry="14.7" fill="#FFFFFF"/>
  <ellipse cx="72.2" cy="54" rx="11.9" ry="14.7" fill="#FFFFFF"/>
  <circle cx="35.8" cy="54" r="6.3" fill="#2B1A12"/>
  <circle cx="72.2" cy="54" r="6.3" fill="#2B1A12"/>
  <circle cx="38.3" cy="50.8" r="2.1" fill="#FFFFFF"/>
  <circle cx="74.7" cy="50.8" r="2.1" fill="#FFFFFF"/>
</svg>
```

Também gerar: ícone da loja 512 × 512 (mesma composição) e uma versão com a folha para o ecrã de abertura.

---

## 4. 🎬 Sequência de abertura (primeira vez e depois)

Duração total até o último botão aparecer: **cerca de 7,5 s**. Linha do tempo:

| Tempo | O que acontece | Detalhe |
|---|---|---|
| **0,0 – 1,8 s** | Tela **toda laranja**, com os olhos piscando | O rosto está ampliado 4× e centrado. Dupla piscada perto de 1,3 s |
| **1,8 – 3,2 s** | **Zoom out** revela o mascote inteiro | Escala 4 → 1 com ease-in-out. O laranja de fundo some entre 2,0 e 2,8 s e revela o creme |
| **3,3 – 4,6 s** | O mascote **bate no chão duas vezes** | 1.º pulo: sobe 78 px, aterra com achatamento (escala 1,10 × 0,88). 2.º pulo menor: sobe 36 px. Sombra no chão encolhe quando ele sobe |
| **4,7 – 5,7 s** | **Sobe do centro da tela até o topo** | Sobe 250 px, passa 12 px do ponto final e assenta com um **pequeno bounce**. Sombra desaparece |
| **5,4 s** | Aparece o **logotipo "citra"** (fade + sobe 10 px) | Logo abaixo do mascote |
| **5,5 s** | Frase de apoio | "Foco, hábitos e notas num só lugar" |
| **5,7 s** | Campo **e-mail** | Fade-in |
| **6,0 s** | Campo **senha** | Fade-in |
| **6,3 s** | Botão **Entrar** | Fade-in |
| **6,6 s** | **Continuar com Google** | Fade-in |
| **6,9 s** | **Usar sem conta** | Fade-in |
| **7,2 s** | Aviso de privacidade | "Seus dados ficam no aparelho. A conta serve só para o backup." |

Cada fade-in dura **0,55 s** e sobe 10 px. As opções entram **uma depois da outra, na ordem de chegada**.

**Regras:**
- Só **a primeira abertura do app** (ou após limpar dados) mostra a sequência completa. Nas outras, uma versão curta de cerca de 1,5 s: olhos piscam e o mascote sobe.
- **Modo "reduzir animações"** do sistema ligado: sem zoom nem pulos, mascote já no topo e botões visíveis.
- **Tocar na tela** durante a abertura não a interrompe, mas os olhos já seguem o toque.
- Não bloquear o app: se os dados já estiverem prontos, a sequência é puramente visual.

> Nota: você escreveu "zoom in e se revelando o logotipo". Interpretei como a câmera **afastando** a partir de dentro da laranja, revelando o mascote no chão. Se a ideia era outra, diga e eu ajusto.

---

## 5. 👆 Interações e reações

| Gesto do usuário | Reação do mascote | Parâmetros |
|---|---|---|
| **Dedo ou cursor em movimento** | Olhos seguem o toque | Suavização 140 ms |
| **Digitar no e-mail** | **Acena a cabeça** a cada letra | Desce 7 px, inclina 3°, achata 0,96 em 0,42 s |
| **Foco no campo de senha** | Olha **para baixo**, na direção do campo | lookX 0,1 · lookY 0,95 |
| **Mostrar a senha** | **Desvia o olhar** e baixa as pálpebras | lookX −1 · lookY −0,75 · pálpebra 0,6. Mantém enquanto a senha estiver visível |
| **Ocultar a senha** | Volta a seguir o toque | |
| **Segurar um botão** | Fica **feliz** (arcos, bochechas, folha balança) | Enquanto estiver pressionado |
| **Toque em Continuar ou Usar sem conta** | **Pulinho** feliz | Sobe 26 px em 0,45 s, humor feliz por 1,4 s |
| **Entrar com e-mail válido** | Pulinho feliz | Humor feliz por 1,8 s |
| **Entrar com e-mail inválido** | **Balança a cabeça** (não) | ±9 px e ±6° em 0,55 s, humor "não" por 1 s |
| **12 s sem tocar** | **Sonolento** | Até o próximo toque |

**Prioridade quando vários estados coincidem** (do mais forte para o mais fraco):
1. Humor temporário (`não`, `feliz` após ação)
2. Botão pressionado (`feliz`)
3. Senha visível (`discreto`)
4. Campo em foco (olha para baixo)
5. Sonolento
6. Seguir o toque

---

## 6. 🧩 Arquitetura sugerida

**Um único componente de mascote**, sem lógica de tela dentro:

```text
CitraMascote(
  humor:   neutro | feliz | discreto | nao | sonolento | surpreso,
  olharX:  -1..1,
  olharY:  -1..1,
  tamanho: dp,
  folha:   true | false,
  piscar:  true | false
)
```

As **telas** decidem o humor e o olhar (tabela de prioridades da secção 5). O componente só desenha e anima.

| Camada | Responsabilidade |
|---|---|
| `CitraMascote` | Desenho vetorial, pálpebras, pupilas, piscar, folha. Sem acesso a toque nem a regras de negócio |
| `ReacaoMascote` | Animações pontuais: acenar, balançar a cabeça, pular (classe de animação ligada ao componente) |
| `OlharController` | Recebe a posição do toque, calcula `olharX` e `olharY` com suavização |
| `AberturaScreen` | Linha do tempo da abertura (secção 4) |
| `LoginScreen` | Liga campos e botões aos humores e às reações |

**Por plataforma:**

| Stack | Sugestão |
|---|---|
| **Jetpack Compose** | `Canvas` ou `ImageVector` para o desenho, `Animatable` e `animateFloatAsState` para o olhar, `pointerInput` para o toque |
| **Flutter** | `CustomPainter` com `AnimationController`, `Listener` para o toque |
| **React Native** | `react-native-svg` com Reanimated, `PanResponder` ou gesture handler |

**Qualidade:**
- 60 fps, sem alocar objetos a cada quadro.
- Acessibilidade: descrição "Citra, o mascote do aplicativo" e **o mascote nunca é o único aviso** de erro (a mensagem de texto continua).
- Respeitar "reduzir animações".
- Funciona **offline** e não depende de imagens externas: tudo é vetorial.

---

## 7. 🤖 Como ordenar ao seu agente de IA

### 7.1 Regras de ouro

1. **Diga a stack.** Compose, Flutter ou React Native muda tudo. Se não disser, o agente chuta.
2. **Dê o desenho, não só a ideia.** Anexe `citra-mascote.svg` e esta especificação.
3. **Um objetivo por pedido.** Mascote estático, depois olhar, depois reações, depois abertura. Revise cada etapa.
4. **Peça um plano antes do código** (até 10 linhas) e as dúvidas dele.
5. **Dê números.** Tempos, distâncias e cores desta página, não adjetivos como "fofo" ou "suave".
6. **Critério de aceite em cada pedido.** O que deve funcionar para você aprovar.
7. **Proíba o que não quer.** Não mexer em outras telas, não adicionar bibliotecas pesadas, não usar imagens rasterizadas.
8. **Peça um commit por etapa**, para poder voltar atrás.

### 7.2 Prompt mestre (copie e cole)

Troque o que estiver entre `[ ]`.

````text
Você é um engenheiro Android sênior. Vamos implementar o mascote do app Citra.

CONTEXTO
- App: Citra (foco, hábitos, notas). Android, offline-first, dados locais.
- Stack: [Jetpack Compose | Flutter | React Native].
- O app É o mascote: uma laranja minimalista com dois olhos. Ele vira o ícone, abre o app
  e reage às ações do usuário. Ele nunca fala nem faz ruído.

ANEXOS
- citra-mascote.svg (desenho oficial, com ids por parte).
- Especificação abaixo. Se algo faltar ou for ambíguo, PERGUNTE antes de supor.

ANTES DE CODAR
Devolva um plano em até 10 linhas, a lista de ficheiros que vai criar ou alterar e suas dúvidas.
Só comece depois de eu aprovar.

ESPECIFICAÇÃO VISUAL (espaço 200 x 212, viewBox "0 -12 200 212")
- Corpo: círculo (100,100) raio 80, #FF8B1F. Brilho: elipse (64,52) rx17 ry8, rotação -35°, #FFB866 a 75%.
- Folha: no topo, #3FA34D, pivô de animação em (100,22).
- Olhos: elipses brancas (74,98) e (126,98), rx17 ry21. Pupilas r9 #2B1A12 com brilho r3.
- Humor feliz: arcos no lugar dos olhos + bochechas #E8451F a 45% + folha balançando.
- Pálpebra (escala Y dos olhos): neutro 1, discreto 0.6, não 0.5, sonolento 0.2, surpreso 1.14.
- Olhar: lookX e lookY de -1 a 1. Pupilas deslocam (lookX*6, lookY*7). O conjunto dos olhos
  desloca (lookX*5, lookY*4). Suavização 140 ms ease-out.
- Piscar: ciclo de 3.2 s com dupla piscada (escala Y 0.08 aos 41% e 48%).

COMPONENTE
CitraMascote(humor, olharX, olharY, tamanho, folha, piscar). Só desenha e anima. Sem lógica de tela.

ABERTURA (primeira abertura)
0.0-1.8s  tela toda laranja, rosto ampliado 4x, olhos piscando (dupla piscada perto de 1.3s).
1.8-3.2s  zoom out (escala 4->1, ease-in-out); o laranja de fundo some entre 2.0 e 2.8s.
3.3-4.6s  dois pulos no chão: 78 px e 36 px, com achatamento ao aterrar; sombra acompanha.
4.7-5.7s  sobe 250 px até o topo, passa 12 px e assenta com pequeno bounce.
5.4s      logotipo "citra". 5.5s frase de apoio.
5.7/6.0/6.3/6.6/6.9/7.2s  e-mail, senha, Entrar, Google, Usar sem conta, aviso de privacidade,
          cada um com fade-in de 0.55s subindo 10 px, um depois do outro.
- Com "reduzir animações" ligado: sem zoom nem pulos; mascote já no topo e tudo visível.
- Aberturas seguintes: versão curta (~1.5 s).

INTERAÇÕES
- Olhos seguem o toque (distância de saturação 110 dp para mascote de 200 dp).
- Digitar e-mail: acena a cabeça a cada letra (desce 7 px, inclina 3°, 0.42 s).
- Foco na senha: olha para baixo (lookX 0.1, lookY 0.95).
- Senha visível: desvia o olhar (lookX -1, lookY -0.75, pálpebra 0.6) enquanto estiver visível.
- Botão pressionado: humor feliz. Toque em Continuar/Usar sem conta: pulinho (26 px, 0.45 s).
- E-mail válido ao Entrar: pulinho e feliz por 1.8 s. E-mail inválido: balança a cabeça
  (±9 px, ±6°, 0.55 s) e humor "não" por 1 s.
- 12 s sem toque: sonolento, até o próximo toque.
- Prioridade: humor temporário > botão pressionado > senha visível > campo em foco
  > sonolento > seguir o toque.

ÍCONE
Ícone adaptativo: fundo #FF8B1F, primeiro plano só com os olhos (sem folha) dentro da zona
segura de 66 dp, e versão monocromática para Android 13+.

REQUISITOS
- 60 fps, sem alocar objetos por quadro. Tudo vetorial, sem imagens rasterizadas.
- Acessibilidade: descrição "Citra, o mascote do aplicativo". O mascote nunca é o único aviso
  de erro: mantenha a mensagem de texto.
- Respeitar "reduzir animações". Funcionar offline.
- Não mexer em telas ou módulos que não citei. Não adicionar bibliotecas pesadas.
- Código comentado só onde não for óbvio. Um commit por etapa.

ENTREGA E CRITÉRIOS DE ACEITE
Siga as etapas abaixo, uma de cada vez, e pare no fim de cada uma para eu validar.
Etapa 1 mascote estático em todos os humores. Etapa 2 olhar e piscar. Etapa 3 reações.
Etapa 4 abertura. Etapa 5 ícone. Etapa 6 polimento (desempenho, acessibilidade, testes).
````

### 7.3 Pedidos por etapa (use depois do plano aprovado)

| Etapa | Pedido curto | Aceite |
|---|---|---|
| **1. Mascote estático** | "Implemente `CitraMascote` a partir do SVG anexo, com todos os humores. Crie uma tela de pré-visualização com um mascote por humor." | Os 6 humores aparecem corretos e escalam sem perder qualidade |
| **2. Olhar e piscar** | "Adicione `olharX/olharY` com suavização de 140 ms e o ciclo de piscar de 3,2 s. Crie `OlharController` que converte o toque em olhar." | Os olhos seguem o dedo sem saltos e piscam em ciclo |
| **3. Reações** | "Adicione acenar, balançar a cabeça e pular, e ligue-os à `LoginScreen` conforme a tabela de interações e a ordem de prioridade." | Cada gesto da tabela provoca a reação certa |
| **4. Abertura** | "Implemente `AberturaScreen` com a linha do tempo da secção 4, incluindo versão curta e modo de reduzir animações." | Tempos e movimentos batem com a tabela, sem travar |
| **5. Ícone** | "Gere o ícone adaptativo (fundo, primeiro plano e monocromático) e o ícone da loja 512 px." | O ícone aparece com o rosto cheio em vários launchers |
| **6. Polimento** | "Revise desempenho, acessibilidade e testes. Liste o que ficou de fora." | 60 fps, descrição de acessibilidade e testes passam |

### 7.4 Depois que o agente entregar, confira

- [ ] Os olhos nunca "saltam" de posição, sempre deslizam.
- [ ] A senha visível faz o mascote desviar o olhar, e voltar ao ocultar.
- [ ] O pulo e o bounce parecem peso de uma laranja, não de uma bola de borracha. Se estiver muito elástico, reduza o achatamento.
- [ ] Nada fica preso se o usuário tocar rápido, trocar de campo ou sair da tela.
- [ ] Com "reduzir animações" tudo continua utilizável.
- [ ] A frase "Foco, hábitos e notas" e os botões têm contraste suficiente.
- [ ] O ícone continua reconhecível em 48 px.

### 7.5 Como corrigir o agente (exemplos)

| Em vez de dizer | Diga |
|---|---|
| "Está estranho" | "Os olhos saltam ao mudar de campo. Anime `olharX/olharY` por 140 ms ease-out" |
| "Faz mais fofo" | "Aumente o achatamento ao aterrar de 0,88 para 0,84 e prolongue a pausa no chão em 80 ms" |
| "Está lento" | "A abertura leva 9 s. Reduza para 7,5 s encurtando só a espera inicial" |
| "Não ficou como o desenho" | "A pupila está em (74,98) no SVG e no código está em (72,100). Corrija" |

---