<template>
<section class="data-panel messages-page">
  <div class="msg-filters">
    <button
      v-for="option in filters"
      :key="option.value"
      type="button"
      :class="{ on: messageTypeFilter === option.value }"
      @click="changeMessageFilter(option.value)"
    >{{ option.label }}</button>
    <button v-if="messageUnread > 0" type="button" class="read-all-btn" @click="markMessagesRead">全部已读（{{ messageUnread }}）</button>
  </div>

  <empty-state
    v-if="!messages.items.length && !messages.loading"
    icon="bell"
    text="暂时没有消息，订单动态、会员升级和优惠都会在这里通知你"
    action-text="去逛逛"
    @action="navigate('shop')"
  />

  <ul v-else class="msg-list">
    <li
      v-for="msg in messages.items"
      :key="msg.id"
      class="msg-row"
      :class="{ unread: !msg.isRead }"
      @click="openMessage(msg)"
    >
      <span class="msg-ico">{{ iconOf(msg.type) }}</span>
      <div class="msg-main">
        <strong>
          <span v-if="!msg.isRead" class="msg-dot"></span>
          {{ msg.title }}
        </strong>
        <p v-if="msg.content">{{ msg.content }}</p>
        <small>{{ formatDate(msg.createdAt) }}<template v-if="msg.linkView"> · 点击查看</template></small>
      </div>
      <span v-if="typeLabelOf(msg.type)" class="tag muted">{{ typeLabelOf(msg.type) }}</span>
    </li>
  </ul>

  <button
    v-if="messages.items.length < messages.total"
    class="ghost load-more"
    @click="loadMessages(false)"
    :disabled="messages.loading"
  >{{ messages.loading ? '加载中…' : '加载更多' }}</button>
</section>
</template>

<script>
import { inject } from 'vue';
export default {
  name: 'MessagesPage',
  setup() {
    const appCtx = inject('appCtx');
    const filters = [
      { value: '', label: '全部' },
      { value: 'ORDER', label: '订单' },
      { value: 'MEMBER', label: '会员' },
      { value: 'COUPON', label: '优惠券' },
      { value: 'SYSTEM', label: '系统' },
    ];
    const ICONS = { ORDER: '📦', MEMBER: '⭐', COUPON: '🎟️', SYSTEM: '📢' };
    const LABELS = { ORDER: '订单', MEMBER: '会员', COUPON: '优惠券', SYSTEM: '系统' };
    const iconOf = (type) => ICONS[type] || '🔔';
    const typeLabelOf = (type) => LABELS[type] || '';
    return { ...appCtx, filters, iconOf, typeLabelOf };
  }
};
</script>

<style scoped>
.msg-filters .read-all-btn {
  margin-left: auto;
  border-color: var(--brand-deep);
  background: var(--brand-deep);
  color: #fff;
  font-weight: 700;
}
.load-more { margin: 14px auto 0; display: block; }
</style>
