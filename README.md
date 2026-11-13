# AI Mode — aplicativo Android dedicado

Aplicativo Android nativo (Java) dedicado ao Modo IA. Não exibe a barra do Chrome, a barra de endereço, o menu de três pontos nem a faixa de categorias da Pesquisa Google.

## Interface e interação

- Tela cheia imersiva: oculta as barras de status e navegação do Android; elas reaparecem temporariamente com um gesto nas bordas.
- Esconde somente a faixa de categorias do Google Search (Modo IA, Tudo, Imagens, Notícias, Vídeos, Shopping, Maps etc.); mantém o cabeçalho do Modo IA.
- O toque no logotipo G que levaria à página inicial do Google é ignorado.
- Zoom por pinça e controles de zoom desativados.
- Barras indicadoras de rolagem ocultas; a rolagem normal da conversa continua habilitada.
- Sem gesto de puxar para atualizar.
- Menus de contexto e seleção de texto: permitidos apenas no campo de entrada e nos nós de mensagem do usuário/assistente (`user-query`, `model-response` e seletores de turnos equivalentes). Textos estáticos de recursos, funções, rótulos, navegação e botões não são selecionáveis.
- Tema claro/escuro acompanha a preferência de aparência do Android.
- Seletor de arquivos e permissão do microfone controlados pelo Android.

Os seletores internos do Google são propriedade do Google e podem mudar sem aviso. O código usa os componentes de mensagens e a faixa de navegação observados no snapshot fornecido, mas futuras alterações na página podem exigir ajuste.

## Arquitetura e dependência de rede

Activity, barras do sistema, estados, seletor de arquivos, permissões, ícone e compilação são nativos. O conteúdo do Modo IA é renderizado em uma Android System WebView, usando o motor Chromium/Blink do próprio Android e APIs adicionais de Jetpack WebKit. A interface do navegador Chrome não aparece.

O Modo IA continua sendo um serviço online do Google. O HTML/MHTML salvo é uma captura da página e não contém o backend, os modelos, o login nem os serviços necessários para operar offline.

## Compilar

Requisitos: JDK 21, Android SDK Platform 36, Build Tools 36.0.0 e Gradle 9.6.0.

```bash
gradle --no-daemon clean :app:assembleDebug :app:assembleRelease
```

Saídas:
- app/build/outputs/apk/debug/app-debug.apk (pacote de teste separado, com sufixo .debug)
- app/build/outputs/apk/release/app-release-unsigned.apk (não instalar; requer assinatura)

## Assinatura permanente para atualizações

**Não coloque o arquivo de chave no repositório público.** O arquivo PKCS#12 é uma chave privada: qualquer pessoa que o obtenha pode assinar atualizações falsas para este mesmo applicationId.

Foi gerada uma chave permanente fora do repositório:
- Arquivo: `aimode-upload.p12`
- Alias: `aimode-upload`
- Tipo: PKCS#12
- Senha: entregue separadamente ao proprietário do projeto.

Faça backup offline. Não perca nem substitua esta chave; ela será usada em todas as versões futuras.

### Configuração única dos Secrets do GitHub

No repositório, abra **Settings → Secrets and variables → Actions → New repository secret**.

Crie exatamente estes dois Secrets:

1. `ANDROID_KEYSTORE_BASE64`: conteúdo completo do arquivo `ANDROID_KEYSTORE_BASE64.txt` fornecido separadamente (não coloque espaços ou quebras de linha extras).
2. `ANDROID_SIGNING_PASSWORD`: senha do arquivo PKCS#12, fornecida separadamente.

O alias `aimode-upload` está configurado no workflow. A mesma senha protege o keystore e a chave.

Depois, abra **Actions → Android APK → Run workflow** e selecione a branch `native-android-foundation`.

Sem os dois Secrets, o workflow publica apenas um APK debug de teste (outro applicationId) e mostra um aviso; não publica um release que possa ser confundido com uma atualização.

### Esquemas de assinatura

O script habilita e verifica V1, V2 e V3 dentro do APK e cria a assinatura V4 em `app-release.apk.idsig`. A V4 é um arquivo lateral e deve acompanhar o APK quando for usada a instalação incremental.

A versão anterior foi distribuída como APK debug, sem uma chave de lançamento persistente. Por isso, a transição para o primeiro APK assinado com esta chave exige desinstalar a versão debug uma última vez. Depois de instalar o APK release assinado com esta chave, futuras versões poderão ser instaladas por cima sem desinstalar, desde que a chave e o `applicationId` não mudem.

## Atualizações

- applicationId: `com.aimode.nativeclient`
- versionCode atual: 3
- versionName atual: 1.0.2

Incremente `versionCode` e `versionName` em cada versão. Nunca altere o `applicationId` nem troque a chave de assinatura depois da primeira instalação release.

Este projeto independente não é afiliado nem endossado pelo Google.
