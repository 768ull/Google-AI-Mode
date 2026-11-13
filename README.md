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
- Certificado SHA-256 esperado: `C4:10:FD:CA:23:5C:0D:D4:50:1B:09:73:39:F0:02:CB:6E:6C:C8:82:FC:51:C3:5F:C4:36:CD:88:8E:67:15:80`.

O workflow verifica a impressão digital antes de assinar. Sem os dois Secrets, somente um APK de teste separado e um arquivo unsigned de entrada privada são gerados; o arquivo unsigned não pode ser instalado. O PKCS#12 e os valores dos Secrets nunca devem ser adicionados a este repositório público.

Este projeto independente não é afiliado nem endossado pelo Google.


## Comportamento da tela inicial

Na tela inicial do Modo IA, o movimento vertical é bloqueado para manter o layout estático conforme a referência. Ao entrar em uma conversa, a rolagem vertical normal é restaurada para permitir navegar pelas mensagens.
