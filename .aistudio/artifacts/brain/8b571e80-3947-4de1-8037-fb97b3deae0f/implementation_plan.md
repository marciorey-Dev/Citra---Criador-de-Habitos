# Plano de Implementação: Caixa de Entrada & Compartilhamento Direto de Notas no Citra

Sistema leve e eficiente de mensagens e notas compartilhadas, integrando a Caixa de Entrada (Inbox) com o sino de notificações no topo da aplicação, envio direto entre usuários por `@username` e transmissão de comunicados globais oficiais com selo de desenvolvedor.

---

## 1. Visão Geral da Funcionalidade

- **Sino de Notificações com Badge**:
  - Localizado no topo da tela principal, ao lado do botão de 3 pontinhos (`MoreVert`).
  - Badge numérico indicando a quantidade de notas novas/não lidas.
  - Ao clicar, abre a **Caixa de Entrada**.
- **Caixa de Entrada (Inbox)**:
  - Lista de notas recebidas em formato de cartões de mensagem limpos e minimalistas.
  - Filtro e visualização instantânea de notas em modo apenas leitura com renderizador Markdown nativo.
  - Ações por nota:
    - **Ler**: Abre o leitor de Markdown da nota.
    - **Baixar**: Salva a nota no banco de dados local do usuário (`Room`), permitindo edição posterior. Mantém a mensagem na caixa com etiqueta "Baixada" (conforme resposta do usuário).
    - **Apagar**: Remove a nota da caixa de entrada.
  - Botão **Limpar Caixa de Entrada**: Remove notas já lidas/processadas.
  - Botão de **Refresh** manual: Consulta o Firebase sob demanda para checar novidades.
  - Verificação automática silenciosa ao iniciar o app caso haja conexão à internet.
- **Envio Direto de Notas por @username**:
  - Diálogo de envio acessível pela Caixa de Entrada (criar nova ou escolher existente) ou diretamente de qualquer nota (botão de Globo Azul 🌐 nos 3 pontinhos e no menu de contexto de toque longo).
  - Campo de texto para `@username` do destinatário com **validação visual instantânea** (indicador de verificação em tempo real, informando o nome e foto do usuário se existir, ou alerta se não for encontrado).
  - Envio rápido e leve gravando diretamente na caixa do destinatário no Firestore.
- **Contas de Desenvolvedor & Notas Globais (Oficial Citra)**:
  - Contas autorizadas:
    - `obitoinfamous@gmail.com`
    - `marcioafonsorey@gmail.com`
    - `marcioreyakm@gmail.com` (e `marcioreyakm@gmail.co`)
  - Ao enviar uma nota, desenvolvedores têm a opção de alternar para **"Enviar para todos os usuários (Comunicado Global)"**.
  - As notas oficiais contam com o selo distintivo **"Oficial Citra"** (laranja cítrico com etiqueta/fita dourada e ícone de mascote/estrela).
  - **Regra de Proteção**: Notas oficiais de desenvolvedores **não podem ser baixadas** para edição local, permanecendo sempre como leitura oficial.

---

## 2. Arquitetura de Dados & Otimização do Firebase (Plano Gratuito)

Para respeitar as cotas gratuitas do Firebase Firestore:

1. **Mensagens Diretas (P2P)**:
   - Caminho: `/users/{recipientUserId}/inbox/{messageId}`
   - O remetente grava diretamente no caminho do destinatário (1 escrita).
   - O destinatário só lê a sua própria subcoleção de inbox sob demanda (no refresh ou na abertura do app).
2. **Comunicados Globais (Broadcasts)**:
   - Caminho: `/broadcasts/{broadcastId}`
   - O desenvolvedor grava 1 único documento na coleção `/broadcasts`.
   - Não há multiplicação de gravações por usuário!
   - Usuários consultam os últimos comunicados no refresh (`limit(15)` ordenado por data decrescente).
   - Estados locais de leitura/exclusão de broadcasts são mantidos no `SharedPreferences` do dispositivo (`citra_inbox_dismissed_broadcasts`), evitando custos extras de banco.
3. **Busca de Destinatário por @username**:
   - Consulta pontual em `/users` filtrando `whereEqualTo("username", cleanUsername).limit(1)`.
   - Executada com debounce de 500ms para evitar requisições desnecessárias enquanto o usuário digita.

---

## 3. Componentes e Telas a Criar / Modificar

### A. Repositório e Modelo de Dados
- **`InboxMessage.kt`** (Novo modelo):
  - `id`: String (UUID)
  - `senderUid`: String
  - `senderDisplayName`: String
  - `senderUsername`: String
  - `senderEmail`: String?
  - `isOfficialDev`: Boolean
  - `title`: String
  - `contentMarkdown`: String
  - `category`: String
  - `sentAtMillis`: Long
  - `isRead`: Boolean
  - `isDownloaded`: Boolean
  - `isBroadcast`: Boolean
- **`InboxRepository.kt`** (Novo repositório):
  - `checkRecipientExists(username: String): Flow<RecipientLookupResult>`
  - `sendDirectNote(recipientUid: String, message: InboxMessage): Result<Unit>`
  - `sendBroadcastNote(message: InboxMessage): Result<Unit>`
  - `fetchUserInbox(userUid: String): Flow<List<InboxMessage>>`
  - `markAsRead(userUid: String, messageId: String)`
  - `markAsDownloaded(userUid: String, messageId: String)`
  - `deleteMessage(userUid: String, messageId: String)`
  - `clearInbox(userUid: String, messageIds: List<String>)`

### B. Interface do Usuário (Jetpack Compose)
1. **`MainActivity.kt`**:
   - Adicionar o ícone de sino com `BadgedBox` e contagem de itens não lidos ao lado do botão de 3 pontinhos.
   - Observar `unreadInboxCount` no `MainViewModel`.
2. **`InboxScreen.kt` / `InboxDialog.kt`** (Nova tela/diálogo):
   - Barra superior com título "Caixa de Entrada", contador de mensagens, botão de refresh animado e ação "Limpar".
   - Lista de mensagens com design cítrico:
     - Badge especial "Oficial Citra" em ouro e laranja para comunicados da equipe.
     - Botão de leitura direta (abre modal com Markdown formatado).
     - Botão de download para o bloco de notas (desabilitado com tooltip explicativo para notas oficiais).
     - Botão de exclusão individual.
   - Botão flutuante ou de ação "+ Enviar Nota" para abrir o fluxo de envio.
3. **`SendNoteDialog.kt`** (Novo diálogo de envio):
   - Campo para digitar o `@destinatário`.
   - Card de feedback instantâneo com animação suave exibindo o avatar, nome e confirmação visual se o usuário existe, ou aviso em tom suave se não encontrado.
   - Toggle exclusivo para contas de desenvolvedor (`obitoinfamous@gmail.com`, `marcioafonsorey@gmail.com`, `marcioreyakm@gmail.com`): "📢 Enviar como Comunicado Oficial para todos os usuários".
   - Seleção da nota: escolher uma nota existente das anotações locais ou redigir título e conteúdo em Markdown na hora.
4. **`NotesSubTab.kt` & `FullScreenNoteEditor.kt`**:
   - Adicionar a opção **"Compartilhar via Citra"** com ícone de Globo Azul (`Icons.Default.Public`) nos menus de 3 pontinhos da nota e no menu de contexto ao pressionar e segurar um card de nota.
   - Dispara diretamente o `SendNoteDialog` já pré-preenchido com a nota selecionada.

### C. Regras de Segurança do Firebase (`firestore.rules`)
- Permitir que qualquer usuário autenticado crie mensagens na subcoleção `inbox` de outro usuário (para envio de notas).
- Permitir que o proprietário leia, atualize e apague mensagens da sua própria subcoleção `inbox`.
- Permitir leitura pública autenticada de `/broadcasts`.
- Permitir gravação em `/broadcasts` estritamente se o e-mail do autor for um dos e-mails autorizados dos desenvolvedores.

---

## 4. Plano de Verificação e Testes

1. **Envio e Recepção P2P**:
   - Enviar nota de um usuário A para um `@username` de B.
   - Validar retorno visual instantâneo do destinatário.
   - Verificar recepção em B, sino de notificação com badge e leitura em Markdown.
   - Testar ação de "Baixar" para as notas locais e conferir etiqueta "Baixada".
2. **Contas de Desenvolvedor & Comunicados Globais**:
   - Logar com uma conta desenvolvedor e verificar ativação do botão de transmissão global.
   - Enviar comunicado e confirmar exibição do selo **Oficial Citra** (laranja com dourado).
   - Confirmar bloqueio de download (apenas leitura disponível).
3. **Performance & Consumo**:
   - Garantir que o refresh só consulta dados necessários e o app abre rapidamente sem travas.
