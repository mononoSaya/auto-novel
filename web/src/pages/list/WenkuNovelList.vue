<script lang="ts" setup>
import {
  ApiWenkuNovel,
  WenkuNovelOutlineDto,
} from '@/data/api/api_wenku_novel';
import { Page } from '@/data/api/common';

import { Loader } from './components/NovelList.vue';

const options = [
  {
    label: '分级',
    tags: ['一般向', 'R18'],
  },
];

const loader: Loader<Page<WenkuNovelOutlineDto>> = (page, query, selected) =>
  ApiWenkuNovel.listNovel({ page, pageSize: 24, query, level: selected[0] });
</script>

<template>
  <ListLayout>
    <n-h1>文库小说</n-h1>
    <RouterNA to="/wenku-edit">新建文库小说</RouterNA>
    <NovelList
      :search="true"
      :options="options"
      :loader="loader"
      v-slot="{ page }"
    >
      <NovelListWenku :items="page.items" />
    </NovelList>
  </ListLayout>
</template>

<style scoped>
.n-card-header__main {
  text-overflow: ellipsis;
}
</style>
