<script lang="ts" setup>
import { computed } from 'vue';

import { menuOption } from '@/components/MainLayout.vue';
import { useUserDataStore } from '@/data/stores/user_data';

const userData = useUserDataStore();
const menuOptions = computed(() => {
  return [
    menuOption('我的收藏', '/favorite-list', userData.isLoggedIn),
    menuOption('阅读历史', '/read-history', userData.isLoggedIn),
    menuOption('网络小说', '/novel-list'),
    menuOption('文库小说', '/wenku-list'),
    menuOption('成为小说家：流派', '/novel-rank/syosetu/1'),
    menuOption('成为小说家：综合', '/novel-rank/syosetu/2'),
    menuOption('成为小说家：异世界转移/转生', '/novel-rank/syosetu/3'),
    menuOption('Kakuyomu：流派', '/novel-rank/kakuyomu/1'),
  ];
});
</script>

<template>
  <MainLayout :side-menu-options="menuOptions">
    <slot />
  </MainLayout>
</template>
