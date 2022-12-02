from abc import ABC, abstractmethod
import copy
from typing import List

from app.cache import BookCache
from app.model import BookMetadata, Episode


class Translator(ABC):
    translator_id: str

    def __init__(self, from_lang: str, to_lang: str) -> None:
        self.from_lang = from_lang
        self.to_lang = to_lang

    @abstractmethod
    def _translate(
        self,
        query_list: List[str],
    ) -> List[str]:
        pass

    def _translate_metadata(
        self,
        metadata: BookMetadata,
    ) -> BookMetadata:
        metadata = copy.deepcopy(metadata)

        query_list = metadata.to_query_list()
        result_list = self._translate(query_list=query_list)
        assert len(query_list) == len(result_list)
        metadata.apply_translated_result(result_list)

        return metadata

    def _translate_episode(
        self,
        episode: Episode,
    ) -> Episode | None:
        query_list = episode.paragraphs
        result_list = self._translate(query_list=query_list)
        return Episode(paragraphs=result_list)

    def translate_metadata(
        self,
        metadata: BookMetadata,
        cache: BookCache,
    ) -> BookMetadata:
        translated_metadata = cache.get_book_metadata(
            lang=self.to_lang,
        )
        if not translated_metadata:
            translated_metadata = self._translate_metadata(
                metadata=metadata,
            )
            cache.save_book_metadata(
                lang=self.to_lang,
                metadata=translated_metadata,
            )
        return translated_metadata

    def translate_episode(
        self,
        episode_id: str,
        episode: Episode | None,
        cache: BookCache,
        allow_request: bool,
    ) -> Episode | None:
        translated_episode = cache.get_episode(
            lang=self.to_lang,
            episode_id=episode_id,
        )
        if not translated_episode and episode and allow_request:
            translated_episode = self._translate_episode(
                episode=episode,
            )
            cache.save_episode(
                lang=self.to_lang,
                episode_id=episode_id,
                episode=translated_episode,
            )
        return translated_episode
