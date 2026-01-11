<template>
  <div class="header-section">
    <div class="header-content">
      <div class="site-title" @click="goToHome" style="cursor: pointer;">
        <h1><img :src="leafImg" alt="leaf" class="title-leaf" />拾光书坊</h1>
        <p class="subtitle">不负好时光</p>
      </div>
      
      <!-- 搜索栏 -->
      <div class="search-bar">
        <el-input
          v-model="localSearchKeyword"
          placeholder="搜索书名、作者、ISBN..."
          size="large"
          class="search-input"
          @keyup.enter="handleSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-button type="primary" size="large" @click="handleSearch" class="search-btn">
          搜索
        </el-button>
      </div>
      
      <!-- 用户功能区 -->
      <div class="user-actions" v-if="userInfo">
<!--        <el-badge :value="0" :hidden="true" class="cart-badge">-->
<!--          <el-button circle size="large" @click="goToCart" class="action-btn">-->
<!--            <el-icon :size="22"><ShoppingCart /></el-icon>-->
<!--          </el-button>-->

<!--          <el-button circle class="action-btn" @click="goToOrders" title="订单列表">-->
<!--            &lt;!&ndash; 若无合适图标，可先用文字；也可换成 Tickets/Document 图标 &ndash;&gt;-->
<!--            <span class="action-text">订单</span>-->
<!--          </el-button>-->

<!--        </el-badge>-->

        <div class="user-actions" v-if="userInfo">
          <div class="action-buttons">
            <!-- 购物车按钮 -->
            <button class="rect-btn" @click="goToCart">
              🛒购物车
            </button>

            <!-- 订单按钮 -->
            <button class="rect-btn" @click="goToOrders">
              我的订单
            </button>
          </div>
        </div>

        <img 
          :src="userInfo.avatar || 'data:image/svg+xml,%3Csvg xmlns=%22http://www.w3.org/2000/svg%22 width=%2248%22 height=%2248%22%3E%3Crect fill=%22%233b82f6%22 width=%2248%22 height=%2248%22 rx=%226%22/%3E%3Ctext fill=%22%23ffffff%22 font-family=%22Arial%22 font-size=%2224%22 font-weight=%22bold%22 x=%2250%25%22 y=%2250%25%22 text-anchor=%22middle%22 dy=%220.35em%22%3EU%3C/text%3E%3C/svg%3E'"
          :alt="userInfo.username"
          class="user-avatar"
          @click="goToProfile"
          @error="(e) => e.target.src = 'data:image/svg+xml,%3Csvg xmlns=%22http://www.w3.org/2000/svg%22 width=%2248%22 height=%2248%22%3E%3Crect fill=%22%23999999%22 width=%2248%22 height=%2248%22 rx=%226%22/%3E%3Ctext fill=%22%23ffffff%22 font-family=%22Arial%22 font-size=%2224%22 font-weight=%22bold%22 x=%2250%25%22 y=%2250%25%22 text-anchor=%22middle%22 dy=%220.35em%22%3E?%3C/text%3E%3C/svg%3E'"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import {useRouter, useRoute, createRouter as $router} from 'vue-router';
import { Search, ShoppingCart } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import { getUserInfo } from '@/api/user';
import leafImg from '@/assets/images/header_leaf3.png'

const router = useRouter();
const route = useRoute();
const userInfo = ref(null);
const localSearchKeyword = ref(route.query.search || '');

// 获取用户信息
const fetchUserInfo = async () => {
  try {
    const res = await getUserInfo();
    if (res.data.code === '200') {
      userInfo.value = res.data.data;
    }
  } catch (error) {
    console.error('获取用户信息失败:', error);
  }
};

// 搜索功能
const handleSearch = () => {
  if (!localSearchKeyword.value.trim()) {
    ElMessage.warning('请输入搜索关键词');
    return;
  }
  router.push({ path: '/products', query: { search: localSearchKeyword.value.trim() } });
};

// 跳转到购物车
const goToCart = () => {
  router.push('/cart');
};

const goToOrders = () => {
  router.push('/orders');
};

// 跳转到个人信息
const goToProfile = () => {
  router.push('/information');
};

// 返回首页
const goToHome = () => {
  router.push('/');
};

onMounted(() => {
  fetchUserInfo();
});
</script>

<style scoped>
.header-section {
  /* 温暖的棕色主题 */
  background-color: #c27935ff; /* 浅棕色 */
  color: white;
  padding: 16px 0; /* 从 28px 缩小到 16px，降低 header 高度 */
  box-shadow: 0 2px 12px rgba(176,134,104,0.22);
  position: sticky;
  top: 0;
  z-index: 100;
  position: relative;
  overflow: hidden;
}

.header-section::before {
  content: '';
  position: absolute;
  top: -50px;
  right: -50px;
  width: 200px;
  height: 200px;
  background-image: url('data:image/svg+xml,%3Csvg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="%23ffffff" opacity="0.1"%3E%3Cpath d="M17,8C8,10 5.9,16.17 3.82,21.34L5.71,22L6.66,19.7C7.14,19.87 7.64,20 8,20C19,20 22,3 22,3C21,5 14,5.25 9,6.25C4,7.25 2,11.5 2,13.5C2,15.5 3.75,17.25 3.75,17.25C7,8 17,8 17,8Z"%3E%3C/path%3E%3C/svg%3E');
  background-size: contain;
  background-repeat: no-repeat;
  opacity: 0.15;
  transform: rotate(-15deg);
}

.header-section::after {
  content: '🍂';
  position: absolute;
  bottom: 10px;
  left: 80px;
  font-size: 48px;
  opacity: 0.2;
  transform: rotate(25deg);
}

.header-content {
  max-width: 1280px;
  padding: 0 24px; /* 给内部内容增加左右间距，避免靠边 */
  margin: 0 auto;
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 32px;
  position: relative;
  z-index: 1;
}

.site-title h1 {
  font-size: 30px; /* 从 36/45 缩小到 30px，减少占高 */
  font-weight: 800;
  margin: 0;
  letter-spacing: 2px;
  text-shadow: 2px 2px 4px rgba(0, 0, 0, 0.1);
  position: relative;
}

.title-leaf {
  width: 64px; /* 从 90px 缩小 */
  height: 64px;
  margin-right: 8px;
  margin-bottom: 0;
  object-fit: contain;
  transform: translateY(4px) rotate(-6deg); /* 轻微下移 */
  vertical-align: middle;
}

.site-title h1 {
  display: inline-flex;
  align-items: center;
  gap: 6px; /* 从 12px 改为 6px，进一步缩短图片与文字间距 */
}

.subtitle {
  margin: -6px 0 0 0; /* 上移：从 3px 改为 -6px */
  font-size: 18px;
  opacity: 0.95;
  text-align: center;
  transform: translateY(-2px); /* 进一步微调上移，视觉更靠近标题 */
}

/* 手写风格标题与副标题居中 */
.site-title {
  display: flex;
  flex-direction: column;
  align-items: center;
}
.site-title h1 {
  /* 使用系统手写字体回退到通用 cursive */
  font-family: 'Brush Script MT', 'Segoe Script', 'Bradley Hand', 'Snell Roundhand', cursive;
  font-size: 45px;
  letter-spacing: 1px;
  margin: 0;
}

.search-bar {
  display: flex;
  gap: 12px;
  max-width: 520px; /* 从 600 缩小 */
  width: 100%;
  justify-self: center;
}

.search-input {
  flex: 1;
}

.search-input :deep(.el-input__wrapper) {
  background: white;
  box-shadow: none;
  border-radius: 24px;
  padding: 4px 16px;
}

.search-btn {
  min-width: 88px; /* 从 100 缩小 */
  border-radius: 24px;
  font-weight: 600;
  font-size: 14px; /* 略小字体 */
  background-color: #d97706 !important;
  border-color: #d97706 !important;
}

.search-btn:hover {
  background-color: #b45309 !important;
  border-color: #b45309 !important;
}

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
  width: 40px; /* 从 48 缩小 */
  height: 40px;
  border-radius: 50%;
  object-fit: cover;
  cursor: pointer;
  border: 2px solid rgba(255, 255, 255, 0.3); /* 边框宽度同步减小 */
  transition: all 0.3s;
}

.user-avatar:hover {
  transform: scale(1.1);
  border-color: white;
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

  /* 移动端缩小叶子图标，避免占用过多空间 */
  .title-leaf {
    width: 44px; /* 从 56 缩小 */
    height: 44px;
    margin-right: 8px;
    transform: translateY(3px) rotate(-6deg); /* 移动端下移 3px */
  }

  /* 移动端也减小 h1 间隙与字体 */
  .site-title h1 {
    gap: 6px;
    font-size: 22px; /* 保持移动端可读但较小 */
  }

  .subtitle {
    margin: -4px 0 0 0; /* 移动端略微上移，保持比例 */
    font-size: 12px; /* 已有移动端字体设置，保留一致 */
    transform: translateY(-1px);
  }
}


.text-btn {
  background: none;
  border: none;
  color: white; /* 保持文字颜色为白色 */
  font-size: 16px;
  font-weight: bold;
  cursor: pointer;
  padding: 8px 16px;
  transition: all 0.3s;
}

.text-btn:hover {
  text-decoration: underline; /* 鼠标悬停时添加下划线效果 */
}

.action-buttons {
  display: flex;
  gap: 8px; /* 调整按钮之间的间距 */
}

.rect-btn {
  background: white; /* 按钮背景色 */
  color: #c27935; /* 按钮文字颜色 */
  border: 1px solid #c27935; /* 按钮边框颜色 */
  border-radius: 4px; /* 按钮圆角 */
  font-size: 16px;
  font-weight: bold;
  cursor: pointer;
  padding: 8px 16px; /* 调整按钮大小 */
  transition: all 0.3s;
}

.rect-btn:hover {
  background: #c27935; /* 鼠标悬停时背景色 */
  color: white; /* 鼠标悬停时文字颜色 */
}
</style>
