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

  <skeleton-list
    v-if="messages.loading && !messages.items.length"
    :count="5"
    :avatar="true"
    :lines="2"
  />
  <empty-state
    v-else-if="!messages.items.length"
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
      :class="{ unread: !msg.isRead, important: isImportant(msg) }"
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
      <span v-if="typeLabelOf(msg.type)" class="tag" :class="tagClass(msg.type)">{{ typeLabelOf(msg.type) }}</span>
    </li>
  </ul>

  <Pager
    :page="messages.page"
    :total="messages.total"
    :size="messages.size"
    :loading="messages.loading"
    @go="onMessagePage"
  />
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

    /**
     * 需要用户特别留意的消息（涉及钱、需要处理）。
     *
     * 为什么按关键词而不是按 type：后端把「订单已发出」和「退款已到账 / 售后未通过」
     * 都发成 TYPE_ORDER，只看类型分不出轻重；而这几类漏看代价最大。
     * 命中后整条加红色左边条 + 浅红底 + 标题变红（与小程序同一套规则）。
     */
    const IMPORTANT_RE = /退款|售后|未通过|失败|异常|缺货/;
    const isImportant = (msg) =>
      IMPORTANT_RE.test(`${msg.title || ''} ${msg.content || ''}`);

    /** 类型标签配色：系统=橙、会员=紫、优惠券=金，订单等保持默认 */
    const tagClass = (type) => {
      if (type === 'SYSTEM') return 'tag-sys';
      if (type === 'MEMBER') return 'tag-member';
      if (type === 'COUPON') return 'tag-coupon';
      return '';
    };

    const onMessagePage = (page) => {
      appCtx.loadMessagesPage(page);
      window.scrollTo({ top: 0, behavior: 'smooth' });
    };
    return { ...appCtx, filters, iconOf, typeLabelOf, isImportant, tagClass, onMessagePage };
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

/* ===== 消息分级配色（2026-11-20）=====
   原来所有消息一个样、类型标签统一灰色，「退款已到账」「售后未通过」这类
   涉及钱的消息混在普通订单通知里，很容易被划过去。 */
/* 醒目消息：左侧红条 + 浅红底 + 标题红色 */
.msg-row.important {
  background: #FEF6F5;
  border-left: 4px solid #C0392B;
  padding-left: 12px;
}
.msg-row.important strong { color: #C0392B; }
.msg-row.important .tag {
  color: #C0392B;
  border-color: #C0392B;
  background: #FDECEA;
}
/* 类型标签分色：系统=橙（要读）、会员=紫（权益）、优惠券=金（好处） */
.tag.tag-sys {
  color: #B45309;
  border-color: #B45309;
  background: #FEF6E7;
}
.tag.tag-member {
  color: #7C3AED;
  border-color: #7C3AED;
  background: #F5F0FE;
}
.tag.tag-coupon {
  color: #B7791F;
  border-color: #B7791F;
  background: #FDF7E7;
}
</style>
