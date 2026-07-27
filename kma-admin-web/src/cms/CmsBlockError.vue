<template>
  <section class="cms-block-error" role="status">
    <strong>此门户区块暂时无法显示</strong>
    <span>其他内容不受影响，请刷新页面重试。</span>
  </section>
</template>

<style scoped>
.cms-block-error {
  display: grid;
  gap: 4px;
  padding: 16px;
  border: 1px solid var(--kma-color-border);
  border-radius: var(--kma-radius-card);
  background: var(--kma-color-surface);
  color: var(--kma-color-text-muted);
  font-size: 13px;
}

.cms-block-error strong {
  color: var(--kma-color-text);
}
</style>
