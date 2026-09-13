<p align="center">
  <img src="docs/banner.png" alt="Hydroid: launcher de jogos para Android" width="100%"/>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/licen%C3%A7a-MIT-5EEAD4?style=flat-square" alt="Licença MIT"/>
  <img src="https://img.shields.io/badge/Android-8.0%2B-8B93FF?style=flat-square&logo=android&logoColor=white" alt="Android 8.0+"/>
  <img src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin + Jetpack Compose"/>
  <img src="https://img.shields.io/github/v/release/DuduSync/Hydroid?style=flat-square&color=5EEAD4&label=release" alt="Release"/>
  <a href="https://hits.sh/github.com/DuduSync/Hydroid/"><img src="https://hits.sh/github.com/DuduSync/Hydroid.svg?label=visualiza%C3%A7%C3%B5es&logo=github&color=5EEAD4&style=flat-square" alt="Visualizações"/></a>
  <a href="https://github.com/DuduSync/Hydroid/releases"><img src="https://img.shields.io/github/downloads/DuduSync/Hydroid/total?style=flat-square&color=8B93FF&label=downloads" alt="Downloads"/></a>
</p>

<p align="center">
  <b>Hydroid</b> é um port Android não-oficial do <a href="https://github.com/hydralauncher/hydra">Hydra Launcher</a>,<br/>
  escrito em Kotlin nativo com Jetpack Compose. Projeto de estudo, sem afiliação com o Hydra Launcher.
</p>

## Baixar

APK universal assinado (arm64 + x86_64):

➡️ [**Hydroid v0.9.9 - Releases**](https://github.com/DuduSync/Hydroid/releases/latest)

## Funcionalidades

**Catálogo e descoberta**

- Busca de jogos na Steam com capas e detalhes (descrição em PT-BR)
- Home de descoberta: **Populares, Lançamentos, Em promoção, Em breve** e **Surpreenda-me** (jogo aleatório)
- **Contagem de downloads** em cada card ("25 downloads disponíveis"), na Biblioteca e acima do botão Baixar
- **Pré-carregamento no boot**: catálogo e contagens carregam em segundo plano (as abas já abrem populadas)
- Página do jogo imersiva: arte em tamanho cheio, sem barras, botão de voltar minimalista
- Detalhes extras do jogo: **How Long To Beat, ProtonDB, galeria de screenshots, requisitos de sistema** e Metacritic

**Conta e fontes**

- **Conta Hydra**: login, sincronização da biblioteca e das fontes
- Perfil da conta: visibilidade do perfil e dos souvenires, presentes na nuvem, bloqueados, segurança e assinatura do Hydra Cloud
- Fontes de download registradas via **Hydra Cloud** (o servidor do Hydra resolve fontes atrás de Cloudflare automaticamente)
- **Loja de fontes**: explore 70+ fontes da comunidade e adicione com um toque, com filtros por tag (Trusted, Safe For Use, Classics...) e a tag **Recomendada pro Hydroid** para fontes com jogos **pré-instalados** (extrai e joga, sem instalador)

**Métodos de download**

- **Real-Debrid** (recomendado): torrents e hosters processados no cloud, download em alta velocidade
- **Premiumize, AllDebrid e TorBox**: alternativas em beta (com aviso no app)
- **Torrent**: engine nativa (libtorrent4j) no aparelho, com peers, seeds, ETA e trackers públicos automáticos
- **Direto**: download HTTP sem conta, com **resolvedores próprios** para Gofile, PixelDrain, MediaFire, FuckingFast, Rootz, Datanodes e VikingFile
- **Navegador interno**: para sites com espera ou login (1fichier, MEGA), o app abre a página, captura a URL e os cookies quando o download começa e assume o download no engine
- O sheet de download mostra **de onde vem** (Torrent, Gofile, 1fichier, MEGA...) e marca o método **Recomendado**: Real-Debrid quando ele suporta o host (lista oficial deles, 278 domínios) ou o teu debrid em torrents
- **Link manual**: cole um magnet ou URL direta na página do jogo pra baixar o que quiser

**Downloads**

- Progresso, velocidade, ETA e badge do método utilizado
- **Pausar e continuar**: retoma de onde parou, mesmo depois de fechar o app
- **Fila de downloads** com limite de simultâneos (1, 2, 3 ou sem limite)
- **Só baixar no Wi-Fi** e **limite de velocidade** por download
- Cancelar um download apaga o arquivo parcial (sem lixo no armazenamento)
- **Uma notificação por download** (progresso individual) além do resumo; toque abre o app, e há aviso quando as notificações estão desativadas no sistema
- Pós-download: **extração automática** de `.zip`/`.rar`, **abrir pasta**, extrair manualmente e apagar (removendo só o que aquele download criou)
- Pasta de download escolhida por você (via SAF), configurável a qualquer momento

**Biblioteca e personalização**

- Biblioteca com busca, ordenação (recentes / nome A-Z / favoritos), **favoritos, coleções e atalhos na tela inicial**
- **Temas**: Sistema, Claro, Escuro, **AMOLED** (preto puro) e **Glassmorphism** (vidro com blur)
- Dock flutuante com pílula deslizante, **gestos de arrastar entre abas** e animações
- **Aba inicial** configurável e **interface em português e inglês**
- Sair com dois toques no voltar (com aviso na tela)

**Sistema**

- **Novidades**: changelog dentro do app, sincronizada direto do GitHub (com tag "Atual" na versão instalada)
- **Atualização automática**: confere o release mais recente, instala o APK com um toque e apaga o APK antigo do cache
- Setup guiado em 4 passos (notificações, otimização de bateria, acesso a arquivos e pasta de downloads)
- Ajustes organizados em submenus (Integrações, Fontes, Configurações, Logs e Créditos), com validação das chaves debrid (cards **verdes quando conectados, vermelhos quando não configurados**) e link de convite do Real-Debrid
- **Limpar cache** com um toque (imagens e temporários, sem tocar nos downloads) e **limpar/compartilhar logs**
- **Logs completos** (rede, ações e downloads) para diagnóstico, com registro de travamentos no próprio log

## Como usar

1. Na primeira abertura, conclua o setup (notificações, bateria, acesso a arquivos e pasta de downloads)
2. (Opcional) Em **Ajustes → Integrações**, configure sua chave do Real-Debrid (ou outro serviço debrid) - sem ela dá pra baixar via Torrent ou Direto
3. Em **Ajustes → Fontes de download**, adicione fontes de download (URLs de catálogo `.json`, formato Hydra)
4. Em **Catálogo**, busque um jogo e abra a página dele (ou explore a home de descoberta)
5. Escolha um repack e toque em **Baixar**: o app mostra de onde vem o download e marca o método **Recomendado**. Sem debrid, o **Direto** resolve os hosters; para 1fichier/MEGA o app abre o **navegador interno** e captura o download quando ele começa
6. Acompanhe em **Downloads** - por padrão os jogos vão para `/storage/emulated/0/Download/HYDROID` (configurável em **Ajustes → Configurações do app**)

## Real-Debrid

<p align="center">
  <a href="http://real-debrid.com/?id=11384117" title="Real-Debrid">
    <img src="docs/real-debrid.png" alt="Real-Debrid" height="64"/>
  </a>
</p>

<p align="center">
  <sub>Assine pelo <a href="http://real-debrid.com/?id=11384117">link de convite</a> e apoie o projeto</sub>
</p>

O Real-Debrid é o motor recomendado de downloads: ele processa torrents e hosters no cloud e devolve um link direto de alta velocidade para o aparelho. Pegue sua chave em [real-debrid.com/apitoken](https://real-debrid.com/apitoken) e cole em **Ajustes → Integrações**.

## Comunidade

Participe do canal oficial do Hydroid no Telegram:

➡️ **[t.me/HydroidOFC](https://t.me/HydroidOFC)**

Novidades, releases, suporte e **reporte de bugs**. Achou um problema? Manda por lá: o app gera logs completos pra facilitar o diagnóstico (e tem o card do Telegram nos Créditos, dentro do app).

## Outros projetos

- [**LoreAPI**](https://loreapi.online) - API de IA compatível com OpenAI. Usei ela no desenvolvimento deste projeto como assistente de programação (análise, ideias e código).

## Aviso legal

O Hydroid não hospeda, indexa nem distribui nenhum conteúdo. As fontes de download são configuradas pelo próprio usuário e os downloads acontecem a partir delas. Baixe apenas conteúdo que você tem o direito de baixar. Marcas e jogos citados pertencem aos seus respectivos donos.

## Créditos

- [Hydra Launcher](https://github.com/hydralauncher/hydra) - projeto original (MIT); grande parte da lógica, das APIs e do ecossistema de fontes vem dele
- Port Android por [DuduSync](https://github.com/DuduSync)
- Programação com auxílio da [LoreAPI](https://loreapi.online) (API de IA compatível com OpenAI, usada como assistente durante o desenvolvimento)

## Apoie o projeto

Se o Hydroid te ajuda, considere apoiar o desenvolvimento via Pix:

<p align="center">
  <a href="https://nubank.com.br/cobrar/7rfap/6aa35e97-c27c-479b-b74b-dbd122db9877">
    <img src="https://img.shields.io/badge/Apoiar%20com-Pix-820AD1?style=for-the-badge&logo=nubank&logoColor=white" alt="Apoiar com Pix"/>
  </a>
</p>

## Licença

[MIT](LICENSE)
