package infra

import appModule
import com.jillesvangurp.ktsearch.bulk
import com.jillesvangurp.ktsearch.index
import infra.common.OperationHistoryRepository
import infra.common.UserRepository
import infra.model.*
import infra.web.DataSourceWebNovelProvider
import infra.wenku.WenkuNovelMetadataRepository
import infra.wenku.WenkuNovelVolumeRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.koin.KoinExtension
import io.kotest.koin.KoinLifecycleMode
import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.Document
import org.bson.types.ObjectId
import org.koin.java.KoinJavaComponent.inject
import org.koin.test.KoinTest
import org.litote.kmongo.coroutine.projection
import org.litote.kmongo.eq
import org.litote.kmongo.id.toId
import java.io.File

class BookRepositoryTest : DescribeSpec(), KoinTest {
    override fun extensions() = listOf(KoinExtension(module = appModule, mode = KoinLifecycleMode.Root))

    private val provider by inject<DataSourceWebNovelProvider>(DataSourceWebNovelProvider::class.java)
    private val es by inject<DataSourceElasticSearch>(DataSourceElasticSearch::class.java)
    private val mongo by inject<DataSourceMongo>(DataSourceMongo::class.java)

    private val wenkuVR by inject<WenkuNovelVolumeRepository>(WenkuNovelVolumeRepository::class.java)
    private val wenkuMR by inject<WenkuNovelMetadataRepository>(WenkuNovelMetadataRepository::class.java)
    private val userR by inject<UserRepository>(UserRepository::class.java)
    private val ohR by inject<OperationHistoryRepository>(OperationHistoryRepository::class.java)

    init {
        describe("delete wenku") {
            val nid = "65320febcdb73b5203ca1bd2"
            val favored = mongo
                .wenkuNovelFavoriteCollection
                .find(WenkuNovelFavoriteModel::novelId eq ObjectId(nid).toId())
                .toList()
            println(favored)
//            wenkuMR.delete(nid)
        }
        describe("build es index") {
            @Serializable
            data class WNMP(
                val providerId: String,
                val bookId: String,
                val titleJp: String,
                val titleZh: String? = null,
                val authors: List<WebNovelAuthor>,
                val type: WebNovelType = WebNovelType.连载中,
                val attentions: List<WebNovelAttention> = emptyList(),
                val keywords: List<String> = emptyList(),
                val visited: Long = 0,
                val gpt: Long = 0,
                val toc: List<WebNovelTocItem>,
                @Contextual val updateAt: Instant,
            )

            suspend fun buildIndex(skip: Int, limit: Int): Int {
                val list = mongo
                    .webNovelMetadataCollection
                    .withDocumentClass<WNMP>()
                    .find()
                    .projection(
                        WebNovelMetadata::providerId,
                        WebNovelMetadata::novelId,
                        WebNovelMetadata::titleJp,
                        WebNovelMetadata::titleZh,
                        WebNovelMetadata::authors,
                        WebNovelMetadata::type,
                        WebNovelMetadata::attentions,
                        WebNovelMetadata::keywords,
                        WebNovelMetadata::gpt,
                        WebNovelMetadata::visited,
                        WebNovelMetadata::toc,
                        WebNovelMetadata::updateAt,
                    )
                    .skip(skip)
                    .limit(limit)
                    .toList()
                es.client.bulk(target = DataSourceElasticSearch.webNovelIndexName) {
                    list.forEach { it ->
                        index(
                            doc = WebNovelMetadataEsModel(
                                providerId = it.providerId,
                                novelId = it.bookId,
                                authors = it.authors.map { it.name },
                                titleJp = it.titleJp,
                                titleZh = it.titleZh,
                                type = it.type,
                                attentions = it.attentions,
                                keywords = it.keywords,
                                hasGpt = it.gpt > 0,
                                visited = it.visited.toInt(),
                                tocSize = it.toc.count { it.chapterId != null },
                                updateAt = it.updateAt.epochSeconds,
                            ),
                            id = "${it.providerId}.${it.bookId}",
                        )
                    }
                }
                return list.size
            }

            var batch = 0
            val batchSize = 500
            while (true) {
                println("batch${batch} start")
                val actualBatchSize = buildIndex(
                    batch * batchSize,
                    batchSize + 50,
                )
                println("batch${batch} finish ${actualBatchSize}")

                if (actualBatchSize == batchSize + 50) {
                    batch += 1
                } else {
                    break
                }
            }
        }

        describe("build wenku es index") {
            @Serializable
            data class WNMP(
                @Contextual @SerialName("_id") val id: ObjectId,
                val title: String,
                val titleZh: String,
                val cover: String,
                val authors: List<String>,
                val artists: List<String>,
                val keywords: List<String>,
                val r18: Boolean = false,
                @Contextual val updateAt: Instant,
            )

            suspend fun buildIndex(skip: Int, limit: Int): Int {
                val list = mongo
                    .wenkuNovelMetadataCollection
                    .withDocumentClass<WNMP>()
                    .find()
                    .projection(
                        WenkuNovelMetadata::id,
                        WenkuNovelMetadata::title,
                        WenkuNovelMetadata::titleZh,
                        WenkuNovelMetadata::cover,
                        WenkuNovelMetadata::authors,
                        WenkuNovelMetadata::artists,
                        WenkuNovelMetadata::keywords,
                        WenkuNovelMetadata::r18,
                        WenkuNovelMetadata::updateAt,
                    )
                    .skip(skip)
                    .limit(limit)
                    .toList()
                es.client.bulk(target = DataSourceElasticSearch.wenkuNovelIndexName) {
                    list.forEach {
                        index(
                            doc = WenkuNovelMetadataEsModel(
                                id = it.id.toHexString(),
                                title = it.title,
                                titleZh = it.titleZh,
                                cover = it.cover,
                                authors = it.authors,
                                artists = it.artists,
                                keywords = it.keywords,
                                r18 = it.r18,
                                updateAt = it.updateAt.epochSeconds,
                            ),
                            id = it.id.toHexString(),
                        )
                    }
                }
                return list.size
            }

            var batch = 0
            val batchSize = 500
            while (true) {
                println("batch${batch} start")
                val actualBatchSize = buildIndex(
                    batch * batchSize,
                    batchSize + 50,
                )
                println("batch${batch} finish ${actualBatchSize}")

                if (actualBatchSize == batchSize + 50) {
                    batch += 1
                } else {
                    break
                }
            }
        }


        describe("kmongo issue 415") {
//            println(setValue(BookEpisode::youdaoParagraphs.pos(0), "test").toBsonDocument())
//            println(setValue(BookEpisode::baiduParagraphs.pos(0), "test").toBsonDocument())
//            println(Updates.set("paragraphsZh.0", "test").toBsonDocument())
        }

        describe("generate sitemap") {
            File("sitemap.txt").printWriter().use { out ->
                mongo
                    .webNovelMetadataCollection
                    .withDocumentClass<Document>()
                    .find()
                    .projection(
                        WebNovelMetadata::providerId,
                        WebNovelMetadata::novelId,
                    )
                    .toList()
                    .forEach {
                        val pid = it.getString("providerId")
                        val nid = it.getString("bookId")
                        out.println("https://books.fishhawk.top/novel/${pid}/${nid}")
                    }

                mongo
                    .wenkuNovelMetadataCollection
                    .projection(WenkuNovelMetadata::id)
                    .toList()
                    .forEach {
                        val nid = it.toHexString()
                        out.println("https://books.fishhawk.top/wenku/${nid}")
                    }
            }
        }
    }
}
