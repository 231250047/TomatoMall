<script setup>
import { ref, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getFavoriteList, removeFavorite } from '@/api/favorites'
import { useRouter } from 'vue-router'
import HeaderBar from '@/components/HeaderBar.vue'
import NavigationBar from '@/components/NavigationBar.vue'
import ProductCard from "@/components/ProductCard.vue";

const router = useRouter()
const favorites = ref([])
const loading = ref(false)
const isAdmin = computed(() => sessionStorage.getItem('role') === 'admin')

// 获取收藏列表
const fetchFavorites = async () => {
  loading.value = true
  try {
    const res = await getFavoriteList()
    if (res.code === '200') {
      favorites.value = res.data || []
    } else {
      ElMessage.error(res.msg || '获取收藏列表失败')
    }
  } catch (error) {
    console.error('获取收藏失败:', error)
    ElMessage.error('获取收藏列表失败')
  } finally {
    loading.value = false
  }
}

// 取消收藏
const handleRemoveFavorite = async (productId) => {
  try {
    await ElMessageBox.confirm('确定要取消收藏吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    const res = await removeFavorite(productId)
    if (res.code === '200') {
      ElMessage.success('取消收藏成功')
      fetchFavorites() // 刷新列表
    } else {
      ElMessage.error(res.msg || '取消收藏失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('取消收藏失败:', error)
      ElMessage.error('取消收藏失败')
    }
  }
}

// 跳转到商品详情
const goToProduct = (productId) => {
  router.push(`/products/${productId}`)
}

onMounted(() => {
  fetchFavorites()
})
</script>

<template>
  <div class="favorites-page-wrapper">
    <!-- 顶部标题栏 -->
    <HeaderBar />

    <div class="favorites-container">
      <NavigationBar v-if="isAdmin" />

      <div class="page-header">
        <h1>我的收藏</h1>
        <p class="subtitle">共 {{ favorites.length }} 件宝贝</p>
      </div>

      <el-divider />

      <div v-loading="loading" class="favorites-content">
        <!-- 空状态 -->
        <div v-if="favorites.length === 0 && !loading" class="empty-state">
          <el-empty description="还没有收藏任何宝贝">
            <el-button
                type="primary"
                @click="$router.push('/products')"
                class="go-shop-btn"
            >
              去逛逛
            </el-button>
          </el-empty>
        </div>

        <!-- 收藏列表 -->
        <div v-else class="favorites-grid">
          <ProductCard
              v-for="favorite in favorites"
              :key="favorite.productId"
              :product="{
                id: String(favorite.productId),
                title: favorite.productTitle,
                price: favorite.price,
                rate: favorite.rate || 0,
                description: favorite.description,
                cover: favorite.productCover,
              }"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>

.favorites-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr); /* 一排5个 */
  gap: 16px;
  padding: 24px 0;
}

.favorites-page-wrapper {
  min-height: 100vh;
  background:
      repeating-linear-gradient(0deg, #faf8f3 0px, #faf8f3 2px, #f5f1e8 2px, #f5f1e8 4px),
      linear-gradient(180deg, rgba(255,250,240,0.55) 0%, rgba(246,239,229,0.45) 50%, rgba(239,230,219,0.5) 100%);
  background-blend-mode: multiply;
  position: relative;
}

.favorites-page-wrapper::before {
  content: '⭐';
  position: fixed;
  top: 150px;
  right: 100px;
  font-size: 120px;
  opacity: 0.05;
  transform: rotate(-15deg);
  z-index: 0;
}

.favorites-container {
  max-width: 1400px;
  margin: 0 auto;
  padding: 24px 80px;
  position: relative;
  z-index: 1;
}

.page-header {
  text-align: center;
  margin: 32px 0;
}

.page-header h1 {
  font-size: 32px;
  font-weight: 700;
  color: #1f2937;
  margin: 0 0 8px;
}

.subtitle {
  color: #6b7280;
  font-size: 16px;
  margin: 0;
}

.favorites-content {
  min-height: 400px;
}

.empty-state {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 400px;
  padding: 60px 20px;
}

.go-shop-btn {
  background-color: #c27935 !important;
  border-color: #c27935 !important;
  padding: 12px 32px;
  font-size: 16px;
}

.go-shop-btn:hover {
  background-color: #9B341F !important;
  border-color: #9B341F !important;
}

.favorites-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.favorite-item {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 20px;
  background: linear-gradient(135deg, #ffffff 0%, #f6efe5 100%);
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  border: 1px solid #e5e7eb;
  transition: all 0.3s;
  cursor: pointer;
}

.favorite-item:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 16px rgba(194, 121, 53, 0.15);
  border-color: #c27935;
}

.product-image-wrapper {
  flex-shrink: 0;
  width: 120px;
  height: 120px;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #e5e7eb;
}

.product-image {
  width: 100%;
  height: 100%;
}

.image-error {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  background: #f5f5f5;
  color: #ccc;
  font-size: 32px;
}

.product-info {
  flex: 1;
  min-width: 0;
}

.product-title {
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
  margin: 0 0 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.product-desc {
  font-size: 14px;
  color: #6b7280;
  margin: 0 0 12px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.product-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.seller-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.seller-avatar {
  border: 1px solid #e5e7eb;
}

.seller-name {
  font-size: 13px;
  color: #4b5563;
  font-weight: 500;
}

.price-rate {
  display: flex;
  align-items: center;
  gap: 12px;
}

.price {
  font-size: 20px;
  font-weight: 700;
  color: #E04E3A;
}

.rate {
  font-size: 14px;
  color: #ffb800;
  font-weight: 600;
}

.actions {
  flex-shrink: 0;
}

.actions :deep(.el-button--danger) {
  background-color: #E04E3A !important;
  border-color: #E04E3A !important;
}

.actions :deep(.el-button--danger:hover) {
  background-color: #9B341F !important;
  border-color: #9B341F !important;
}

@media (max-width: 768px) {
  .favorites-container {
    padding: 16px 20px;
  }

  .favorite-item {
    flex-direction: column;
    align-items: stretch;
  }

  .product-image-wrapper {
    width: 100%;
    height: 200px;
  }

  .product-meta {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }

  .actions {
    width: 100%;
  }

  .actions .el-button {
    width: 100%;
  }
}
</style>