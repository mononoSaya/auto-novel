<script lang="ts">
export function menuOption(
  text: string,
  href: string,
  show?: boolean
): MenuOption {
  return {
    label: () => h(RouterLink, { to: { path: href } }, { default: () => text }),
    key: href,
    show,
  };
}

export function dropdownOption(
  label: string,
  key: string,
  icon: Component
): MenuOption {
  return {
    label,
    key,
    icon: () => h(NIcon, null, { default: () => h(icon) }),
  };
}
</script>

<script lang="ts" setup>
import { ColorLensFilled, LogOutFilled, MenuFilled } from '@vicons/material';
import { MenuOption, NIcon, useThemeVars } from 'naive-ui';
import { Component, computed, h, ref } from 'vue';
import { RouterLink, useRoute } from 'vue-router';

import { SignInDto } from '@/data/api/api_auth';
import { useSettingStore } from '@/data/stores/setting';
import { useUserDataStore } from '@/data/stores/user_data';
import { useIsDesktop } from '@/data/util';

withDefaults(
  defineProps<{
    sideMenuOptions: MenuOption[];
  }>(),
  { sideMenuOptions: () => [] }
);

const isDesktop = useIsDesktop(850);
const userData = useUserDataStore();
const setting = useSettingStore();
const topMenuOptions = computed(() => {
  return [
    menuOption('首页', '/'),
    menuOption('网络小说', '/novel-list'),
    menuOption('文库小说', '/wenku-list'),
    menuOption('论坛', '/forum'),
    menuOption('工具箱', '/toolbox'),
  ];
});

const path = useRoute().path;
function getTopMenuOptionKey() {
  if (path.startsWith('/forum')) {
    return '/forum';
  } else if (path.startsWith('/wenku')) {
    return '/wenku-list';
  } else if (path.startsWith('/novel')) {
    return '/novel-list';
  } else if (path.startsWith('/toolbox')) {
    return '/toolbox';
  } else {
    return path;
  }
}

const collapsedMenuOptions = computed(() => {
  const signed = userData.info !== undefined;
  return [
    menuOption('首页', '/'),
    menuOption('我的收藏', '/favorite-list', signed),
    menuOption('阅读历史', '/read-history', signed),
    menuOption('网络小说', '/novel-list'),
    menuOption('文库小说', '/wenku-list'),
    {
      label: '排行',
      children: [
        menuOption('成为小说家：流派', '/novel-rank/syosetu/1'),
        menuOption('成为小说家：综合', '/novel-rank/syosetu/2'),
        menuOption('成为小说家：异世界转移/转生', '/novel-rank/syosetu/3'),
        menuOption('Kakuyomu：流派', '/novel-rank/kakuyomu/1'),
      ],
    },
    menuOption('论坛', '/forum'),
    menuOption('工具箱', '/toolbox'),
  ];
});

const userDropdownOptions = computed(() => {
  return [
    { label: '管理员模式', key: 'admin', show: userData.isAdmin },
    menuOption('网站管理', '/admin', userData.asAdmin),
    dropdownOption('切换主题', 'darkTheme', ColorLensFilled),
    dropdownOption('退出登录', 'signOut', LogOutFilled),
  ];
});
function handleUserDropdownSelect(key: string | number) {
  if (key === 'signOut') {
    userData.deleteProfile();
  } else if (key === 'darkTheme') {
    setting.isDark = !setting.isDark;
  } else if (key === 'admin') {
    userData.toggleAdminMode();
  }
}

const showLoginModal = ref(false);

function onSignInSuccess(profile: SignInDto): void {
  userData.setProfile(profile);
  showLoginModal.value = false;
}

const vars = useThemeVars();
</script>

<template>
  <n-layout style="overflow-x: overlay">
    <n-layout-header bordered>
      <div
        class="container"
        style="display: flex; align-items: center; height: 50px"
      >
        <n-popover v-if="!isDesktop" trigger="click" :width="280">
          <template #trigger>
            <n-icon size="24"> <MenuFilled /> </n-icon>
          </template>
          <n-menu :value="path" :options="collapsedMenuOptions" />
        </n-popover>
        <router-link v-if="isDesktop" to="/">
          <n-icon
            size="30"
            :color="vars.primaryColor"
            style="margin-right: 8px; margin-bottom: 8px"
          >
            <svg
              version="1.0"
              xmlns="http://www.w3.org/2000/svg"
              width="512.000000pt"
              height="512.000000pt"
              viewBox="0 0 512.000000 512.000000"
              preserveAspectRatio="xMidYMid meet"
            >
              <g
                transform="translate(0.000000,512.000000) scale(0.100000,-0.100000)"
                stroke="none"
              >
                <path
                  d="M1438 4566 c-65 -34 -120 -64 -123 -67 -2 -3 96 -199 219 -435 l224
-429 -614 -3 -614 -2 0 -400 0 -400 -185 0 -185 0 0 -750 0 -750 185 0 185 0
0 -400 0 -400 2030 0 2030 0 0 400 0 400 185 0 185 0 0 750 0 750 -185 0 -185
0 -2 398 -3 397 -614 3 c-534 2 -612 4 -607 17 3 8 104 204 225 435 l219 420
-124 64 -124 63 -19 -31 c-10 -17 -128 -241 -261 -499 l-243 -467 -481 2 -481
3 -245 470 c-135 259 -252 482 -260 497 l-15 27 -117 -63z m349 -1669 c203
-106 213 -388 18 -498 -187 -105 -416 29 -414 241 1 118 50 199 150 249 59 30
70 32 139 29 43 -3 89 -12 107 -21z m1789 -5 c98 -50 162 -169 151 -279 -23
-224 -292 -330 -461 -182 -62 55 -88 108 -94 192 -8 119 44 213 150 267 58 29
69 31 138 27 50 -2 89 -11 116 -25z m-126 -1372 l0 -280 -890 0 -890 0 0 280
0 280 890 0 890 0 0 -280z"
                />
              </g>
            </svg>
          </n-icon>
        </router-link>
        <div v-if="isDesktop">
          <n-menu
            :value="getTopMenuOptionKey()"
            mode="horizontal"
            :options="topMenuOptions"
          />
        </div>

        <div style="flex: 1"></div>

        <n-space v-if="userData.username">
          <router-link v-if="isDesktop" to="/read-history">
            <n-button quaternary>历史</n-button>
          </router-link>
          <router-link v-if="isDesktop" to="/favorite-list">
            <n-button quaternary>收藏</n-button>
          </router-link>
          <n-dropdown
            trigger="click"
            :options="userDropdownOptions"
            @select="handleUserDropdownSelect"
          >
            <n-button quaternary> @{{ userData.username }} </n-button>
          </n-dropdown>
        </n-space>

        <n-button
          v-else
          quaternary
          style="margin-right: 4px"
          @click="showLoginModal = true"
        >
          登录/注册
        </n-button>
      </div>
    </n-layout-header>

    <n-layout :native-scrollbar="false" style="height: calc(100vh - 51px)">
      <slot name="full-width" />
      <n-layout class="container" style="padding-bottom: 48px" has-sider>
        <n-layout-content>
          <slot />
        </n-layout-content>
        <n-layout-sider
          v-if="sideMenuOptions.length > 0 && isDesktop"
          style="margin-left: 12px"
        >
          <n-menu :value="path" :options="sideMenuOptions" />
        </n-layout-sider>
      </n-layout>
    </n-layout>
  </n-layout>

  <n-modal v-model:show="showLoginModal">
    <n-card
      style="width: min(400px, calc(100% - 16px))"
      :bordered="false"
      size="large"
      role="dialog"
      aria-modal="true"
    >
      <n-tabs
        class="card-tabs"
        default-value="signin"
        size="large"
        animated
        style="margin: 0 -4px"
        pane-style="padding-left: 4px; padding-right: 4px; box-sizing: border-box;"
      >
        <n-tab-pane name="signin" tab="登录">
          <SignInForm @signIn="onSignInSuccess" />
        </n-tab-pane>

        <n-tab-pane name="signup" tab="注册">
          <SignUpForm @signUp="onSignInSuccess" />
        </n-tab-pane>
      </n-tabs>
    </n-card>
  </n-modal>
</template>

<style>
.container {
  max-width: 1000px;
  margin: 0 auto;
  padding-left: 30px;
  padding-right: 30px;
}
@media only screen and (max-width: 600px) {
  .container {
    padding-left: 12px;
    padding-right: 12px;
  }
}
</style>
