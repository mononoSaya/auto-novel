<script lang="ts" setup>
import { CommentFilled } from '@vicons/material';
import { useMessage } from 'naive-ui';
import { ref, watch } from 'vue';

import { ApiComment, Comment1 } from '@/data/api/api_comment';
import { Page } from '@/data/api/common';
import { Ok, ResultState } from '@/data/result';
import { useUserDataStore } from '@/data/stores/user_data';

const userData = useUserDataStore();
const message = useMessage();

const { site } = withDefaults(
  defineProps<{
    site: string;
    locked: boolean;
  }>(),
  { locked: false }
);

const commentPage = ref<ResultState<Page<Comment1>>>();
const currentPage = ref(1);

async function loadComments(page: number) {
  const result = await ApiComment.listComment({ site, page: page - 1, pageSize: 10 });
  if (result.ok) {
    commentPage.value = Ok({
      ...result.value,
      page,
      items: result.value.items.map((it) => ({ ...it, page: 1 })),
    });
  } else {
    commentPage.value = result;
  }
}

watch(currentPage, (page) => loadComments(page), { immediate: true });

function onReplied() {
  showInput.value = false;
  if (commentPage.value?.ok && currentPage.value === 1) {
    loadComments(currentPage.value);
  }
}

const showInput = ref(false);
function toggleInput() {
  if (!userData.isLoggedIn) {
    message.info('请先登录');
    return;
  }
  showInput.value = !showInput.value;
}
</script>

<template>
  <SectionHeader title="评论">
    <n-button v-if="!locked" @click="toggleInput()">
      <template #icon>
        <n-icon :component="CommentFilled" />
      </template>
      发表评论
    </n-button>
  </SectionHeader>

  <n-p v-if="locked">评论区已锁定，不能再回复。</n-p>

  <template v-if="showInput">
    <CommentInput
      :site="site"
      :placeholder="`发表回复`"
      @replied="onReplied()"
    />
    <n-divider />
  </template>

  <ResultView
    :result="commentPage"
    :showEmpty="(it: Page<Comment1>) => it.items.length === 0 && !locked"
    v-slot="{ value }"
  >
    <template v-for="comment in value.items">
      <Comment :site="site" :comment="comment" :locked="locked" />
      <n-divider />
    </template>

    <n-pagination
      v-if="value.pageNumber > 1"
      v-model:page="currentPage"
      :page-count="value.pageNumber"
      :page-slot="7"
      style="margin-top: 20px"
    />
  </ResultView>
</template>
