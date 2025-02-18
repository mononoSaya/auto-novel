package api

import api.plugins.AuthenticatedUser
import api.plugins.authenticateDb
import api.plugins.authenticatedUser
import api.plugins.authenticatedUserOrNull
import infra.common.OperationHistoryRepository
import infra.common.UserRepository
import infra.model.*
import infra.web.providers.Syosetu
import infra.web.WebNovelChapterRepository
import infra.web.WebNovelFileRepository
import infra.web.WebNovelFilter
import infra.web.WebNovelMetadataRepository
import infra.wenku.WenkuNovelMetadataRepository
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.cachingheaders.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.resources.post
import io.ktor.server.resources.put
import io.ktor.server.routing.*
import io.ktor.util.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId
import org.koin.ktor.ext.inject
import java.nio.file.Path

@Resource("/novel")
private class WebNovelRes {
    @Resource("")
    class List(
        val parent: WebNovelRes,
        val page: Int,
        val pageSize: Int,
        val provider: String = "",
        val type: Int = 0,
        val level: Int = 0,
        val translate: Int = 0,
        val sort: Int = 0,
        val query: String? = null,
    )

    @Resource("/rank/{providerId}")
    class Rank(val parent: WebNovelRes, val providerId: String)

    @Resource("/favored")
    class Favored(
        val parent: WebNovelRes,
        val page: Int,
        val pageSize: Int,
        val sort: FavoriteListSort,
    )

    @Resource("/read-history")
    class ReadHistory(
        val parent: WebNovelRes,
        val page: Int,
        val pageSize: Int,
    )

    @Resource("/{providerId}/{novelId}")
    class Id(val parent: WebNovelRes, val providerId: String, val novelId: String) {
        @Resource("/favored")
        class Favored(val parent: Id)

        @Resource("/read-history")
        class ReadHistory(val parent: Id)

        @Resource("/wenku")
        class Wenku(val parent: Id)

        @Resource("/glossary")
        class Glossary(val parent: Id)

        @Resource("/chapter/{chapterId}")
        class Chapter(val parent: Id, val chapterId: String)

        @Resource("/translate/{translatorId}")
        class Translate(val parent: Id, val translatorId: TranslatorId) {
            @Resource("/metadata")
            class Metadata(val parent: Translate)

            @Resource("/check-chapter/{chapterId}")
            class CheckChapter(val parent: Translate, val chapterId: String, val sync: Boolean)

            @Resource("/chapter/{chapterId}")
            class Chapter(val parent: Translate, val chapterId: String)
        }

        @Resource("/file/{lang}/{type}")
        class File(val parent: Id, val lang: NovelFileLang, val type: NovelFileType)
    }
}

fun Route.routeWebNovel() {
    val service by inject<WebNovelApi>()

    get<WebNovelRes.List> { loc ->
        call.tryRespond {
            service.list(
                queryString = loc.query?.ifBlank { null },
                filterProvider = loc.provider.ifEmpty { null },
                filterType = when (loc.type) {
                    1 -> WebNovelFilter.Type.连载中
                    2 -> WebNovelFilter.Type.已完结
                    3 -> WebNovelFilter.Type.短篇
                    else -> WebNovelFilter.Type.全部
                },
                filterLevel = when (loc.level) {
                    1 -> WebNovelFilter.Level.一般向
                    2 -> WebNovelFilter.Level.R18
                    else -> WebNovelFilter.Level.全部
                },
                filterTranslate = when (loc.translate) {
                    1 -> WebNovelFilter.Translate.AI
                    else -> WebNovelFilter.Translate.全部
                },
                filterSort = when (loc.sort) {
                    1 -> WebNovelFilter.Sort.点击
                    2 -> WebNovelFilter.Sort.相关
                    else -> WebNovelFilter.Sort.更新
                },
                page = loc.page,
                pageSize = loc.pageSize,
            )
        }
    }
    get<WebNovelRes.Rank> { loc ->
        val options = call.request.queryParameters.toMap().mapValues { it.value.first() }
        call.caching = CachingOptions(CacheControl.MaxAge(maxAgeSeconds = 3600 * 2))
        call.tryRespond {
            service.listRank(providerId = loc.providerId, options = options)
        }
    }

    authenticateDb {
        get<WebNovelRes.ReadHistory> { loc ->
            val user = call.authenticatedUser()
            call.tryRespond {
                service.listReadHistory(
                    user = user,
                    page = loc.page,
                    pageSize = loc.pageSize,
                )
            }
        }
        put<WebNovelRes.Id.ReadHistory> { loc ->
            val user = call.authenticatedUser()
            val chapterId = call.receive<String>()
            call.tryRespond {
                service.updateReadHistory(
                    user = user,
                    providerId = loc.parent.providerId,
                    novelId = loc.parent.novelId,
                    chapterId = chapterId,
                )
            }
        }

        get<WebNovelRes.Favored> { loc ->
            val user = call.authenticatedUser()
            call.tryRespond {
                service.listFavored(
                    user = user,
                    page = loc.page,
                    pageSize = loc.pageSize,
                    sort = loc.sort,
                )
            }
        }
        put<WebNovelRes.Id.Favored> { loc ->
            val user = call.authenticatedUser()
            call.tryRespond {
                service.updateFavored(
                    user = user,
                    providerId = loc.parent.providerId,
                    novelId = loc.parent.novelId,
                    favored = true,
                )
            }
        }
        delete<WebNovelRes.Id.Favored> { loc ->
            val user = call.authenticatedUser()
            call.tryRespond {
                service.updateFavored(
                    user = user,
                    providerId = loc.parent.providerId,
                    novelId = loc.parent.novelId,
                    favored = false,
                )
            }
        }
    }

    // Get
    authenticateDb(optional = true) {
        get<WebNovelRes.Id> { loc ->
            val user = call.authenticatedUserOrNull()
            call.tryRespond {
                service.getMetadata(
                    user = user,
                    providerId = loc.providerId,
                    novelId = loc.novelId,
                )
            }
        }
    }
    get<WebNovelRes.Id.Chapter> { loc ->
        call.tryRespond {
            service.getChapter(
                providerId = loc.parent.providerId,
                novelId = loc.parent.novelId,
                chapterId = loc.chapterId,
            )
        }
    }

    // Update
    authenticateDb {
        post<WebNovelRes.Id> { loc ->
            @Serializable
            class Body(
                val title: String? = null,
                val introduction: String? = null,
                val toc: Map<String, String>,
            )

            val user = call.authenticatedUser()
            val body = call.receive<Body>()
            call.tryRespond {
                service.updateMetadata(
                    user = user,
                    providerId = loc.providerId,
                    novelId = loc.novelId,
                    title = body.title,
                    introduction = body.introduction,
                    toc = body.toc,
                )
            }
        }
        put<WebNovelRes.Id.Glossary> { loc ->
            val user = call.authenticatedUser()
            val body = call.receive<Map<String, String>>()
            call.tryRespond {
                service.updateGlossary(
                    user = user,
                    providerId = loc.parent.providerId,
                    novelId = loc.parent.novelId,
                    glossary = body,
                )
            }
        }
        put<WebNovelRes.Id.Wenku> { loc ->
            val user = call.authenticatedUser()
            val body = call.receive<String>()
            call.tryRespond {
                service.updateWenkuId(
                    user = user,
                    providerId = loc.parent.providerId,
                    novelId = loc.parent.novelId,
                    wenkuId = body,
                )
            }
        }
        delete<WebNovelRes.Id.Wenku> { loc ->
            val user = call.authenticatedUser()
            call.tryRespond {
                service.updateWenkuId(
                    user = user,
                    providerId = loc.parent.providerId,
                    novelId = loc.parent.novelId,
                    wenkuId = null,
                )
            }
        }
    }

    // Translate
    get<WebNovelRes.Id.Translate> { loc ->
        call.tryRespond {
            service.getTranslateTask(
                providerId = loc.parent.providerId,
                novelId = loc.parent.novelId,
                translatorId = loc.translatorId,
            )
        }
    }
    post<WebNovelRes.Id.Translate.Metadata> { loc ->
        @Serializable
        class Body(
            val title: String? = null,
            val introduction: String? = null,
            val toc: Map<String, String>,
        )

        val body = call.receive<Body>()
        call.tryRespond {
            service.updateMetadataTranslation(
                providerId = loc.parent.parent.providerId,
                novelId = loc.parent.parent.novelId,
                title = body.title,
                introduction = body.introduction,
                toc = body.toc,
            )
        }
    }
    post<WebNovelRes.Id.Translate.CheckChapter> { loc ->
        call.tryRespond {
            service.checkIfChapterNeedTranslate(
                providerId = loc.parent.parent.providerId,
                novelId = loc.parent.parent.novelId,
                translatorId = loc.parent.translatorId,
                chapterId = loc.chapterId,
                sync = loc.sync,
            )
        }
    }
    put<WebNovelRes.Id.Translate.Chapter> { loc ->
        @Serializable
        class Body(
            val glossaryUuid: String? = null,
            val paragraphsZh: List<String>,
        )

        val body = call.receive<Body>()
        call.tryRespond {
            service.updateChapterTranslation(
                providerId = loc.parent.parent.providerId,
                novelId = loc.parent.parent.novelId,
                translatorId = loc.parent.translatorId,
                chapterId = loc.chapterId,
                glossaryUuid = body.glossaryUuid,
                paragraphsZh = body.paragraphsZh,
            )
        }
    }

    // File
    get<WebNovelRes.Id.File> { loc ->
        call.tryRespondRedirect {
            val path = service.updateFile(
                providerId = loc.parent.providerId,
                novelId = loc.parent.novelId,
                lang = loc.lang,
                type = loc.type,
            )
            "../../../../../../../files-web/${path}"
        }
    }
}

class WebNovelApi(
    private val metadataRepo: WebNovelMetadataRepository,
    private val chapterRepo: WebNovelChapterRepository,
    private val fileRepo: WebNovelFileRepository,
    private val userRepo: UserRepository,
    private val wenkuMetadataRepo: WenkuNovelMetadataRepository,
    private val operationHistoryRepo: OperationHistoryRepository,
) {
    @Serializable
    data class NovelOutlineDto(
        val providerId: String,
        val novelId: String,
        val titleJp: String,
        val titleZh: String?,
        val type: WebNovelType?,
        val attentions: List<WebNovelAttention>,
        val keywords: List<String>,
        val extra: String?,
        val total: Long,
        val jp: Long,
        val baidu: Long,
        val youdao: Long,
        val gpt: Long,
        val updateAt: Long?,
    )

    private fun WebNovelMetadataOutline.asDto() =
        NovelOutlineDto(
            providerId = providerId,
            novelId = novelId,
            titleJp = titleJp,
            titleZh = titleZh,
            type = type,
            attentions = attentions,
            keywords = keywords,
            extra = extra,
            total = total,
            jp = jp,
            baidu = baidu,
            youdao = youdao,
            gpt = gpt,
            updateAt = updateAt?.epochSeconds,
        )

    suspend fun list(
        queryString: String?,
        filterProvider: String?,
        filterType: WebNovelFilter.Type,
        filterLevel: WebNovelFilter.Level,
        filterTranslate: WebNovelFilter.Translate,
        filterSort: WebNovelFilter.Sort,
        page: Int,
        pageSize: Int,
    ): PageDto<NovelOutlineDto> {
        validatePageNumber(page)
        validatePageSize(pageSize)
        return metadataRepo
            .search(
                userQuery = queryString,
                filterProvider = filterProvider,
                filterType = filterType,
                filterLevel = filterLevel,
                filterTranslate = filterTranslate,
                filterSort = filterSort,
                page = page,
                pageSize = pageSize,
            )
            .asDto(pageSize) { it.asDto() }
    }

    suspend fun listRank(
        providerId: String,
        options: Map<String, String>,
    ): PageDto<NovelOutlineDto> {
        val items = metadataRepo
            .listRank(providerId, options)
            .getOrElse { throwInternalServerError("从源站获取失败:" + it.message) }
        return PageDto(
            items = items.map { it.asDto() },
            pageNumber = 1,
        )
    }

    suspend fun listReadHistory(
        user: AuthenticatedUser,
        page: Int,
        pageSize: Int,
    ): PageDto<NovelOutlineDto> {
        validatePageNumber(page)
        validatePageSize(pageSize)
        return userRepo
            .listReaderHistoryWebNovel(
                userId = user.id,
                page = page,
                pageSize = pageSize,
            )
            .asDto(pageSize) { it.asDto() }
    }

    suspend fun listFavored(
        user: AuthenticatedUser,
        page: Int,
        pageSize: Int,
        sort: FavoriteListSort,
    ): PageDto<NovelOutlineDto> {
        validatePageNumber(page)
        validatePageSize(pageSize)
        return userRepo
            .listFavoriteWebNovel(
                userId = user.id,
                page = page,
                pageSize = pageSize,
                sort = sort,
            )
            .asDto(pageSize) { it.asDto() }
    }

    // Get
    private fun throwNovelNotFound(): Nothing =
        throwNotFound("小说不存在")

    private fun validateId(providerId: String, novelId: String) {
        if (providerId == Syosetu.id && novelId != novelId.lowercase()) {
            throw BadRequestException("成为小说家id应当小写")
        }
    }

    @Serializable
    data class NovelTocItemDto(
        val titleJp: String,
        val titleZh: String?,
        val chapterId: String?,
        val createAt: Long?,
    )

    private fun WebNovelTocItem.asDto() =
        NovelTocItemDto(
            titleJp = titleJp,
            titleZh = titleZh,
            chapterId = chapterId,
            createAt = createAt?.epochSeconds,
        )

    @Serializable
    data class NovelDto(
        val wenkuId: String?,
        val titleJp: String,
        val titleZh: String?,
        val authors: List<WebNovelAuthor>,
        val type: WebNovelType?,
        val attentions: List<WebNovelAttention>,
        val keywords: List<String>,
        val introductionJp: String,
        val introductionZh: String?,
        val glossary: Map<String, String>,
        val toc: List<NovelTocItemDto>,
        val visited: Long,
        val syncAt: Long,
        val favored: Boolean?,
        val lastReadChapterId: String?,
        val jp: Long,
        val baidu: Long,
        val youdao: Long,
        val gpt: Long,
    )

    private suspend fun buildNovelDto(
        novel: WebNovelMetadata,
        user: AuthenticatedUser?,
    ): NovelDto {
        val novelId = novel.id.toHexString()
        val favored = user?.id?.let { userRepo.isUserFavoriteWebNovel(it, novelId) }
        val history = user?.id?.let { userRepo.getReaderHistory(it, novelId) }
        return NovelDto(
            wenkuId = novel.wenkuId,
            titleJp = novel.titleJp,
            titleZh = novel.titleZh,
            authors = novel.authors,
            type = novel.type,
            attentions = novel.attentions,
            keywords = novel.keywords,
            introductionJp = novel.introductionJp,
            introductionZh = novel.introductionZh,
            glossary = novel.glossary,
            toc = novel.toc.map { it.asDto() },
            visited = novel.visited,
            syncAt = novel.syncAt.epochSeconds,
            favored = favored,
            lastReadChapterId = history?.chapterId,
            jp = novel.jp,
            baidu = novel.baidu,
            youdao = novel.youdao,
            gpt = novel.gpt,
        )
    }

    suspend fun getMetadata(
        user: AuthenticatedUser?,
        providerId: String,
        novelId: String,
    ): NovelDto {
        validateId(providerId, novelId)
        val novel = metadataRepo.getNovelAndSave(providerId, novelId)
            .getOrElse { throwInternalServerError("从源站获取失败:" + it.message) }
        val dto = buildNovelDto(novel, user)
        if (user != null) {
            metadataRepo.increaseVisited(
                userIdOrIp = user.id,
                providerId = novel.providerId,
                novelId = novel.novelId,
            )
        }
        return dto
    }

    @Serializable
    data class ChapterDto(
        val titleJp: String,
        val titleZh: String?,
        val prevId: String?,
        val nextId: String?,
        val paragraphs: List<String>,
        val baiduParagraphs: List<String>?,
        val youdaoParagraphs: List<String>?,
        val gptParagraphs: List<String>?,
    )

    suspend fun getChapter(
        providerId: String,
        novelId: String,
        chapterId: String,
    ): ChapterDto {
        validateId(providerId, novelId)
        val novel = metadataRepo.getNovelAndSave(providerId, novelId)
            .getOrElse { throwInternalServerError("从源站获取失败:" + it.message) }

        val toc = novel.toc.filter { it.chapterId != null }
        val currIndex = toc.indexOfFirst { it.chapterId == chapterId }
        if (currIndex == -1) throwInternalServerError("章节不在目录中")

        val chapter = chapterRepo.getOrSyncRemote(providerId, novelId, chapterId)
            .getOrElse { throwInternalServerError("从源站获取失败:" + it.message) }

        return ChapterDto(
            titleJp = toc[currIndex].titleJp,
            titleZh = toc[currIndex].titleZh,
            prevId = toc.getOrNull(currIndex - 1)?.chapterId,
            nextId = toc.getOrNull(currIndex + 1)?.chapterId,
            paragraphs = chapter.paragraphs,
            baiduParagraphs = chapter.baiduParagraphs,
            youdaoParagraphs = chapter.youdaoParagraphs,
            gptParagraphs = chapter.gptParagraphs,
        )
    }

    // Update
    suspend fun updateReadHistory(
        user: AuthenticatedUser,
        providerId: String,
        novelId: String,
        chapterId: String,
    ) {
        val novel = metadataRepo.get(providerId, novelId)
            ?: throwNovelNotFound()
        userRepo.updateReadHistoryWebNovel(
            userId = user.id,
            novelId = novel.id.toHexString(),
            chapterId = chapterId,
        )
    }

    suspend fun updateFavored(
        user: AuthenticatedUser,
        providerId: String,
        novelId: String,
        favored: Boolean,
    ) {
        val novel = metadataRepo.get(providerId, novelId)
            ?: throwNovelNotFound()
        if (favored) {
            val total = userRepo.countFavoriteWebNovelByUserId(
                userId = user.id,
            )
            if (total >= 5000) {
                throwBadRequest("收藏夹已达到上限")
            }
            userRepo.addFavoriteWebNovel(
                userId = user.id,
                novelId = novel.id.toHexString(),
            )
        } else {
            userRepo.removeFavoriteWebNovel(
                userId = user.id,
                novelId = novel.id.toHexString(),
            )
        }
    }

    suspend fun updateMetadata(
        user: AuthenticatedUser,
        providerId: String,
        novelId: String,
        title: String?,
        introduction: String?,
        toc: Map<String, String>,
    ): NovelDto {
        val metadata = metadataRepo.get(providerId, novelId)
            ?: throwNovelNotFound()

        val tocZh = mutableMapOf<Int, String>()
        val tocRecord = mutableListOf<Operation.WebEdit.Toc>()
        metadata.toc.forEachIndexed { index, item ->
            val newTitleZh = toc[item.titleJp]
            if (newTitleZh != null && newTitleZh != item.titleZh) {
                tocZh[index] = newTitleZh
                tocRecord.add(
                    Operation.WebEdit.Toc(
                        jp = item.titleJp,
                        old = item.titleZh,
                        new = newTitleZh,
                    )
                )
            }
        }

        if (title == null && introduction == null && tocZh.isEmpty()) {
            throwBadRequest("修改为空")
        }

        // Add patch
        operationHistoryRepo.create(
            operator = ObjectId(user.id),
            operation = Operation.WebEdit(
                providerId = providerId,
                novelId = novelId,
                old = Operation.WebEdit.Data(
                    titleZh = metadata.titleZh,
                    introductionZh = metadata.introductionZh,
                ),
                new = Operation.WebEdit.Data(
                    titleZh = title,
                    introductionZh = introduction,
                ),
                toc = tocRecord,
            )
        )

        val novel = metadataRepo.updateTranslation(
            providerId = providerId,
            novelId = novelId,
            titleZh = title ?: metadata.titleZh,
            introductionZh = introduction ?: metadata.introductionZh,
            tocZh = tocZh,
        )

        return buildNovelDto(novel!!, user)
    }

    suspend fun updateGlossary(
        user: AuthenticatedUser,
        providerId: String,
        novelId: String,
        glossary: Map<String, String>,
    ) {
        val novel = metadataRepo.get(providerId, novelId)
            ?: throwNovelNotFound()
        if (novel.glossary == glossary)
            throwBadRequest("修改为空")
        metadataRepo.updateGlossary(
            providerId = providerId,
            novelId = novelId,
            glossary = glossary,
        )
        operationHistoryRepo.create(
            operator = ObjectId(user.id),
            operation = Operation.WebEditGlossary(
                providerId = providerId,
                novelId = novelId,
                old = novel.glossary,
                new = glossary,
            )
        )
    }

    suspend fun updateWenkuId(
        user: AuthenticatedUser,
        providerId: String,
        novelId: String,
        wenkuId: String?,
    ) {
        if (wenkuId != null && wenkuMetadataRepo.get(wenkuId) == null)
            throwNotFound("文库版不存在")
        val updateResult = metadataRepo.updateWenkuId(
            providerId = providerId,
            novelId = novelId,
            wenkuId = wenkuId,
        )
        if (updateResult.matchedCount == 0L)
            throwNovelNotFound()
    }

    // Translate
    @Serializable
    data class TranslateTaskDto(
        val title: String? = null,
        val introduction: String? = null,
        val toc: List<String>,
        val glossaryUuid: String?,
        val glossary: Map<String, String>,
        val chapters: List<ChapterIdWithState>,
    ) {
        @Serializable
        data class ChapterIdWithState(
            val id: String,
            val state: ChapterState,
        )

        @Serializable
        enum class ChapterState {
            @SerialName("untranslated")
            Untranslated,

            @SerialName("translated")
            Translated,

            @SerialName("expired")
            TranslatedAndExpired,
        }
    }

    suspend fun getTranslateTask(
        providerId: String,
        novelId: String,
        translatorId: TranslatorId,
    ): TranslateTaskDto {
        validateId(providerId, novelId)

        val novel = metadataRepo.getNovelAndSave(providerId, novelId, 10)
            .getOrElse { throwInternalServerError("从源站获取失败:" + it.message) }

        val title = novel.titleJp.takeIf { novel.titleZh == null }
        val introduction = novel.introductionJp.takeIf { novel.introductionZh == null }
        val toc = novel.toc
            .mapNotNull { tocItem -> tocItem.titleJp.takeIf { tocItem.titleZh == null } }
            .distinct()

        val chapterTranslationOutlines = chapterRepo.getTranslationOutlines(
            providerId = providerId,
            novelId = novelId,
            translatorId = translatorId,
        )
        val chapters = novel.toc
            .mapNotNull { it.chapterId }
            .map { chapterId ->
                val chapterTranslationOutline = chapterTranslationOutlines.find {
                    it.chapterId == chapterId
                }
                val chapterState = if (chapterTranslationOutline?.translated != true) {
                    TranslateTaskDto.ChapterState.Untranslated
                } else if (chapterTranslationOutline.glossaryUuid == novel.glossaryUuid) {
                    TranslateTaskDto.ChapterState.Translated
                } else {
                    TranslateTaskDto.ChapterState.TranslatedAndExpired
                }
                TranslateTaskDto.ChapterIdWithState(
                    id = chapterId,
                    state = chapterState,
                )
            }

        return TranslateTaskDto(
            title = title,
            introduction = introduction,
            toc = toc,
            glossaryUuid = novel.glossaryUuid,
            glossary = novel.glossary,
            chapters = chapters,
        )
    }

    suspend fun updateMetadataTranslation(
        providerId: String,
        novelId: String,
        title: String?,
        introduction: String?,
        toc: Map<String, String>,
    ) {
        val metadata = metadataRepo.get(providerId, novelId)
            ?: return

        val titleZh = title.takeIf { metadata.titleZh == null }
        val introductionZh = introduction.takeIf { metadata.introductionZh == null }
        val tocZh = mutableMapOf<Int, String>()
        metadata.toc.forEachIndexed { index, item ->
            if (item.titleZh == null) {
                val newTitleZh = toc[item.titleJp]
                if (newTitleZh != null) {
                    tocZh[index] = newTitleZh
                }
            }
        }

        if (titleZh == null &&
            introductionZh == null &&
            tocZh.isEmpty()
        ) return

        metadataRepo.updateTranslation(
            providerId = providerId,
            novelId = novelId,
            titleZh = title ?: metadata.titleZh,
            introductionZh = introduction ?: metadata.introductionZh,
            tocZh = tocZh,
        )
    }

    suspend fun checkIfChapterNeedTranslate(
        providerId: String,
        novelId: String,
        translatorId: TranslatorId,
        chapterId: String,
        sync: Boolean,
    ): List<String> {
        val novel = metadataRepo.get(providerId, novelId)
            ?: throwNovelNotFound()

        val chapter = chapterRepo.getOrSyncRemote(
            providerId = providerId,
            novelId = novelId,
            chapterId = chapterId,
            forceSync = sync,
        ).getOrElse { throwInternalServerError("从源站获取失败:" + it.message) }

        val (oldGlossaryUuid, translated) = chapter.run {
            when (translatorId) {
                TranslatorId.Baidu -> Pair(baiduGlossaryUuid, baiduParagraphs)
                TranslatorId.Youdao -> Pair(youdaoGlossaryUuid, youdaoParagraphs)
                TranslatorId.Gpt -> Pair(gptGlossaryUuid, gptParagraphs)
            }
        }

        return if (
            translated != null &&
            oldGlossaryUuid == novel.glossaryUuid
        ) {
            emptyList()
        } else {
            chapter.paragraphs
        }
    }

    @Serializable
    data class TranslateStateDto(val jp: Long, val zh: Long)

    suspend fun updateChapterTranslation(
        providerId: String,
        novelId: String,
        chapterId: String,
        translatorId: TranslatorId,
        glossaryUuid: String?,
        paragraphsZh: List<String>,
    ): TranslateStateDto {
        val novel = metadataRepo.get(providerId, novelId)
            ?: throwNovelNotFound()
        if (glossaryUuid != novel.glossaryUuid) {
            throwBadRequest("术语表uuid失效")
        }

        val chapter = chapterRepo.get(providerId, novelId, chapterId)
            ?: throwNotFound("章节不存在")
        if (chapter.paragraphs.size != paragraphsZh.size) {
            throwBadRequest("翻译文本长度不匹配")
        }

        val zh = chapterRepo.updateTranslation(
            providerId = providerId,
            novelId = novelId,
            chapterId = chapterId,
            translatorId = translatorId,
            glossary = novel.glossaryUuid?.let { Glossary(it, novel.glossary) },
            paragraphsZh = paragraphsZh,
        )
        return TranslateStateDto(jp = novel.jp, zh = zh)
    }

    // File
    suspend fun updateFile(
        providerId: String,
        novelId: String,
        lang: NovelFileLang,
        type: NovelFileType,
    ): Path {
        return fileRepo.makeFile(
            providerId = providerId,
            novelId = novelId,
            lang = lang,
            type = type,
        ) ?: throwNovelNotFound()
    }
}
