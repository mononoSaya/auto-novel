package infra.provider.providers

import infra.client
import infra.web.providers.Pixiv
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class PixivTest : DescribeSpec({
    val provider = Pixiv(client)

    describe("getMetadata") {
        it("常规") {
            // https://www.pixiv.net/novel/series/9406879
            val metadata = provider.getMetadata("9406879")
            metadata.title.shouldBe("メス堕ちシリーズ")
            metadata.authors.first().name.shouldBe("から")
            metadata.authors.first().link.shouldBe("https://www.pixiv.net/users/46135214")
            metadata.introduction.shouldBe(
                "男子高校生が振った同級生の友達から復讐されるお話です。\n" +
                        "TSF、女装無理な方はブラウザバック推奨です。"
            )
            metadata.toc[0].title.shouldBe("女装→メス堕ち→TSF")
            metadata.toc[0].chapterId.shouldBe("18304868")
            metadata.toc[1].title.shouldBe("女装→メス堕ち→TSF")
            metadata.toc[1].chapterId.shouldBe("18457702")
        }
        it("常规，非R18") {
            // https://www.pixiv.net/novel/series/10539710
            val metadata = provider.getMetadata("10539710")
        }
        it("常规，目录很多页") {
            // https://www.pixiv.net/novel/series/870363
            val metadata = provider.getMetadata("870363")
            metadata.toc[0].title.shouldBe("【番外編】強運少女は夢をかなえた？")
            metadata.toc[0].chapterId.shouldBe("8592479")
        }
        it("短篇") {
            // https://www.pixiv.net/novel/show.php?id=19776346
            val metadata = provider.getMetadata("s19776346")
            metadata.title.shouldBe("メカクレ青年が全自動矯正機で熟女にとりさんにされる話")
            metadata.authors.first().name.shouldBe("たれ")
            metadata.authors.first().link.shouldBe("https://www.pixiv.net/users/60498514")
            metadata.toc[0].title.shouldBe("无名")
            metadata.toc[0].chapterId.shouldBe("19776346")
        }
    }

    describe("getEpisode") {
        it("常规") {
            // https://www.pixiv.net/novel/show.php?id=18304868
            val episode = provider.getChapter("9406879", "18304868")
            episode.paragraphs.first().shouldBe("「私と付き合ってください。」")
            episode.paragraphs.last().shouldBe("俺はそれに従うしかなかった。")
        }
        it("常规，多页") {
            // https://www.pixiv.net/novel/show.php?id=18199707
            val episode = provider.getChapter("870363", "18199707")
            episode.paragraphs.first().shouldBe("　｢ふわぁ～あ～……久々に凄い暇で眠いな～……｣")
            episode.paragraphs.last().shouldBe("　風香の叫びが青空に響いた。")
        }
    }
})
