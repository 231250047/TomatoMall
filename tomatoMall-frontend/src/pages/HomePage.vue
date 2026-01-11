<script setup>
sessionStorage.setItem('change','false');
import { ref, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, ShoppingCart } from '@element-plus/icons-vue'
import { getAdvertisements } from '@/api/advertisements'
import { getAllProducts } from '@/api/product'
import { getUserInfo } from '@/api/user'
import { useRouter } from 'vue-router'
import ProductCard from "@/components/ProductCard.vue";
import HeaderBar from "@/components/HeaderBar.vue";
import deco1 from '@/assets/images/deco_1.jpg'
import deco2 from '@/assets/images/deco_2.jpg'
import deco3 from '@/assets/images/deco_3.jpg'
import deco4 from '@/assets/images/deco_4.jpg'
import deco5 from '@/assets/images/deco_5.jpg'
import starIcon from '@/assets/images/star.png'
import bookIcon from '@/assets/images/book.png'
// 添加店铺图片导入
import shop1 from '@/assets/images/shop/shop1.png'
import shop2 from '@/assets/images/shop/shop2.png'
import shop3 from '@/assets/images/shop/shop3.png'
import shop4 from '@/assets/images/shop/shop4.png'
import shop5 from '@/assets/images/shop/shop5.png'
import shop6 from '@/assets/images/shop/shop6.png'
import shop7 from '@/assets/images/shop/shop7.png'
import shop8 from '@/assets/images/shop/shop8.png'
import shop9 from '@/assets/images/shop/shop9.png'

const router = useRouter()

// 用户信息
const userInfo = ref(null)

// 广告数据
const advertisements = ref([])
const loading = ref(false)

// 商品数据
const allProducts = ref([])
const searchKeyword = ref('')
const selectedCategory = ref('all') // 选中的分类，默认为全部

// 分类映射
const categoryMap = {
  all: '全部排行',
  education: '教辅',
  literature: '文学',
  art: '艺术',
  management: '管理',
  history: '历史',
  philosophy: '哲学宗教',
  health: '保健养生',
  science: '科技'
}

// 搜索推荐（死数据）
const searchRecommendations = [
  '软件测试', '红楼梦', '数据结构', '离散数学', '计算机网络',
  '水浒传', '平凡的世界', '机器学习', '三体', '人工智能'
]

// 处理推荐搜索点击
const handleRecommendSearch = (keyword) => {
  searchKeyword.value = keyword
  handleSearch()
}

// 获取用户信息
const fetchUserInfo = async () => {
  try {
    const res = await getUserInfo()
    if (res.data.code === '200') {
      userInfo.value = res.data.data
    }
  } catch (error) {
    console.error('获取用户信息失败:', error)
  }
}

// 获取广告数据
const fetchAdvertisements = async () => {
  try {
    loading.value = true
    const res = await getAdvertisements()
    advertisements.value = res
  } catch (error) {
    ElMessage.error('广告加载失败')
  } finally {
    loading.value = false
  }
}

// 获取所有商品
const fetchProducts = async () => {
  try {
    const res = await getAllProducts()
    allProducts.value = res.data || []
  } catch (error) {
    console.error('获取商品失败:', error)
  }
}

// 推荐商品（随机推荐6个）
const recommendedProducts = computed(() => {
  const products = [...allProducts.value]
  for (let i = products.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [products[i], products[j]] = [products[j], products[i]]
  }
  return products.slice(0, 10)
})

// 当前选中分类的商品（按评分排序）
const currentCategoryProducts = computed(() => {
  let products = []
  if (selectedCategory.value === 'all') {
    // 显示全部商品
    products = [...allProducts.value]
  } else {
    // 显示特定分类的商品
    products = allProducts.value.filter(p => p.tag === selectedCategory.value)
  }
  // 按评分降序排序
  products.sort((a, b) => (b.rate || 0) - (a.rate || 0))
  return products
})

// 在现有的 computed 和 methods 中添加：
const getCategoryCount = (categoryKey) => {
  if (categoryKey === 'all') {
    return allProducts.value.length
  }
  return allProducts.value.filter(p => p.tag === categoryKey).length
}
// 店铺数据
const featuredStores = ref({
  leftStores: [
    [
      { name: '博库网旗舰店', discount: '5折封顶', image: shop1 },
      { name: '中信出版', discount: '好书低至5折', image: shop2 }
    ],
    [
      { name: '中华商务', discount: '正版原版 低至5折', image: shop3 },
      { name: '凯迪克图书旗舰店', discount: '首次关注可领3元券', image: shop4 }
    ]
  ],
  mainStore: {
    name: '文轩网官方旗舰店',
    discount: '—— 低至5折 ——',
    image: shop9
  },
  rightStores: [
    [
      { name: '杂志铺旗舰店', discount: '杂志订阅不止5折', image: shop5 },
      { name: '海豚传媒春日阅读季', discount: '领券享满300减80', image: shop6 }
    ],
    [
      { name: '额尔古纳河右岸', discount: '低至1折起', image: shop7 },
      { name: '读客好书', discount: '全场包邮', image: shop8 }
    ]
  ]
})
// 搜索功能
const handleSearch = () => {
  if (!searchKeyword.value.trim()) {
    ElMessage.warning('请输入搜索关键词')
    return
  }
  router.push({ path: '/products', query: { search: searchKeyword.value.trim() } })
}

// 跳转到购物车
const goToCart = () => {
  router.push('/cart')
}

// 跳转到个人信息
const goToProfile = () => {
  router.push('/information')
}

onMounted(() => {
  fetchUserInfo()
  fetchAdvertisements()
  fetchProducts()
})

const scrollToRecommend = () => {
  const el = document.getElementById('recommend');
  if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' });
};

// 处理广告点击
const handleAdClick = (ad) => {
  if (ad.relatedUrl && ad.relatedUrl.trim()) {
    window.open(ad.relatedUrl, '_blank')
  } else {
    router.push('/advertisements')
  }
}
</script>

<template>
  <div class="home-page-wrapper">
    <!-- 装饰图片（deco1 与 deco3 互换位置） -->
    <img :src="deco5" class="deco-img deco-1" alt="" />
    
    <img :src="deco1" class="deco-img deco-3" alt="" />

    <!-- 顶部标题栏（带用户功能区） -->
    <HeaderBar />

    <!-- 搜索推荐 -->
    <div class="search-recommendations">
      <div class="recommendations-container">
        <span class="recommendations-label">热门搜索：</span>
        <div class="recommendations-tags">
          <el-tag
              v-for="keyword in searchRecommendations"
              :key="keyword"
              @click="handleRecommendSearch(keyword)"
              class="recommendation-tag"
              effect="plain"
          >
            {{ keyword }}
          </el-tag>
        </div>
      </div>
    </div>

    <div class="home-container">
      <!-- 三栏布局：左活动 + 中间广告 + 右功能 -->
      <div class="main-layout">
        <!-- 左侧栏：活动列表 -->
        <div class="left-sidebar">
          <h3 class="sidebar-title"></h3>
          <div class="activity-list">
            <div class="activity-item">
              <div class="activity-content" @click="$router.push('/special-price')">特价书籍</div>
            </div>
            <div class="activity-item">
              <div class="activity-content" @click="$router.push('/new-arrivals')">书架上新</div>
            </div>
            <div class="activity-item">
              <div class="activity-content">限时优惠</div>
            </div>
            <div class="activity-item">
              <div class="activity-content" @click="$router.push('/rank')">热门推荐</div>
            </div>
            <div class="activity-item">
              <div class="activity-content">每日签到</div>
            </div>
          </div>
        </div>

        <!-- 中间：广告轮播 -->
        <div class="center-content">
          <el-carousel
              v-loading="loading"
              :interval="5000"
              height="360px"
              trigger="hover"
              class="ad-carousel"
          >
            <el-carousel-item v-for="(ad, index) in advertisements" :key="index">
              <div class="carousel-content">
                <el-image :src="ad.imgUrl" fit="cover" class="ad-image">
                  <template #error>
                    <div class="image-error">
                      <i class="el-icon-picture-outline"></i>
                      <p>广告图加载失败</p>
                    </div>
                  </template>
                </el-image>
                <!-- 广告文字叠加在图片上 -->
                <div class="ad-overlay">
                  <h3 class="ad-title">{{ ad.title }}</h3>
                  <p class="ad-desc">{{ ad.content }}</p>
                </div>
              </div>
            </el-carousel-item>
          </el-carousel>
        </div>

        <!-- 右侧栏：功能区域 -->
        <div class="right-sidebar">
          <div class="function-item" @click="$router.push('/favorites')">
            <div class="function-icon">⭐</div>
            <div class="function-text">宝藏收藏</div>
          </div>
          <div class="function-item" @click="$router.push('/cart')">
            <div class="function-icon">🛒</div>
            <div class="function-text">收藏的店</div>
          </div>
          <div class="function-item" @click="$router.push('/orders')">
            <div class="function-icon">📦</div>
            <div class="function-text">买过的店</div>
          </div>
          <div class="function-item" @click="$router.push('/history')">
            <div class="function-icon">🕐</div>
            <div class="function-text">我的足迹</div>
          </div>
        </div>
      </div>

      <!-- 猜你喜欢 -->
      <div id="recommend" class="section recommend-section">
        <div class="section-header">
          <h2 class="section-title">
            <img :src="starIcon" class="section-icon" alt="star" />
            猜你喜欢
          </h2>
          <el-button text type="primary" @click="$router.push('/products')" class="view-all-btn">
            查看全部 →
          </el-button>
        </div>
        <div class="product-grid">
          <ProductCard
            v-for="product in recommendedProducts"
            :key="product.id"
            :product="product"
            @click="$router.push(`/products/${product.id}`)"
          />
        </div>
      </div>

      <!-- 分类榜单区域 - 左右布局 -->
      <div class="section category-section">
        <div class="category-layout">
          <!-- 左侧分类导航 -->
          <div class="category-sidebar">
            <h3 class="category-sidebar-title">图书分类</h3>
            <div class="category-nav">
              <div
                  v-for="(tagName, tagKey) in categoryMap"
                  :key="tagKey"
                  :class="['category-nav-item', { active: selectedCategory === tagKey }]"
                  @click="selectedCategory = tagKey"
              >
                <span class="category-name">{{ tagName }}</span>
                <span class="category-count">({{ getCategoryCount(tagKey) }})</span>
              </div>
            </div>
          </div>

          <!-- 右侧商品展示区域 -->
          <div class="category-content">
            <div class="category-header">
              <h2 class="category-title">
                <img :src="bookIcon" class="section-icon" alt="book" />
                {{ categoryMap[selectedCategory] }}
                <span class="count">({{ currentCategoryProducts.length }})</span>
              </h2>
            </div>

            <div class="category-product-grid">
              <ProductCard
                  v-for="product in currentCategoryProducts"
                  :key="product.id"
                  :product="product"
                  @click="$router.push(`/products/${product.id}`)"
              />
            </div>

        <div v-if="currentCategoryProducts.length === 0" class="empty-state">
          <p>该分区暂无商品</p>
        </div>
      </div>
    </div>
  </div>
      <!-- 精选好店 - 动态版本 -->
      <div class="section featured-stores-section">
        <div class="section-header">
          <h2 class="section-title">
            <img :src="starIcon" class="section-icon" alt="star" />
            图书精选好店
          </h2>
        </div>

        <div class="stores-grid">
          <!-- 左侧店铺列表 -->
          <div class="stores-list">
            <div class="store-row" v-for="(row, rowIndex) in featuredStores.leftStores" :key="`left-${rowIndex}`">
              <div class="store-item" v-for="(store, storeIndex) in row" :key="`left-${rowIndex}-${storeIndex}`">
                <div class="store-header">
                  <h3 class="store-name">{{ store.name }}</h3>
                  <span class="store-discount">{{ store.discount }}</span>
                </div>
                <div class="store-product">
                  <img :src="store.image" :alt="store.name + '商品'" class="product-img" />
                </div>
              </div>
            </div>
          </div>

          <!-- 中间主推店铺 -->
          <div class="featured-store">
            <div class="featured-store-header">
              <h2 class="featured-store-name">{{ featuredStores.mainStore.name }}</h2>
              <p class="featured-store-discount">{{ featuredStores.mainStore.discount }}</p>
            </div>
            <div class="featured-store-product">
              <img :src="featuredStores.mainStore.image" :alt="featuredStores.mainStore.name + '主推商品'" class="featured-product-img" />
            </div>
          </div>

          <!-- 右侧店铺列表 -->
          <div class="stores-list">
            <div class="store-row" v-for="(row, rowIndex) in featuredStores.rightStores" :key="`right-${rowIndex}`">
              <div class="store-item" v-for="(store, storeIndex) in row" :key="`right-${rowIndex}-${storeIndex}`">
                <div class="store-header">
                  <h3 class="store-name">{{ store.name }}</h3>
                  <span class="store-discount">{{ store.discount }}</span>
                </div>
                <div class="store-product">
                  <img :src="store.image" :alt="store.name + '商品'" class="product-img" />
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>

</template>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=ZCOOL+QingKe+HuangYou&display=swap');

/* 手写风格标题：用于站点名（如“时光书坊”） */
.site-title,
.site-title h1 {
  font-family: 'ZCOOL QingKe HuangYou', 'Brush Script MT', 'Segoe Script', 'KaiTi', cursive;
  font-weight: 700;
  letter-spacing: 0.6px;
  /* 若需要更手写感可放大下面的值 */
  font-size: 28px;
  line-height: 1.05;
}

.home-page-wrapper {
  min-height: 100vh;
  /* 保持条格可见：把条纹放在上层、渐变设置为半透明，并启用混合模式 */
  /* 条纹在上，渐变从上深到下浅：顶部带一点暖棕，向下过渡到更浅的纸张米色 */
  background:
    repeating-linear-gradient(0deg, #faf8f3 0px, #faf8f3 2px, #f5f1e8 2px, #f5f1e8 4px),
    linear-gradient(180deg, rgba(211, 125, 63, 0.16) 0%, rgba(217, 138, 82, 0.08) 50%, rgba(255,250,240,0.03) 100%);
  background-blend-mode: multiply;
  position: relative;
}

.deco-img {
  position: fixed;
  z-index: 0;
  opacity: 0.15;
  pointer-events: none;
  mix-blend-mode: multiply;
}

.deco-1 {
  top: 120px;
  right: 100px;
  width: 360px;
  transform: rotate(-15deg);
}

.deco-2 {
  bottom: 100px;
  left: 80px;
  width: 180px;
  transform: rotate(20deg);
}

.deco-3 {
  top: 450px;
  left: 50px;
  width: 150px;
  transform: rotate(10deg);
}

.home-container {
  padding: 0 80px; /* 左右留白，悬浮组件宽度约70px + 边距 */
  position: relative;
  z-index: 1;
}

/* Header 样式已统一到 `HeaderBar.vue` 组件中，页面不再保留重复样式 */

/* 广告轮播 */
.ad-carousel {
  max-width: 1400px;
  margin: 24px auto;
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
}

.carousel-content {
  display: flex;
  height: 100%;
}

/* 左半边图片区域 */
.ad-image-section {
  flex: 1;
  overflow: hidden;
}

.ad-image-section .ad-image {
  width: 100%;
  height: 100%;
}

/* 右半边内容区域 */
.ad-content-section {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 48px 40px;
  background: linear-gradient(135deg, #fffaf0 0%, #f6efe5 100%);
}

.ad-title {
  font-size: 32px;
  font-weight: 700;
  margin: 0 0 16px;
  color: #1f2937;
  line-height: 1.2;
}

.ad-desc {
  font-size: 16px;
  margin: 0 0 32px;
  color: #4b5563;
  line-height: 1.6;
  flex: 1;
}

.image-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #999;
  background: #f5f5f5;
}

/* 内容区域 */
.section {
  max-width: 1600px;
  margin: 0 auto;
  padding: 48px 24px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.section-title {
  font-size: 24px;
  font-weight: 700;
  color: #1f2937;
  margin: 0;
  display: flex;
  align-items: center;
  gap: 2px; /* 缩小图标与文字之间的间距 */
}

.section-title .icon {
  font-size: 28px;
}

.section-title .count {
  font-size: 18px;
  color: #6b7280;
  font-weight: 500;
  margin-left: 8px;
}

/* 分类榜单区域 - 左右布局 */
.category-layout {
  display: flex;
  gap: 24px;
  max-width: 1400px;
  margin: 0 auto;
  min-height: 600px;
}

/* 左侧分类导航 */
.category-sidebar {
  width: 220px;
  background: white;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  border: 1px solid #e5e7eb;
  overflow: hidden;
  height: fit-content;
  position: sticky;
  top: 20px;
}

.category-sidebar-title {
  background: linear-gradient(135deg, #c27935 0%, #d4874a 100%);
  color: white;
  text-align: center;
  margin: 0;
  padding: 16px;
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 0.5px;
}

.category-nav {
  padding: 8px 0;
}

.category-nav-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 20px;
  cursor: pointer;
  transition: all 0.3s ease;
  border-bottom: 1px solid #f8f9fa;
  color: #4a5568;
  font-size: 14px;
}

.category-nav-item:hover {
  background: linear-gradient(135deg, #fffbf5 0%, #f8f4ed 100%);
  color: #c27935;
  transform: translateX(4px);
}

.category-nav-item.active {
  background: linear-gradient(135deg, #c27935 0%, #d4874a 100%);
  color: white;
  font-weight: 600;
  box-shadow: inset 4px 0 0 #9B341F;
}

.category-nav-item.active:hover {
  background: linear-gradient(135deg, #9B341F 0%, #c27935 100%);
  transform: translateX(0);
}

.category-name {
  flex: 1;
}

.category-count {
  font-size: 12px;
  opacity: 0.8;
  margin-left: 8px;
}

.category-nav-item.active .category-count {
  opacity: 0.9;
}

/* 右侧商品展示区域 */
.category-content {
  flex: 1;
  background: white;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  border: 1px solid #e5e7eb;
  padding: 24px;
}

.category-header {
  margin-bottom: 24px;
  padding-bottom: 16px;
  border-bottom: 2px solid #f1f3f4;
}

.category-title {
  font-size: 24px;
  font-weight: 700;
  color: #1f2937;
  margin: 0;
  display: flex;
  align-items: center;
  gap: 8px;
}

.category-title .count {
  font-size: 16px;
  color: #6b7280;
  font-weight: 500;
  margin-left: 8px;
}

.category-product-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 16px;
  height: auto; /* 动态调整高度  //min-height: 400px; */
}

/* 空状态样式 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 300px;
  color: #9ca3af;
  font-size: 16px;
}

.empty-state::before {
  content: "📚";
  font-size: 48px;
  margin-bottom: 16px;
  opacity: 0.5;
}

/* 移除原来的分类选择器样式 */
.category-selector {
  display: none;
}

/* 响应式设计 */
@media (max-width: 1024px) {
  .category-layout {
    flex-direction: column;
    gap: 16px;
  }

  .category-sidebar {
    width: 100%;
    position: static;
  }

  .category-nav {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    padding: 16px;
  }

  .category-nav-item {
    flex: 0 0 auto;
    padding: 8px 16px;
    border-radius: 20px;
    border: 1px solid #e5e7eb;
    border-bottom: 1px solid #e5e7eb;
    background: white;
  }

  .category-nav-item:hover {
    transform: translateY(-2px);
    transform: translateX(0);
  }

  .category-nav-item.active {
    box-shadow: none;
  }
}

@media (max-width: 768px) {
  .category-product-grid {
    grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
    gap: 12px;
  }

  .category-content {
    padding: 16px;
  }

  .category-title {
    font-size: 20px;
  }
}

/* 精选好店区域 */
.featured-stores-section {
  background: linear-gradient(135deg, #fffbf5 0%, #f8f4ed 100%);
  border-radius: 16px;
  margin-top: 48px;
  padding: 32px 24px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.06);
}

.stores-grid {
  display: flex;
  gap: 24px;
  align-items: stretch;
  max-width: 1400px;
  margin: 0 auto;
}

/* 左右两侧店铺列表 */
.stores-list {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.store-row {
  display: flex;
  gap: 16px;
}

.store-item {
  flex: 1;
  background: white;
  border-radius: 12px;
  padding: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  border: 1px solid #e5e7eb;
  transition: all 0.3s ease;
  cursor: pointer;
}

.store-item:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 25px rgba(0, 0, 0, 0.12);
  border-color: #c27935;
}

.store-header {
  margin-bottom: 12px;
}

.store-name {
  font-size: 14px;
  font-weight: 600;
  color: #1f2937;
  margin: 0 0 4px 0;
  line-height: 1.3;
}

.store-discount {
  font-size: 12px;
  color: #dc2626;
  font-weight: 500;
  background: linear-gradient(135deg, #fef2f2 0%, #fee2e2 100%);
  padding: 2px 8px;
  border-radius: 4px;
  display: inline-block;
}

.store-product {
  display: flex;
  justify-content: center;
}

.product-img {
  width: 60px;
  height: 75px;
  object-fit: cover;
  border-radius: 6px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

/* 中间主推店铺 */
.featured-store {
  flex: 0 0 300px;
  background: linear-gradient(135deg, #f6efe5 0%, #ede0d3 100%);
  border-radius: 16px;
  padding: 24px;
  text-align: center;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
  border: 2px solid #c27935;
  position: relative;
  overflow: hidden;
}

.featured-store::before {
  content: '';
  position: absolute;
  top: -50%;
  left: -50%;
  width: 200%;
  height: 200%;
  background: radial-gradient(circle, rgba(194, 121, 53, 0.05) 0%, transparent 70%);
  animation: rotate 20s linear infinite;
}

@keyframes rotate {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.featured-store-header {
  position: relative;
  z-index: 1;
  margin-bottom: 20px;
}

.featured-store-name {
  font-size: 20px;
  font-weight: 700;
  color: #8b5a2b;
  margin: 0 0 8px 0;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.1);
}

.featured-store-discount {
  font-size: 14px;
  color: #c27935;
  font-weight: 600;
  margin: 0;
  letter-spacing: 1px;
}

.featured-store-product {
  position: relative;
  z-index: 1;
}

.featured-product-img {
  width: 120px;
  height: 150px;
  object-fit: cover;
  border-radius: 12px;
  box-shadow: 0 8px 25px rgba(0, 0, 0, 0.15);
  transition: transform 0.3s ease;
}

.featured-store:hover .featured-product-img {
  transform: scale(1.05);
}

/* 响应式设计 */
@media (max-width: 1024px) {
  .stores-grid {
    flex-direction: column;
    gap: 20px;
  }

  .featured-store {
    flex: none;
    order: -1;
  }

  .stores-list {
    flex-direction: row;
    overflow-x: auto;
    padding-bottom: 8px;
  }

  .store-row {
    flex: none;
    min-width: 200px;
  }

  .store-row:last-child {
    margin-right: 16px;
  }
}

@media (max-width: 768px) {
  .featured-stores-section {
    padding: 20px 16px;
    margin-top: 32px;
  }

  .stores-grid {
    gap: 16px;
  }

  .store-item {
    padding: 12px;
  }

  .store-name {
    font-size: 13px;
  }

  .store-discount {
    font-size: 11px;
  }

  .product-img {
    width: 50px;
    height: 60px;
  }

  .featured-store {
    padding: 20px;
  }

  .featured-store-name {
    font-size: 18px;
  }

  .featured-product-img {
    width: 100px;
    height: 125px;
  }
}
/* 空状态 */
.empty-state {
  text-align: center;
  padding: 80px 20px;
  color: #9ca3af;
  font-size: 16px;
}

.product-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr); /* 固定5列 */
  grid-template-rows: repeat(2, auto);   /* 固定2行 */
  gap: 16px;
  max-width: 1200px; /* 限制最大宽度，避免卡片过宽 */
  margin: 0 auto;    /* 居中显示 */
}

/* 分类区域间隔 */
.category-section {
  border-top: 1px solid #e5e7eb;
}

.category-section:first-of-type {
  border-top: none;
}

/* 替换搜索按钮相关颜色为浮动按钮色 */
:deep(.search-button),
:deep(.search-bar .el-button),
:deep(.header-search .el-button) {
  background: transparent !important;
  border: 1px solid rgba(255,255,255,0.95) !important;
  color: #ffffff !important;
  box-shadow: none !important;
  backdrop-filter: none !important;
}

/* 悬停时轻微填色以示反馈（使用浮动 hover 色） */
:deep(.search-button:hover),
:deep(.search-bar .el-button:hover),
:deep(.header-search .el-button:hover) {
  background: rgba(255,255,255,0.06) !important;
  border-color: #ffffff !important;
}

/* 若搜索按钮为图标形式，确保图标颜色与文字一致 */
:deep(.search-button .el-icon),
:deep(.search-bar .el-button .el-icon),
:deep(.header-search .el-button .el-icon) {
  color: #ffffff !important;
}

/* 响应式 */
@media (max-width: 1200px) {
  .header-content {
    flex-wrap: wrap;
  }
  
  .search-bar {
    order: 3;
    flex-basis: 100%;
    max-width: none;
  }
  
  .product-grid {
    grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  }
}

@media (max-width: 768px) {
  .header-section {
    padding: 16px 20px;
  }
  
  .header-content {
    gap: 16px;
  }
  
  .site-title h1 {
    font-size: 22px;
  }
  
  .subtitle {
    font-size: 12px;
  }
  
  .ad-carousel {
    height: 280px;
    margin: 16px;
  }
  
  .section {
    padding: 32px 16px;
  }
  
  .section-title {
    font-size: 20px;
  }

  .product-grid {
    grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
    gap: 12px;
  }
  
  .carousel-content {
    flex-direction: column;
  }
  
  .ad-image-section {
    height: 50%;
  }
  
  .ad-content-section {
    height: 50%;
    padding: 24px 20px;
  }
  
  .ad-title {
    font-size: 22px;
  }
  
  .ad-desc {
    font-size: 14px;
    margin-bottom: 16px;
  }
}

.ad-btn {
  background-color: #c27935 !important;
  border-color: #c27935 !important;
  min-width: 160px;
  align-self: flex-start;
}
.ad-btn:hover {
  background-color: #9B341F !important;
  border-color: #9B341F !important;
}

.view-all-btn {
  color: #c27935 !important;
}
.view-all-btn:hover {
  color: #9B341F !important;
}

.section-icon {
  width: 52px;
  height: 52px;
  object-fit: contain;
  display: inline-block;
  margin-right: 4px; /* 更靠近文字 */
  vertical-align: -4px; /* 微调垂直对齐，使图标与文字更贴合 */
}

@media (max-width: 768px) {
  .section-icon {
    width: 36px;
    height: 36px;
    margin-right: 4px;
    vertical-align: -3px;
  }
}

/* 搜索推荐区域 */
.search-recommendations {
  background: linear-gradient(135deg, #fffbf5 0%, #f8f4ed 100%);
  border-bottom: 1px solid rgba(226, 214, 196, 0.3);
  padding: 16px 0;
  position: relative;
  z-index: 1;
}

.recommendations-container {
  max-width: 1400px;
  margin: 0 auto;
  padding: 0 80px;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}

.recommendations-label {
  color: #8b5a2b;
  font-size: 14px;
  font-weight: 900;
  margin-right: 8px;
  white-space: nowrap;
}

.recommendations-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  flex: 1;
}

.recommendation-tag {
  cursor: pointer;
  transition: all 0.3s ease;
  background: rgba(194, 121, 53, 0.08);
  border-color: rgba(194, 121, 53, 0.2);
  color: #8b5a2b;
  font-size: 13px;
  padding: 4px 12px;
}

.recommendation-tag:hover {
  background: rgba(194, 121, 53, 0.15);
  border-color: #c27935;
  color: #7a4f30;
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(194, 121, 53, 0.12);
}

/* 三栏主布局 */
.main-layout {
  display: flex;
  gap: 4px;
  max-width: 1100px;
  margin: 24px auto;
  align-items: stretch;
}

/* 左侧活动栏 */
.left-sidebar {
  width: 200px;
  height: 100%;
  background: white;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
  border: 1px solid #e5e7eb;
  overflow: hidden;
  margin-top: 30px; /* 添加顶部间距，让它与左侧栏底部对齐 */
}

.sidebar-title {
  background: #c27935;
  color: white;
  text-align: center;
  margin: 0;
  padding: 16px;
  font-size: 16px;
  font-weight: 600;
}

.activity-list {
  padding: 0;
}

.activity-item {
  border-bottom: 1px solid #f3f4f6;
  transition: background-color 0.3s;
  cursor: pointer;
}

.activity-item:hover {
  background-color: #fef7f0;
}

.activity-item:last-child {
  border-bottom: none;
}

.activity-content {
  padding: 20px 16px;
  font-size: 14px;
  color: #4b5563;
  text-align: center;
}

/* 中间广告区域 */
.center-content {
  flex: 1;
  min-width: 0;
}

.ad-carousel {
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
  height: 360px;
  width: 700px; /* 固定宽度，间距才明显 */
}

.carousel-content {
  position: relative;
  height: 100%;
}

.ad-image {
  width: 100%;
  height: 100%;
}

/* 广告文字叠加层 */
.ad-overlay {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  background: linear-gradient(transparent, rgba(0,0,0,0.7));
  color: white;
  padding: 40px 32px 32px;
}

.ad-overlay .ad-title {
  font-size: 24px;
  font-weight: 700;
  margin: 0 0 8px;
}

.ad-overlay .ad-desc {
  font-size: 14px;
  margin: 0 0 16px;
  opacity: 0.9;
}

.ad-overlay .ad-btn {
  background-color: #c27935 !important;
  border-color: #c27935 !important;
}

.ad-overlay .ad-btn:hover {
  background-color: #9B341F !important;
  border-color: #9B341F !important;
}

/* 右侧功能栏 */
.right-sidebar {
  width: 120px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 27px; /* 添加顶部间距，让它与左侧栏底部对齐 */
}

.function-item {
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
  border: 1px solid #e5e7eb;
  padding: 16px 12px;
  text-align: center;
  cursor: pointer;
  transition: all 0.3s;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.function-item:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(194, 121, 53, 0.15);
  border-color: #c27935;
}

.function-icon {
  font-size: 24px;
  line-height: 1;
}

.function-text {
  font-size: 12px;
  color: #4b5563;
  font-weight: 500;
  line-height: 1.2;
}
</style>

