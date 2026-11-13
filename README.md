# AI Mode — aplicativo Android dedicado

Aplicativo Android nativo (Java) dedicado ao Modo IA. Não exibe a barra do Chrome, a barra de endereço, o menu de três pontos nem a faixa de categorias da Pesquisa Google.

## Tela cheia e câmera frontal

- Modo imersivo: oculta as barras de status e navegação do Android; elas reaparecem temporariamente com um gesto nas bordas.
- A janela ocupa a área do recorte da tela (câmera frontal/punch-hole), em vez de deixar uma faixa preta acima do conteúdo.
- O layout não aplica padding superior do recorte; mantém as margens laterais de segurança e respeita o teclado virtual.
- O atributo de recorte usa ALWAYS em Android 15+ e SHORT_EDGES em versões anteriores compatíveis.

## Interface e interação

- Esconde somente a faixa de categorias do Google Search (Modo IA, Tudo, Imagens, Notícias, Vídeos, Shopping, Maps etc.); mantém o cabeçalho do Modo IA.
- O toque no logotipo G que levaria à página inicial do Google é ignorado.
- Zoom por pinça e controles de zoom desativados.
- Barras indicadoras de rolagem ocultas; a rolagem normal da conversa continua habilitada.
- Sem gesto de puxar para atualizar.
- Menus de contexto e seleção de texto: permitidos apenas no campo de entrada e nos nós de mensagem do usuário/assistente. Textos estáticos de recursos, funções, rótulos, navegação e botões não são selecionáveis.
- Tema claro/escuro acompanha a preferência de aparência do Android.
- Seletor de arquivos e permissão do microfone controlados pelo Android.

Os seletores internos do Google são propriedade do Google e podem mudar sem aviso. O código usa os componentes observados no snapshot fornecido; futuras alterações na página podem exigir ajuste.

## Arquitetura e dependência de rede

Activity, barras do sistema, estados, seletor de arquivos, permissões, ícone e compilação são nativos. O conteúdo do Modo IA é renderizado em uma Android System WebView, usando o motor Chromium/Blink do próprio Android e APIs adicionais de Jetpack WebKit.

O Modo IA continua sendo um serviço online do Google. O HTML/MHTML salvo é uma captura da página e não contém o backend, os modelos, o login nem os serviços necessários para operar offline.

## Compilar

Requisitos: JDK 21, Android SDK Platform 36, Build Tools 36.0.0 e Gradle 9.6.0.

gradle --no-daemon clean :app:assembleDebug :app:assembleRelease

## Assinatura permanente para atualizações

A chave PKCS#12 privada fica fora do repositório público. Os Secrets esperados no GitHub são ANDROID_KEYSTORE_BASE64 e ANDROID_SIGNING_PASSWORD. O alias é aimode-upload. Sem os dois Secrets, o workflow publica somente APK debug de teste, não um release para atualização.

O script habilita e verifica V1, V2 e V3 dentro do APK, e cria V4 em um arquivo .idsig separado. A primeira migração de APK debug para release assinado exige instalação separada; depois, futuras atualizações podem instalar por cima desde que a chave e o applicationId não mudem.

## Atualizações

- applicationId: com.aimode.nativeclient
- versionCode atual: 4
- versionName atual: 1.0.3

Incremente versionCode e versionName em cada versão. Nunca altere o applicationId nem troque a chave depois da primeira instalação release.

Este projeto independente não é afiliado nem endossado pelo Google.