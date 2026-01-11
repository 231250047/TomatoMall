<script setup>
import { ref, onMounted, defineProps } from 'vue';
import { useRoute } from 'vue-router';
import router from '../router/index';
import { getAllProducts, getProductsBySellerId } from "@/api/product.ts";

const productList = ref([]);
const props = defineProps({
  selectMode: {
    type: Boolean,
    default: false
  }
});
const emit = defineEmits(['select-product']);

const selectedId = ref(null);

// 获取商品信息
const route = useRoute();

// 生成本地占位头像（避免依赖外部服务）
const generateAvatarUrl = (sellerId) => {
  const isOld = !sellerId || sellerId === 'old';
  const initial = isOld ? '旧' : 'U';
  const bgColor = isOld ? 'cccccc' : '3b82f6';
  return `data:image/svg+xml,%3Csvg xmlns=%22http://www.w3.org/2000/svg%22 width=%2248%22 height=%2248%22%3E%3Crect fill=%22%23${bgColor}%22 width=%2248%22 height=%2248%22 rx=%226%22/%3E%3Ctext fill=%22%23ffffff%22 font-family=%22Arial%22 font-size=%2224%22 font-weight=%22bold%22 x=%2250%25%22 y=%2250%25%22 text-anchor=%22middle%22 dy=%220.35em%22%3E${initial}%3C/text%3E%3C/svg%3E`;
};

const getProductInfo = () => {
  const sellerId = route.query.sellerId;
  const searchKeyword = route.query.search; // 获取搜索关键词
  const fetcher = sellerId ? getProductsBySellerId(Number(sellerId)) : getAllProducts();
  fetcher.then(res => {
    console.log('ProductList API 原始响应:', res);
    console.log('第一个商品数据示例:', res.data?.[0]);
    let products = res.data.map(x => {
      const sid = x.sellerId || x.seller_id || null;
      console.log(`商品 ${x.id} 的 sellerId:`, sid, 'sellerAvatar:', x.sellerAvatar, '原始数据:', x);
      return {
        id: x.id,
        title: x.title,
        price: x.price,
        staff: x.staff,
        rate: x.rate || 0,
        description: x.description,
        cover: x.cover,
        detail: x.detail,
        sellerId: sid,
        status: x.status,
        // 优先使用后端返回的 sellerAvatar，否则使用占位头像
        sellerAvatar: x.sellerAvatar || generateAvatarUrl(sid),
        sellerName: x.sellerName || '未知卖家',
      };
    });
    
    // 如果有搜索关键词，过滤商品
    if (searchKeyword && searchKeyword.trim()) {
      const keyword = searchKeyword.trim().toLowerCase();
      products = products.filter(p => 
        p.title?.toLowerCase().includes(keyword) || 
        p.description?.toLowerCase().includes(keyword) ||
        p.sellerName?.toLowerCase().includes(keyword)
      );
    }
    
    productList.value = products;
  }).catch(error => {
    console.error('获取商品信息失败', error);
  });
};

// 选择处理：设置选中并通知父组件完整的 product 对象
const handleSelect = (product) => {
  selectedId.value = product.id;
  emit('select-product', product);
};

// 查看详情（接收 event 防止冒泡）
const handleViewDetails = (event, productId) => {
  if (event && event.stopPropagation) event.stopPropagation();
  router.push(`/products/${productId}`);
};

onMounted(() => {
  getProductInfo();
});
</script>

<template>
  <div class="product-list-container">
    <!-- 空状态显示 -->
    <div v-if="productList.length === 0" class="empty-state">
      <el-empty description="没有找到相关商品">
        <el-button type="primary" class="back-home-btn" @click="$router.push('/home')">返回首页</el-button>
      </el-empty>
    </div>

    <!-- 商品列表 -->
    <div v-else class="product-list">
      <div class="product-item" v-for="product in productList" :key="product.id">
        <div
          class="product-card"
          :class="{ selected: selectedId === product.id }"
          tabindex="0"
          @click="props.selectMode ? handleSelect(product) : handleViewDetails($event, product.id)"
          @keydown.enter="props.selectMode ? handleSelect(product) : handleViewDetails($event, product.id)"
        >
          <div class="image-wrap">
            <img 
              class="product-image" 
              :src="product.cover" 
              :alt="product.title"
              @error="(e) => e.target.src = 'data:image/svg+xml,%3Csvg xmlns=%22http://www.w3.org/2000/svg%22 width=%22120%22 height=%22120%22%3E%3Crect fill=%22%23f0f0f0%22 width=%22120%22 height=%22120%22/%3E%3Ctext fill=%22%23999%22 font-family=%22sans-serif%22 font-size=%2214%22 x=%2250%25%22 y=%2250%25%22 text-anchor=%22middle%22 dy=%22.3em%22%3E无图片%3C/text%3E%3C/svg%3E'"
            />
          </div>
        </div>

        <div class="info-wrap">
          <h3 class="product-title">{{ product.title }}</h3>
          <div class="seller-line">
            <img 
              :src="product.sellerAvatar" 
              :alt="product.sellerName"
              class="seller-avatar"
              @error="(e) => e.target.src = 'data:image/svg+xml,%3Csvg xmlns=%22http://www.w3.org/2000/svg%22 width=%2228%22 height=%2228%22%3E%3Crect fill=%22%233b82f6%22 width=%2228%22 height=%2228%22 rx=%223%22/%3E%3Ctext fill=%22%23ffffff%22 font-family=%22Arial%22 font-size=%2214%22 font-weight=%22bold%22 x=%2250%25%22 y=%2250%25%22 text-anchor=%22middle%22 dy=%220.35em%22%3E?%3C/text%3E%3C/svg%3E'"
            />
            <span class="seller-name">{{ product.sellerName || '旧商品' }}</span>
          </div>
          <p class="product-description" v-if="product.description">{{ product.description }}</p>
          <div class="meta-row">
            <div class="price">
              <span class="currency">￥</span><span class="amount">{{ product.price }}</span>
            </div>
            <div class="rating">
              <span class="stars" :title="`评分 ${product.rate}/10`">
                <!-- 背景五颗灰星 -->
                <span class="stars-back">★★★★★</span>
                <!-- 前景五颗金星，宽度根据评分百分比控制，实现部分填充 -->
                <span class="stars-front" :style="{ width: ((product.rate / 10) * 100) + '%' }">★★★★★</span>
                <span class="rate-num">{{ product.rate.toFixed(1) }}/10</span>
              </span>
            </div>
          </div>
        </div>

        <div class="actions-wrap">
          <el-radio v-if="props.selectMode" v-model="selectedId" :label="product.id" @change="handleSelect(product)">
            选择
          </el-radio>

          <!-- 已移除“查看详情”按钮，点击卡片/回车将进入详情页 -->
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.product-list-container {
  min-height: 400px;
}

.empty-state {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 400px;
  padding: 60px 20px;
}

.product-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 18px;
  padding: 18px;
  background: linear-gradient(180deg,#f6f8fb 0%, #ffffff 100%);
}

.back-home-btn {
  background-color: #c27935ff !important;
  border-color: #b08968 !important;
  color: #fff !important;
}
.back-home-btn:hover {
  background-color: #8B5A2B !important;
  border-color: #8B5A2B !important;
}

/* 当父组件或根节点带有 wide-layout 类时，改为一行一项的列表布局（横向长方形） */
.product-list-container.wide-layout .product-list {
  display: block;
  max-width: 1400px;
  margin: 24px auto;
  padding: 0 24px;
  background: transparent;
}

.product-list-container.wide-layout .product-item {
  display: flex;
  flex-direction: row;
  height: 220px;
  margin-bottom: 20px;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 2px 12px rgba(0,0,0,0.08);
  transition: all 0.3s;
}

.product-list-container.wide-layout .image-wrap {
  width: 280px;
  height: 220px;
  flex-shrink: 0;
}

.product-list-container.wide-layout .product-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.product-list-container.wide-layout .info-wrap {
  flex: 1;
  padding: 20px 24px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

/* 卡片改为可点击/可聚焦展示（增加悬停效果） */
.product-card {
  display: flex;
  gap: 14px;
  align-items: center;
  padding: 12px 0;
  background: transparent;
  border-radius: 6px; /* 轻微圆角，看起来更柔和 */
  box-shadow: none;
  border-bottom: 1px solid #e9e9e9;
  transition: transform .18s ease, box-shadow .18s ease, background .18s ease;
  cursor: pointer;
}

/* 悬停效果：轻微抬升、柔和阴影与淡背景（在桌面端更明显） */
.product-card:hover {
  transform: translateY(-6px);
  box-shadow: 0 12px 30px rgba(15,23,42,0.06);
  background: rgba(255,255,255,0.96);
}

/* 聚焦态便于键盘导航 */
.product-card:focus {
  outline: 2px solid rgba(96,165,250,0.18);
  outline-offset: 2px;
  box-shadow: 0 8px 24px rgba(59,130,246,0.06);
}

/* 保留选中状态但更低调 */
.product-card.selected {
  outline: none;
  background: rgba(96,165,250,0.06);
}

/* 左侧图片：在卡片悬停时放大更顺滑 */
.image-wrap {
  position: relative;
  width: 120px;
  min-width: 120px;
  height: 120px;
  border-radius: 6px;
  overflow: hidden;
  flex-shrink: 0;
}
.product-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  transition: transform .36s cubic-bezier(.2,.9,.2,1);
}
.product-card:hover .product-image {
  transform: scale(1.06);
}

/* 中间信息 */
.info-wrap {
  flex: 1;
  min-width: 0;
}
.product-title {
  font-size: 15px;
  font-weight: 700;
  color: #111827;
  margin: 0 0 6px;
  line-height: 1.2;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.product-description {
  font-size: 13px;
  color: #6b7280;
  margin: 0 0 10px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.seller-line { font-size: 13px; color: #4b5563; margin-bottom: 6px; display: flex; align-items: center; }
.seller-name { font-weight: 600; color: #111827; }
.seller-avatar { 
  width: 28px;
  height: 28px;
  border-radius: 50%;
  object-fit: cover;
  margin-right: 8px;
}
.meta-row {
  display: flex;
  gap: 12px;
  align-items: center;
}

/* 价格 */
.price {
  display:flex;
  align-items: baseline;
  gap: 4px;
}
.currency { font-size: 13px; color:#ef4444; }
.amount { font-size: 20px; font-weight: 800; color: #b91c1c; }

/* 评分：五颗星按比例填充（满分 10 -> 5 星） */
.stars {
  position: relative;
  display: inline-block;
  font-size: 16px;
  line-height: 1;
  margin-right: 8px;
  vertical-align: middle;
}
.stars-back,
.stars-front {
  display: inline-block;
  white-space: nowrap;
  font-family: inherit;
  letter-spacing: 2px;
}
/* 灰色背景星 */
.stars-back {
  color: #e6e6e6;
}
/* 金色前景星，使用 overflow:hidden 控制填充宽度 */
.stars-front {
  position: absolute;
  left: 0;
  top: 0;
  overflow: hidden;
  color: #8B5A2B;
  pointer-events: none;
}

/* 评分数字 */
.rate-num {
  font-size: 12px;
  color: #6b7280;
  margin-left: 6px;
}

/* 操作区 */
.actions-wrap {
  display:flex;
  flex-direction:column;
  gap:8px;
  align-items:flex-end;
}
.details-btn {
  border-radius: 18px;
  padding: 6px; /* 仅图标时减少横向内边距 */
  background: linear-gradient(90deg, #b08968 0%, #8B5A2B 100%);
  color: #fff;
  border: none;
  box-shadow: 0 6px 14px rgba(59,130,246,0.18);
  transition: transform .12s ease, box-shadow .12s ease, opacity .12s ease;
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.details-btn .details-icon {
  font-size: 14px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

/* 响应 */
@media (max-width: 640px) {
  .product-card { flex-direction: column; align-items: stretch; gap: 10px; transform: none; box-shadow: none; background: transparent; }
  .image-wrap { width: 100%; height: 180px; min-width: unset; border-radius: 4px; }
  .actions-wrap { flex-direction: row; justify-content: space-between; align-items:center; }
}
</style>
