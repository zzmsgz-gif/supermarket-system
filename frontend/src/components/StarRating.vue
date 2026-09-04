<template>
  <span class="review-stars">
    <span>{{ fullStars }}</span><span class="star-dim">{{ emptyStars }}</span>
  </span>
</template>

<script setup>
import { computed } from 'vue';

const props = defineProps({
  rating: { type: Number, default: 0 },
});

const r = computed(() => Math.max(0, Math.min(5, Number(props.rating) || 0)));
const fullStars = computed(() => '★'.repeat(r.value));
const emptyStars = computed(() => '☆'.repeat(Math.max(5 - r.value, 0)));
</script>
