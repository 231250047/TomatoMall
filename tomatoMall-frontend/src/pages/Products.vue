<script setup>
import { computed } from 'vue';
import NavigationBar from "@/components/NavigationBar.vue";
import ProductList from "@/components/ProductList.vue";
import HeaderBar from "@/components/HeaderBar.vue";

const isAdmin = computed(() => sessionStorage.getItem('role') === 'admin');
sessionStorage.setItem('change','false');
</script>

<template>
  <div class="products-page-wrapper">
    <!-- 顶部标题栏 -->
    <HeaderBar />
    <div class="products-container">
      <NavigationBar v-if="isAdmin" />
      <ProductList class="wide-layout"></ProductList>
    </div>
  </div>
</template>

<style scoped>
.products-page-wrapper {
  min-height: 100vh;
  /* 与首页保持一致：把条纹放上层，渐变半透明叠加，并启用混合模式 */
  background:
    repeating-linear-gradient(0deg, #faf8f3 0px, #faf8f3 2px, #f5f1e8 2px, #f5f1e8 4px),
    linear-gradient(180deg, rgba(255,250,240,0.55) 0%, rgba(246,239,229,0.45) 50%, rgba(239,230,219,0.5) 100%);
  background-blend-mode: multiply;
  position: relative;
}

.products-page-wrapper::before {
  content: '🍂';
  position: fixed;
  bottom: 80px;
  right: 60px;
  font-size: 90px;
  opacity: 0.08;
  transform: rotate(15deg);
  z-index: 0;
}
.products-container {
  padding: 0 80px; /* 与首页保持一致的左右留白 */
  position: relative;
  z-index: 1;
}


/* Header 样式已统一到 `HeaderBar.vue` 组件中，页面不再保留重复样式 */

.user-actions {
  display: flex;
  align-items: center;
  gap: 16px;
}

.action-btn {
  background: rgba(255, 255, 255, 0.2);
  border: 1px solid rgba(255, 255, 255, 0.3);
  color: white;
  transition: all 0.3s;
}

.action-btn:hover {
  background: rgba(255, 255, 255, 0.3);
  transform: scale(1.05);
}

.user-avatar {
  cursor: pointer;
  border: 3px solid rgba(255, 255, 255, 0.3);
  transition: all 0.3s;
}

.user-avatar:hover {
  transform: scale(1.1);
  border-color: white;
}

/* 宽布局样式 */
:deep(.wide-layout .product-list) {
  max-width: 1400px;
  margin: 24px auto;
  padding: 0 24px;
}

:deep(.wide-layout .product-item) {
  display: flex;
  flex-direction: row;
  height: 220px;
  margin-bottom: 20px;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 2px 12px rgba(0,0,0,0.08);
  transition: all 0.3s;
}

:deep(.wide-layout .product-item:hover) {
  transform: translateY(-4px);
  box-shadow: 0 6px 20px rgba(0,0,0,0.12);
}

:deep(.wide-layout .product-image) {
  width: 280px;
  height: 220px;
  flex-shrink: 0;
}

:deep(.wide-layout .product-image img) {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

:deep(.wide-layout .product-info) {
  flex: 1;
  padding: 20px 24px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

:deep(.wide-layout .product-title) {
  font-size: 20px;
  font-weight: 600;
  margin-bottom: 12px;
  color: #1a1a1a;
}

:deep(.wide-layout .product-description) {
  font-size: 14px;
  color: #666;
  line-height: 1.6;
  margin-bottom: 12px;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

:deep(.wide-layout .product-price) {
  font-size: 28px;
  font-weight: 700;
  color: #f56c6c;
}

@media (max-width: 768px) {
  .header-content {
    flex-wrap: wrap;
  }
  
  .search-bar {
    order: 3;
    flex-basis: 100%;
    max-width: none;
  }
  
  :deep(.wide-layout .product-item) {
    flex-direction: column;
    height: auto;
  }
  
  :deep(.wide-layout .product-image) {
    width: 100%;
    height: 200px;
  }
}
</style>
