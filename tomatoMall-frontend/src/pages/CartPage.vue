<script setup>
import NavigationBar from "@/components/NavigationBar.vue";
import { ShoppingCart, Plus, Minus, Delete, Picture } from '@element-plus/icons-vue';
import {ref, onMounted, computed} from 'vue';
import router from '../router/index';
import {changeAmount, deleteFromCart, getCart} from "@/api/cart.js";
import {ElMessage} from "element-plus";
import {getStockpile} from "@/api/product.js";

const isAdmin = computed(() => sessionStorage.getItem('role') === 'admin')

// 使用ref定义响应式数据
const cartList = ref([]);
const total = ref(0);
const totalAmount = ref(0);
const selectedItem = ref([]);//存储被选中的商品
// 全选：使用 computed 的 getter/setter 自动反映所有项选中状态
const selectAll = computed({
  get() {
    return cartList.value.length > 0 && cartList.value.every(i => !!i.isSelected);
  },
  set(val) {
    // 设置所有项的选中状态，并同步 selectedItem
    cartList.value = cartList.value.map(i => ({ ...i, isSelected: !!val }));
    selectedItem.value = val ? cartList.value.map(i => i.cartItemId) : [];
    sessionStorage.setItem('selectedItems', JSON.stringify(selectedItem.value));
    sessionStorage.setItem('totalPayment', totalPayment.value.toFixed(2));
  }
});

// const totalPayment = ref(0);

// 获取商品信息的函数
const getCartInfo = () => {


  getCart().then(res => {
    console.log(res.data);
    cartList.value = res.data.items.map(x => {
      return {
        cartItemId: String(x.cartItemId),
        productId: x.productId,
        title: x.title,
        price: x.price,
        description: x.description,
        cover: x.cover,
        detail: x.detail,
        quantity: x.quantity,
        isSelected:false,//初始时状态全部设定为未选中
      };
    });
    total.value = res.data.total;
    totalAmount.value = res.data.totalAmount;

  }).catch(error => {
    console.error('获取购物车信息失败', error);
  });



};

// 页面挂载时获取商品信息
onMounted(() => {
  getCartInfo();
});

// 处理点击查看详情的逻辑
const handleShowDetails = (id) => {
  router.push(`/products/${id}`);
};

// 删除商品
const handleDeleteItem = (cartItemId) => {
  //修改选中的总价
  const item = cartList.value.find(item => item.cartItemId === cartItemId);
  if(item.isSelected){
    totalPayment.value-=item.price*item.quantity;
  }

  deleteFromCart(cartItemId).then((res) => {
    if(res.code === "200") {
      ElMessage({
        message: "删除成功！",
        type: 'success',
        center: true,
      })

    }else if(res.code === "400") {
      ElMessage({
        message: "购物车商品不存在",
        type: 'error',
        center: true,
      })

    }

  })
  window.location.reload();//强制刷新页面
};

// 修改商品数量
const handleChangeQuantity = (cartItemId, quantity,productId,item) => {
  const stockAmount=ref(0)
  console.log("quantity: "+quantity);
  getStockpile(productId).then(res=>{
    if(res.code === "200") {
      stockAmount.value=Math.max(0, res.data.amount - res.data.frozen);
    }
    console.log("stock: "+stockAmount.value)
    if(quantity > stockAmount.value) {
      ElMessage({
        message: "库存不足！",
        type: 'error',
        center: true,
      })
    }else{

      if(item.isSelected) {
        totalPayment.value-=item.price*item.quantity;
        totalPayment.value+=item.price*quantity;
      }
      item.quantity=quantity;
      changeAmount(cartItemId, quantity).then((res) => {
        if(res.code === "200") {
          ElMessage({
            message: "修改成功！",
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
    }


  })


};

// 勾选/取消勾选商品（接收 checkbox 新值，避免与 v-model 冲突）
const handleToggleSelection = (cartItemId, checked) => {
  const index = cartList.value.findIndex(item => item.cartItemId === cartItemId);
  if (index === -1) return;

  // 保证 item.isSelected 与 checkbox 值一致（v-model 已更新，这里以传入的 checked 为准）
  cartList.value[index].isSelected = !!checked;

  // 更新 selectedItems 列表
  if (checked) {
    if (!selectedItem.value.includes(cartItemId)) selectedItem.value.push(cartItemId);
  } else {
    selectedItem.value = selectedItem.value.filter(id => id !== cartItemId);
  }

  // 同步到 sessionStorage
  sessionStorage.setItem('selectedItems', JSON.stringify(selectedItem.value));
  sessionStorage.setItem('totalPayment', totalPayment.value.toFixed(2));

  console.log('切换后的isSelected:', cartList.value[index].isSelected);
  console.log('更新后的selectedItems:', selectedItem.value);
};

// 使用 computed 计算总价（替代手动更新）

const totalPayment = computed(() => {
  const discount = parseInt(sessionStorage.getItem('discount')) || 0;
  const subtotal=  cartList.value
      .filter(item => selectedItem.value.includes(item.cartItemId))
      .reduce((sum, item) => sum + item.price * item.quantity, 0);

  if(subtotal > 300) {
    sessionStorage.setItem('totalPayment', subtotal-discount);
    return subtotal - discount;
  }else if(subtotal > 200 && discount <= 30) {
    sessionStorage.setItem('totalPayment', subtotal-discount);
    return subtotal - discount;
  }else if(subtotal > 50 && discount <= 5) {
    sessionStorage.setItem('totalPayment', subtotal-discount);
    return subtotal - discount;
  }

  sessionStorage.setItem('totalPayment', subtotal);
  return subtotal;
});

const checkout = ()=>{
  router.push('/order');
}

// 全选/取消全选（接收 checkbox 传入的新值）
const handleSelectAll = (checked) => {
  // 将值交给 selectAll 的 setter 处理
  selectAll.value = !!checked;
};

// ticketShow removed — coupon/ticket UI has been removed



</script>

<template>
  <div class="cart-page">
    <NavigationBar v-if="isAdmin"></NavigationBar>
    
    <div class="cart-container">
      <div class="cart-header">
        <h1><el-icon><ShoppingCart /></el-icon> 购物车</h1>
        <p class="cart-count">共 {{ cartList.length }} 件商品</p>
      </div>

      <!-- 空购物车状态 -->
      <div v-if="cartList.length === 0" class="empty-cart">
        <el-empty description="购物车是空的">
          <el-button type="primary" @click="$router.push('/home')">去逛逛</el-button>
        </el-empty>
      </div>

      <!-- 商品列表 -->
      <div v-else class="cart-content">
        <div class="cart-list">
          <div v-for="item in cartList" :key="item.cartItemId" class="cart-item">
            <el-checkbox 
              v-model="item.isSelected" 
              @change="handleToggleSelection(item.cartItemId, $event)"
              class="item-checkbox"
            />
            
            <div class="item-image" @click="handleShowDetails(item.productId)">
              <el-image :src="item.cover" fit="cover">
                <template #error>
                  <div class="image-slot">
                    <el-icon><Picture /></el-icon>
                  </div>
                </template>
              </el-image>
            </div>
            
            <div class="item-details">
              <h3 class="item-title" @click="handleShowDetails(item.productId)">{{ item.title }}</h3>
              <p class="item-desc">{{ item.description }}</p>
              <div class="item-price">
                <span class="price-label">单价：</span>
                <span class="price-value">¥{{ item.price }}</span>
              </div>
            </div>
            
            <div class="item-actions">
              <div class="quantity-control">
                <el-button 
                  circle 
                  size="small" 
                  @click="handleChangeQuantity(item.cartItemId, item.quantity - 1, item.productId, item)"
                  :disabled="item.quantity <= 1"
                >
                  <el-icon><Minus /></el-icon>
                </el-button>
                <span class="quantity">{{ item.quantity }}</span>
                <el-button 
                  circle 
                  size="small"
                  @click="handleChangeQuantity(item.cartItemId, item.quantity + 1, item.productId, item)"
                >
                  <el-icon><Plus /></el-icon>
                </el-button>
              </div>
              
              <div class="item-total">
                <span class="total-label">小计：</span>
                <span class="total-value">¥{{ (item.price * item.quantity).toFixed(2) }}</span>
              </div>
              
              <el-button 
                type="danger" 
                text 
                @click="handleDeleteItem(item.cartItemId)"
                class="delete-btn"
              >
                <el-icon><Delete /></el-icon> 删除
              </el-button>
            </div>
          </div>
        </div>

        <!-- 底部结算栏 -->
        <div class="cart-footer">
          <div class="footer-left">
            <el-checkbox v-model="selectAll" @change="handleSelectAll($event)">全选</el-checkbox>
            <span class="selected-count">已选 {{ selectedItem.length }} 件</span>
          </div>
          
          <div class="footer-right">
            <div class="total-section">
              <span class="total-label">总金额：</span>
              <span class="total-amount">¥{{ totalPayment.toFixed(2) }}</span>
            </div>
            <el-button 
              type="primary" 
              size="large" 
              @click="checkout"
              :disabled="selectedItem.length === 0"
              class="checkout-btn"
            >
              结算 ({{ selectedItem.length }})
            </el-button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.cart-page {
  min-height: 100vh;
  background: 
    linear-gradient(90deg, transparent 0%, rgba(139, 69, 19, 0.03) 50%, transparent 100%),
    repeating-linear-gradient(0deg, #faf8f3 0px, #faf8f3 2px, #f5f1e8 2px, #f5f1e8 4px);
  position: relative;
}

.cart-page::before {
  content: '🛒';
  position: fixed;
  top: 180px;
  right: 100px;
  font-size: 90px;
  opacity: 0.05;
  transform: rotate(-15deg);
  z-index: 0;
}

.cart-page::after {
  content: '📚';
  position: fixed;
  bottom: 140px;
  left: 80px;
  font-size: 80px;
  opacity: 0.05;
  transform: rotate(20deg);
  z-index: 0;
}

.cart-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 24px 24px 120px;
  position: relative;
  z-index: 1;
}

.cart-header {
  background: #d8aa84;
  padding: 24px;
  border-radius: 12px;
  margin-bottom: 20px;
  box-shadow: 0 2px 8px rgba(101,67,33,0.08);
}

.cart-header h1 {
  font-size: 24px;
  font-weight: 600;
  color: #fffaf0;
  margin: 0 0 8px 0;
  display: flex;
  align-items: center;
  gap: 8px;
}

.cart-count {
  color: #ffefdd;
  font-size: 14px;
  margin: 0;
}

.empty-cart {
  background: white;
  padding: 80px 24px;
  border-radius: 12px;
  text-align: center;
}

.checkout-btn {
  background-color: #c27935 !important;
  border-color: #c27935 !important;
}
.checkout-btn:hover {
  background-color: #9B341F !important;
  border-color: #9B341F !important;
}

.empty-cart .el-button--primary {
  background-color: #c27935 !important;
  border-color: #c27935 !important;
}
.empty-cart .el-button--primary:hover {
  background-color: #9B341F !important;
  border-color: #9B341F !important;
}

.cart-list {
  background: white;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
}

.cart-item {
  display: flex;
  align-items: center;
  padding: 20px;
  border-bottom: 1px solid #f0f0f0;
  transition: background 0.3s;
}

.cart-item:hover {
  background: #fafafa;
}

.cart-item:last-child {
  border-bottom: none;
}

.item-checkbox {
  margin-right: 16px;
}

.item-image {
  width: 120px;
  height: 120px;
  margin-right: 20px;
  cursor: pointer;
  border-radius: 8px;
  overflow: hidden;
  flex-shrink: 0;
}

.item-image :deep(.el-image) {
  width: 100%;
  height: 100%;
}

.image-slot {
  display: flex;
  justify-content: center;
  align-items: center;
  width: 100%;
  height: 100%;
  background: #f5f5f5;
  color: #909399;
  font-size: 32px;
}

.item-details {
  flex: 1;
  min-width: 0;
  margin-right: 20px;
}

.item-title {
  font-size: 16px;
  font-weight: 600;
  color: #1a1a1a;
  margin: 0 0 8px 0;
  cursor: pointer;
  transition: color 0.3s;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.item-title:hover {
  color: #667eea;
}

.item-desc {
  font-size: 13px;
  color: #999;
  margin: 0 0 12px 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.item-price {
  display: flex;
  align-items: baseline;
  gap: 4px;
}

.price-label {
  font-size: 13px;
  color: #666;
}

.price-value {
  font-size: 20px;
  font-weight: 700;
  color: #E04E3A; /* 枫叶红（更亮） */
}

.item-actions {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 12px;
  min-width: 180px;
}

.quantity-control {
  display: flex;
  align-items: center;
  gap: 12px;
}

.quantity {
  font-size: 16px;
  font-weight: 600;
  min-width: 30px;
  text-align: center;
}

.item-total {
  display: flex;
  align-items: baseline;
  gap: 4px;
}

.total-label {
  font-size: 13px;
  color: #666;
}

.total-value {
  font-size: 18px;
  font-weight: 700;
  color: #E04E3A; /* 枫叶红（更亮） */
}

.delete-btn {
  font-size: 13px;
}

.cart-footer {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  background: linear-gradient(135deg, #ffffff 0%, #fffbeb 100%);
  border-top: 1px solid #fde68a;
  padding: 20px 24px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  box-shadow: 0 -2px 12px rgba(245, 158, 11, 0.15);
  z-index: 100;
}

.footer-left {
  display: flex;
  align-items: center;
  gap: 24px;
}

.selected-count {
  font-size: 14px;
  color: #666;
}

.footer-right {
  display: flex;
  align-items: center;
  gap: 24px;
}

.total-section {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.total-section .total-label {
  font-size: 14px;
  color: #666;
}

.total-amount {
  font-size: 28px;
  font-weight: 700;
  color: #E04E3A; /* 枫叶红（统一亮色） */
}

/* 把咖啡 emoji 换成图片 */
.total-amount::before {
  content: '';
  display: inline-block;
  width: 28px; /* 从 24px 调大为 28px */
  height: 28px; /* 从 24px 调大为 28px */
  margin-right: 8px;
  vertical-align: -4px;
  background-image: url('@/assets/images/coffee.png');
  background-size: contain;
  background-repeat: no-repeat;
  background-position: center;
}

.checkout-btn {
  min-width: 140px;
  height: 48px;
  font-size: 16px;
  font-weight: 600;
}

/* 统一删除按钮为枫叶红 */
:deep(.el-button--danger) {
  background-color: #E04E3A !important; /* 枫叶红（更亮） */
  border-color: #E04E3A !important; /* 枫叶红（更亮） */
   color: #ffffff !important;
   box-shadow: none !important;
}
:deep(.el-button--danger:hover) {
  background-color: #9B341F !important;
  border-color: #9B341F !important;
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

@media (max-width: 768px) {
  .cart-item {
    flex-wrap: wrap;
  }
  
  .item-image {
    width: 80px;
    height: 80px;
  }
  
  .item-actions {
    width: 100%;
    flex-direction: row;
    justify-content: space-between;
    margin-top: 12px;
  }
  
  .cart-footer {
    flex-direction: column;
    gap: 16px;
  }
  
  .footer-left, .footer-right {
    width: 100%;
    justify-content: space-between;
  }

  /* 移动端适配 coffee 图标（从 20px 调大为 24px） */
  .total-amount::before {
    width: 24px;
    height: 24px;
    vertical-align: -3px;
    margin-right: 6px;
  }
}
</style>
