<template>
  <div>
    <router-view />
  </div>

  <!-- 全局侧边悬浮按钮 -->
  <FloatingButtons @toggle-ai="toggleOpen" />

  <!-- 全局悬浮 AI 窗口 -->
  <div class="ai-floating" :class="{ open: isOpen }" aria-live="polite">
    <transition name="ai-fade">
        <div v-if="isOpen" class="ai-panel" role="dialog" aria-label="AI 智能助手窗口">
          <!-- 保留最小化/关闭操作为浮动按钮，由 AiChat 自身渲染头部样式 -->
          <div class="ai-panel-actions float-actions">
            <button class="btn" @click="toggleOpen" title="最小化" aria-label="最小化">
              <el-icon><Minus/></el-icon>
            </button>
            <button class="btn" @click="closePanel" title="关闭" aria-label="关闭">
              <el-icon><Close/></el-icon>
            </button>
          </div>

          <!-- 限制 AiChat 大小由外层容器控制 -->
          <div class="ai-panel-body">
            <AiChat />
          </div>
        </div>
    </transition>
  </div>
</template>

<style scoped>
.ai-floating {
  position: fixed;
  right: 110px;
  bottom: 24px;
  z-index: 999; /* 低于侧边栏按钮但高于其他内容 */
  display: flex;
  align-items: flex-end;
  flex-direction: column;
  gap: 8px;
  pointer-events: auto;
}

/* 展开面板 */
.ai-panel {
  width: 380px;
  height: 520px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.18);
  border-radius: 8px;
  overflow: hidden;
  background: #fff;
  display: flex;
  flex-direction: column;
}

/* 头部样式（包含最小化 / 关闭） */
/* 浮动操作按钮（最小化/关闭）——不控制 AiChat 的头部样式，由 AiChat 自身管理 */
.ai-panel-actions .btn {
  background: transparent;
  border: none;
  color: #fff;
  font-size: 16px;
  cursor: pointer;
  margin-left: 8px;
  width: 36px;
  height: 32px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  line-height: 1;
  box-sizing: border-box;
  border-radius: 4px;
}

.ai-panel-actions .btn:hover {
  background: rgba(255,255,255,0.08);
}

/* 限制 AiChat 内部容器的最大宽高以适应浮窗 */
.ai-panel-body {
  flex: 1;
  min-height: 0; /* 允许内部滚动 */
  display: flex;
  flex-direction: column;
}

/* 覆盖 AiChat 的宽高，使其适配浮窗（不修改组件源码） */
.ai-panel-body .ai-chat-container {
  width: 100%;
  height: 100%;
  max-width: 100%;
  max-height: 100%;
  border-radius: 0;
  box-shadow: none;
}

/* 隐藏 AiChat 内部自带的头部，避免与浮窗外层头部重复
   使用深度选择器以覆盖子组件的 scoped 样式 */
.float-actions {
  position: absolute;
  top: 8px;
  right: 8px;
  z-index: 3;
  display: flex;
  gap: 6px;
}

/* 简单过渡 */
.ai-fade-enter-active,
.ai-fade-leave-active {
  transition: all 0.18s ease;
}
.ai-fade-enter-from,
.ai-fade-leave-to {
  transform: translateY(8px);
  opacity: 0;
}

/* 响应式：小屏幕把浮窗稍微收紧到屏幕内 */
@media (max-width: 420px) {
  .ai-panel { right: 12px; left: 12px; width: auto; height: 60vh; }
  .ai-floating { right: 12px; bottom: 12px; }
}
</style>

<script setup>
import { ref } from 'vue'
import AiChat from '@/components/AiChat.vue'
import FloatingButtons from '@/components/FloatingButtons.vue'
import { Minus, Close } from '@element-plus/icons-vue'

const isOpen = ref(false)

const toggleOpen = () => {
  isOpen.value = !isOpen.value
}
const closePanel = () => {
  isOpen.value = false
}
</script>
