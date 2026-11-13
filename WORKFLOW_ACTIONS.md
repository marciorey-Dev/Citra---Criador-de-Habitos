# ⚙️ Workflow de Compilação do APK no GitHub Actions

O projeto **Citra** utiliza o workflow [`.github/workflows/android-apk-ci.yml`](.github/workflows/android-apk-ci.yml) para compilar automaticamente o APK Android nos servidores do GitHub Actions.

---

## 🔧 Configuração incluída no repositório
- **Workflow CI/CD:** `.github/workflows/android-apk-ci.yml`
- **Configuração Firebase:** `app/google-services.json` (projeto `gen-lang-client-0583135907`) e `app/src/main/res/values/firebase_applet_config.xml` (banco Firestore `ai-studio-android-citracri-1b640eb0-8bf4-45fb-994e-c615ecbe0b43`).
- **Assinatura Debug / Google Sign-In:** `debug.keystore.base64` restaura no runner exatamente a mesma keystore cuja impressão digital SHA-1 está registrada no Firebase, garantindo que o login com Google funcione no APK gerado.

---

## ▶️ Como compilar e baixar o APK no GitHub Actions

### 1. Compilação automática (`push` / `pull_request`)
Sempre que você enviar alterações para o repositório (`git push`) ou abrir um `pull_request`, o workflow **Citra Android APK Workflow** compila automaticamente a variante `debug`.

### 2. Disparo manual (1 clique)
1. Acesse seu repositório no GitHub e abra a aba **Actions**.
2. Selecione **Citra Android APK Workflow** na barra lateral esquerda.
3. Clique em **Run workflow**.
4. Escolha a variante (`debug` ou `release`) e, se quiser rodar a suíte unitária local antes do build, marque **Executar testes unitários locais antes de compilar**.
5. Clique em **Run workflow**.

### 3. Baixar o `.apk`
1. Após a conclusão do job (check verde ✔️), abra a execução do workflow.
2. Role até a seção **Artifacts** no final da página.
3. Baixe **Citra-APK-debug** (ou **Citra-APK-release**), extraia o `.zip` e instale o `.apk` no dispositivo Android.

---

## 🔐 Secrets opcionais no GitHub

Cadastre em **Settings → Secrets and variables → Actions** apenas se desejar personalizar credenciais ou assinar um APK `release`:

| Secret | Finalidade |
| --- | --- |
| `GOOGLE_SERVICES_JSON_BASE64` | Sobrescreve `app/google-services.json` usando conteúdo em Base64 |
| `GOOGLE_SERVICES_JSON` | Sobrescreve `app/google-services.json` usando JSON puro |
| `GEMINI_API_KEY` | Preenche `GEMINI_API_KEY` no `.env` em tempo de build, se necessário |
| `RELEASE_KEYSTORE_BASE64` | Keystore `.jks` de produção em Base64 (alias `upload`) para builds `release` |
| `RELEASE_STORE_PASSWORD` | Senha da keystore de release |
| `RELEASE_KEY_PASSWORD` | Senha da chave `upload` |

> Se a variante `release` for solicitada sem os secrets de assinatura configurados, o workflow emite um aviso e compila a variante `debug` automaticamente.

---

## 🏷️ Publicação automática em Releases
Ao enviar uma tag de versão começando com `v` (por exemplo, `git tag v1.2.4 && git push origin v1.2.4`), o workflow compila o APK, calcula o checksum `.sha256` e publica uma **GitHub Release** com os arquivos anexados.
