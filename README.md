# AI Mode — aplicativo Android dedicado

Este projeto substitui o WebAPK original por um aplicativo Android nativo (Java), sem a barra do Chrome, sem o título “Pesquisa Google”, sem o endereço google.com e sem o menu de três pontos.

**O que é nativo:** Activity, barras do sistema, carregamento, tela de erro, escolha de arquivos, permissões, ícone e fluxo de compilação. O conteúdo interativo do Modo IA é exibido em uma única Android System WebView, usando o mecanismo Chromium/Blink fornecido pelo Android. A biblioteca Jetpack WebKit complementa suas APIs; ela não é outro navegador nem um mecanismo independente.

## Recursos

- Pinça de zoom e controles de zoom desativados.
- Barras indicadoras de rolagem ocultas; a rolagem vertical do conteúdo continua ativa para respostas longas.
- Sem gesto de puxar para atualizar.
- Sem barra de endereço, título de página, abas, menu de navegador ou janelas pop-up.
- O toque no logotipo “G” do Google, que normalmente leva à página inicial, é ignorado; a tela permanece no Modo IA.
- Tema claro/escuro acompanha a preferência de aparência do Android. A paleta da página é escolhida pelo próprio Google quando a página suporta `prefers-color-scheme`; o WebView pode aplicar escurecimento algorítmico como alternativa.
- Menus de contexto e seleção de texto na interface estática são desativados. Campos de texto, caixas de entrada e conteúdo identificado como mensagens/conversas permanecem selecionáveis.
- Navegação restrita a hosts Google necessários para a experiência; não transforma o WebView em navegador geral.
- Seletor de arquivos Android e solicitação de microfone controlada por permissão e origem.
- Ícone fornecido pelo usuário em formato legado e adaptativo.
- R8 e remoção de recursos não usados no build release.
- GitHub Actions para compilar APK debug e release unsigned; com Secrets de assinatura, gera release V1/V2/V3 e assinatura V4 lateral.

## Limite importante: o serviço continua online

O APK enviado é um WebAPK gerado a partir de uma instalação web. O HTML/MHTML salvo é apenas uma captura da página; não contém o backend do Modo IA, nem os modelos, nem os serviços de conta e resposta. Portanto, o aplicativo não consegue oferecer o Modo IA real offline nem remover toda dependência de rede. O endereço do serviço continua sendo acessado pela WebView, mas a interface de navegador do Chrome não aparece.

O Android System WebView é fornecido pelo dispositivo e atualizado pelo sistema/loja. Empacotar Chromium inteiro dentro deste APK aumentaria muito o tamanho e não é o objetivo desta versão. O Google pode restringir login ou alguns fluxos em navegadores incorporados; o projeto não contorna essas políticas.

## Compilar

Requisitos: JDK 21, Android SDK Platform 36, Build Tools 36.0.0 e Gradle 9.6.0.

```bash
gradle --no-daemon clean :app:assembleDebug :app:assembleRelease
```

Saídas:

- app/build/outputs/apk/debug/app-debug.apk
- app/build/outputs/apk/release/app-release-unsigned.apk

## Chave permanente de atualização

A chave privada não está no repositório e não foi criada automaticamente. Gere uma única chave e mantenha cópias seguras offline:

```bash
keytool -genkeypair -v -keystore aimode-upload.jks -alias aimode-upload -keyalg RSA -keysize 4096 -validity 10000
base64 -w 0 aimode-upload.jks
```

No GitHub, em Settings → Secrets and variables → Actions, crie os Secrets:

- ANDROID_KEYSTORE_BASE64
- ANDROID_KEYSTORE_PASSWORD
- ANDROID_KEY_ALIAS
- ANDROID_KEY_PASSWORD

O workflow usa essa mesma chave em atualizações futuras. V1, V2 e V3 ficam no APK. V4 gera um arquivo .apk.idsig separado, que deve ser distribuído junto quando necessário. Sem os quatro Secrets, a compilação gera APK debug e release sem assinatura.

## Atualizações

O identificador do app permanece `com.aimode.nativeclient`. A correção seguinte usa `versionCode 2` e `versionName 1.0.1`, para instalar sobre a versão 1.0.0 sem trocar o pacote. Não altere o applicationId nem troque a chave depois da primeira publicação. Incremente versionCode e versionName em cada versão.

## Estrutura principal

```text
app/src/main/AndroidManifest.xml
app/src/main/java/com/aimode/nativeclient/MainActivity.java
app/src/main/res/values/
app/src/main/res/values-night/
app/src/main/res/values-v31/
app/src/main/res/values-night-v31/
.github/workflows/android.yml
scripts/sign-release.sh
```

Este projeto independente não é afiliado nem endossado pelo Google.
