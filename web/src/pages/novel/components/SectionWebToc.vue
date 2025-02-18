<script lang="ts" setup>
import { computed } from 'vue';
import { createReusableTemplate } from '@vueuse/core';
import { NA, NText, useThemeVars } from 'naive-ui';

import { WebNovelTocItemDto } from '@/data/api/api_web_novel';
import { useIsDesktop } from '@/data/util';

const [DefineTocItem, ReuseTocItem] = createReusableTemplate<{
  item: {
    index?: number;
    titleJp: string;
    titleZh?: string;
    chapterId?: string;
    createAt?: number;
  };
}>();

const props = defineProps<{
  reverse: boolean;
  providerId: string;
  novelId: string;
  toc: WebNovelTocItemDto[];
  lastReadChapterId?: string;
}>();

const isDesktop = useIsDesktop(600);

const readableToc = computed(() => {
  const newToc = [];
  let index = 0;
  for (const it of props.toc) {
    newToc.push({
      index: it.chapterId ? index : undefined,
      titleJp: it.titleJp,
      titleZh: it.titleZh,
      chapterId: it.chapterId,
      createAt: it.createAt,
    });
    if (it.chapterId) {
      index += 1;
    }
  }
  return newToc;
});
const reverseToc = computed(() => readableToc.value.slice().reverse());
const lastReadTocItem = computed(() => {
  if (props.lastReadChapterId) {
    return readableToc.value.find(
      (it) => it.chapterId === props.lastReadChapterId
    );
  } else {
    return undefined;
  }
});

const vars = useThemeVars();
</script>

<template>
  <DefineTocItem v-slot="{ item }">
    <component
      :is="item.chapterId ? NA : NText"
      :href="`/novel/${providerId}/${novelId}/${item.chapterId}`"
    >
      <div v-if="isDesktop" style="width: 100; display: flex; padding: 6px">
        <span style="flex: 1 1 0">{{ item.titleJp }}</span>
        <n-text depth="3" style="flex: 1 1 0">{{ item.titleZh }}</n-text>
        <n-text depth="3" style="width: 155px; text-align: right">
          <template v-if="item.createAt">
            <n-time :time="item.createAt * 1000" format="yyyy-MM-dd HH:mm" />
            [{{ item.index }}]
          </template>
        </n-text>
      </div>

      <div v-else style="width: 100; padding: 6px">
        {{ item.titleJp }}
        <br />
        <n-text depth="3">
          {{ item.titleZh }}
          <template v-if="item.createAt">
            <br />
            <n-time :time="item.createAt * 1000" format="yyyy-MM-dd HH:mm" />
            [{{ item.index }}]
          </template>
        </n-text>
      </div>
    </component>
  </DefineTocItem>

  <n-list style="background-color: #0000">
    <n-card
      v-if="lastReadTocItem"
      :bordered="false"
      embedded
      style="margin-bottom: 8px"
      content-style="padding: 6px 0px 0px;"
    >
      <b style="padding-left: 6px">上次读到:</b>
      <ReuseTocItem :item="lastReadTocItem" />
    </n-card>
    <n-list-item
      v-for="tocItem in reverse ? reverseToc : readableToc"
      style="padding: 0px"
    >
      <ReuseTocItem :item="tocItem" />
    </n-list-item>
  </n-list>
</template>

<style scoped>
a:visited {
  color: color-mix(in srgb, v-bind('vars.primaryColor') 50%, red);
}
</style>
