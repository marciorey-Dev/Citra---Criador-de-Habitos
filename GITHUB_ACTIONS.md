# 🚀 Compilação pelo GitHub Actions (Citra Android)

Este projeto está configurado para compilar o APK diretamente nos servidores do GitHub usando `.github/workflows/build-apk.yml` mantendo `app/google-services.json` **fora do repositório público** (via `.gitignore`) e injetando-o com segurança por **GitHub Secrets**.

---

## 🔐 Passo obrigatório (1x): Configurar o `google-services.json` no GitHub Secrets

Como `app/google-services.json` está no `.gitignore` para não expor configurações do projeto Firebase em repositórios públicos, cadastre-o uma única vez nos **Secrets** do repositório:

1. No GitHub, abra seu repositório → **Settings** → **Secrets and variables** → **Actions** → **New repository secret**.
2. Escolha **uma** das duas opções abaixo (a opção Base64 evita erros de formatação):

### Opção A — `GOOGLE_SERVICES_JSON_BASE64` *(Recomendada)*
Gere o texto em Base64 no seu computador e cole no valor do secret `GOOGLE_SERVICES_JSON_BASE64`:
- **Linux / macOS:**
  ```bash
  base64 -w 0 app/google-services.json
  ```
- **Windows (PowerShell):**
  ```powershell
  [Convert]::ToBase64String([IO.File]::ReadAllBytes("app\google-services.json"))
  ```

### Opção B — `GOOGLE_SERVICES_JSON` *(JSON direto)*
- Nome do secret: `GOOGLE_SERVICES_JSON`
- Valor: cole o conteúdo inteiro do arquivo `app/google-services.json`.

### Opção C — Secrets individuais do Firebase *(Geração dinâmica)*
Se preferir que o GitHub Actions monte `app/google-services.json` dinamicamente antes do build, cadastre estes secrets:
- `FIREBASE_PROJECT_ID` (ex.: `gen-lang-client-0656311562`)
- `FIREBASE_APP_ID` (ID do app Android no Firebase, ex.: `1:...:android:...`)
- `FIREBASE_API_KEY` (Chave Web/Android do projeto Firebase)
- `FIREBASE_WEB_CLIENT_ID` (Client ID OAuth 2.0 Web `client_type: 3`, usado no `default_web_client_id`)
- *(Opcionais)* `FIREBASE_PROJECT_NUMBER`, `FIREBASE_STORAGE_BUCKET`, `FIREBASE_ANDROID_CLIENT_ID`

---

## 📱 Como gerar e baixar o APK de Testes (Debug)
1. Envie o projeto para o seu repositório no GitHub (`git push`).
2. Abra o repositório no GitHub e acesse a aba **Actions**.
3. O build **Compilar APK Android (Citra)** inicia automaticamente a cada `push` ou `pull_request`.
   - Para disparar manualmente: clique em **Compilar APK Android (Citra)** → **Run workflow** → escolha `debug` (e marque **Executar testes unitários** se desejar) → **Run workflow**.
4. Quando o workflow terminar com status verde (✔️), role até a seção **Artifacts** e baixe **Citra-Test-APK**.
5. Extraia o `.zip` e instale o arquivo `.apk` no seu Android.

---

## 🔑 Secrets opcionais para APK Release
Para compilar a variante **`release`** pelo GitHub Actions (`Run workflow` → `release`), cadastre também em **Settings → Secrets and variables → Actions**:

| Secret | Descrição |
| --- | --- |
| `RELEASE_KEYSTORE_BASE64` | Arquivo `.jks` de upload codificado em Base64 (alias obrigatório: `upload`) |
| `RELEASE_STORE_PASSWORD` | Senha da keystore de release |
| `RELEASE_KEY_PASSWORD` | Senha da chave (`upload`) |

> Se a variante `release` for executada sem esses secrets de assinatura, o workflow emite um aviso e gera o **Debug APK** automaticamente.

---

## 🏷️ Publicação automática em GitHub Releases
Ao enviar uma tag semântica começando com `v` (exemplo: `git tag v1.2.4 && git push origin v1.2.4`), o workflow compila o APK e cria automaticamente uma **Release** no GitHub com o `.apk` anexado.
