package api

import api.plugins.*
import infra.common.OperationHistoryRepository
import infra.model.Operation
import infra.model.UserOutline
import infra.model.WebNovelTocItem
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.resources.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

@Resource("/operation-history")
private class OperationHistoryRes {
    @Resource("")
    class List(
        val parent: OperationHistoryRes,
        val page: Int,
        val pageSize: Int,
        val type: String,
    )

    @Resource("/{id}")
    class Id(val parent: OperationHistoryRes, val id: String)

    // Wait for deprecate
    @Resource("/toc-merge")
    class TocMergeHistory(val parent: OperationHistoryRes) {
        @Resource("/")
        class List(val parent: TocMergeHistory, val page: Int)

        @Resource("/{id}")
        class Id(val parent: TocMergeHistory, val id: String)
    }
}

fun Route.routeOperationHistory() {
    val api by inject<OperationHistoryApi>()

    get<OperationHistoryRes.List> { loc ->
        call.tryRespond {
            api.listOperationHistory(
                page = loc.page,
                pageSize = loc.pageSize,
                type = loc.type,
            )
        }
    }
    get<OperationHistoryRes.TocMergeHistory.List> { loc ->
        call.tryRespond {
            api.listTocMergeHistory(
                page = loc.page,
                pageSize = 10,
            )
        }
    }

    authenticateDb {
        delete<OperationHistoryRes.Id> { loc ->
            val user = call.authenticatedUser()
            call.tryRespond {
                api.deleteOperationHistory(user = user, id = loc.id)
            }
        }
        delete<OperationHistoryRes.TocMergeHistory.Id> { loc ->
            val user = call.authenticatedUser()
            call.tryRespond {
                api.deleteTocMergeHistory(user = user, id = loc.id)
            }
        }
    }
}

class OperationHistoryApi(
    private val operationHistoryRepo: OperationHistoryRepository,
) {
    @Serializable
    data class OperationHistoryDto(
        val id: String,
        val operator: UserOutline,
        val operation: Operation,
        val createAt: Long,
    )

    suspend fun listOperationHistory(
        page: Int,
        pageSize: Int,
        type: String,
    ): PageDto<OperationHistoryDto> {
        validatePageNumber(page)
        validatePageSize(pageSize)
        return operationHistoryRepo.list(
            page = page,
            pageSize = pageSize,
            type = type,
        ).asDto(pageSize) {
            OperationHistoryDto(
                id = it.id.toHexString(),
                operator = it.operator,
                operation = it.operation,
                createAt = it.createAt.epochSeconds,
            )
        }
    }

    suspend fun deleteOperationHistory(
        user: AuthenticatedUser,
        id: String,
    ) {
        user.shouldBeAtLeastMaintainer()
        operationHistoryRepo.delete(id)
    }

    // Wait for deprecate
    @Serializable
    data class TocMergeHistoryDto(
        val id: String,
        val providerId: String,
        val novelId: String,
        val tocOld: List<WebNovelApi.NovelTocItemDto>,
        val tocNew: List<WebNovelApi.NovelTocItemDto>,
        val reason: String,
    )

    suspend fun listTocMergeHistory(
        page: Int,
        pageSize: Int,
    ): PageDto<TocMergeHistoryDto> {
        validatePageNumber(page)
        validatePageSize(pageSize)

        fun WebNovelTocItem.asDto() =
            WebNovelApi.NovelTocItemDto(
                titleJp = titleJp,
                titleZh = titleZh,
                chapterId = chapterId,
                createAt = createAt?.epochSeconds,
            )
        return operationHistoryRepo.listMergeHistory(
            page = page,
            pageSize = pageSize,
        ).asDto(pageSize) {
            TocMergeHistoryDto(
                id = it.id.toHexString(),
                providerId = it.providerId,
                novelId = it.novelId,
                tocOld = it.tocOld.map { it.asDto() },
                tocNew = it.tocNew.map { it.asDto() },
                reason = it.reason,
            )
        }
    }

    suspend fun deleteTocMergeHistory(
        user: AuthenticatedUser,
        id: String,
    ) {
        user.shouldBeAtLeastMaintainer()
        operationHistoryRepo.deleteMergeHistory(id)
    }
}
