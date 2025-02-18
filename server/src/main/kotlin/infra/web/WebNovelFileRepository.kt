package infra.web

import infra.DataSourceMongo
import infra.model.NovelFileLang
import infra.model.NovelFileType
import infra.model.WebNovelChapter
import infra.model.WebNovelMetadata
import kotlinx.datetime.toKotlinInstant
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.*

class WebNovelFileRepository(
    private val mongo: DataSourceMongo,
) {
    private val root = Path("./data/files-web")

    suspend fun makeFile(
        providerId: String,
        novelId: String,
        lang: NovelFileLang,
        type: NovelFileType,
    ): Path? {
        val filePath = root / "${providerId}.${novelId}.${lang.value}.${type.value}"

        val novel = mongo
            .webNovelMetadataCollection
            .findOne(WebNovelMetadata.byId(providerId, novelId))
            ?: return null

        val shouldMake = if (filePath.exists()) {
            val createAt = filePath.readAttributes<BasicFileAttributes>()
                .creationTime()
                .toInstant()
                .toKotlinInstant()
            novel.changeAt > createAt
        } else true


        if (shouldMake) {
            val chapters = novel.toc
                .mapNotNull { it.chapterId }
                .mapNotNull { chapterId ->
                    mongo.webNovelChapterCollection
                        .findOne(WebNovelChapter.byId(providerId, novelId, chapterId))
                        ?.let { chapterId to it }
                }
                .toMap()
            when (type) {
                NovelFileType.EPUB -> makeEpubFile(filePath, lang, novel, chapters)
                NovelFileType.TXT -> makeTxtFile(filePath, lang, novel, chapters)
            }
        }

        return filePath.relativeTo(root)
    }
}