# AI Mode — aplicativo Android dedicado

Aplicativo Android em Java para o Modo IA, sem barras do Chrome, com tema do sistema, tela imersiva e WebView do Android/Chromium.

## Build

GitHub Actions compila APK debug para pull requests e APK release para pushes/manual runs da branch de publicação.

## Assinatura

A publicação release exige a chave permanente guardada fora do repositório e cadastrada nos Secrets do GitHub Actions:

- `ANDROID_KEYSTORE_BASE64`: conteúdo Base64 do arquivo `aimode-upload.p12`.
- `ANDROID_SIGNING_PASSWORD`: senha do keystore e da chave.
- Alias obrigatório: `aimode-upload`.
- Certificado SHA-256 esperado: `C4:10:FD:CA:23:5C:0D:D4:50:1B:09:73:39:F0:02:CB:6E:6C:C8:82:FC:51:C3:5F:C4:36:CD:88:8E:67:15:80`.

O workflow verifica a impressão digital antes de assinar. Sem os dois Secrets, o APK de release não é publicado; não há geração de chave temporária. O arquivo PKCS#12 e os valores dos Secrets nunca devem ser adicionados a este repositório público.

## Versão

- applicationId: `com.aimode.nativeclient`
- versionCode: 9
- versionName: `1.0.8`

Este projeto independente não é afiliado nem endossado pelo Google.

## Áudio local das respostas (1.0.5)

As respostas do Modo IA recebem um botão **🔊 Ouvir**. A fala é gerada no próprio aparelho com o MediaPipe Text-to-Speech do Google AI Edge e o modelo neural PocketTTS 100M INT8 em português do Brasil (voz padrão **Rafael**).

- O modelo é baixado sob demanda na primeira reprodução (aprox. 126 MB) e armazenado no espaço privado do aplicativo.
- Depois do download, a síntese e a reprodução funcionam offline; o conteúdo da resposta do Modo IA continua vindo do serviço online do Google.
- A implementação usa CPU/LiteRT para não depender dos dispositivos Pixel/Samsung exigidos pela API GenAI TTS do ML Kit.
- O modelo tem licença CC-BY-4.0. A arquitetura, os pesos e as vozes originais são de Kyutai; pacote LiteRT disponibilizado por LiteRT Community. Consulte https://huggingface.co/litert-community/pocket-tts para atribuição e termos.

O runtime MediaPipe Tasks Audio 1.2.0 exige Android API 26 ou superior. Por isso, o minSdk desta versão passa a ser 26 (Android 8.0).


### Áudio integrado ao Modo IA (1.0.8)

O controle de voz é inserido dentro de cada bloco de resposta do Modo IA como uma ação contextual **🔊 Ouvir** / **Parar áudio**. Não existe botão nativo flutuante sobre a tela. O texto é extraído apenas da resposta correspondente ao botão tocado.

O runtime MediaPipe Tasks Audio foi atualizado para **1.2.2**. Na primeira abertura após a atualização, o cache local antigo do PocketTTS é removido e o modelo português é baixado novamente para evitar incompatibilidade entre o formato do pacote e o runtime atualizado. O arquivo oficial continua com o nome `pocket_tts_pt_int8.litertlm`, a voz `rafael` e o locale `pt-BR`.

### Assinatura estável (1.0.7 em diante)

O workflow usa a chave permanente `aimode-upload` quando os Secrets `ANDROID_KEYSTORE_BASE64` e `ANDROID_SIGNING_PASSWORD` estão cadastrados. Se os Secrets estiverem ausentes, a compilação ainda termina e publica um artefato de release **sem assinatura** para assinatura privada posterior. O PKCS#12 e os Secrets nunca devem ser adicionados ao repositório público.
