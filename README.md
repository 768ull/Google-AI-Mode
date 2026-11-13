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
- versionCode: 6
- versionName: `1.0.5`

Este projeto independente não é afiliado nem endossado pelo Google.

## Áudio local das respostas (1.0.5)

As respostas do Modo IA recebem um botão **🔊 Ouvir**. A fala é gerada no próprio aparelho com o MediaPipe Text-to-Speech do Google AI Edge e o modelo neural PocketTTS 100M INT8 em português do Brasil (voz padrão **Rafael**).

- O modelo é baixado sob demanda na primeira reprodução (aprox. 126 MB) e armazenado no espaço privado do aplicativo.
- Depois do download, a síntese e a reprodução funcionam offline; o conteúdo da resposta do Modo IA continua vindo do serviço online do Google.
- A implementação usa CPU/LiteRT para não depender dos dispositivos Pixel/Samsung exigidos pela API GenAI TTS do ML Kit.
- O modelo tem licença CC-BY-4.0. A arquitetura, os pesos e as vozes originais são de Kyutai; pacote LiteRT disponibilizado por LiteRT Community. Consulte https://huggingface.co/litert-community/pocket-tts para atribuição e termos.

O runtime MediaPipe Tasks Audio 1.2.0 exige Android API 26 ou superior. Por isso, o minSdk desta versão passa a ser 26 (Android 8.0).
