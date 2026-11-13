# AI Mode — aplicativo Android dedicado

Aplicativo Android em Java para o Modo IA, sem barras do Chrome, com tema do sistema, tela imersiva e WebView do Android/Chromium.

## Build e publicação

GitHub Actions compila APK debug e release. O APK de teste usa um identificador separado para poder ser instalado ao lado do aplicativo principal.

## Assinatura

A publicação release exige a chave permanente guardada fora do repositório e cadastrada nos Secrets do GitHub Actions:

- `ANDROID_KEYSTORE_BASE64`: conteúdo Base64 do arquivo `aimode-upload.p12`.
- `ANDROID_SIGNING_PASSWORD`: senha do keystore e da chave.
- Alias obrigatório: `aimode-upload`.
- Certificado SHA-256 esperado: `C4:10:FD:CA:23:5C:0D:D4:50:1B:09:73:39:F0:02:CB:6E:6C:C8:82:FC:51:C3:5F:C4:36:CD:88:8E:67:15:80`.

O workflow verifica a impressão digital antes de assinar. Sem os dois Secrets, somente um APK de teste separado e um arquivo unsigned de entrada privada são gerados; o arquivo unsigned não pode ser instalado. O PKCS#12 e os valores dos Secrets nunca devem ser adicionados a este repositório público.

## Versão 1.3.0

- applicationId principal: `com.aimode.nativeclient`
- versionCode: 14
- versionName: `1.4.0`
- minSdk: 26 (Android 8.0)
- ABI: ARM64

Este projeto independente não é afiliado nem endossado pelo Google.

### Leitura restrita à resposta da IA (1.4.0)

O botão **Ouvir** extrai apenas conteúdo dentro de elementos explicitamente marcados como resposta do assistente. Foram removidos os fallbacks que liam parágrafos genéricos, o conteúdo da página principal ou o corpo inteiro do documento. Quando o Google muda a marcação e nenhuma resposta identificável é encontrada, o botão não inicia a leitura. Controles, botões, menus, ações, fontes e citações em componentes de interface são excluídos da extração.

## Áudio das respostas

O botão **🔊 Ouvir** fica integrado à interface do Modo IA. A síntese usa a API nativa Android `TextToSpeech`, priorizando o mecanismo **Speech Services by Google** (`com.google.android.tts`) quando instalado e usando o mecanismo padrão configurado no Android como alternativa.

- Voz solicitada: português do Brasil (`pt-BR`).
- O texto é dividido em trechos compatíveis com o limite do mecanismo, sem bloquear a interface do WebView.
- Não baixa nem carrega o modelo PocketTTS de 100 milhões de parâmetros dentro do processo do aplicativo.
- Não precisa baixar um pacote de 126 MB. A disponibilidade offline depende da voz pt-BR instalada no mecanismo de voz do aparelho.
- Caso faltem dados de voz em português, o Android abre a tela de instalação de dados do mecanismo TTS.

O serviço do sistema é assíncrono e informa conclusão ou erro de cada trecho por `UtteranceProgressListener`. Consulte a documentação Android: https://developer.android.com/reference/android/speech/tts/TextToSpeech
