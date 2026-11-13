# AI Mode — aplicativo Android dedicado

Aplicativo Android em Java para o Modo IA, sem barras do Chrome, com tema do sistema, tela imersiva e WebView do Android/Chromium.

## Build

GitHub Actions compila APK debug para pull requests e APK release para pushes/manual runs da branch de publicação.

## Assinatura

O workflow tem dois caminhos:

1. **Permanente (recomendado):** configura os Secrets `ANDROID_KEYSTORE_BASE64` e `ANDROID_SIGNING_PASSWORD`. O alias é `aimode-upload`. A mesma chave será usada nas futuras versões e permite instalar atualizações por cima da versão anterior.
2. **Temporário (fallback):** quando os Secrets não existem, o workflow gera uma chave nova durante a execução e entrega um APK release assinado V1/V2/V3/V4. Este APK é instalável, mas NÃO aceita atualizações futuras assinadas com outra chave; para atualizar depois, será preciso desinstalar este APK primeiro. Configure os Secrets para evitar isso.

A chave permanente PKCS#12 nunca deve ser colocada em repositório público. Faça backup offline e cadastre os dois Secrets em Settings → Secrets and variables → Actions.

V1, V2 e V3 são assinaturas dentro do APK. V4 é um arquivo lateral `.idsig`, publicado junto ao APK.

## Versão

- applicationId: `com.aimode.nativeclient`
- versionCode: 5
- versionName: `1.0.4`

Este projeto independente não é afiliado nem endossado pelo Google.