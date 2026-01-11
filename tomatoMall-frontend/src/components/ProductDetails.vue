<script setup>
import {ref, onMounted, computed} from 'vue'
import {ElMessage, ElLoading, ElMessageBox} from 'element-plus'
import {Picture, ShoppingCart} from '@element-plus/icons-vue'
import {deleteProduct, getProduct, getStockpile, updateStockpile} from '../api/product'
import {addToCart, getCart, changeAmount} from "../api/cart"
import router from "@/router/index.js";
import {addComment, deleteComment,getComment} from "@/api/comment.js";
import { addFavorite, removeFavorite, checkFavorite } from '@/api/favorites'

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

// 成色显示映射（避免模板引用时未定义）
const conditionMap = {
  '10_NEW': '10成新',
  '9_NEW': '9成新', // 新增映射
  '8_NEW': '8成新',
  '5_NEW': '5成新',
  '4_NEW': '4成新',
  'OLD': '旧'
}

const props = defineProps({
  id: {
    type: String,
    required: true
  }
})




// 收藏相关状态
const isFavorited = ref(false)
const favoriteLoading = ref(false)

// 检查收藏状态
const checkFavoriteStatus = async () => {
  try {
    const res = await checkFavorite(props.id)
    if (res.code === '200') {
      isFavorited.value = res.data
    }
  } catch (error) {
    console.error('检查收藏状态失败:', error)
  }
}

// 切换收藏状态
const toggleFavorite = async () => {
  const token = sessionStorage.getItem('token')
  if (!token) {
    ElMessage.warning('请先登录')
    router.push('/login')
    return
  }

  favoriteLoading.value = true

  try {
    if (isFavorited.value) {
      const res = await removeFavorite(props.id)
      if (res.code === '200') {
        isFavorited.value = false
        ElMessage.success('已取消收藏')
      } else {
        ElMessage.error(res.msg || '取消收藏失败')
      }
    } else {
      const res = await addFavorite(props.id)
      if (res.code === '200') {
        isFavorited.value = true
        ElMessage.success('收藏成功')
      } else {
        ElMessage.error(res.msg || '收藏失败')
      }
    }
  } catch (error) {
    console.error('收藏操作失败:', error)
    ElMessage.error('操作失败，请重试')
  } finally {
    favoriteLoading.value = false
  }
}

// ...existing code...

onMounted(async () => {
  await loadProductDetail()
  fetchComments(props.id)
  checkFavoriteStatus() // 添加：检查收藏状态
})



// 商品详情数据
const productDetail = ref({
  id: '',
  title: '',
  price: 0,
  rate: 0,
  description: '',
  cover: '',
  detail: '',
  specifications: [],
  tag: '',
  condition: '' // 新增：成色字段
})

// 添加到购物车（带 409 并发冲突重试提示）
const tryAddCart = (productId, quantity, retries = 1) => {
  if (quantity > stockpile.value.amount) {
    ElMessage({ message: "库存不足！", type: 'error', center: true })
    return
  }

  addToCart(productId, quantity)
    .then(res => {
      if (res.code === "200") {
        ElMessage({ message: "添加成功！", type: 'success', center: true })
      }
    })
    .catch(err => {
      const status = err?.response?.status
      // 409：同一商品已存在购物车，自动改为增加数量
      if (status === 409) {
        getCart().then(cartRes => {
          const items = cartRes?.data?.items || []
          const existing = items.find(x => String(x.productId) === String(productId))
          if (existing) {
            const newQty = (existing.quantity || 0) + quantity
            changeAmount(String(existing.cartItemId), newQty).then(updateRes => {
              if (updateRes.code === "200") {
                ElMessage({ message: "已在购物车，数量已增加", type: 'success', center: true })
              } else {
                ElMessage({ message: updateRes.msg || "数量增加失败", type: 'error', center: true })
              }
            }).catch(() => {
              ElMessage({ message: "数量增加失败", type: 'error', center: true })
            })
          } else {
            // 未找到则退回一次轻量重试
            if (retries > 0) tryAddCart(productId, quantity, retries - 1)
          }
        }).catch(() => {
          // 获取购物车失败：退回一次轻量重试
          if (retries > 0) tryAddCart(productId, quantity, retries - 1)
        })
      }
      // API 层已弹出错误提示，这里仅避免未处理异常
      console.warn('addToCart failed', err)
    })
}
// 卖家 & 库存信息
const sellerInfo = ref({ id: null, name: '' })
const stockpile = ref({ amount: 0, frozen: 0 })

// 加载商品详情的函数
const loadProductDetail = async () => {
  const loading = ElLoading.service({
    lock: true,
    text: '加载中...'
  })

  try {
    const [productRes, stockRes] = await Promise.all([
      getProduct(props.id), // 获取商品详情
      getStockpile(props.id)  // 获取库存
    ])

    if (productRes.code === "200") {
      productDetail.value = productRes.data

      if (productRes.data && productRes.data.sellerId) {
        sellerInfo.value.id = productRes.data.sellerId
      }

      if (!productRes.data.sellerAvatar || productRes.data.sellerAvatar === '') {
        const isOld = !productRes.data.sellerId || productRes.data.sellerId === 'old';
        const initial = isOld ? '旧' : 'U';
        const bgColor = isOld ? 'cccccc' : '3b82f6';
        const avatarUrl = `data:image/svg+xml,%3Csvg xmlns=%22http://www.w3.org/2000/svg%22 width=%2248%22 height=%2248%22%3E%3Crect fill=%22%23${bgColor}%22 width=%2248%22 height=%2248%22 rx=%226%22/%3E%3Ctext fill=%22%23ffffff%22 font-family=%22Arial%22 font-size=%2224%22 font-weight=%22bold%22 x=%2250%25%22 y=%2250%25%22 text-anchor=%22middle%22 dy=%220.35em%22%3E${initial}%3C/text%3E%3C/svg%3E`;
        productDetail.value.sellerAvatar = avatarUrl;
      } else {
        // 使用后端返回的真实头像
      }

      if (productRes.data.sellerName) {
        sellerInfo.value.name = productRes.data.sellerName;
      }
    } else {
      throw new Error(productRes.msg || '商品数据异常');
    }

    if (stockRes.code === "200") {
      if (stockRes.data == null) {
        stockpile.value.amount = 0;
        stockpile.value.frozen = 0;
      } else {
        stockpile.value = stockRes.data;
      }
    } else {
      throw new Error(stockRes.msg || '库存数据异常');
    }
  } catch (error) {
    ElMessage.error(error.response?.data?.msg || '数据加载失败')
  } finally {
    loading.close()
  }
}

// 格式化价格显示
const formatPrice = (price) => {
  return `¥${price.toFixed(2)}`
}

// 页面加载时获取数据
onMounted(async () => {
  await loadProductDetail()
  fetchComments(props.id)
})

//判断是否处于库存管理模式下
const isAdmin = sessionStorage.getItem('change') === 'true';
const username = sessionStorage.getItem('username')

// 新增：判断是否是当前用户发布的商品
const isOwnProduct = computed(() => {
  return productDetail.value.sellerName === username
})

//删除商品
const handleDelete = () => {
  deleteProduct(props.id).then(res => {
    if(res.code === "200") {
      ElMessage({
        message: "删除成功！",
        type: 'success',
        center: true,
      })

    }else if(res.code === "400") {
      ElMessage({
        message: "商品不存在",
        type: 'error',
        center: true,
      })

    }

  })
  router.push('/warehouse').then(()=>{
    window.location.reload();
  });

}

const dialogVisible = ref(false); // 控制弹框显示与否
const handleCheckStock =() => {
  dialogVisible.value = true; // 显示弹框
}

const handleUpdateStock=()=>{
  updateStockpile(props.id,stockpile.value.amount).then(res => {
    if(res.code === "200") {
      ElMessage({
        message: "调整库存成功！",
        type: 'success',
        center: true,
      })
    }else if(res.code === "400") {
      ElMessage({
        message: "商品不存在！",
        type: 'error',
        center: true,
      })
    }
  })

}

//修改信息
const handleUpdateInfo =(id) =>{
  router.push(`/updateProduct/${id}`);
}



const cart_quantity = ref(1);
// 兼容旧调用：封装到重试版本
const addCart = (productId, quantity) => tryAddCart(productId, quantity)


//添加评论
const commentInput = ref('')
const addAComment = (productId,commentStr) => {
  addComment(productId,commentStr).then(res=>{
    if(res.code === "200") {
      ElMessage({
        message: "添加成功！",
        type: 'success',
        center: true,
      })
    }else{
      ElMessage({
        message: "添加失败！",
        type: 'error',
        center: true,
      })

    }

  })
  window.location.reload();//强制刷新页面
}
//删除评论
const deleteAComment = (commentId) => {
  deleteComment(commentId).then(res=>{
    if(res.code === "200") {
      ElMessage({
        message: "删除成功！",
        type: 'success',
        center: true,
      })
    }else{
      ElMessage({
        message: "删除失败！",
        type: 'error',
        center: true,
      })

    }
  })
  console.log('评论id',commentId)
  window.location.reload();//强制刷新页面
}
//获取评论
const commentList = ref([])
const fetchComments = (productId) => {
  getComment(productId).then(res=>{
    if(res.code === "200") {
      commentList.value = res.data.map(x => {
        return {
          id: x.id,
          productId: x.productId,
          username: x.username,
          commentStr:x.commentStr
        };
      });
      console.log('评论区内容：',res.data);

    }else{
      ElMessage({
        message: "展示评论区失败！",
        type: 'error',
        center: true,
      })

    }
  })

}

// 修改：startChat 传递真实的 sellerName（username）
const startChat = (sellerName = '') => {
  if (!sellerName) {
    ElMessage.error('无法发起聊天：无效的商家信息')
    return
  }
  router.push({ 
    name: 'chat', 
    params: { sellerId: sellerName }
  })
}
</script>
<template>
  <div class="product-container">
    <!-- 管理者模式下，右上角的管理按钮 -->
    <div v-if="isAdmin" class="action-buttons">
      <el-button @click="handleDelete" type="danger" size="small">删除</el-button>
      <el-button @click="handleCheckStock" type="primary" size="small">查询库存</el-button>
      <el-button @click="handleUpdateInfo(props.id)" type="success" size="small">更新信息</el-button>
    </div>

    <!-- 非管理模式下，底部固定栏显示购物车功能 -->
    <div v-if="!isAdmin" class="bottom-cart-bar">
      <div class="cart-bar-content">
        <div class="cart-info">
          <div class="cart-price">{{ formatPrice(productDetail.price) }}</div>
          <div class="cart-stock">库存：{{ stockpile.amount }}</div>
        </div>
        <div class="cart-actions">
          <el-input-number
              v-model="cart_quantity"
              :min="1"
              :max="Math.max(1, stockpile.amount)"
              :disabled="stockpile.amount === 0"
              size="large"
              class="quantity-input"
          />
          <el-button 
            @click="addCart(props.id, cart_quantity)" 
            type="primary" 
            size="large" 
            class="add-cart-btn"
            :disabled="stockpile.amount === 0"
          >
            <el-icon style="margin-right: 8px;"><ShoppingCart /></el-icon>
            {{ stockpile.amount === 0 ? '已售罄' : '加入购物车' }}
          </el-button>

          <!-- 收藏按钮 -->
          <el-button
              :type="isFavorited ? 'warning' : ''"
              :plain="!isFavorited"
              size="large"
              class="favorite-btn"
              :loading="favoriteLoading"
              @click="toggleFavorite"
          >
            {{ isFavorited ? '已收藏' : '收藏' }}
          </el-button>


        </div>
      </div>
    </div>


    <!-- 显示库存-->
    <div v-if="dialogVisible" title="库存管理" >
      <div>
        <p>当前库存数量: <strong>{{ stockpile.amount }}</strong></p>
        <p>冻结数量: <strong>{{ stockpile.frozen }}</strong></p>
        <el-input-number v-model="stockpile.amount" :min="0" label="修改库存数量" style="width: 100%;" />
      </div>
      <span slot="footer" class="dialog-footer">
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleUpdateStock">确定</el-button>
      </span>
    </div>


    <!-- 商品主区域 -->
    <div class="product-main">
      <!-- 商品图片 -->
      <div class="product-image">
        <el-image
            v-if="productDetail.cover"
            :src="productDetail.cover"
            fit="contain"
            class="cover-image"
            :preview-src-list="[productDetail.cover]"
        >
          <template #error>
            <div class="image-slot">
              <el-icon :size="60"><Picture /></el-icon>
              <p>图片加载失败</p>
            </div>
          </template>
        </el-image>
        <div v-else class="image-slot">
          <el-icon :size="60"><Picture /></el-icon>
          <p>暂无封面</p>
        </div>
      </div>
      <!-- 商品描述区 -->
      <div class="product-info">
        <!-- 右上角成色徽章 -->
        <span :class="['condition-badge', `cond-${productDetail.condition}`]" v-if="productDetail.condition">
          {{ conditionMap[productDetail.condition] || productDetail.condition }}
        </span>
        <h1 class="title">{{ productDetail.title }}</h1>

        <div class="price-rate">
          <span class="price">{{ formatPrice(productDetail.price) }}</span>
          <span class="rate" v-if="productDetail.rate">
          ⭐ {{ productDetail.rate.toFixed(1) }}
        </span>
        </div>
        <div class="seller-block">
          <el-avatar :size="48" :src="productDetail.sellerAvatar" class="seller-avatar">
            <template #default>
              <span style="font-size: 16px; font-weight: 600;">{{ productDetail.sellerName?.[0] || '旧' }}</span>
            </template>
          </el-avatar>
          <div class="seller-meta">
            <div class="seller-name">{{ productDetail.sellerName || '旧商品（无卖家信息）' }}</div>
            <el-button v-if="productDetail.sellerId" link size="small" @click="() => $router.push({ path: '/products', query: { sellerId: productDetail.sellerId } })">查看该商家发布</el-button>
          </div>

          <!-- 新增：将聊一聊按钮放在右侧容器，靠右显示 -->
          <div class="seller-actions">
            <el-button
              v-if="productDetail.sellerId && !isAdmin && !isOwnProduct"
              class="chat-btn"
              size="small"
              @click="startChat(productDetail.sellerName || '')"
            >
              在线客服
            </el-button>
          </div>
        </div>
        <!-- 新增分类展示 -->
        <div class="category-tag">
          <el-tag type="info" size="large">
            {{ categoryMap[productDetail.tag] || '未分类' }}
          </el-tag>
        </div>
        <p class="description" v-if="productDetail.description">
          {{ productDetail.description }}
        </p>
        <div class="detail" v-if="productDetail.detail">
          <h3>商品详情</h3>
          <p>{{ productDetail.detail }}</p>
        </div>
      </div>
    </div>

    <!-- 规格参数表格 -->
    <div class="specifications" v-if="productDetail.specifications?.length">
      <el-table
          :data="productDetail.specifications"
          border
          size="small"
          :show-header="false"
      >
        <el-table-column prop="item" width="120" />
        <el-table-column prop="value" />
      </el-table>
    </div>


    <!-- 评论区 -->
    <div class="comment-section">
      <h3>商品评论</h3>

      <!-- 评论输入框 -->
      <div class="comment-input">
        <el-input
            v-model="commentInput"
            type="textarea"
            :rows="3"
            placeholder="请输入您的评论..."
            maxlength="200"
            show-word-limit
        />
        <div class="submit-btn">
          <el-button
              type="primary"
              @click="addAComment(props.id, commentInput)"
              :disabled="!commentInput.trim()"
          >
            发表评论
          </el-button>
        </div>
      </div>

      <!-- 评论列表 -->
      <div class="comment-list">
        <div v-if="commentList.length === 0" class="no-comments">
          暂无评论，快来发表第一条评论吧~
        </div>

        <div v-for="comment in commentList" :key="comment.id" class="comment-item">
          <div class="comment-content">{{ comment.commentStr }}</div>

          <!-- 删除按钮（管理员或评论所有者可见） -->

          <div class="comment-actions">
            <el-button
                v-if="isAdmin || comment.username === username"
                type="danger"
                size="small"
                @click="deleteAComment(comment.id)"
            >
              删除
            </el-button>
          </div>
        </div>
      </div>
    </div>


  </div>
</template>


<style scoped>
.product-container {
  max-width: 1280px;
  margin: 0 auto;
  padding: 32px 80px 120px;
  background: 
    linear-gradient(90deg, transparent 0%, rgba(139, 69, 19, 0.03) 50%, transparent 100%),
    repeating-linear-gradient(0deg, #faf8f3 0px, #faf8f3 2px, #f5f1e8 2px, #f5f1e8 4px);
  min-height: 100vh;
  position: relative;
}

.product-container::before {
  content: '☕';
  position: fixed;
  top: 200px;
  right: 120px;
  font-size: 80px;
  opacity: 0.06;
  transform: rotate(-15deg);
  z-index: 0;
}

.product-container::after {
  content: '🖊️';
  position: fixed;
  bottom: 150px;
  left: 100px;
  font-size: 70px;
  opacity: 0.06;
  transform: rotate(20deg);
  z-index: 0;
}

/* 商品主区域布局 */
.product-main {
  display: flex;
  gap: 40px;
  margin-bottom: 40px;
  background: linear-gradient(135deg, #ffffff 0%, #f6efe5 100%);
  padding: 32px;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(245, 158, 11, 0.1);
  position: relative;
  z-index: 1;
}

.product-image {
  flex: 0 0 420px;
  border: 1px solid #e5e7eb;
  padding: 16px;
  border-radius: 8px;
  background: #fafbfd;
}

.cover-image {
  width: 100%;
  height: 420px;
  display: block;
  object-fit: cover;
  border-radius: 6px;
}

/* 图片加载失败占位样式 */
.image-slot {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  width: 100%;
  height: 350px;
  background: #f5f7fa;
  color: #909399;
}

.image-slot p {
  margin-top: 10px;
  font-size: 14px;
}

.product-info {
  flex: 1;
  position: relative; /* 为右上角徽章定位提供参照 */
}

/* 成色右上角徽章 */
.condition-badge {
  position: absolute;
  top: 20px;
  right: 20px;
  /* 橙色渐变背景，与主题呼应 */
  background: linear-gradient(135deg, #c27935 0%, #d97706 100%);
  color: #ffffff;
  padding: 6px 14px;
  /* 不对称圆角设计，增加设计感 */
  border-radius: 12px 2px 12px 2px;
  font-weight: 600;
  font-size: 14px;
  /* 增加柔和阴影 */
  box-shadow: 0 4px 12px rgba(194, 121, 53, 0.3);
  z-index: 2;
  /* 微弱的内边框增加质感 */
  border: 1px solid rgba(255, 255, 255, 0.2);
  letter-spacing: 0.5px;
}

/* 不同成色使用不同字体风格：越旧越潦草越接近手写 */
.cond-10_NEW {
  font-family: "Helvetica Neue", Arial, sans-serif;
  font-weight: 700;
  letter-spacing: 0.5px;
  transform: none;
}

.cond-9_NEW {
  font-family: "Helvetica Neue", Arial, sans-serif;
  font-weight: 700;
  letter-spacing: 0.4px;
  transform: translateY(0.5px);
  font-size: 14px;
  opacity: 0.98;
}

.cond-8_NEW {
  font-family: Georgia, "Times New Roman", serif;
  font-style: italic;
  font-weight: 600;
  letter-spacing: 0.6px;
  transform: translateY(1px) rotate(-1deg);
}

.cond-5_NEW {
  font-family: "Segoe Script", "Bradley Hand", "Brush Script MT", cursive;
  font-weight: 600;
  letter-spacing: 0.8px;
  transform: translateY(2px) rotate(-2deg);
  font-size: 15px;
}

.cond-4_NEW {
  font-family: "Bradley Hand", "Brush Script MT", "Lucida Handwriting", cursive;
  font-weight: 500;
  letter-spacing: 1px;
  transform: translateY(3px) rotate(-3deg);
  opacity: 0.95;
}

.cond-OLD {
  /* 最潦草、最接近手写：使用手写/涂鸦风格回退字体并增加倾斜、粗糙感 */
  font-family: "Comic Sans MS", "Lucida Handwriting", "Brush Script MT", cursive;
  font-weight: 500;
  letter-spacing: 1.5px;
  transform: translateY(4px) rotate(-4deg);
  opacity: 0.92;
  text-shadow: 0.5px 0.5px 0 rgba(0,0,0,0.06);
}

/* 小屏时轻微收敛手写效果，避免拥挤 */
@media (max-width: 768px) {
  .cond-5_NEW, .cond-4_NEW, .cond-OLD {
    transform: translateY(2px) rotate(-2deg);
    letter-spacing: 0.8px;
    font-size: 13px;
  }
}

/* 文字样式 */
.title {
  font-size: 26px;
  margin: 0 0 16px;
  color: #111827;
  font-weight: 700;
  line-height: 1.3;
}

.price-rate {
  display: flex;
  align-items: center;
  gap: 24px;
  margin-bottom: 24px;
  padding-bottom: 20px;
  border-bottom: 1px solid #e5e7eb;
}

.price {
  font-size: 28px;
  color: #E04E3A; /* 枫叶红（统一亮色） */
  font-weight: 800;
}

.rate {
  font-size: 16px;
  color: #ffb800;
  font-weight: 600;
}

/* 卖家信息样式 */
.seller-block {
  display: flex;
  align-items: center;
  gap: 16px;
  margin: 20px 0 24px;
  padding: 16px;
  background: #f8fafc;
  border-radius: 8px;
}

.seller-avatar {
  border: 2px solid #e5e7eb;
  box-shadow: 0 2px 6px rgba(0,0,0,0.08);
}

.seller-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.seller-name {
  font-weight: 600;
  color: #1f2937;
  font-size: 15px;
}


.description {
  font-size: 15px;
  line-height: 1.8;
  color: #4b5563;
  margin-bottom: 24px;
}

.detail {
  margin-top: 32px;
  padding: 20px;
  background: linear-gradient(135deg, #f6efe5 0%, #efe6db 100%);
  border-radius: 8px;
  border-left: 4px solid #b08968;
  position: relative;
}

.detail::before {
  content: '🍂'; /* 枫叶装饰 */
  position: absolute;
  top: 16px;
  right: 20px;
  font-size: 32px;
  opacity: 0.3;
}

.detail h3 {
  font-size: 18px;
  margin-bottom: 12px;
  color: #1f2937;
  font-weight: 700;
}

/* 修改：将 h3 前的 ☕ emoji 替换为 assets 中的 coffee.png（放大尺寸） */
.detail h3::before {
  content: '';
  display: inline-block;
  width: 40px; /* 放大为 36px */
  height: 40px; /* 放大为 36px */
  margin-right: 10px;
  vertical-align: middle;
  background-image: url('@/assets/images/coffee.png');
  background-size: contain;
  background-repeat: no-repeat;
  background-position: center;
}

.detail p {
  line-height: 1.9;
  color: #374151;
  font-size: 15px;
}

/* 规格表格样式 */
.specifications {
  margin-top: 40px;
  background: linear-gradient(135deg, #fffbeb 0%, #ffffff 100%);
  padding: 24px;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(245, 158, 11, 0.1);
  position: relative;
  z-index: 1;
}

/* 将规格区左侧强调色改为橙色，与页面主色调一致 */
.specifications {
  border-left: 4px solid #c27935; /* 橙色强调线 */
}

/* 装饰图标透明度调整（保持原样） */
.specifications::before {
  content: '📖';
  position: absolute;
  top: 20px;
  right: 20px;
  font-size: 28px;
  opacity: 0.2;
}

/* 规格表格内文字/标题局部强调为橙色 */
.specifications :deep(.el-table__cell),
.specifications :deep(.el-table .cell) {
  color: #6b4a2a; /* 主文字仍清晰，可选深棕 */
}
.specifications :deep(.el-table__cell:first-child) {
  color: #c27935; /* 第一列项名使用橙色强调 */
  font-weight: 600;
}

/* 若需要，覆盖表格边框强调色为橙色（轻微透明） */
.specifications :deep(.el-table__body-wrapper) {
  border-color: rgba(194,121,53,0.12);
}

/* 右上角按钮容器 */
.action-buttons {
  position: absolute;
  top: 32px;
  right: 80px;
  z-index: 50;
  display: flex;
  gap: 12px;
}

/* 底部购物车固定栏 */
.bottom-cart-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  background: linear-gradient(180deg, rgba(255,255,255,0.95) 0%, rgba(255,255,255,0.98) 100%);
  backdrop-filter: blur(10px);
  box-shadow: 0 -4px 20px rgba(0, 0, 0, 0.08);
  z-index: 999;
  padding: 16px 0;
  border-top: 1px solid #e5e7eb;
}

.cart-bar-content {
  max-width: 1280px;
  margin: 0 auto;
  padding: 0 80px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.cart-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.cart-price {
  font-size: 26px;
  color: #E04E3A; /* 枫叶红（统一亮色） */
  font-weight: 800;
}

.cart-stock {
  font-size: 15px;
  color: #6b7280;
}

.cart-actions {
  display: flex;
  align-items: center;
  gap: 16px;
}

.quantity-input {
  width: 140px;
}

.add-cart-btn {
  min-width: 180px;
  height: 48px;
  font-size: 17px;
  font-weight: 600;
  border-radius: 24px;
  background-color: #6B3E1E !important;
  border-color: #6B3E1E !important;
}

.add-cart-btn:hover {
  background-color: #4B2D18 !important;
  border-color: #4B2D18 !important;
}

.add-cart-btn.is-disabled {
  background-color: #f5eadd !important;
  border-color: #f5eadd !important;
}

/* 评论区样式 */
.comment-section {
  margin-top: 40px;
  padding: 32px;
  background: linear-gradient(135deg, #ffffff 0%, #f6efe5 100%);
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(245, 158, 11, 0.1);
  position: relative;
  z-index: 1;
}

.comment-section::before {
  content: '🖊️';
  position: absolute;
  top: 20px;
  right: 24px;
  font-size: 32px;
  opacity: 0.15;
}

.comment-section h3 {
  font-size: 20px;
  margin-bottom: 24px;
  color: #1f2937;
  font-weight: 700;
  display: flex;
  align-items: center;
  gap: 8px;
}

.comment-section h3::before {
  content: '💬';
  font-size: 24px;
}

.comment-input {
  margin-bottom: 30px;
}

.submit-btn {
  margin-top: 10px;
  text-align: right;
}

.submit-btn :deep(.el-button--primary) {
  background-color: #c27935 !important;
  border-color: #c27935 !important;
}
.submit-btn :deep(.el-button--primary:hover) {
  background-color: #9B341F !important;
  border-color: #9B341F !important;
}

.comment-list {
  margin-top: 20px;
}

.no-comments {
  text-align: center;
  color: #999;
  padding: 20px;
}

.comment-item {
  position: relative;
  padding: 20px;
  margin-bottom: 16px;
  border-radius: 8px;
  background: linear-gradient(135deg, #f6efe5 0%, #efe6db 100%);
  border-left: 4px solid #b08968;
  transition: all 0.3s;
}

.comment-item:hover {
  background: linear-gradient(135deg, #efe6db 0%, #dcc090 100%);
  box-shadow: 0 2px 8px rgba(245, 158, 11, 0.2);
}

.comment-header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 12px;
  font-size: 15px;
}

.username {
  font-weight: bold;
  color: #7A3A1A;
}

.time {
  color: #9ca3af;
}

.comment-content {
  line-height: 1.8;
  color: #374151;
  font-size: 15px;
}

.comment-actions {
  position: absolute;
  right: 20px;
  bottom: 20px;
}

/* 分类标签样式 */
.category-tag {
  margin-bottom: 20px;
}

.category-tag :deep(.el-tag) {
  font-size: 15px;
  padding: 8px 16px;
  border-radius: 20px;
}

/* 规格表格样式优化 */
.specifications {
  margin-top: 40px;
  background: linear-gradient(135deg, #fffbeb 0%, #ffffff 100%);
  padding: 24px;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(245, 158, 11, 0.1);
  position: relative;
  z-index: 1;
}

/* 将规格区左侧强调色改为橙色，与页面主色调一致 */
.specifications {
  border-left: 4px solid #c27935; /* 橙色强调线 */
}

/* 装饰图标透明度调整（保持原样） */
.specifications::before {
  content: '📖';
  position: absolute;
  top: 20px;
  right: 20px;
  font-size: 28px;
  opacity: 0.2;
}

/* 规格表格内文字/标题局部强调为橙色 */
.specifications :deep(.el-table__cell),
.specifications :deep(.el-table .cell) {
  color: #6b4a2a; /* 主文字仍清晰，可选深棕 */
}
.specifications :deep(.el-table__cell:first-child) {
  color: #c27935; /* 第一列项名使用橙色强调 */
  font-weight: 600;
}

/* 若需要，覆盖表格边框强调色为橙色（轻微透明） */
.specifications :deep(.el-table__body-wrapper) {
  border-color: rgba(194,121,53,0.12);
}

/* 成色显示样式 */
.condition-display {
  font-size: 15px;
  color: #7A3A1A;
}

/* 覆盖本组件内的 danger 按钮为暗红色，符合页面总体色调 */
:deep(.el-button--danger) {
  background-color: #E04E3A !important; /* 枫叶红（统一亮色） */
  border-color: #E04E3A !important; /* 枫叶红（统一亮色） */
  color: #ffffff !important;
  box-shadow: none !important;
}
:deep(.el-button--danger:hover) {
  background-color: #9B341F !important; /* hover 更深枫叶色 */
  border-color: #9B341F !important; /* hover 更深枫叶色 */
}

/* text 模式（仅文字） */
:deep(.el-button--danger.is-text) {
  color: #E04E3A !important;
  background-color: transparent !important;
}
:deep(.el-button--danger.is-text:hover) {
  color: #9B341F !important;
  background-color: rgba(192,65,43,0.06) !important;
}
/* 图标继承颜色 */
:deep(.el-button--danger .el-icon),
:deep(.el-button--danger.is-text .el-icon) {
  color: inherit !important;
}

/* 聊一聊按钮使用统一浮动按钮色 */
.chat-btn {
  background-color: #c27935 !important;
  border-color: #c27935 !important;
  color: #ffffff !important;
  font-weight: 600;
  border-radius: 6px;
  padding: 6px 12px;
}

.chat-btn:hover {
  background-color: #9B341F !important;
  border-color: #9B341F !important;
}

/* 响应式设计 */
@media (max-width: 1024px) {
  .product-container {
    padding: 24px 40px 120px;
  }
  
  .cart-bar-content {
    padding: 0 40px;
  }
  
  .action-buttons {
    right: 40px;
  }
}

@media (max-width: 768px) {
  .product-container {
    padding: 16px 20px 140px;
  }
  
  .product-main {
    flex-direction: column;
    gap: 24px;
    padding: 20px;
  }
  
  .product-image {
    flex: 0 0 auto;
    width: 100%;
  }
  
  .cover-image {
    height: 320px;
  }
  
  .title {
    font-size: 24px;
  }
  
  .price {
    font-size: 28px;
  }
  
  .cart-bar-content {
    padding: 0 20px;
    flex-direction: column;
    gap: 12px;
  }
  
  .cart-actions {
    width: 100%;
    justify-content: space-between;
  }
  
  .add-cart-btn {
    flex: 1;
  }
  
  .action-buttons {
    right: 20px;
    top: 16px;
  }

  /* 移动端适配：减小 coffee 图标，避免占用过多空间 */
  .detail h3::before {
    width: 24px;
    height: 24px;
    margin-right: 8px;
  }
}

.seller-actions {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>