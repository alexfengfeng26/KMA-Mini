<script setup lang="ts">
import { validityMeta } from '../domain/partyKnowledge'

interface CitationCardData {
  chunkId: number
  docId: number
  docTitle: string
  documentNumber?: string
  issuingAuthority?: string
  validityStatus?: string
  chunkIndex: number
  section?: string
  content: string
}

defineProps<{ citation: CitationCardData }>()
defineEmits<{ open: [citation: CitationCardData] }>()
</script>

<template>
  <button class="portal-citation" @click="$emit('open', citation)">
    <span class="citation-heading">
      <strong>{{ citation.docTitle }}</strong>
      <span>
        {{ citation.documentNumber || '无文号' }} · {{ citation.issuingAuthority || '机关待补充' }} ·
        {{ validityMeta(citation.validityStatus).label }}
      </span>
    </span>
    <span class="citation-content">{{ citation.content }}</span>
    <i>{{ citation.section || `第 ${citation.chunkIndex + 1} 节` }} →</i>
  </button>
</template>
