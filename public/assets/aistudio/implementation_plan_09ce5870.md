# Citra — Arquitetura de Alarmes Absolutos & Timer Global (Padrão Relógio e Calendário Android)

Plano técnico e de experiência do usuário para eliminar a dependência de cache ou uso recente em **Tarefas, Hábitos e Timer (Arancia)**, corrigindo os conflitos de bloqueio de tela, o desaparecimento dos mascotes Citras na pausa, a dupla contabilização de sessões e integrando a nova celebração interativa diretamente na tela do Timer.

---

## Revisão do Usuário & Decisões Críticas

> [!IMPORTANT]
> As decisões abaixo foram confirmadas na etapa de alinhamento e revisadas com suas correções do Timer. Nenhuma modificação será aplicada ao código até sua aprovação final.

- **Classificação & Permissão no Android 14+ (Confirmado)**: Manter o Citra explicitamente posicionado como aplicativo de Calendário e Alarme usando `USE_EXACT_ALARM` (com fallback compatível com `SCHEDULE_EXACT_ALARM` para Android 12–13), garantindo isenção automática do *App Standby Bucket* restrito mesmo após semanas sem abrir o app.
- **Comportamento na Tela Bloqueada (Confirmado)**: Apenas **Tarefas com "Toque em Loop Contínuo" ativo** e o **Fim do Timer (Arancia)** despertam a tela cheia em modo despertador (`FullScreenIntent`). **Hábitos** notificam com som contínuo opcional sem sequestrar a tela cheia por padrão.
- **Verificador de Saúde dos Alarmes & Fabricantes (Confirmado)**: Diagnóstico guiado verificando bateria irrestrita, tela cheia no Android 14+, notificações e instruções por fabricante (Xiaomi, Samsung, Motorola, Realme/Oppo).
- **Timer Global, Anti-Duplicidade & Celebração na Tela (Revisão Integrada)**:
  - Contabilização global do Timer independente de o usuário estar na tela do Timer, em outro aplicativo ou com o celular na tela de bloqueio por longos períodos.
  - Trava atômica por token único de sessão para impedir definitivamente que uma sessão seja contabilizada duas vezes ou gere dois Citras.
  - Visibilidade garantida dos mascotes Citras acumulados durante toda a fase de Pausa (parada ou rodando) e após retorno do segundo plano.
  - Celebração integrada na própria tela do Timer (ocultando temporariamente o dial laranja para dar lugar ao `🎉` grande, título único parabenizando a sessão, partículas de Citras explodindo pela tela e botão **Continuar** que abre a Pausa).

---

## 1. Visão Geral & Diagnóstico dos Conflitos Atuais

- **Por que Tarefas e Hábitos falham fora do cache**:
  1. **Receptor de alarme encerra antes de reagendar**: O processamento assíncrono em banco não utiliza `goAsync()`, levando o sistema a suspender o processo antes de gravar e agendar o alarme seguinte.
  2. **Áudio executado sem Serviço em Primeiro Plano (`ForegroundService`)**: O `MediaPlayer` tocado diretamente no receptor sofre corte imediato pelo sistema quando o app está fora da memória.
  3. **Fila única frágil & ausência de `Direct Boot`**: Um único slot compartilhado entre domínios e a falta de índice em *Device Encrypted Storage* impedem a recuperação antes do PIN pós-reinício.
  4. **Tela de alarme acoplada à inicialização pesada**: Tentar abrir a interface principal inteira falha ou atrasa na tela de bloqueio no Android 14+ sem `AlarmActivity` dedicada e sem checar `canUseFullScreenIntent`.
- **Por que o Timer (Arancia) esquece, não notifica, perde Citras na Pausa e duplica sessões**:
  1. **Colisão de IDs de Notificação**: A notificação silenciosa contínua do cronômetro e a notificação de conclusão usam o **mesmo identificador numérico**, fazendo com que o cancelamento do progresso ou o canal de baixa importância apague ou silencie o alerta de término após longos períodos na tela de bloqueio.
  2. **Contagem dependente da tela (`viewModelScope`)**: Quando o aparelho entra em repouso na tela de bloqueio ou o usuário troca de aplicativo, a corrotina de UI congela e o receptor de fim do Timer morre antes de gravar a sessão no Room (porque não usa `goAsync()` nem serviço dedicado).
  3. **Instâncias desconexas de preferências para os mascotes Citras**: Quando o fundo registra um novo Citra no armazenamento local, o fluxo em memória observado pela tela do Timer e pela tela de Pausa não recebe a atualização — fazendo os Citras sumirem durante a Pausa ou só aparecerem se o usuário estiver olhando a tela exata na hora do término.
  4. **Dupla contabilização por corrida entre UI e Receptor**: Tanto a camada de interface quanto o receptor de alarme gravam `FocusSession` e criam o mascote de forma independente, sem trava transacional; quando ambos acordam próximos ao fim do ciclo, a mesma sessão é contada em dobro.

---

## 2. Experiência do Usuário & Design Visual

### Fluxos Principais do Usuário

1. **Timer Global em Segundo Plano & Tela de Bloqueio**:
   - Ao iniciar o foco (ou a pausa), um **Serviço Global de Timer** assume o controle do tempo real (`targetEndTimeMillis`), exibindo na barra de notificações um cronômetro regressivo nativo (`Chronometer`) com botões de **Pausar**, **Retomar** e **Interromper**.
   - Se o usuário bloquear o celular por horas, usar outros aplicativos ou fechar o Citra dos recentes, o `AlarmClock` dispara no segundo exato, o serviço de conclusão contabiliza a sessão **uma única vez**, gera o mascote Citra e aciona a notificação de alto destaque (em canal e ID próprios) + despertador na tela bloqueada.
2. **Nova Celebração Integrada na Tela do Timer**:
   - Assim que o usuário está na tela do Timer ao término do foco — ou **assim que volta ao app** vindo da tela de bloqueio ou de outro aplicativo após o fim do foco:
     - O **dial laranja** do relógio recolhe suavemente (`AnimatedVisibility` / transição de escala e opacidade).
     - No centro da mesma tela surge o **ícone grande `🎉`** acompanhado de **apenas um título parabenizando a sessão** (ex.: *"Sessão Concluída!"*), **sem** o diálogo modal antigo e sem blocos extras de texto.
     - Uma **explosão contínua de partículas de Citras** anima a tela inteira usando o motor físico do overlay.
     - O botão largo **"Continuar"** confirma a celebração, faz o dial retornar suavemente já posicionado na **fase de Pausa** e libera o acesso imediato ao descanso — mantendo todos os mascotes Citras acumulados (incluindo o recém-ganho) visíveis e interativos no fundo durante toda a Pausa.
3. **Mascotes Citras Visíveis na Pausa e em Qualquer Retorno**:
   - A camada de física dos Citras permanece ativa tanto no modo ocioso quanto durante toda a contagem da **Pausa** (parada ou rodando), sincronizada em tempo real com uma fonte única de verdade compartilhada entre segundo plano e UI.
4. **Verificador de Saúde dos Alarmes ("Modo Relógio Absoluto")**:
   - Painel em **Notificações & Alarmes** (com banner de aviso na criação de alarmes/timer se alguma proteção estiver faltando) exibindo status ao vivo de: **Alarmes Exatos**, **Tela Cheia no Bloqueio (Android 14+)**, **Bateria Sem Restrições** e **Guia do Fabricante** (Xiaomi/HyperOS, Samsung, Motorola, Realme/Oppo).
5. **Tela Dedicada de Despertador & Hábitos Discretos**:
   - Tarefas com **"Toque em Loop Contínuo"** e **Fim do Timer** abrem a **Tela Dedicada de Alarme** sobre o bloqueio (`#090D16` + relógio pulsante `#FF6A00`) com botões grandes de **Desligar** e **Adiar 10 minutos** (ou abrir celebração/pausa no caso do Timer). Hábitos tocam notificação prioritária com loop opcional sem sequestrar a tela cheia.

---

## 3. Decisões Críticas de Produto & Compromissos (Trade-Offs)

### Decisão 1: Token Atômico de Sessão Global vs. Contabilização Dupla (UI + Receptor)
- **Abordagem Escolhida**: Cada ciclo de foco iniciado recebe um `sessionToken` único persistido junto ao `targetEndTimeMillis`. A conclusão passa por um **Coordenador Global de Conclusão Idempotente** que verifica e marca atomicamente o token como concluído dentro de armazenamento transacional antes de inserir `FocusSession` e adicionar o mascote Citra.
- **Por quê**: Elimina 100% a condição de corrida entre o loop de UI (quando o celular acorda) e o `BroadcastReceiver` de alarme, garantindo que cada sessão gere exatamente 1 registro de estatística e 1 mascote Citra — nunca zero e nunca dois.
- **Alternativas Descartadas**: Deixar apenas a `ViewModel` contabilizar (falha quando fora da tela/cache) ou confiar em checagens por janela de tempo aproximada (propenso a falhas quando há múltiplas sessões curtas seguidas).

### Decisão 2: Fonte Única Reativa para Mascotes Citras + Celebração Pendente
- **Abordagem Escolhida**: Transformar o gerenciador de estado do Timer/Citras em um singleton observado reativamente (com listener de alterações no armazenamento e recarregamento obrigatório no `onResume`), além de persistir uma flag `pendingCelebration` quando o foco termina fora da tela do Timer.
- **Por quê**: Se o usuário estava na tela de bloqueio ou em outro app quando o Timer acabou, ao reabrir o Citra ele vê imediatamente o novo mascote contabilizado, a celebração com partículas de Citras + `🎉` + botão **Continuar**, e ao clicar segue para a Pausa com todos os Citras visíveis na tela.
- **Alternativas Descartadas**: Emitir eventos efêmeros de UI (`SharedFlow`) no momento do alarme (eventos perdidos quando a Activity não está visível).

### Decisão 3: `AlarmManager.setAlarmClock` + Serviços Tipados + `WorkManager` Watchdog
- **Abordagem Escolhida**: Slots independentes de `AlarmClock` (Timer, Tarefas/Soneca e Hábitos), `AlarmRingService` e `TimerForegroundService` tipados (`systemExempted` / `mediaPlayback`), índice em *Direct Boot* e `WorkManager` reconciliador a cada 6 horas, com `USE_EXACT_ALARM` + `canUseFullScreenIntent`.
- **Por quê**: Único conjunto de mecanismos que isenta o app do *Restricted Standby Bucket* após semanas sem uso, fura *Deep Doze*, sobrevive a reinícios com PIN na tela e não perde acesso a áudios importados (`takePersistableUriPermission`).
- **Alternativas Descartadas**: `WorkManager` isolado (atrasa em *Doze*) ou `MediaPlayer` dentro de `BroadcastReceiver` (cortado pelo sistema em segundos).

---

## 4. Arquitetura Técnica & Estratégia de Dados *(Referência Técnica)*

### Diagrama Integrado: Alarmes Absolutos & Timer Global

```
┌───────────────────────────────────────────────────────────────────────────┐
│                    ESTADO GLOBAL PERSISTENTE (SINGLETON)                  │
│  ┌────────────────────┐  ┌─────────────────────┐  ┌────────────────────┐  │
│  │  Banco Room (CE)   │  │ Snapshot Timer +    │  │ Índice Direct Boot │  │
│  │ Tarefas, Hábitos & │  │ Token Idempotente & │  │ (DE Storage) p/    │  │
│  │   FocusSessions    │  │ PendingCelebration  │  │ pós-reinício c/PIN │  │
│  └─────────┬──────────┘  └──────────┬──────────┘  └─────────┬──────────┘  │
└────────────┼────────────────────────┼───────────────────────┼─────────────┘
             │                        │                       │
             ▼                        ▼                       ▼
┌───────────────────────────────────────────────────────────────────────────┐
│                MOTOR DE AGENDAMENTO & SERVIÇOS DE PRIMEIRO PLANO          │
│                                                                           │
│  ┌──────────────────────────────────┐   ┌──────────────────────────────┐  │
│  │   Agendador Multi-Slot           │   │   TimerForegroundService     │  │
│  │   (AlarmManager.setAlarmClock)   │   │ • Cronômetro nativo na barra │  │
│  │  [Timer]   [Tarefas]   [Hábitos] │   │ • Ações: Pausar/Retomar/Fim  │  │
│  └─────────────────┬────────────────┘   └──────────────┬───────────────┘  │
│                    │                                   │                  │
│  • BootReceiver (LOCKED_BOOT, BOOT, TIME_SET, UPDATE)  │                  │
│  • WorkManager Watchdog (reconciliação a cada 6h)      │                  │
└────────────────────┼───────────────────────────────────┼──────────────────┘
                     │ Término Exato (Tela Bloqueada / Outro App / Na Tela)
                     ▼
┌───────────────────────────────────────────────────────────────────────────┐
│        COORDENADOR GLOBAL DE CONCLUSÃO (goAsync + Trava Atômica)          │
│                                                                           │
│  1. Adquire goAsync() + WakeLock até concluir todo o bloco (finally)      │
│  2. Valida sessionToken único: se já processado, aborta (ZERO duplicata) │
│  3. Grava FocusSession no Room + incrementa 1 mascote Citra + marca       │
│     pendingCelebration = true + agenda próximo alarme da fila             │
│  4. Notifica em ID e Canal próprios de Conclusão (nunca colide c/ progresso│
└────────────────────┬───────────────────────────────────┬──────────────────┘
                     │                                   │
       [Alarme com Loop / Fim Timer]            [Ao Entrar/Voltar na Tela]
                     │                                   │
                     ▼                                   ▼
┌─────────────────────────────────────┐   ┌─────────────────────────────────┐
│ AlarmRingService + AlarmActivity    │   │     Tela do Timer (Compose)     │
│ • AudioFocus + USAGE_ALARM em loop  │   │ • Oculta dial laranja p/ mostrar│
│ • Acorda tela de bloqueio (API 34+) │   │   🎉 grande + título único      │
│ • Botões: Desligar / Adiar / Abrir  │   │ • Explosão partículas de Citras │
└─────────────────────────────────────┘   │ • Botão "Continuar" -> Pausa    │
                                          │   (Citras sempre visíveis)      │
                                          └─────────────────────────────────┘
```

### Detalhamento dos Mecanismos-Chave

1. **Separação Total de Notificações do Timer**:
   - **Notificação Contínua do Cronômetro**: Canal silencioso (`IMPORTANCE_LOW`) com `setUsesChronometer(true)` e `setChronometerCountDown(true)`, usando ID exclusivo de progresso. Cancelada **sem** interferir no alerta final.
   - **Notificação & Alarme de Conclusão do Timer**: Canal de alerta de alta prioridade (`IMPORTANCE_HIGH` / `CATEGORY_ALARM`) com ID próprio e som configurado, acionando o `AlarmRingService` + `FullScreenIntent` na tela de bloqueio para que nunca seja silenciada nem perdida.

2. **Coordenador Global de Conclusão com Trava Atômica (Anti-Duplicidade)**:
   - Toda sessão de foco ativa armazena `sessionToken` + estado `isCompletedProcessed`.
   - Ao chegar a zero (via `AlarmClock` no receptor com `goAsync()`, via serviço ativo ou ao detectar tempo expirado no retorno da UI), uma operação sincronizada atômica verifica `sessionToken`: apenas o primeiro executor grava a `FocusSession`, gera 1 mascote Citra, define `pendingCelebration = true` e avança o snapshot para `BREAK`. Qualquer chamada concorrente torna-se *no-op*.

3. **Visibilidade Contínua dos Citras & Celebração na Tela**:
   - O `CitraPhysicsOverlay` recebe atualização imediata de uma instância única reativa do repositório/preferências (com observador de mudanças e refresh no `onResume`), permanecendo visível durante a fase de **Pausa** (parada ou contando).
   - Enquanto `pendingCelebration` estiver ativo na tela do Timer, o dial laranja dá lugar ao layout comemorativo minimalista (`🎉` grande, título único parabenizando a sessão, partículas de Citras explodindo na tela e botão **"Continuar"** que limpa `pendingCelebration` e revela o dial já pronto na fase de Pausa).

4. **Resiliência Total para Tarefas e Hábitos (Curto e Longo Prazo)**:
   - Slots independentes no `AlarmManager.setAlarmClock` + busca ilimitada de próxima ocorrência anual para hábitos e tarefas + espelho em *Direct Boot* (`DeviceProtectedStorage`) + `WorkManager` Watchdog periódico + `takePersistableUriPermission` para áudios customizados do armazenamento local + Painel de Saúde dos Alarmes com detecção de fabricante.

---

## Verificação Planejada (Após Aprovação)

- Compilação completa validando permissões (`USE_EXACT_ALARM`, `USE_FULL_SCREEN_INTENT`), serviços tipados e atributos `directBootAware` no manifesto.
- Testes automatizados locais (Robolectric) verificando:
  1. Disparo de conclusão do Timer após expirar com aplicativo fechado/bloqueado e garantia estrita de **exatamente 1 `FocusSession` e 1 mascote Citra** mesmo quando receptor e UI tentam finalizar ao mesmo tempo.
  2. Transição da celebração (`🎉` + título único + partículas + botão **Continuar** abrindo a Pausa com os Citras visíveis).
  3. Reagendamento de tarefas agendadas para +365 dias à frente e restauração após reinício / mudança de fuso horário.
