<p align="center">
  <img src="docs/banner.png" alt="Hydroid — Launcher de jogos para Android" width="100%"/>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/licen%C3%A7a-MIT-5EEAD4?style=flat-square" alt="Licença MIT"/>
  <img src="https://img.shields.io/badge/Android-8.0%2B-8B93FF?style=flat-square&logo=android&logoColor=white" alt="Android 8.0+"/>
  <img src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin + Jetpack Compose"/>
  <img src="https://img.shields.io/badge/status-MVP%20funcional-FFA657?style=flat-square" alt="Status: MVP funcional"/>
</p>

<p align="center">
  <b>Hydroid</b> é um port Android não-oficial do <a href="https://github.com/hydralauncher/hydra">Hydra Launcher</a>,<br/>
  escrito em Kotlin nativo com Jetpack Compose. Projeto de estudo, sem afiliação com o Hydra Launcher.
</p>

## Screenshots

| Catálogo | Repacks e métodos | Downloads | Biblioteca |
| :---: | :---: | :---: | :---: |
| ![Catálogo](docs/screenshots/catalogo.jpg) | ![Métodos de download](docs/screenshots/metodos.jpg) | ![Downloads](docs/screenshots/downloads.jpg) | ![Biblioteca](docs/screenshots/biblioteca.jpg) |

## Funcionalidades

- Busca de jogos na Steam com capas e detalhes (descrição em PT-BR)
- Fontes de download registradas via **Hydra Cloud** — o servidor do Hydra resolve as fontes atrás de Cloudflare automaticamente
- Repacks por jogo com **método de download explícito**:
  - **Real-Debrid** — torrents e hosters processados no cloud
  - **Direto** — download HTTP direto, sem precisar de conta (quando a fonte oferece link de arquivo real)
  - **Torrent** — em breve (engine local)
- Downloads com progresso, velocidade e badge do método utilizado
- Biblioteca local com capas
- Temas claro e escuro (Material 3)

## Como usar

1. Em **Ajustes**, configure sua chave da API do Real-Debrid
2. Ainda em **Ajustes**, adicione fontes de download (URLs de catálogo `.json`, formato Hydra)
3. Em **Catálogo**, busque um jogo e abra a página dele
4. Escolha um repack e toque em **Real-Debrid** ou **Direto**
5. Acompanhe em **Downloads** — os arquivos ficam em `Android/data/gg.hydroid.app/files/Downloads`

<p align="center">
  <img src="docs/screenshots/ajustes.jpg" alt="Ajustes" width="320"/>
</p>

### Fontes de download

O formato é o mesmo do Hydra:

```json
{
  "name": "Minha fonte",
  "downloads": [
    {
      "title": "Nome do jogo [Pre-Instalado]",
      "fileSize": "1.5 GB",
      "uris": ["magnet:?xt=urn:btih:..."],
      "uploadDate": "2026-01-01T00:00:00.000Z"
    }
  ]
}
```

Documentação oficial: [docs.hydralauncher.gg/download-sources](https://docs.hydralauncher.gg/download-sources)

Fontes da comunidade: [library.hydra.wiki](https://library.hydra.wiki/). Fontes atrás de Cloudflare são registradas automaticamente pelo servidor do Hydra ao adicionar a URL.

## Real-Debrid

<p align="center">
  <a href="http://real-debrid.com/?id=11384117" title="Real-Debrid">
    <img src="docs/real-debrid.png" alt="Real-Debrid" height="64"/>
  </a>
</p>

<p align="center">
  <sub>Assine pelo <a href="http://real-debrid.com/?id=11384117">link de convite</a> e apoie o projeto</sub>
</p>

O Real-Debrid é o motor recomendado de downloads: ele processa torrents e hosters no cloud e devolve um link direto de alta velocidade para o aparelho. Pegue sua chave em [real-debrid.com/apitoken](https://real-debrid.com/apitoken) e cole em **Ajustes**.

## Compilando

Requisitos: JDK 17+ e Android SDK (API 34). O Gradle wrapper está incluído.

```bash
cd android
echo "sdk.dir=/caminho/para/android-sdk" > local.properties   # ou defina ANDROID_HOME
./gradlew assembleDebug
```

O APK sai em `android/app/build/outputs/apk/debug/app-debug.apk`.

## Arquitetura

```
android/app/src/main/java/gg/hydroid/app/
├── MainActivity.kt       # navegação e tema
├── data/
│   ├── api/              # Steam, Hydra Cloud, Real-Debrid e fontes
│   ├── model/            # modelos serializáveis
│   └── store/            # persistência local (JSON)
├── download/             # engine de download (Real-Debrid e direto)
└── ui/                   # telas Compose (Catálogo, Biblioteca, Downloads, Ajustes)
```

Fluxo de um download: busca na API pública da Steam → repacks do jogo via API do Hydra Cloud (mesmo endpoint do launcher desktop) → escolha do método → o Real-Debrid processa o torrent no cloud e devolve um link direto → download para o aparelho.

## Aviso legal

O Hydroid não hospeda, indexa nem distribui nenhum conteúdo. As fontes de download são configuradas pelo próprio usuário e os downloads acontecem a partir delas. Baixe apenas conteúdo que você tem o direito de baixar. Marcas e jogos citados pertencem aos seus respectivos donos.

## Créditos

- [Hydra Launcher](https://github.com/hydralauncher/hydra) — projeto original (MIT); grande parte da lógica, das APIs e do ecossistema de fontes vem dele
- Port Android por [DuduSync](https://github.com/DuduSync)
- Doações (Pix): [nubank.com.br/cobrar/7rfap](https://nubank.com.br/cobrar/7rfap/6aa35e97-c27c-479b-b74b-dbd122db9877)

<p align="center">
  <img src="docs/screenshots/creditos.jpg" alt="Créditos" width="320"/>
</p>

## Apoie o projeto

Se o Hydroid te ajuda, considere apoiar o desenvolvimento via Pix:

<p align="center">
  <a href="https://nubank.com.br/cobrar/7rfap/6aa35e97-c27c-479b-b74b-dbd122db9877">
    <img src="https://img.shields.io/badge/Apoiar%20com-Pix-820AD1?style=for-the-badge&logo=nubank&logoColor=white" alt="Apoiar com Pix"/>
  </a>
</p>

## Licença

[MIT](LICENSE)
