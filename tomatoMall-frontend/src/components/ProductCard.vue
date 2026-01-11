// 单个商品的简要信息
<script setup>
import { useRouter } from 'vue-router'
const router = useRouter()
// 添加类型检查
const props = defineProps({
  product: {
    type: Object,
    required: true,
    validator: (value) => {
      return 'id' in value && typeof value.id === 'string'
    }
  }
})

// 点击跳转处理
// 添加错误处理
const handleClick = () => {
  try {
    if (!props.product?.id) {
      throw new Error('商品ID不存在')
    }
    router.push({
      path: `/products/${props.product.id}`,
      query: { t: Date.now() } // 添加时间戳防止路由缓存
    })
  } catch (error) {
    console.error('路由跳转失败:', error)
    ElMessage.error('无法查看商品详情')
  }
}

const formatPrice = (price) => {
  return `¥${price.toFixed(2)}`
}
</script>

<template>
  <div class="product-card vertical" @click="handleClick">
    <!-- 图片容器 -->
    <div class="image-container">
      <el-image
          :src="product.cover || '/default-cover.jpg'"
          fit="contain"
          class="product-cover"
      />
    </div>

    <!-- 商品信息 -->
    <div class="product-info">
      <h3 class="title">{{ product.title }}</h3>
      <div class="meta">
        <span class="price">{{ formatPrice(product.price) }}</span>
        <span class="rate">⭐ {{ product.rate.toFixed(1) }}</span>
      </div>
      <p class="description">{{ product.description }}</p>
<!--      <div class="seller-info">-->
<!--        <span class="seller-label">卖家：</span>-->
<!--        <span class="seller-name">{{ product.sellerName || '未知' }}</span>-->
<!--      </div>-->
    </div>
  </div>
</template>

<style scoped>
/* 修改为垂直布局 */
.product-card.vertical {
  display: flex;
  flex-direction: column;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  overflow: hidden;
  transition: all 0.3s;
  background: white;
  cursor: pointer;
}

.product-card.vertical:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(107, 62, 30, 0.12);
  border-color: #b08968;
}

.image-container {
  width: 100%;
  height: 160px; /* 减小图片高度 原 240 */
  overflow: hidden;
  background: #f3f4f6;
}

.product-cover {
  width: 100%;
  height: 100%;
  object-fit: contain;
  transition: transform 0.3s;
  background: #fff; /* 可选，让留白更自然 */
}

.product-card.vertical:hover .product-cover {
  transform: scale(1.05);
}

.product-info {
  padding: 12px; /* 减小内边距 */
  display: flex;
  flex-direction: column;
  gap: 8px;
}

/* 调整文字样式 */
.title {
  font-size: 14px; /* 减小标题字体 */
  font-weight: 600;
  margin: 0;
  color: #1f2937;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  line-height: 1.5;
  min-height: 36px; /* 减小标题高度 */
}

.meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

/* 商品卡片价格统一为枫叶红 */
.price {
  color: #C0412B; /* 枫叶红 */
  font-weight: 800;
}

.rate {
  font-size: 14px;
  color: #8B5A2B;
  font-weight: 500;
}

.description {
  font-size: 12px;
  color: #6b7280;
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  line-height: 1.6;
}

.seller-info {
  font-size: 12px;
  color: #9ca3af;
  padding-top: 8px;
  border-top: 1px solid #f3f4f6;
}

.seller-label {
  color: #6b7280;
}

.seller-name {
  color: #7A3A1A;
  font-weight: 500;
}
</style>