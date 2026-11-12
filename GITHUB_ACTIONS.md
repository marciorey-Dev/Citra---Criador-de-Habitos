# 🚀 Compilação pelo GitHub Actions (Citra Android)

Este projeto está configurado para compilar o APK diretamente nos servidores do GitHub usando o workflow `.github/workflows/build-apk.yml`.

## ✅ O que já vem pronto no repositório
- **Gradle Wrapper (`gradlew` + `gradle/wrapper/*`)** configurado para **Gradle 9.3.1**.
- **Android SDK `compileSdk 36.1`** e **Build Tools 36.0.0** instalados automaticamente no runner Linux (`ubuntu-latest`) com **Java 21 (Temurin)**.
- **Assinatura compatível com Firebase e Login Google (`debug.keystore.base64`)**: o workflow restaura automaticamente a `debug.keystore` com o SHA-1 registrado no `app/google-services.json`, garantindo que o login com Google e o Firestore funcionem no APK de testes.
- **Variáveis de ambiente (`.env`)**: criadas automaticamente a partir de `.env.example` durante o build.

---

## 📱 Como gerar e baixar o APK de Testes (Debug)
1. Envie o projeto para o seu repositório no GitHub (`git push`).
2. Abra o repositório no GitHub e acesse a aba **Actions**.
3. O build **Compilar APK Android (Citra)** inicia automaticamente a cada `push` ou `pull_request`.
   - Para disparar manualmente: clique em **Compilar APK Android (Citra)** → **Run workflow** → escolha `debug` (e marque **Executar testes unitários** se desejar) → **Run workflow**.
4. Quando o workflow terminar com status verde (✔️), role até a seção **Artifacts** e baixe **Citra-Test-APK**.
5. Extraia o `.zip` e instale o arquivo `.apk` no seu Android.

---

## 🔐 Secrets opcionais para APK Release
Se quiser compilar a variante **`release`** pelo GitHub Actions (`Run workflow` → `release`), cadastre em **Settings → Secrets and variables → Actions** do repositório:

| Secret | Descrição |
| --- | --- |
| `RELEASE_KEYSTORE_BASE64` | Arquivo `.jks` de upload codificado em Base64 (alias obrigatório: `upload`) |
| `RELEASE_STORE_PASSWORD` | Senha da keystore de release |
| `RELEASE_KEY_PASSWORD` | Senha da chave (`upload`) |
| `GOOGLE_SERVICES_JSON` | *(Opcional)* Conteúdo do `app/google-services.json`, caso decida não versionar o arquivo no repo |

> Se a variante `release` for executada sem esses secrets, o workflow emite um aviso e gera o **Debug APK** automaticamente para não falhar a compilação.

---

## 🏷️ Publicação automática em GitHub Releases
Ao enviar uma tag semântica começando com `v` (exemplo: `git tag v1.2.4 && git push origin v1.2.4`), o workflow compila o APK e cria automaticamente uma **Release** no GitHub com o `.apk` anexado.
