<script setup lang="ts">
import { computed } from 'vue'
import { inlineKmaTags, parseKmaMarkup } from './kmaMarkup'

const props = defineProps<{ source: string }>()
const blocks = computed(() => parseKmaMarkup(props.source))
const segmented = (value: string) => inlineKmaTags(value)
</script>

<template>
  <div class="kma-markup">
    <template v-for="(block, blockIndex) in blocks" :key="`${block.type}-${blockIndex}`">
      <p v-if="block.type === 'paragraph'">
        <template v-for="(segment, index) in segmented(block.text)" :key="index">
          <span v-if="segment.type === 'text'">{{ segment.value }}</span>
          <span
            v-else-if="segment.type === 'badge'"
            class="kma-markup__badge"
            :data-tone="segment.tone || 'info'"
            >{{ segment.value }}</span
          >
          <a v-else class="kma-markup__download" :href="segment.asset || '#'" @click.prevent>{{
            segment.value
          }}</a>
        </template>
      </p>
      <aside v-else-if="block.type === 'callout'" class="kma-markup__callout" :data-tone="block.tone">
        {{ block.text }}
      </aside>
      <ol v-else-if="block.type === 'steps'" class="kma-markup__steps">
        <li v-for="item in block.items" :key="item">{{ item }}</li>
      </ol>
      <div v-else class="kma-markup__faq">
        <details v-for="item in block.items" :key="item.question">
          <summary>{{ item.question }}</summary>
          <p>{{ item.answer }}</p>
        </details>
      </div>
    </template>
  </div>
</template>
