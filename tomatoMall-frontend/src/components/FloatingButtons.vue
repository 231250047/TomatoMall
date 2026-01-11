<template>
  <div v-if="showFloating" class="floating-buttons">
    <!-- 回首页按钮（在 /home 隐藏） -->
    <button v-if="route.path !== '/home'" class="float-btn" @click="goHome" title="回首页">
      <img :src="homeIcon" class="home-icon" alt="home" />
      <span class="text">首页</span>
    </button>
    <!-- 卖书按钮 -->
    <button v-if="isAdmin" class="float-btn" @click="goToCreateProduct" title="上新" >
      <img :src="sellIcon" class="sell-icon" alt="卖书" />
      <span class="text">上新</span>
    </button>
    
    <!-- 消息按钮 -->
    <button class="float-btn" @click="toggleMessages" title="消息">
      <img :src="messageIcon" class="message-icon" alt="消息" />
      <span class="text">消息</span>
      <span v-if="unreadCount > 0" class="badge">{{ unreadCount }}</span>
    </button>
    
    <!-- AI助手按钮 -->
    <button class="float-btn" @click="toggleAiChat" title="AI助手">
      <img :src="aiIcon" class="ai-icon" alt="AI" />
      <span class="text">AI助手</span>
    </button>
    
    <!-- 回到顶部按钮 -->
    <button class="float-btn" @click="scrollToTop" title="回到顶部" v-show="showBackToTop">
      <img :src="topIcon" class="top-icon" alt="回到顶部" />
      <span class="text">回顶部</span>
    </button>
  </div>
  
  <!-- 消息面板 -->
  <div v-if="showMessages" class="messages-panel">
    <div class="messages-header">
      <h3>消息中心</h3>
      <button @click="toggleMessages" class="close-btn">✕</button>
    </div>
    <div class="messages-content">
      <!-- 列表视图 -->
      <div v-if="!selectedMessage">
        <div v-if="messages.length === 0" class="empty-messages">
          <span class="empty-icon">📭</span>
          <p>暂无消息</p>
        </div>
        <div v-else class="message-list">
          <div 
            v-for="message in messages" 
            :key="message.id" 
            class="message-item"
            :class="{ unread: !message.read }"
            @click="openMessage(message)"
          >
            <div class="message-avatar">
              <img :src="message.senderAvatar" :alt="message.senderName" />
            </div>
            <div class="message-info">
              <div class="message-sender">{{ message.senderName }}</div>
              <div class="message-text">{{ message.content }}</div>
              <div class="message-time">{{ message.time }}</div>
            </div>
          </div>
        </div>
      </div>
      <!-- 详情视图 -->
      <div v-else class="message-detail">
        <div class="detail-header">
          <button class="back-link" @click="selectedMessage = null">← 返回</button>
          <div class="detail-sender">{{ selectedMessage.senderName }}</div>
          <div class="detail-time">{{ selectedMessage.time }}</div>
        </div>
        <div class="detail-body">
          <div class="detail-content">{{ selectedMessage.content }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import aiIcon from '@/assets/images/ai.png'
import sellIcon from '@/assets/images/sell.png'
import messageIcon from '@/assets/images/message.png'
import topIcon from '@/assets/images/top.png'
import homeIcon from '@/assets/images/home.png'
import { ref as __ref } from 'vue'; // ...existing code uses ref already
const route = useRoute();
// 当路由为登录或注册时隐藏浮动按钮
const showFloating = computed(() => {
  const p = route.path || '';
  // 在首页与登录页不展示悬浮栏
  return !(p === '/' || p === '/login' || p === '/register');
});

const isAdmin = sessionStorage.getItem('role') === 'admin';

const router = useRouter();
const emit = defineEmits(['toggle-ai']);
const showBackToTop = ref(false);
const showMessages = ref(false);

// 死数据列表（messageList），并用作 messages 的初始数据
const messageList = [
  {
    id: 1,
    senderName: '张三',
    senderAvatar: 'https://ui-avatars.com/api/?name=张三&background=3b82f6&color=fff',
    content: '你好，这本书还在吗？想问一下书的具体成色和是否包邮。',
    time: '2分钟前',
    read: false
  },
  {
    id: 2,
    senderName: '李四',
    senderAvatar: 'https://ui-avatars.com/api/?name=李四&background=10b981&color=fff',
    content: '请问能否降价一点？我可以当面交易。',
    time: '1小时前',
    read: false
  },
  {
    id: 3,
    senderName: '王五',
    senderAvatar: 'https://ui-avatars.com/api/?name=王五&background=f59e0b&color=fff',
    content: '已支付，请尽快发货，谢谢！',
    time: '昨天',
    read: true
  }
];
const messages = ref([...messageList]);
// 当前打开的消息详情
const selectedMessage = ref(null);

// 未读消息数量
const unreadCount = computed(() => {
  return messages.value.filter(m => !m.read).length+1;
});

// 跳转到发布商品页面
const goToCreateProduct = () => {
  router.push('/create-product');
};

// 切换消息面板
const toggleMessages = () => {
  showMessages.value = !showMessages.value;
};

// 打开消息详情（使用 messageList 的数据）
const openMessage = (message) => {
  // 标记已读并显示详情
  message.read = true;
  selectedMessage.value = message;
};

// 切换 AI 助手显示
const toggleAiChat = () => {
  emit('toggle-ai');
};

// 回到顶部
const scrollToTop = () => {
  window.scrollTo({
    top: 0,
    behavior: 'smooth'
  });
};

// 监听滚动，显示/隐藏回到顶部按钮
const handleScroll = () => {
  showBackToTop.value = window.scrollY > 300;
};

// 跳转首页
const goHome = () => {
  router.push('/home');
};

onMounted(() => {
  window.addEventListener('scroll', handleScroll);
});

onUnmounted(() => {
  window.removeEventListener('scroll', handleScroll);
});
</script>

<style scoped>
.floating-buttons {
  position: fixed;
  right: 24px;
  top: 50%;
  transform: translateY(-50%);
  z-index: 998;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.float-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 68px;
  padding: 12px 8px;
  background: linear-gradient(135deg, #c27935ff 0%, #c27935ff 100%);
  color: white;
  border: none;
  border-radius: 12px;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(145, 82, 36, 0.4);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  font-size: 12px;
  font-weight: 600;
  position: relative;
}

.float-btn:hover {
  transform: translateX(-8px) scale(1.05);
  box-shadow: 0 8px 20px rgba(125, 74, 38, 0.6);
  background: linear-gradient(135deg, #8c4f23ff 0%, #8c4f23ff 100%);
}

.float-btn:active {
  transform: translateX(-8px) scale(0.98);
}

.float-btn .icon {
  font-size: 24px;
  margin-bottom: 4px;
  line-height: 1;
}

.float-btn .text {
  font-size: 11px;
  line-height: 1.2;
  white-space: nowrap;
}

.badge {
  position: absolute;
  top: 4px;
  right: 4px;
  background: #E04E3A; /* 枫叶红（统一亮色） */
  color: white;
  border-radius: 10px;
  padding: 2px 6px;
  font-size: 10px;
  font-weight: 700;
  min-width: 18px;
  text-align: center;
}

/* 消息面板 */
.messages-panel {
  position: fixed;
  right: 110px;
  top: 50%;
  transform: translateY(-50%);
  width: 360px;
  max-height: 600px;
  background: white;
  border-radius: 16px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.12);
  z-index: 999;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.messages-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px;
  background: #c27935ff;
  color: white;
}

.messages-header h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

.close-btn {
  background: none;
  border: none;
  color: white;
  font-size: 24px;
  cursor: pointer;
  padding: 0;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  transition: background 0.3s;
}

.close-btn:hover {
  background: rgba(255, 255, 255, 0.2);
}

.messages-content {
  flex: 1;
  overflow-y: auto;
}

.empty-messages {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  color: #9ca3af;
}

.empty-icon {
  font-size: 64px;
  margin-bottom: 16px;
}

.empty-messages p {
  margin: 0;
  font-size: 15px;
}

.message-list {
  padding: 0;
}

.message-item {
  display: flex;
  gap: 12px;
  padding: 16px 20px;
  cursor: pointer;
  transition: background 0.3s;
  border-bottom: 1px solid #f3f4f6;
}

.message-item:hover {
  background: #f9fafb;
}

.message-item.unread {
  background: #eff6ff;
}

.message-item.unread:hover {
  background: #dbeafe;
}

.message-avatar {
  flex-shrink: 0;
}

.message-avatar img {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  object-fit: cover;
}

.message-info {
  flex: 1;
  min-width: 0;
}

.message-sender {
  font-size: 14px;
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 4px;
}

.message-text {
  font-size: 13px;
  color: #6b7280;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-bottom: 4px;
}

.message-time {
  font-size: 12px;
  color: #9ca3af;
}

.ai-icon {
  width: 36px;
  height: 36px;
  object-fit: contain;
  margin-bottom: 2px;
  display: inline-block;
  line-height: 1;
}

.sell-icon {
  width: 36px;
  height: 36px;
  object-fit: contain;
  margin-bottom: 2px;
  display: inline-block;
  line-height: 1;
}

.message-icon {
  width: 36px;
  height: 36px;
  object-fit: contain;
  margin-bottom: 2px;
  display: inline-block;
  line-height: 1;
}

.top-icon {
  width: 36px;
  height: 36px;
  object-fit: contain;
  margin-bottom: 2px;
  display: inline-block;
  line-height: 1;
}

.float-btn img,
.float-btn .ai-icon,
.float-btn .sell-icon {
  transition: filter 0.22s ease, opacity 0.22s ease, transform 0.22s ease;
}

.float-btn:hover img,
.float-btn:hover .ai-icon,
.float-btn:hover .sell-icon {
  filter: brightness(0.78);
  opacity: 0.95;
  transform: translateX(-2px);
}

.float-btn:hover .message-icon {
  filter: brightness(0.78);
  opacity: 0.95;
  transform: translateX(-2px);
}

.home-icon {
  width: 36px;
  height: 36px;
  object-fit: contain;
  display: inline-block;
  margin-bottom: 2px;
}

@media (max-width: 768px) {
  .float-btn img,
  .float-btn .ai-icon,
  .float-btn .sell-icon {
    transition: filter 0.18s ease, opacity 0.18s ease;
  }
  
  .floating-buttons {
    right: 12px;
    gap: 8px;
  }
  
  .float-btn {
    width: 56px;
    padding: 10px 6px;
  }
  
  .float-btn .icon {
    font-size: 20px;
  }
  
  .float-btn .text {
    font-size: 10px;
  }
  
  .messages-panel {
    right: 80px;
    width: calc(100vw - 100px);
    max-width: 340px;
  }
  
  .ai-icon,
  .sell-icon,
  .message-icon,
  .top-icon,
  .home-icon {
    width: 28px;
    height: 28px;
    margin-bottom: 2px;
  }
}

/* 详情面板样式 */
.message-detail {
  padding: 16px;
}
.detail-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.back-link {
  background: none;
  border: none;
  color: #6B3E1E;
  font-weight: 700;
  cursor: pointer;
}
.detail-sender {
  font-size: 16px;
  font-weight: 700;
  color: #1f2937;
}
.detail-time {
  margin-left: auto;
  font-size: 12px;
  color: #9ca3af;
}
.detail-body {
  background: #fffaf0;
  padding: 14px;
  border-radius: 8px;
  border: 1px solid #efe6db;
}
.detail-content {
  white-space: pre-wrap;
  color: #374151;
}
</style>
