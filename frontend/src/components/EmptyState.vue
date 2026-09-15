<template>
  <div class="empty-state">
    <div class="es-icon">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
        <template v-if="icon === 'ticket'">
          <path d="M3 9V7a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v2a2 2 0 0 0 0 4v2a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-2a2 2 0 0 0 0-4Z" />
          <path d="M12 7v10" stroke-dasharray="2.5 3" />
        </template>
        <template v-else-if="icon === 'receipt'">
          <path d="M6 2h12a1 1 0 0 1 1 1v19l-3.5-2.5L12 22l-3.5-2.5L5 22V3a1 1 0 0 1 1-1z" />
          <path d="M9 8h6M9 12h6" />
        </template>
        <template v-else-if="icon === 'star'">
          <path d="M12 3.5l2.6 5.3 5.9.9-4.3 4.1 1 5.8-5.2-2.7-5.2 2.7 1-5.8-4.3-4.1 5.9-.9z" />
        </template>
        <template v-else-if="icon === 'heart'">
          <path d="M12 21s-7-4.9-7-10.2A4.3 4.3 0 0 1 12 7.9 4.3 4.3 0 0 1 19 10.8C19 16.1 12 21 12 21z" />
        </template>
        <template v-else-if="icon === 'bell'">
          <path d="M18 9a6 6 0 1 0-12 0c0 5-2 6-2 6h16s-2-1-2-6" />
          <path d="M10.5 19a1.8 1.8 0 0 0 3 0" />
        </template>
        <template v-else>
          <path d="M2 3h3l2.4 11.2a1.8 1.8 0 0 0 1.8 1.4h8.5a1.8 1.8 0 0 0 1.8-1.4L21.5 7H6" />
          <circle cx="9" cy="20" r="1.4" /><circle cx="18" cy="20" r="1.4" />
        </template>
      </svg>
    </div>
    <p class="es-text">{{ text }}</p>
    <button v-if="actionText" class="ghost" @click="$emit('action')">{{ actionText }}</button>
  </div>
</template>

<script>
export default {
  name: 'EmptyState',
  props: {
    icon: { type: String, default: 'cart' },
    text: { type: String, required: true },
    actionText: { type: String, default: '' },
  },
  emits: ['action'],
};
</script>
