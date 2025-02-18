package infra

import com.jillesvangurp.ktsearch.KtorRestClient
import com.jillesvangurp.ktsearch.Node
import com.jillesvangurp.ktsearch.SearchClient
import com.jillesvangurp.ktsearch.createIndex
import infra.model.WebNovelAttention
import infra.model.WebNovelType
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable

@Serializable
data class WebNovelMetadataEsModel(
    val providerId: String,
    val novelId: String,
    val titleJp: String,
    val titleZh: String?,
    val authors: List<String>,
    val type: WebNovelType = WebNovelType.连载中,
    val attentions: List<WebNovelAttention> = emptyList(),
    val keywords: List<String>,
    val tocSize: Int,
    val visited: Int,
    val hasGpt: Boolean,
    val updateAt: Long,
)

@Serializable
data class WenkuNovelMetadataEsModel(
    val id: String,
    val title: String,
    val titleZh: String,
    val cover: String,
    val authors: List<String>,
    val artists: List<String>,
    val keywords: List<String>,
    val r18: Boolean,
    val updateAt: Long,
)

class DataSourceElasticSearch(url: String) {
    val client = SearchClient(
        KtorRestClient(
            Node(url, 9200)
        )
    )

    companion object {
        const val webNovelIndexName = "web-index"
        const val wenkuNovelIndexName = "wenku-index-alt"
    }

    init {
        runBlocking {
            runCatching {
                client.createIndex(webNovelIndexName) {
                    mappings(dynamicEnabled = false) {
                        keyword(WebNovelMetadataEsModel::providerId)
                        text(WebNovelMetadataEsModel::titleJp) { analyzer = "icu_analyzer" }
                        text(WebNovelMetadataEsModel::titleZh) { analyzer = "icu_analyzer" }
                        keyword(WebNovelMetadataEsModel::authors)
                        keyword(WebNovelMetadataEsModel::type)
                        keyword(WebNovelMetadataEsModel::attentions)
                        keyword(WebNovelMetadataEsModel::keywords)
                        number<Int>(WebNovelMetadataEsModel::tocSize)
                        number<Int>(WebNovelMetadataEsModel::visited)
                        bool(WebNovelMetadataEsModel::hasGpt)
                        date(WebNovelMetadataEsModel::updateAt)
                    }
                }
            }

            runCatching {
                client.createIndex(wenkuNovelIndexName) {
                    mappings(dynamicEnabled = false) {
                        text(WenkuNovelMetadataEsModel::title) { analyzer = "icu_analyzer" }
                        text(WenkuNovelMetadataEsModel::titleZh) { analyzer = "icu_analyzer" }
                        keyword(WenkuNovelMetadataEsModel::authors)
                        keyword(WenkuNovelMetadataEsModel::artists)
                        keyword(WenkuNovelMetadataEsModel::keywords)
                        bool(WenkuNovelMetadataEsModel::r18)
                        date(WenkuNovelMetadataEsModel::updateAt)
                    }
                }
            }
        }
    }
}