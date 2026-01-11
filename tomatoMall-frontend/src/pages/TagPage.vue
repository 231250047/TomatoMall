<script setup>
import {ref, onMounted} from 'vue'
import {useRoute} from 'vue-router'
import {getTagProducts} from '@/api/product'
import NavigationBar from "@/components/NavigationBar.vue";
import {watch} from 'vue'
import router from "@/router/index.js";

// Props 声明
defineProps({
  tag: {
    type: String,
    required: true
  }
})

const route = useRoute()
const categoryMap = {
  education: '教辅',
  literature: '文学',
  art: '艺术',
  management: '管理',
  history: '历史',
  philosophy: '哲学宗教',
  health: '保健养生',
  science: '科技'
}

// 响应式数据
const currentTag = ref(route.params.tag || 'education')
const categoryName = ref(categoryMap[currentTag.value] || '未知分类')
const products = ref([])

// 商品加载逻辑保持不变
const loadProducts = async () => {
  try {
    const res = await getTagProducts(currentTag.value)
    if (res?.data?.length > 0) {
      products.value = res.data
    } else {
      products.value = []
      ElMessage.warning('该分类暂无商品')
    }
  } catch (error) {
    ElMessage.error(error.response?.data?.msg || '加载失败')
    products.value = []
  }
}

// 点击查看详情处理
const handleShowDetails = (id) => {
  router.push(`/products/${id}`)
}

// 生命周期和监听保持不变
onMounted(() => setTimeout(loadProducts, 50))
watch(
    () => route.params.tag,
    (newTag) => {
      if (newTag && newTag !== currentTag.value) {
        currentTag.value = newTag
        categoryName.value = categoryMap[newTag] || '未知分类'
        loadProducts()
      }
    }
)
</script>

<template>
  <el-container class="category-container">
    <el-header class="category-header">
      <h1>{{ categoryName }}</h1>
      <NavigationBar></NavigationBar>
    </el-header>

    <el-container class="main-wrapper">
      <el-aside width="200px" class="fixed-aside">
        <el-menu router :default-active="tag">
          <el-menu-item
              v-for="(name, key) in categoryMap"
              :key="key"
              :index="`/tag/${key}`"
              :route="{ name: 'tag', params: { tag: key } }"
          >
            {{ name }}
          </el-menu-item>
        </el-menu>
      </el-aside>

      <el-main class="fixed-main">
        <div class="product-list">
          <div
              class="product-item"
              v-for="product in products"
              :key="product.id"
              @click="handleShowDetails(product.id)"
          >
            <div class="product-card">
              <div class="avatar-container">
                <el-avatar :size="100" :src="product.cover"></el-avatar>
              </div>
              <div class="product-info">
                <h3 class="product-title">{{ product.title }}</h3>
                <p class="product-price">￥{{ product.price.toFixed(2) }}</p>
                <p class="product-rating">评分: {{ product.rate.toFixed(1) }}</p>
                <p class="product-description" v-if="product.description">
                  {{ product.description }}
                </p>
              </div>
            </div>
          </div>
        </div>
      </el-main>
    </el-container>
  </el-container>
</template>
<style scoped>
.category-container {
  padding-top: 40px;
  height: calc(100vh);
  max-width: 1000px;
  display: flex;
  flex-direction: column;
  margin: 0 auto;

}

.category-header {
  height: 80px !important; /* 固定标题高度 */
  display: flex;
  align-items: center;
  justify-content: center;
  background: #ffffff;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
  z-index: 999;
}

.main-wrapper {
  flex: 1;
  min-height: 0; /* 修复 flex 容器溢出问题 */
  display: flex; /* 新增 */
}
/* 商品列表新样式 */
.product-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 25px;
  padding: 20px;
  /* 新增以下属性保证统一行高 */
  grid-auto-rows: minmax(300px, auto);
  align-items: stretch;
}

.product-item {
  transition: transform 0.3s ease;
  cursor: pointer;
  /* 新增高度控制 */
  height: 100%;
}

.product-card {
  width: 100%;
  height: 100%; /* 改为继承父级高度 */
  padding: 20px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 3px 10px rgba(0,0,0,0.1);
  display: flex;
  flex-direction: column;
  justify-content: space-between; /* 新增空间分布 */
}

.product-item:hover {
  transform: translateY(-5px);
}

.product-card {
  width: 100%;
  height: 100%;
  padding: 20px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 3px 10px rgba(0,0,0,0.1);
  display: flex;
  flex-direction: column;
  align-items: center;
}


.fixed-aside {
  font-size: 20px;
  width: 200px;
  height: calc(100vh - 140px); /* 60导航栏 + 80标题 */
  flex-shrink: 0; /* 禁止侧边栏缩放 */
}


.fixed-main {
  flex: 1;
  min-width: 0; /* 防止内容溢出 */
  height: calc(100vh - 140px);
  background: #f8f9fa;
}

.category-header {
  background-color: #8B5A2B;
  color: white;
  display: flex;
  align-items: center;
  box-shadow: 0 2px 4px rgba(0, 0, 0, .1);
}


/* 添加悬停缩放效果 */
.product-item:hover .avatar-container img {
  transform: scale(1.05);
}
/* 商品信息区域优化 */
.product-info {
  text-align: center;
  width: 100%;
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

/* 文字截断优化 */
.product-title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.product-description {
  font-size: 14px;
  color: #909399;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 3; /* 显示最多3行 */
  -webkit-box-orient: vertical;
  overflow: hidden;
  margin-top: auto; /* 保证底部对齐 */
}

.product-info {
  margin-top: 10px;
}

.product-title {
  font-size: 18px;
  font-weight: bold;
  color: #333;
}

.product-price {
  font-size: 16px;
  color: #e74c3c;
}

.product-rating {
  font-size: 14px;
  color: #f39c12;
}


/* 优化图片容器 */
.avatar-container {
  width: 100%;
  height: 200px;
  margin-bottom: 15px;
  display: flex;
  justify-content: center;
  align-items: center;
  overflow: hidden;
}

/* 修改el-avatar样式 */
.avatar-container >>> .el-avatar {
  width: 200px !important;  /* 固定圆形直径 */
  height: 200px !important;
  border-radius: 50% !important; /* 强制圆形 */
  transition: transform 0.3s ease;
}

.avatar-container >>> .el-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;  /* 保持比例填充 */
  border-radius: 50%; /* 确保图片圆形 */
}

/* 响应式调整 */
@media (max-width: 1200px) {
  .avatar-container >>> .el-avatar {
    width: 180px !important;
    height: 180px !important;
  }
}

@media (max-width: 768px) {
  .avatar-container >>> .el-avatar {
    width: 160px !important;
    height: 160px !important;
  }
}
</style>