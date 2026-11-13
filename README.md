# AI Mode — aplicativo Android dedicado

Aplicativo Android em Java para o Modo IA, sem barras do Chrome, com tema do sistema, tela imersiva e WebView do Android/Chromium.

## Build

GitHub Actions compila APK debug para pull requests e APK release para pushes/manual runs da branch de publicação.

## Assinatura

A publicação release **exige a chave permanente** armazenada nos Secrets do GitHub Actions:

- `ANDROID_KEYSTORE_BASE64`: conteúdo Base64 do arquivo PKCS#12 permanente.
- `ANDROID_SIGNING_PASSWORD`: senha do keystore e da chave.
- Alias obrigatório: `aimode-upload`.

O workflow confere também a impressão digital SHA-256 do certificado permanente antes de assinar. Se os Secrets estiverem ausentes ou se o keystore não for o certificado esperado, a publicação é interrompida. **Não é gerada chave temporária.**

Nunca coloque o arquivo PKCS#12 ou os valores dos Secrets no repositório público. Os APKs de release são assinados V1/V2/V3/V4; o arquivo V4 `.idsig` acompanha o APK.

## Versão

- applicationId: `com.aimode.nativeclient`
- versionCode: 7
- versionName: `1.0.6`

Este projeto independente não é afiliado nem endossado pelo Google.

## Áudio local das respostas (1.0.5)

As respostas do Modo IA recebem um botão **🔊 Ouvir**. A fala é gerada no próprio aparelho com o MediaPipe Text-to-Speech do Google AI Edge e o modelo neural PocketTTS 100M INT8 em português do Brasil (voz padrão **Rafael**).

- O modelo é baixado sob demanda na primeira reprodução (aprox. 126 MB) e armazenado no espaço privado do aplicativo.
- Depois do download, a síntese e a reprodução funcionam offline; o conteúdo da resposta do Modo IA continua vindo do serviço online do Google.
- A implementação usa CPU/LiteRT para não depender dos dispositivos Pixel/Samsung exigidos pela API GenAI TTS do ML Kit.
- O modelo tem licença CC-BY-4.0. A arquitetura, os pesos e as vozes originais são de Kyutai; pacote LiteRT disponibilizado por LiteRT Community. Consulte https://huggingface.co/litert-community/pocket-tts para atribuição e termos.

O runtime MediaPipe Tasks Audio 1.2.0 exige Android API 26 ou superior. Por isso, o minSdk desta versão passa a ser 26 (Android 8.0).


### Correção do botão de áudio (1.0.6)

O controle **🔊 Ouvir resposta** agora é um botão nativo flutuante do Android, sempre visível sobre a página do Modo IA após o carregamento. Ele não depende das classes CSS ou dos atributos internos do Google, que podem mudar sem aviso. Ao tocar, o app tenta localizar o último texto de resposta na área principal e envia esse texto ao PocketTTS local. O mesmo botão muda para **Parar áudio** durante a reprodução.
