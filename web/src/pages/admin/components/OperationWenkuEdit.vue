<script lang="ts" setup>
import { isEqual } from 'lodash-es';

import { OperationWenkuEdit } from '@/data/api/api_operation';

defineProps<{ op: OperationWenkuEdit }>();
</script>

<template>
  <RouterNA :to="`/wenku/${op.novelId}`">
    {{ op.novelId }}
  </RouterNA>
  <n-p v-if="op.old?.title !== op.new.title">
    标题：{{ op.old?.title }} => {{ op.new.title }}
  </n-p>
  <n-p v-if="op.old?.titleZh !== op.new.titleZh">
    中文标题：{{ op.old?.titleZh }} => {{ op.new.titleZh }}
  </n-p>
  <n-p v-if="!isEqual(op.old?.authors, op.new.authors)">
    作者：{{ op.old?.authors }} => {{ op.new.authors }}
  </n-p>
  <n-p v-if="!isEqual(op.old?.artists, op.new.artists)">
    插图：{{ op.old?.artists }} => {{ op.new.artists }}
  </n-p>
  <n-p v-if="op.old?.introduction !== op.new.introduction">
    简介：{{ op.old?.introduction }} => {{ op.new.introduction }}
  </n-p>
</template>
