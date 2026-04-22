<script setup>
import {ref, onMounted, nextTick} from 'vue'
import {recommendBooks} from '@/api/aiService'
import {ElMessage} from 'element-plus'

// 响应式数据
const userInput = ref('')
const messages = ref([])
const isLoading = ref(false)
const chatContainer = ref(null)

// 加载历史消息
onMounted(() => {
  const savedMessages = localStorage.getItem('aiChatMessages')
  if (savedMessages) {
    messages.value = JSON.parse(savedMessages)
    scrollToBottom()
  }
})

// 发送消息
const handleSendMessage = () => {
  const message = userInput.value.trim()
  if (!message || isLoading.value) return

  // 添加用户消息
  messages.value.push({
    role: 'user',
    content: message,
    timestamp: new Date().toISOString()
  })
  userInput.value = ''
  saveMessages()
  scrollToBottom()

  // 获取AI回复 - 使用RAG推荐系统
  isLoading.value = true
  console.log("用户查询：" + message)

  recommendBooks(message)
      .then(res => {
        messages.value.push({
          role: 'assistant',
          content: res,
          timestamp: new Date().toISOString()
        })
        saveMessages()
      })
      .catch(error => {
        ElMessage.error('RAG推荐服务请求失败')
        console.error('RAG请求错误:', error)
      })
      .finally(() => {
        isLoading.value = false
        scrollToBottom()
      })


}

// 保存聊天记录
const saveMessages = () => {
  localStorage.setItem('aiChatMessages', JSON.stringify(messages.value))
}

// 简易 Markdown -> HTML 渲染器（带基本转义与常用格式）
const escapeHtml = (str) => {
  if (!str) return ''
  return str.replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

const markdownToHtml = (raw) => {
  if (!raw && raw !== 0) return ''
  let s = String(raw)
  s = escapeHtml(s)

  // Horizontal rule
  s = s.replace(/^---$/gm, '<hr/>')

  // Headings
  s = s.replace(/^###\s+(.*)$/gm, '<h3>$1</h3>')
  s = s.replace(/^##\s+(.*)$/gm, '<h2>$1</h2>')
  s = s.replace(/^#\s+(.*)$/gm, '<h1>$1</h1>')

  // Bold and italic
  s = s.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
  s = s.replace(/\*(.+?)\*/g, '<em>$1</em>')

  // Split lines and build lists/paragraphs
  const lines = s.split(/\r?\n/)
  const out = []
  let inUl = false
  let inOl = false
  let lastLiIndex = -1

  const closeLists = () => {
    if (inUl) { out.push('</ul>'); inUl = false }
    if (inOl) { out.push('</ol>'); inOl = false }
  }

  for (let i = 0; i < lines.length; i++) {
    const rawLine = lines[i]
    const line = rawLine.trim()

    // peek next non-empty line for list continuation decisions
    let j = i + 1
    while (j < lines.length && lines[j].trim() === '') j++
    const nextLine = j < lines.length ? lines[j].trim() : null
    const nextOl = nextLine && nextLine.match(/^\d+\.\s+(.*)$/)
    const nextUl = nextLine && nextLine.match(/^[-\*\+]\s+(.*)$/)

    if (line === '') {
      // If a blank line but next non-empty line is a list item, keep list open.
      if (nextOl || nextUl) {
        // do nothing (preserve current list state)
        continue
      }
      closeLists()
      out.push('<br/>')
      continue
    }

    const olMatch = line.match(/^\d+\.\s+(.*)$/)
    const ulMatch = line.match(/^[-\*\+]\s+(.*)$/)

    if (olMatch) {
      if (inUl) { out.push('</ul>'); inUl = false }
      if (!inOl) { out.push('<ol>'); inOl = true }
      out.push('<li>' + olMatch[1] + '</li>')
      lastLiIndex = out.length - 1
      continue
    }

    if (ulMatch) {
      if (inOl) { out.push('</ol>'); inOl = false }
      if (!inUl) { out.push('<ul>'); inUl = true }
      out.push('<li>' + ulMatch[1] + '</li>')
      lastLiIndex = out.length - 1
      continue
    }

    // Normal paragraph or already converted heading/hr
    if (/^<h\d>/.test(line) || /^<hr\/>/.test(line)) {
      closeLists()
      out.push(line)
    } else {
      // If currently inside a list, and this line is a descriptive continuation
      // (not a heading/hr), append it to the last list item instead of closing the list.
      if ((inOl || inUl) && lastLiIndex >= 0) {
        // append with a line break inside the existing <li>
        out[lastLiIndex] = out[lastLiIndex].replace(/<\/li>$/,'<br/>' + line + '</li>')
      } else {
        closeLists()
        out.push('<p>' + line + '</p>')
      }
    }
  }

  closeLists()
  return out.join('')
}

// 模板中使用：把 message 内容渲染为 HTML
const formatMessage = (text) => {
  return markdownToHtml(text)
}

// 清空聊天记录
const clearHistory = () => {
  messages.value = []
  localStorage.removeItem('aiChatMessages')
  ElMessage.success('聊天记录已清空')
}

// 滚动到底部
const scrollToBottom = () => {
  nextTick(() => {
    if (chatContainer.value) {
      chatContainer.value.scrollTop = chatContainer.value.scrollHeight
    }
  })
}

// 处理键盘事件
const handleKeyDown = (e) => {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    handleSendMessage()
  }
}
</script>

<template>
  <div class="ai-chat-container">
    <div class="chat-header">
      <h2>📚 RAG智能图书推荐</h2>
    </div>

    <div ref="chatContainer" class="chat-messages">
      <div
          v-for="(message, index) in messages"
          :key="index"
          :class="['message', message.role]"
      >
        <div class="message-avatar">
          <el-icon v-if="message.role === 'user'">
            <User/>
          </el-icon>
          <el-icon v-else>
            <ChatDotRound/>
          </el-icon>
        </div>
        <div class="message-content">
          <div class="message-text" v-html="formatMessage(message.content)"></div>
          <div class="message-time">
            {{ new Date(message.timestamp).toLocaleTimeString() }}
          </div>
        </div>
      </div>

      <div v-if="isLoading" class="message assistant">
        <div class="message-avatar">
          <el-icon>
            <ChatDotRound/>
          </el-icon>
        </div>
        <div class="message-content">
          <div class="message-text loading">
            <el-icon class="is-loading">
              <Loading/>
            </el-icon>
            <span>AI正在思考中...</span>
          </div>
        </div>
      </div>
    </div>

    <div class="chat-input">
      <el-input
          v-model="userInput"
          type="textarea"
          :rows="3"
          placeholder="请输入您的图书需求，如：推荐几本Java编程相关的书..."
          @keydown="handleKeyDown"
          :disabled="isLoading"
          resize="none"
      />
      <el-button
          type="primary"
          class="send-btn"
          @click="handleSendMessage"
          :disabled="!userInput.trim() || isLoading"
          :loading="isLoading"
      >
        发送
      </el-button>
    </div>
  </div>
</template>

<style scoped>
.ai-chat-container {
  display: flex;
  flex-direction: column;
  height: 100%;
  max-width: 800px;
  margin: 0 auto;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
  overflow: hidden;
}

.chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 20px;
  background: #c27935ff;
  color: white;
}

.chat-header h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 500;
}

.chat-messages {
  flex: 1;
  padding: 16px;
  overflow-y: auto;
  background-color: #f5f7fa;
}

.message {
  display: flex;
  margin-bottom: 16px;
  animation: fadeIn 0.3s ease;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.message-avatar {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--el-bg-color);
  border-radius: 50%;
  margin-right: 12px;
  flex-shrink: 0;
}

.message.user .message-avatar {
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.message.assistant .message-avatar {
  color: var(--el-color-success);
  background: var(--el-color-success-light-9);
}

.message-content {
  max-width: 75%;
}

.message-text {
  padding: 12px 16px;
  border-radius: 4px;
  line-height: 1.5;
  word-break: break-word;
  background: white;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.1);
}

.message-text p { margin: 6px 0; }
.message-text h1, .message-text h2, .message-text h3 { margin: 8px 0; font-weight: 700; }
.message-text h1 { font-size: 18px }
.message-text h2 { font-size: 16px }
.message-text h3 { font-size: 15px }
.message-text ul, .message-text ol { margin: 8px 0 8px 18px; padding: 0; }
.message-text li { margin: 4px 0; }

.message.user .message-text {
  background: var(--el-color-primary);
  color: white;
  border-top-right-radius: 0;
}

.message.assistant .message-text {
  background: white;
  color: var(--el-text-color-primary);
  border-top-left-radius: 0;
}

.message-time {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 4px;
}

.loading {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--el-text-color-secondary);
}

.chat-input {
  padding: 12px;
  border-top: 1px solid var(--el-border-color);
  background: white;
}

.send-btn {
  margin-top: 12px;
  width: 100%;
}

.send-btn {
  background: #c27935ff !important;
  border-color: #c27935ff !important;
  color: #fff !important;
}
.send-btn:hover {
  background: #8c4f23ff !important;
  border-color: #8c4f23ff !important;
}
.send-btn[disabled], .send-btn.is-disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

/* 响应式调整 */
@media (max-width: 768px) {
  .ai-chat-container {
    border-radius: 0;
  }

  .message-content {
    max-width: 70%;
  }
}
</style>