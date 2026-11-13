# 🚀 Compilação pelo GitHub Actions (Citra Android)

Este projeto está configurado para compilar o APK diretamente nos servidores do GitHub usando `.github/workflows/build-apk.yml`, com `app/google-services.json` já incluído no projeto para compilar imediatamente (e também aceitando Secrets como sobrescrita opcional).

---

## ✅ O que já vem pronto no projeto
- **`app/google-services.json`**: já está no repositório com as credenciais Firebase do app `com.aistudio.habitcraft.krqpmv` (projeto `gen-lang-client-0656311562`), o `certificate_hash` SHA-1 da `debug.keystore` e o `oauth_client` Web (`default_web_client_id`).
- **`debug.keystore.base64`**: restaura a assinatura de debug compatível com Firebase Auth e Google Sign-In.

---

## 🔐 Secrets opcionais (para sobrescrever o Firebase sem alterar o código)
Se você quiser apontar o build para outro projeto Firebase via GitHub Secrets (**Settings → Secrets and variables → Actions**), o workflow aceita automaticamente:
- **`GOOGLE_SERVICES_JSON_BASE64`**: conteúdo do `app/google-services.json` em Base64.
- **`GOOGLE_SERVICES_JSON`**: conteúdo JSON completo do `app/google-services.json`.
- Ou os secrets individuais **`FIREBASE_PROJECT_ID`**, **`FIREBASE_APP_ID`**, **`FIREBASE_API_KEY`**, **`FIREBASE_WEB_CLIENT_ID`**.

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
