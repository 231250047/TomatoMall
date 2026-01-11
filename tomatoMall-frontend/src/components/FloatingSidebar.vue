<script setup>
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { Plus, ChatDotRound, Top } from '@element-plus/icons-vue';
import { ref, computed } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { Plus, ChatDotRound, Top, House } from '@element-plus/icons-vue';
 
const router = useRouter();
const route = useRoute();

// 在首页与登录页不展示侧边栏
const showFloating = computed(() => {
  const p = route.path || '';
  return !(p === '/' || p === '/login');
});
 
 // 滚动到页面顶部
 const scrollToTop = () => {
   window.scrollTo({ top: 0, behavior: 'smooth' });
 };
 
 // 跳转到发布书籍页面
 const goToCreateProduct = () => {
   router.push('/create-product');
 };
 
+// 跳转首页
+const goHome = () => {
+  router.push('/');
+};
+
 // AI助手相关（由父组件通过 emit 或全局状态管理）
 const emit = defineEmits(['toggle-ai']);
 
 const toggleAI = () => {
   emit('toggle-ai');
 };
</script>

<template>
-  <div class="floating-sidebar">
+  <div v-if="showFloating" class="floating-sidebar">
     <!-- 卖书按钮 -->
     <div class="sidebar-btn" @click="goToCreateProduct" title="上架新书">
       <el-icon :size="20"><Plus /></el-icon>
       <span class="btn-label">上架新书</span>
     </div>
 
+    <!-- 回首页按钮（在 /home 隐藏） -->
+    <div v-if="route.path !== '/home'" class="sidebar-btn" @click="goHome" title="回首页">
+      <el-icon :size="20"><House /></el-icon>
+      <span class="btn-label">首页</span>
+    </div>
+
     <!-- AI助手按钮 -->
     <div class="sidebar-btn" @click="toggleAI" title="AI智能助手">
       <el-icon :size="20"><ChatDotRound /></el-icon>
       <span class="btn-label">AI助手</span>
     </div>
 
     <!-- 回到顶部按钮 -->
     <div class="sidebar-btn" @click="scrollToTop" title="回到页面顶部">
       <el-icon :size="20"><Top /></el-icon>
       <span class="btn-label">回顶部</span>
     </div>
   </div>
</template>

<style scoped>
.floating-sidebar {
  position: fixed;
  right: 24px;
  top: 50%;
  transform: translateY(-50%);
  z-index: 9999;
  display: flex;
  flex-direction: column;
  gap: 12px;
  pointer-events: auto;
}

.sidebar-btn {
  width: 72px;
  height: 72px;
  border-radius: 12px;
  background: linear-gradient(135deg, var(--color-accent) 0%, var(--color-accent-hover) 100%);
  color: #fff;
  border: none;
  cursor: pointer;
  box-shadow: 0 4px 16px rgba(217, 119, 6, 0.4);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  user-select: none;
}

.sidebar-btn:hover {
  transform: translateX(-4px) scale(1.05);
  box-shadow: 0 8px 24px rgba(107, 62, 30, 0.5);
  background: linear-gradient(135deg, var(--color-accent-hover) 0%, var(--color-accent) 100%);
}

.sidebar-btn:active {
  transform: translateX(-2px) scale(1.02);
}

.btn-label {
  font-size: 12px;
  font-weight: 600;
  line-height: 1;
  white-space: nowrap;
}

/* 响应式：移动端调整位置和大小 */
@media (max-width: 768px) {
  .floating-sidebar {
    right: 12px;
    gap: 8px;
  }

  .sidebar-btn {
    width: 56px;
    height: 56px;
    border-radius: 10px;
  }

  .btn-label {
    font-size: 10px;
  }
}
</style>
