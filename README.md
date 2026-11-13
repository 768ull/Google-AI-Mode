# AI Mode — aplicativo Android dedicado

Aplicativo Android em Java para o Modo IA, sem barras do Chrome, com tema do sistema, tela imersiva e WebView do Android/Chromium.

## Versão 1.6.0

- applicationId: `com.aimode.nativeclient`
- versionCode: 16
- versionName: `1.6.0`
- minSdk: 26 (Android 8.0)
- ABI: ARM64

## Recursos

O aplicativo mantém a experiência original do Modo IA em WebView, sem botão de alto-falante, síntese de voz local ou download de modelos de áudio. O microfone do próprio Modo IA continua sujeito às permissões normais do Android.

## Build e publicação

GitHub Actions compila APK debug e release. O APK de teste usa um identificador separado para poder ser instalado ao lado do aplicativo principal.

## Assinatura

A publicação release exige a chave permanente guardada fora do repositório e cadastrada nos Secrets do GitHub Actions:

- `ANDROID_KEYSTORE_BASE64`: conteúdo Base64 do arquivo `aimode-upload.p12`.
- `ANDROID_SIGNING_PASSWORD`: senha do keystore e da chave.
- Alias obrigatório: `aimode-upload`.
- Certificado SHA-256 esperado: `0E:E2:BA:96:CF:B6:CD:09:64:C3:C2:AA:A7:58:77:D7:A5:C8:F8:B2:8C:74:99:E5:24:C6:94:90:93:1F:8C:24`.

O workflow verifica a impressão digital e valida as assinaturas V1, V2, V3 e V4 (incluindo o arquivo `.idsig`) antes de publicar o APK. O repositório não contém a chave privada. Os dois Secrets são obrigatórios; sem eles, o build de publicação falha em vez de gerar um APK unsigned enganoso.

Este projeto independente não é afiliado nem endossado pelo Google.


## Nova chave de assinatura

A chave permanente foi rotacionada. O novo certificado SHA-256 é `0E:E2:BA:96:CF:B6:CD:09:64:C3:C2:AA:A7:58:77:D7:A5:C8:F8:B2:8C:74:99:E5:24:C6:94:90:93:1F:8C:24`. Cadastre o P12 em Base64 e a senha nos Secrets `ANDROID_KEYSTORE_BASE64` e `ANDROID_SIGNING_PASSWORD`. Nunca faça commit da chave privada. APKs instalados com certificados antigos exigem desinstalação única antes da migração.

## Comportamento da tela inicial

Na tela inicial do Modo IA, o movimento vertical é bloqueado para manter o layout estático conforme a referência. Ao entrar em uma conversa, a rolagem vertical normal é restaurada para permitir navegar pelas mensagens.
