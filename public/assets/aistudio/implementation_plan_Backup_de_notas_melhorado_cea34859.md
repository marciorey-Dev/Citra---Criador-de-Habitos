**implementation\_plan Backup de notas melhorado**

**\[ Categoria: Geral \]**  • Exportado pelo Citra em 23/11/2026 13:58

# **Plano de Implementação: Persistência, Portabilidade e Recuperação Automática de Mídias nas Notas**

## **1\. Diagnóstico: Onde Está o Problema Hoje**

A análise técnica da arquitetura atual de notas, mídias e backups identificou **seis causas raiz** que explicam por que imagens, planos de fundo e áudios quebram tanto com o passar das semanas quanto após desinstalar/reinstalar ou importar notas:

**1\. Backups manuais e exportações de notas descartam todas as mídias:**

   O gerador de backup completo e a exportação de notas em lote serializam apenas o texto Markdown bruto (que contém caminhos locais do aparelho antigo) e o texto da URI do fundo. Os arquivos binários de imagem e áudio não são incluídos no arquivo exportado, e o fluxo de importação no repositório e no ViewModel nunca extrai nem restaura anexos de mídia.

**2\. O "backup automático persistente" é apagado na desinstalação:**

   O gerenciador de backup automático grava na pasta externa privada do aplicativo (Android/data). Desde o Android 11, o sistema remove tanto os arquivos internos quanto essa pasta privada no momento em que o app é desinstalado.

**3\. Armazenamento dividido e caminhos absolutos frágeis:**

   Hoje cada fluxo salva em um lugar diferente (pasta pública de Imagens, pasta pública de Gravações/Músicas, pasta privada de fundos ou pasta de imagens restauradas). Pior: o Markdown da nota guarda o caminho absoluto do sistema de arquivos. Se o caminho mudar, a pasta de fallback variar ou a permissão do sistema expirar, o link fica morto.

**4\. Bloqueio por Scoped Storage após reinstalar o app:**

   Mesmo quando uma foto ou áudio permanece fisicamente em uma pasta pública (Citra Pic ou Citra Records), ao desinstalar e reinstalar o aplicativo o Android atribui um **novo identificador de usuário (UID)** ao app. Pelas regras de Scoped Storage do Android 10–16, o app reinstalado perde o direito de abrir diretamente por caminho de arquivo (File) mídias criadas pela instalação anterior, causando falha silenciosa de leitura ou reprodução.

**5\. Auto-cura incompleta e ausência de reparo na visualização de imagens:**

   O resolvedor de mídia procura áudios e anexos apenas em um subconjunto de diretórios (deixando de fora a própria pasta de fotos das notas e a pasta de fundos) e não consulta o MediaStore nem recupera arquivos por nome portátil. Além disso, o bloco de áudio tenta resolver o caminho ao carregar, mas o bloco de imagem passa o caminho cru diretamente para o carregador de imagem sem auto-cura, e a rotina que checa se um arquivo é válido retorna verdadeiro por engano quando ocorre erro de leitura.

**6\. Extração limitada na sincronização e risco de memória:**

   A rotina auxiliar de anexos ignora imagens de fundo das notas, ignora URIs de conteúdo (content://), descarta silenciosamente mídias acima de 8 MB e tenta codificar tudo em Base64 dentro de um único JSON em memória — o que falha para notas com vários áudios ou fotos de alta resolução.

## **2\. Solução Proposta**

Para garantir confiabilidade de longo prazo no uso diário e restauração sem intervenção manual após backups ou reinstalações, a arquitetura passará a operar sobre **quatro pilares**:

### **Pilar A — Cofre Interno Canônico \+ Espelhamento Seguro (MediaStore)**

* **Fonte da verdade estável:** Toda imagem (inline ou de fundo) e todo áudio (gravado ou importado) passará a ser gravado obrigatoriamente no **Cofre Interno de Mídias** do aplicativo (subpastas padronizadas e estáveis dentro do armazenamento interno privado), usando identificadores únicos e imutáveis por arquivo. O armazenamento interno nunca sofre revogação de permissão do Scoped Storage entre sessões, atualizações ou semanas de uso.

* **Espelhamento opcional/visível no sistema:** Em paralelo, o sistema continuará espelhando cópias para Citra Pic (galeria/imagens) e Citra Records (gravações/áudios) usando a API oficial MediaStore (com RELATIVE\_PATH e IS\_PENDING no Android 10+, sem precisar de permissões invasivas de galeria). Se o usuário apagar a cópia da galeria, a nota no Citra continua intacta no cofre interno.

* **Referências portáteis com fallback:** As notas passam a gravar caminhos internos canônicos acompanhados de metadados/nomes portáteis estáveis (ou esquema padronizado de mídia do Citra), eliminando dependência frágil de URIs temporárias content:// de seletores externos.

### **Pilar B — Pacote Portátil .zip via Streaming (Backup Completo e Exportação de Notas)**

* **Formato principal portátil (\`.citra.zip\` / \`.zip\`):** Tanto o **Backup Completo do App** quanto a **Exportação de Notas (unitária ou em lote)** passam a gerar um arquivo .zip gravado via **streaming direto em disco** (ZipOutputStream com buffer pequeno), sem nunca carregar todos os arquivos em memória RAM nem converter mídias para Base64.

* **Sem limite baixo artificial de 8 MB:** Como a gravação e a leitura são em fluxo contínuo (streaming), áudios longos, imagens em alta resolução e até documentos externos vinculados (PDF/Word) são empacotados integralmente com baixo uso de memória.

* **Estrutura interna do pacote portátil:**

* Manifesto e dados estruturados (backup.json ou notes.json, incluindo versão do formato e mapa de mídias por nota).

* Pasta dedicada media/images/, media/backgrounds/, media/audio/ e media/docs/, onde cada arquivo referenciado pelas notas é copiado com nome único determinístico.

* Mapeamento explícito ligando cada URL/caminho encontrado na nota (Markdown inline, imagem de fundo, áudio ou anexo) ao seu caminho relativo dentro do pacote .zip.

* **Compatibilidade legada:** Arquivos .json antigos ou .md avulsos continuam sendo aceitos normalmente na importação.

### **Pilar C — Motor Unificado de Reconciliação e Reescrita Automática na Importação**

Ao importar um pacote .zip, um backup ou notas individuais (inclusive após desinstalar e reinstalar o app em outro aparelho):

**1\.** O importador lê o arquivo em streaming, extrai todos os binários da pasta media/ para as pastas correspondentes do **Cofre Interno Canônico** do app (e atualiza o espelho MediaStore quando aplicável), evitando duplicação se o mesmo arquivo (por hash/nome canônico) já existir no cofre.

**2\.** Para cada nota importada, o motor varre **todas** as ocorrências de mídia:

* Imagens no corpo Markdown (\!\[...\](...))

* Áudios no corpo Markdown (\[audio:...\](...))

* Imagem de fundo da nota (backgroundImageUri)

* Documentos externos vinculados quando empacotados

**3\.** Cada referência antiga (que apontava para o aparelho/instalação anterior) é **automaticamente reescrita** para apontar diretamente para o novo caminho válido no Cofre Interno do dispositivo atual antes mesmo de salvar no banco Room e nos arquivos .md locais.

**4\.** O arquivo salvo já nasce 100% funcional: imagens abrem na hora, fundos aparecem e áudios tocam sem nenhuma ação manual do usuário.

### **Pilar D — Auto-Cura Contínua na Renderização \+ Backup Persistente Opcional (SAF)**

* **Auto-cura para todos os blocos (Imagens, Fundos e Áudios):**

* Unificação completa do resolvedor de mídia para varrer o Cofre Interno (images, backgrounds, audio, Citra Pic, Citra Records), MediaStore (consulta por nome de exibição DISPLAY\_NAME para arquivos próprios quando aplicável) e pastas de documentos.

* Correção da validação de integridade de arquivos (tratando erros de leitura/permissão como inválidos em vez de silenciá-los).

* Atualização automática no banco se uma mídia for encontrada em um local alternativo e trazida de volta para o Cofre Interno (auto-reparo persistente em segundo plano, evitando repetir buscas futuras).

* Aplicação desse resolvedor também no componente de bloco de imagem, na visualização em tela cheia e na imagem de fundo da nota — não apenas no player de áudio.

* **Proteção contra desinstalação no backup automático:**

* Adição da opção de vincular uma **pasta persistente do sistema** via *Storage Access Framework* (OpenDocumentTree, ex.: pasta em Documentos, Downloads ou armazenamento sincronizado) para que o backup automático crie/atualize lá um pacote completo resistente à desinstalação, respeitando integralmente as políticas da Google Play (zero permissões amplas de armazenamento).

## **3\. Diagrama de Arquitetura e Fluxo de Dados**

\[1. CAPTURA / USO DIÁRIO\]  
  Câmera / Photo Picker / Gravador / Importação avulsa  
           │  
           ▼  
  ┌─────────────────────────────────────────────────┐  
  │ Cofre Interno Canônico (App filesDir)           │  
  │  ├─ citra\_media/images/   (imagens inline)      │  
  │  ├─ citra\_media/audio/    (gravações e áudios)  │  
  │  └─ citra\_media/bg/       (fundos das notas)    │  
  └────────┬───────────────────────────┬────────────┘  
           │ referência canônica       │ espelho assíncrono (MediaStore)  
           ▼                           ▼  
  Nota (Room \+ .md local)     Pastas Visíveis: Citra Pic / Citra Records  
   
\========================================================================  
   
\[2. EXPORTAÇÃO / BACKUP PORTÁTIL (.zip via Streaming)\]  
  Notas selecionadas ou Backup Completo  
           │  
           ▼  
  Coletor de Mídias (varre Markdown \+ Fundo \+ Docs)  
           │  resolve arquivo real \+ gera mapa de referências relativas  
           ▼  
  Empacotador Streaming (ZipOutputStream \-\> disco)  
   ├─ manifest / backup.json / notes.json  (caminhos normalizados p/ "media/...")  
   └─ media/{images\|audio\|bg\|docs}/\<arquivo\_unico\>  
   
\========================================================================  
   
\[3. REINSTALAÇÃO / IMPORTAÇÃO COM RECONCILIAÇÃO AUTOMÁTICA\]  
  Usuário importa pacote .zip (ou backup JSON legado)  
           │  
           ▼  
  Extrator Streaming (ZipInputStream)  
   ├─ 1) Extrai mídias para o Cofre Interno Canônico **do** novo app  
   ├─ 2) Constrói tabela: caminho\_no\_zip \-\> novo\_caminho\_no\_cofre  
   ├─ 3) Reescreve TODOS os links no conteúdo Markdown e backgroundImageUri  
   └─ 4) Persiste Nota no Room \+ sincroniza .md \+ agenda espelho MediaStore  
           │  
           ▼  
  Nota pronta: imagens, fundos e áudios funcionam imediatamente sem ação manual  
   
\========================================================================  
   
\[4. AUTO-CURA EM TEMPO DE EXECUÇÃO\]  
  Abertura da Nota (Imagem / Fundo / Player de Áudio)  
           │  
           ▼  
  Caminho existe e é legível?  
   ├─ SIM ──► Exibe / Reproduz normalmente  
   └─ NÃO ──► Buscador Unificado (todas **as** pastas internas \+ Citra Pic/Records \+ MediaStore)  
                ├─ Encontrou ──► Copia p/ Cofre Interno, corrige link na Nota e exibe  
                └─ Ausente   ──► Mostra indicador claro de mídia ausente \+ opção de relink

## **4\. Fluxos de Experiência do Usuário (UX) e Confiabilidade**

**1\. Ao anexar imagem, fundo ou gravar áudio em uma nota:**

* A gravação ou cópia ocorre primeiro no Cofre Interno Canônico (garantia de leitura permanente) e o espelho na pasta Citra Pic ou Citra Records é criado sem bloquear o editor.

* Se o usuário importar uma nota .md que referencia um URI temporário ou caminho externo, o processo de persistência traz imediatamente a mídia para dentro do Cofre Interno antes de concluir o salvamento.

**2\. Ao fazer Backup Completo ou Exportar Notas:**

* Na tela de Backup e na Exportação em Lote de Notas, a opção principal passa a gerar um pacote .zip completo contendo dados \+ todas as mídias vinculadas (com barra de progresso real por item/mídia e suporte a cancelamento).

* Opcionalmente nas configurações de Backup, o usuário pode escolher uma pasta persistente via seletor nativo do Android (SAF) para salvar automaticamente pacotes completos fora da pasta apagada na desinstalação.

**3\. Ao restaurar Backup ou importar Notas após reinstalar o app:**

* O seletor de importação aceita .zip (novo formato completo), .json (legado), .md e documentos.

* Durante a importação, uma etapa automática extrai e valida cada imagem, fundo e áudio, reescreve os caminhos das notas e exibe um resumo claro ao final (ex.: *"12 notas importadas e 28 mídias restauradas e vinculadas"*). Se alguma mídia estiver ausente dentro de um arquivo legado sem binários, o sistema informa quantas mídias faltaram em vez de falhar silenciosamente.

**4\. Ao abrir uma nota antiga ou com mídia movida:**

* Blocos de imagem, áudio e planos de fundo executam a verificação rápida. Se o arquivo antigo tiver sido movido para outra pasta conhecida do Citra, ele é recuperado para o Cofre Interno e o link da nota é atualizado automaticamente.

* Caso a mídia realmente não exista mais no aparelho (ex.: nota vinda de um backup antigo de texto puro), o componente exibe um estado visual honesto de *"Mídia não encontrada no dispositivo"* com botão direto para reanexar/substituir, em vez de deixar um espaço vazio ou travar o player.

## **5\. Conformidade, Trade-offs e Plano de Verificação**

### **Conformidade e Compatibilidade**

* **Políticas da Google Play:** Nenhuma permissão ampla de armazenamento (READ\_MEDIA\_IMAGES, READ\_MEDIA\_VIDEO, MANAGE\_EXTERNAL\_STORAGE) será adicionada. Seleção de imagens e importação/exportação de backups continuarão usando Android Photo Picker e Storage Access Framework (OpenDocument, CreateDocument, OpenDocumentTree), e o espelhamento em Citra Pic/Citra Records usará MediaStore nativo.

* **Compatibilidade Retroativa:** Notas existentes no banco local com caminhos antigos serão migradas progressivamente pela rotina de auto-cura/consolidação sem perda de dados; backups .json antigos continuarão sendo importáveis (com tentativa de recuperação caso arquivos equivalentes existam em MediaStore ou pastas do app).

### **Trade-offs Considerados**

* **Uso de espaço em disco:** Manter a cópia no Cofre Interno \+ espelho em Citra Pic/Citra Records duplica o armazenamento de mídias geradas pelo app, porém é o mecanismo necessário no Android moderno para garantir que a mídia dentro da nota sobreviva se o usuário limpar a galeria ou se o sistema alterar acessos do Scoped Storage. Para mitigar desperdício, aplicaremos deduplicação por hash/conteúdo dentro do Cofre Interno e limpeza segura apenas de mídias órfãs após exclusão definitiva da lixeira.

* **Tamanho dos backups \`.zip\`:** Como todas as mídias são incluídas sem limite artificial de 8 MB, o arquivo final terá o tamanho real das mídias; o uso de streaming em disco garante que mesmo pacotes de centenas de megabytes sejam gerados e restaurados sem erro de memória (OutOfMemoryError).

### **Estratégia de Verificação**

* **Testes automatizados locais (Robolectric \+ JUnit):**

**1\.** Teste de ciclo completo de empacotamento .zip e restauração simulando "desinstalação" (apagando diretórios originais e trocando caminhos de destino) para confirmar que todas as imagens inline, imagens de fundo e áudios são extraídos ao novo Cofre Interno, que os links Markdown e metadados são reescritos para o novo destino e que todos os arquivos resultantes são legíveis.

**2\.** Teste de streaming com múltiplos anexos e arquivos grandes garantindo integridade binária (hash idêntico antes e depois da importação) e ausência de duplicatas ao reimportar a mesma nota.

**3\.** Teste do motor de auto-cura verificando recuperação de mídias espalhadas em pastas legadas (Citra Pic, Citra Records, images, note\_backgrounds) para o Cofre Interno e detecção correta de arquivos corrompidos/inexistentes.

**4\.** Teste de compatibilidade com backups .json legados.

* **Validação de build:** Compilação completa do projeto Android (compile\_applet) e execução da suíte de testes unitários/Robolectric após a implementação.

