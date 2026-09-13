package gg.hydroid.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class SteamSearchResponse(
    val total: Int = 0,
    val items: List<SteamSearchItem> = emptyList()
)

@Serializable
data class SteamSearchItem(
    val type: String = "app",
    val name: String,
    val id: Long,
    val tiny_image: String = "",
    val metascore: String? = null
)

@Serializable
data class SteamFeaturedResponse(
    val top_sellers: SteamFeaturedCategory? = null,
    val new_releases: SteamFeaturedCategory? = null,
    val specials: SteamFeaturedCategory? = null,
    val coming_soon: SteamFeaturedCategory? = null
)

@Serializable
data class SteamFeaturedCategory(val items: List<SteamFeaturedItem> = emptyList())

@Serializable
data class SteamFeaturedItem(
    val id: Long = 0,
    val name: String = "",
    val header_image: String? = null,
    val large_capsule_image: String? = null,
    val small_capsule_image: String? = null,
    val final_price: Long = 0,
    val original_price: Long? = null,
    val discount_percent: Int = 0,
    val currency: String? = null
)

@Serializable
data class SteamAppDetailsResponse(
    @kotlinx.serialization.json.JsonNames("data") val data: Map<String, SteamAppDetails> = emptyMap()
)

@Serializable
data class SteamAppDetails(
    val type: String? = null,
    val name: String? = null,
    val detailed_description: String? = null,
    val short_description: String? = null,
    val header_image: String? = null,
    val release_date: SteamReleaseDate? = null,
    val developers: List<String> = emptyList(),
    val publishers: List<String> = emptyList(),
    val genres: List<SteamGenre> = emptyList(),
    val screenshots: List<SteamScreenshot> = emptyList(),
    val metacritic: SteamMetacritic? = null,
    val recommendations: SteamRecommendations? = null,
    val pc_requirements: kotlinx.serialization.json.JsonElement? = null,
    val website: String? = null
)

@Serializable
data class SteamScreenshot(
    val id: Long = 0,
    val path_thumbnail: String = "",
    val path_full: String = ""
)

@Serializable
data class SteamMetacritic(val score: Int = 0, val url: String = "")

@Serializable
data class SteamRecommendations(val total: Int = 0)

@Serializable
data class HltbEntry(
    val title: String = "",
    val duration: String = "",
    val accuracy: String? = null
)

@Serializable
data class ProtonTier(
    val tier: String = "",
    val bestReportedTier: String = "",
    val confidence: String = "",
    val score: Double = 0.0,
    val total: Int = 0
)

@Serializable
data class SteamReleaseDate(val date: String = "")

@Serializable
data class SteamGenre(val description: String = "")

@Serializable
data class RdUser(
    val id: String = "",
    val username: String = "",
    val email: String? = null,
    val points: Int = 0,
    val premium: Int = 0,
    val expiration: String? = null
)

@Serializable
data class RdAddMagnetResponse(val id: String, val uri: String? = null)

@Serializable
data class RdTorrentInfo(
    val id: String = "",
    val status: String = "",
    val progress: Float = 0f,
    val name: String? = null,
    val links: List<String> = emptyList(),
    val original_filename: String? = null
)

@Serializable
data class RdUnrestrictLink(
    val id: String = "",
    val download: String = "",
    val filename: String = "",
    val filesize: Long = 0,
    val link: String = ""
)

@Serializable
data class DownloadSource(
    val id: String,
    val name: String,
    val url: String,
    val status: String = "active",
    val downloadCount: Int = 0,
    val createdAt: String = ""
)

@Serializable
data class GameRepack(
    val id: String,
    val title: String,
    val fileSize: String? = null,
    val uris: List<String> = emptyList(),
    val unavailableUris: List<String> = emptyList(),
    val uploadDate: String? = null,
    val downloadSourceName: String = ""
)

@Serializable
data class LibraryGame(
    val appId: Long,
    val name: String,
    val headerImage: String = "",
    val addedAt: Long = System.currentTimeMillis(),
    val favorite: Boolean = false,
    val collectionIds: List<String> = emptyList()
)

@Serializable
data class GameCollection(
    val id: String,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class ActiveDownload(
    val id: String,
    val title: String,
    val stage: String = "resolvendo", // resolvendo | cloud | baixando | concluido | erro
    val method: String = "rd",        // rd | direto | torrent
    val progress: Float = 0f,
    val bytesDownloaded: Long = 0,
    val totalBytes: Long = 0,
    val speedBps: Long = 0,
    val error: String? = null,
    val savePath: String? = null,
    val uri: String? = null,
    val peers: Int = 0,
    val seeds: Int = 0,
    val savedPaths: List<String> = emptyList(),
    // cabecalhos extras capturados no navegador interno (ex.: Cookie do 1fichier)
    val headers: Map<String, String>? = null
)

@Serializable
data class HydraAuth(
    val accessToken: String = "",
    val refreshToken: String = "",
    val expiration: Long = 0L
)

@Serializable
data class HydraUser(
    val id: String = "",
    val username: String = "",
    val displayName: String = "",
    val email: String? = null,
    val profileImageUrl: String? = null,
    val backgroundImageUrl: String? = null,
    val bio: String = "",
    val profileVisibility: String = "PUBLIC",
    val souvenirsVisibility: String = "PUBLIC",
    val allowCloudGifts: Boolean = true,
    val hasPassword: Boolean = true,
    val subscription: HydraSubscription? = null
)

@Serializable
data class HydraSubscription(
    val status: String = "",
    val expiresAt: String? = null,
    val plan: HydraPlan? = null
)

@Serializable
data class HydraPlan(val type: String = "")

@Serializable
data class HydraRemoteGame(
    val id: String = "",
    val objectId: String = "0",
    val shop: String = "",
    val title: String = "",
    val coverImageUrl: String? = null,
    val libraryImageUrl: String? = null
)

@Serializable
data class HydraRemoteSource(
    val id: String = "",
    val name: String? = null,
    val url: String = ""
)
