<script lang="ts" setup>
import MarkdownIt from 'markdown-it/lib';
import { useThemeVars } from 'naive-ui';

defineProps<{ source: string }>();

const md = new MarkdownIt();
const vars = useThemeVars();
</script>

<template>
  <div class="markdown" v-html="md.render(source)" />
</template>

<style>
.markdown a {
  transition: color 0.3s v-bind('vars.cubicBezierEaseInOut');
  cursor: pointer;
  text-decoration: none;
  color: v-bind('vars.primaryColor');
}
.markdown p {
  transition: color 0.3s v-bind('vars.cubicBezierEaseInOut');
  box-sizing: border-box;
  margin: 16px 0 16px 0;
  font-size: v-bind('vars.fontSize');
  line-height: v-bind('vars.lineHeight');
  color: v-bind('vars.textColor2');
}
.markdown ul,
.markdown ol {
  font-size: v-bind('vars.fontSize');
  padding: 0 0 0 2em;
}
.markdown li {
  transition: color 0.3s v-bind('vars.cubicBezierEaseInOut');
  line-height: v-bind('vars.lineHeight');
  margin: 0.25em 0 0 0;
  margin-bottom: 0;
  color: v-bind('vars.textColor2');
}
.markdown code {
  transition: color 0.3s v-bind('vars.cubicBezierEaseInOut'),
    background-color 0.3s v-bind('vars.cubicBezierEaseInOut'),
    border-color 0.3s v-bind('vars.cubicBezierEaseInOut');
  padding: 0.05em 0.35em 0 0.35em;
  font-size: 0.9em;
  color: v-bind('vars.textColor2');
  background-color: v-bind('vars.codeColor');
  border-radius: v-bind('vars.borderRadiusSmall');
  border: 1px solid #0000;
  line-height: 1.4;
  box-sizing: border-box;
  display: inline-block;
}
.markdown img {
  max-width: 100%;
}
.markdown table {
  border-spacing: 0;
  border-collapse: collapse;
}
.markdown th {
  white-space: nowrap;
  background-color: v-bind('vars.actionColor');
}
.markdown th,
.markdown td {
  padding: 12px;
  border-bottom: 1px solid v-bind('vars.dividerColor');
}
.markdown tr th:not(:last-child),
.markdown td:not(:last-child) {
  border-right: 1px solid v-bind('vars.dividerColor');
}
</style>
