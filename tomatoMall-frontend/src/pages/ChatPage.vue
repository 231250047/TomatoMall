<template>
  <div class="chat-page">
    <!-- 装饰图片（使用 deco_2 和 deco_4） -->
    <img :src="deco2" class="deco-img deco-2" alt="" />
    <img :src="deco4" class="deco-img deco-4" alt="" />

    <div class="chat-header">
      <el-button type="text" @click="$router.back()" class="back-btn" title="返回" aria-label="返回">←</el-button>
      <div class="title">
        <div class="title-main">
          <span class="seller-color">{{ sellerNameDisplay }}</span>
        </div>
      </div>
      <div class="spacer"></div>
    </div>

    <div class="chat-window" ref="chatWindow">
      <div v-for="(m, idx) in messages" :key="idx" :class="['message-row', m.me ? 'me' : 'other']">
        <!-- 他人消息：使用 el-avatar 组件自动处理头像 -->
        <el-avatar v-if="!m.me" :size="40" :src="sellerAvatarDisplay" class="avatar">
          <span style="font-size: 16px; font-weight: 600;">{{ getInitial(sellerInfo.name || sellerId) }}</span>
        </el-avatar>
        
        <div class="bubble" :class="m.me ? 'bubble-me' : 'bubble-other'">
          <div class="msg-text">{{ m.text }}</div>
          <div class="msg-time">{{ m.time }}</div>
        </div>
        
        <!-- 我的消息：使用 el-avatar 组件自动处理头像 -->
        <el-avatar v-if="m.me" :size="40" :src="userAvatarDisplay" class="avatar">
          <span style="font-size: 16px; font-weight: 600;">{{ getInitial(currentUserInfo.name || currentUserInfo.username) }}</span>
        </el-avatar>
      </div>

      <div v-if="messages.length === 0" class="empty-tip">暂无消息，开始聊吧～</div>
    </div>

    <div class="chat-input">
      <el-input
        v-model="input"
        placeholder="输入消息..."
        type="textarea"
        :rows="2"
        class="input-area"
        @keydown.enter.prevent="handleEnter"
      />
      <el-button class="send-btn" @click="send">发送</el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, watch, computed } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getUserInfo, getUserInfoByUsername } from '../api/user'
import deco2 from '@/assets/images/deco_2.jpg'
import deco4 from '@/assets/images/deco_4.jpg'

const route = useRoute()
const sellerId = route.params.sellerId || ''
const messages = ref([])
const input = ref('')
const chatWindow = ref(null)

// 当前用户信息（买家）
const currentUserInfo = ref({
  username: sessionStorage.getItem('username') || '我',
  name: '',
  avatar: ''
})

// 卖家信息
const sellerInfo = ref({
  username: sellerId,
  name: '',
  avatar: ''
})

// 获取用户名首字母（用于默认头像显示）
const getInitial = (name) => {
  if (!name) return '?'
  return String(name)[0].toUpperCase()
}

// 卖家展示信息
const sellerNameDisplay = computed(() => {
  return sellerInfo.value.name || (sellerId ? `商家 ${sellerId}` : '商家')
})

const sellerAvatarDisplay = computed(() => {
  // 如果有头像 URL 且不为空字符串，返回 URL；否则返回 undefined 让 el-avatar 显示 fallback
  const avatar = sellerInfo.value.avatar
  return (avatar && avatar.trim() !== '') ? avatar : undefined
})

// 买家展示信息
const userAvatarDisplay = computed(() => {
  // 如果有头像 URL 且不为空字符串，返回 URL；否则返回 undefined 让 el-avatar 显示 fallback
  const avatar = currentUserInfo.value.avatar
  return (avatar && avatar.trim() !== '') ? avatar : undefined
})

// 获取当前用户（买家）信息
const loadCurrentUserInfo = async () => {
  try {
    const res = await getUserInfo()
    console.log('当前用户信息:', res)
    // 修改：参考 HomePage，使用 res.data.code 和 res.data.data
    if (res.data.code === '200' && res.data.data) {
      currentUserInfo.value = {
        username: res.data.data.username || currentUserInfo.value.username,
        name: res.data.data.name || res.data.data.username || '',
        avatar: res.data.data.avatar || ''
      }
      console.log('买家头像 URL:', currentUserInfo.value.avatar)
    }
  } catch (error) {
    console.warn('获取当前用户信息失败', error)
  }
}

// 获取卖家信息（通过 getUserInfoByUsername 获取完整信息）
const loadSellerInfo = async () => {
  if (!sellerId) return
  try {
    const res = await getUserInfoByUsername(sellerId)
    console.log('卖家信息:', res)
    
    if (res.data.code === '200' && res.data.data) {
      sellerInfo.value = {
        username: res.data.data.username || sellerId,
        name: res.data.data.name || sellerId,
        avatar: res.data.data.avatar || ''
      }
      console.log('设置后的卖家信息:', sellerInfo.value)
    }
  } catch (error) {
    console.warn('获取卖家信息失败', error)
  }
}

// 死数据回复列表（按发送次数索引）
const chatList = [
  '商家回复：您好，已收到您的消息，我们会尽快处理。',
  '商家回复：感谢反馈，我们正在联系相关人员处理。',
  '商家回复：您好，问题已处理，感谢耐心等待。',
  // 可继续添加更多默认回复
];

// 记录买家发送次数
const sendCount = ref(0);

// 发送消息（本地展示），并在 3s 后根据 sendCount 回复 chatList 中对应项
const send = () => {
  const text = input.value && input.value.trim()
  if (!text) return
  messages.value.push({
    from: currentUserInfo.value.username,
    text,
    time: new Date().toLocaleTimeString(),
    me: true
  })
  input.value = ''
  nextTickScroll()
  ElMessage({ message: '消息已发送（仅演示）', type: 'success', center: true, duration: 800 })

  // 增加发送计数并在 3s 后按序回复
  sendCount.value++
  const idx = sendCount.value - 1
  const replyText = chatList[idx] ?? chatList[chatList.length - 1]

  setTimeout(() => {
    messages.value.push({
      from: sellerInfo.value.name || sellerId,
      text: replyText,
      time: new Date().toLocaleTimeString(),
      me: false
    })
    nextTickScroll()
  }, 3000)
}

// 支持 Enter 发送（在 textarea 中按 Enter 触发）
const handleEnter = () => {
  send()
}

// 将背景设置到全局 body（进入页面时设置，离开时恢复）
let __prevBodyStyle = {}
onMounted(async () => {
  // 保存之前的 body 样式值（尽量多保存常用字段）
  __prevBodyStyle.background = document.body.style.background
  __prevBodyStyle.backgroundBlendMode = document.body.style.backgroundBlendMode
  __prevBodyStyle.backgroundColor = document.body.style.backgroundColor

  // 设置页面背景：条纹 + 渊变（参考 HomePage），装饰图片由组件内固定 img 提供
  document.body.style.background =
    'repeating-linear-gradient(0deg, #faf8f3 0px, #faf8f3 2px, #f5f1e8 2px, #f5f1e8 4px),' +
    'linear-gradient(180deg, rgba(211,125,63,0.16) 0%, rgba(217,138,82,0.08) 50%, rgba(255,250,240,0.03) 100%)'
  document.body.style.backgroundBlendMode = 'multiply'
  document.body.style.backgroundColor = 'transparent'

  await Promise.all([
    loadCurrentUserInfo(),
    loadSellerInfo()
  ])
  nextTickScroll()
})

onBeforeUnmount(() => {
  // 恢复之前的 body 样式
  document.body.style.background = __prevBodyStyle.background || ''
  document.body.style.backgroundBlendMode = __prevBodyStyle.backgroundBlendMode || ''
  document.body.style.backgroundColor = __prevBodyStyle.backgroundColor || ''
})

// 确保滚动到底部
const nextTickScroll = () => {
  setTimeout(() => {
    if (chatWindow.value) {
      chatWindow.value.scrollTop = chatWindow.value.scrollHeight
    }
  }, 50)
}

// 当 messages 变化时滚动到底部
watch(messages, () => nextTickScroll())
</script>

<style scoped>
.chat-page {
  max-width: 900px;
  margin: 24px auto;
  padding: 12px;
  /* 使用页面大背景（由 inline style 注入图片），并设定字体与主色参考 HomePage */
  background-color: rgba(255,250,240,0.8); /* 稍微更不透明以突出聊天卡片 */
  border-radius: 12px;
  box-shadow: 0 8px 30px rgba(0,0,0,0.06);
  color: #1f2937; /* 主文字颜色，参考 HomePage */
  font-size: 16px; /* 全局字体略大 */
  --muted-color: #6b7280; /* 次要文字颜色 */
}

/* header */
.chat-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 12px;
  border-bottom: 1px solid rgba(0,0,0,0.06);
  background-color: rgba(217,138,82,0.08); /* 浅棕背景 */
  border-radius: 12px 12px 0 0;
}

.back-btn {
  color: var(--muted-color);
  font-weight: 800;    /* 加粗箭头 */
  font-size: 20px;     /* 放大箭头显示 */
  padding: 6px 10px;   /* 增大点击区域 */
  line-height: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.title {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.title-main {
  font-weight: 800;
  color: #1f2937;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 20px; /* 标题更醒目 */
}

/* 移除原有 seller-name 的背景/盒式样式（已不再用） */
/* 新的轻量行内样式：仅设置棕色文字，与 ProductDetails 加入购物车颜色一致 */
.seller-color {
  color: #6B3E1E;
  background: none;
  padding: 0;
  border-radius: 0;
  box-shadow: none;
  font-weight: inherit;
  font-size: inherit;
  line-height: inherit;
  white-space: normal;
}

/* 如果还有旧的 seller-name 定义，覆盖为无样式以避免残留 */
.seller-name {
  background: none !important;
  color: inherit !important;
  padding: 0 !important;
  border-radius: 0 !important;
  box-shadow: none !important;
}

.spacer {
  flex: 1;
}

/* 聊天窗口 */
.chat-window {
  height: 480px;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  background: linear-gradient(180deg, rgba(245,245,245,0.4) 0%, transparent 100%);
  border-radius: 8px;
  margin: 16px 8px;
}

/* 消息行 */
.message-row {
  display: flex;
  align-items: flex-end;
  gap: 12px;
}

/* 他人消息在左 */
.message-row.other {
  justify-content: flex-start;
}

/* 我方消息在右 */
.message-row.me {
  justify-content: flex-end;
}

/* 头像 */
.avatar {
  flex-shrink: 0;
  border: 2px solid rgba(0,0,0,0.04);
}

/* 卖家头像背景色 */
.message-row.other .avatar {
  background-color: #b45309;
  color: #fff;
}

/* 买家头像背景色 */
.message-row.me .avatar {
  background-color: #6B3E1E;
  color: #fff;
}

/* 气泡 - 调整为与 HomePage 元素一致的边缘样式 */
.bubble {
  max-width: 70%;
  padding: 12px 14px; /* 稍微增加内间距 */
  border-radius: 16px; /* 更圆润，和 HomePage 按钮一致的风格 */
  box-shadow: 0 4px 14px rgba(0,0,0,0.06);
  display: inline-block;
  border: 1px solid rgba(230,214,195,0.9); /* 与 HomePage 边框色调一致 */
  background-clip: padding-box;
}

/* 他人消息（浅色气泡） */
.bubble-other {
  background: #efe6db; /* 与 HomePage 的柔和米色保持一致 */
  color: #111827;
  border-color: #e6d6c3;
}

/* 我的消息（橘色气泡），保留主色调但加边框以统一边缘风格 */
.bubble-me {
  background: #c27935; /* 买家气泡改为橙色，和其他页面一致 */
  color: #fff;
  border-color: rgba(155,52,31,0.9);
}

/* 文本与时间：微调时间颜色以配合新的气泡边缘 */
.msg-text {
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 15px;
  line-height: 1.4;
}

.msg-time {
  margin-top: 6px;
  font-size: 11px;
  opacity: 0.85;
}

/* 对方时间颜色调整（与 HomePage 次要色一致） */
.message-row.other .msg-time {
  color: #7a4f30;
  opacity: 0.9;
}

/* 我方时间颜色（浅色，便于在橘色气泡上可读） */
.message-row.me .msg-time {
  color: rgba(255,255,255,0.9);
}

/* 空提示 */
.empty-tip {
  color: #9ca3af;
  text-align: center;
  margin-top: 40%;
}

/* 输入区 */
.chat-input {
  display: flex;
  gap: 12px; /* 增大左右控件间距 */
  align-items: center;
  padding: 16px 16px; /* 上下更大，左右更宽 */
  border-top: 2px solid #eee; /* 略增边框厚度，视觉更明显 */
}

.input-area :deep(.el-textarea__inner) {
  border-radius: 12px;
  min-height: 72px; /* 提高输入框高度 */
  padding: 10px 14px; /* 增加左右内边距 */
  resize: none;
}

.send-btn {
  background-color: #c27935; /* 统一橙色 */
  color: #fff;
  border-color: #c27935;
  font-weight: 600;
  padding: 10px 20px; /* 微调左右内边距以配合更宽的输入区 */
  border-radius: 12px;
}

.send-btn:hover {
  background-color: #9B341F;
  border-color: #9B341F;
}

/* 装饰图片样式（固定在页面） */
.deco-img {
  position: fixed;
  z-index: 0;
  opacity: 0.12;
  pointer-events: none;
  mix-blend-mode: multiply;
}

.deco-2 {
  top: 120px;
  right: 80px;
  width: 380px;
  transform: rotate(-12deg);
}

.deco-4 {
  bottom: 80px;
  left: 60px;
  width: 280px;
  transform: rotate(18deg);
}

/* 使主要容器置于装饰图片之上 */
.chat-page { position: relative; z-index: 1; }

/* 响应式 */
@media (max-width: 768px) {
  .chat-page { padding: 8px; }
  .chat-window { height: 360px; }
  .bubble { max-width: 78%; }
  .title-main { font-size: 16px; gap:6px; }
  .seller-name { font-size: 14px; padding: 3px 8px; }
}
</style>
