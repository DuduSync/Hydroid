# Hydroid

Port Android não-oficial do [Hydra Launcher](https://github.com/hydralauncher/hydra), em desenvolvimento. O Hydroid leva a experiência de biblioteca + catálogo + downloads do Hydra para o celular, em Kotlin nativo com Jetpack Compose.

> Projeto de estudo, sem afiliação com o Hydra Launcher. Licença MIT.

## Status

MVP funcional:

- Busca de jogos na Steam com capas e detalhes (PT-BR)
- Fontes de download registradas via **Hydra Cloud** (o servidor do Hydra resolve as fontes atrás de Cloudflare)
- Repacks por jogo com **método de download explícito**: `Real-Debrid`, `Direto` (links HTTP) e `Torrent` (em breve)
- Downloads de torrent via **Real-Debrid**: magnet → cloud da RD → link direto → celular, com progresso e velocidade
- Downloads diretos (sem RD) quando a fonte oferece link de arquivo real
- Biblioteca local e histórico de downloads persistentes
- Temas claro e escuro (Material 3)

## Como usar

1. Em **Ajustes**, configure sua chave da API do Real-Debrid ([real-debrid.com/apitoken](https://real-debrid.com/apitoken))
2. Ainda em **Ajustes**, adicione fontes de download (URLs de catálogo `.json`, formato Hydra)
3. Em **Catálogo**, busque um jogo e abra a página dele
4. Escolha um repack e o método de download (`Real-Debrid` ou `Direto`)
5. Acompanhe em **Downloads** — os arquivos ficam em `Android/data/gg.hydroid.app/files/Downloads`

### Fontes de download

O formato é o mesmo do Hydra (`{ "name": "...", "downloads": [{ "title", "fileSize", "uris": [...], "uploadDate" }] }`).
Veja a documentação oficial: [docs.hydralauncher.gg/download-sources](https://docs.hydralauncher.gg/download-sources)

Fontes da comunidade podem ser encontradas em [library.hydra.wiki](https://library.hydra.wiki/). Fontes atrás de Cloudflare são registradas automaticamente pelo servidor do Hydra ao adicionar a URL.

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
│   ├── api/              # Steam, Hydra Cloud, Real-Debrid, fontes
│   ├── model/            # modelos serializáveis
│   └── store/            # persistência local (JSON)
├── download/             # engine de download (RD e direto)
└── ui/                   # telas Compose (Catálogo, Biblioteca, Downloads, Ajustes)
```

Fluxo de um download: busca na API pública da Steam → repacks do jogo via API do Hydra Cloud (mesmo endpoint do launcher desktop) → escolha do método → Real-Debrid processa o torrent no cloud e devolve um link direto → download para o aparelho.

## Aviso legal

O Hydroid não hospeda, indexa nem distribui nenhum conteúdo. As fontes de download são configuradas pelo próprio usuário e os downloads acontecem a partir delas. Baixe apenas conteúdo que você tem o direito de baixar. Marcas e jogos citados pertencem aos seus respectivos donos.

## Créditos

- [Hydra Launcher](https://github.com/hydralauncher/hydra) — projeto original (MIT), grande parte da lógica e das APIs vem dele
- Port Android por [DuduSync](https://github.com/DuduSync)
- Doações: _link em breve_

## Licença

[MIT](LICENSE)
