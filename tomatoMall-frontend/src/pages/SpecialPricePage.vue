<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getAllProducts } from '@/api/product'
import HeaderBar from '@/components/HeaderBar.vue'
import NavigationBar from '@/components/NavigationBar.vue'
import ProductCard from "@/components/ProductCard.vue";

const products = ref([])
const loading = ref(false)

const fetchSpecialProducts = async () => {
  loading.value = true
  try {
    const res = await getAllProducts()
    // 只保留价格小于10元的商品
    products.value = (res.data || []).filter(item => Number(item.price) < 10)
  } catch (error) {
    ElMessage.error('获取特价商品失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchSpecialProducts()
})
</script>

<template>
  <div class="favorites-page-wrapper">
    <HeaderBar />
    <div class="favorites-container">
      <div class="page-header">
        <h1>特价书籍</h1>
<!--        <p class="subtitle">共 {{ products.length }} 件宝贝</p>-->
      </div>
      <el-divider />
      <div v-loading="loading" class="favorites-content">
        <div v-if="products.length === 0 && !loading" class="empty-state">
          <el-empty description="暂无特价商品" />
        </div>
        <div v-else class="favorites-grid">
          <ProductCard
              v-for="product in products"
              :key="product.id"
              :product="{
              id: String(product.id),
              title: product.title,
              price: product.price,
              rate: product.rate || 0,
              description: product.description,
              cover: product.cover,
            }"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 直接复用 FavoritesPage.vue 的样式 */
.favorites-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 16px;
  padding: 24px 0;
}
.favorites-page-wrapper { min-height: 100vh; background: repeating-linear-gradient(0deg, #faf8f3 0px, #faf8f3 2px, #f5f1e8 2px, #f5f1e8 4px), linear-gradient(180deg, rgba(255,250,240,0.55) 0%, rgba(246,239,229,0.45) 50%, rgba(239,230,219,0.5) 100%); background-blend-mode: multiply; position: relative; }
.favorites-container { max-width: 1400px; margin: 0 auto; padding: 24px 80px; position: relative; z-index: 1; }
.page-header { text-align: center; margin: 32px 0; }
.page-header h1 { font-size: 32px; font-weight: 700; color: #1f2937; margin: 0 0 8px; }
.subtitle { color: #6b7280; font-size: 16px; margin: 0; }
.favorites-content { min-height: 400px; }
.empty-state { display: flex; justify-content: center; align-items: center; min-height: 400px; padding: 60px 20px; }
</style>