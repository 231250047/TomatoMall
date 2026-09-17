<script setup lang="ts">
import router from '@/router/index.js'; // @ 是一个常见的 路径别名被配置为指向项目的 src 目录
import { ref, onMounted  } from 'vue'
import { ElMessage, ElLoading, ElMessageBox } from 'element-plus';

import {
  checkout,
  ShoppingAddress,
  CheckoutRequest,
  cartItem
} from '@/api/order'

import { getCart } from '@/api/cart'


// 完整商品数据
const selectedCartItems = ref<cartItem[]>([]);

// 收货表单
const shoppingForm = ref<ShoppingAddress>({
  name: '',
  phone: '',
  postalCode: '',
  address: ''
})


// 支付方式
const paymentMethod = ref('ALIPAY')

// 从sessionStorage获取已选商品
const selectedItems = ref<number[]>( // 数值数组
    JSON.parse(sessionStorage.getItem('selectedItems') || '[]').map(Number)
)
const totalAmount = ref<string>(
    sessionStorage.getItem('totalPayment') || "0.00"
)

// 获取商品信息的函数
const loadSelectedItems = () => {
  getCart().then(res => {
    // 过滤出已选商品
    console.log('原始购物车数据:', res.data.items);
    selectedCartItems.value = res.data.items.filter((x: cartItem) =>
        selectedItems.value.includes(x.cartItemId) //number[]
    );
    console.log('过滤结果:', JSON.parse(JSON.stringify(selectedCartItems.value)))
  }).catch(error => {
    console.error('获取选中商品失败', error);
  });
};

// 提交订单
const discount = sessionStorage.getItem('discount') || "0";
const submitting = ref(false);
const submitOrder = async () => {
  if (submitting.value) return;
  if (!validateForm()) return;
  submitting.value = true;
  const loading = ElLoading.service({
    lock: true,
    text: '正在提交订单...',
  });
  try {
    const orderData = {
      cartItemIds: selectedItems.value.map(String), // string[]
      shoppingAddress: shoppingForm.value,
      paymentMethod: paymentMethod.value,
      discount: discount,
    };
    console.log('提交订单参数:', JSON.stringify(orderData, null, 2));

    // Keep the same key across timeouts and reloads for the same checkout payload.
    const payload = JSON.stringify(orderData);
    const saved = JSON.parse(sessionStorage.getItem('checkoutAttempt') || 'null');
    const requestId = saved?.payload === payload ? saved.requestId : crypto.randomUUID();
    sessionStorage.setItem('checkoutAttempt', JSON.stringify({ payload, requestId }));
    const orderInfo = await checkout({ ...orderData, requestId });
    sessionStorage.removeItem('checkoutAttempt');
    console.log('订单信息:', JSON.stringify(orderInfo, null, 2));

    ElMessage.success('订单提交成功');
    // 清空选中状态
    sessionStorage.removeItem('selectedItems');
    sessionStorage.removeItem('totalPayment');

    // 新开一个网页跳转到支付页面
    const paymentUrl = `/payment/${orderInfo.orderId}`;
    window.open(paymentUrl, '_blank'); // 打开新页面

    // 弹出确认弹窗
    ElMessageBox.confirm(
        '是否已成功支付？',
        '支付确认',
        {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'info',
        }
    )
        .then(() => {
          // 用户点击“确定”，跳转到订单详情页
          router.push(`/orders/${orderInfo.orderId}`);
        })
        .catch(() => {
          // 用户点击“取消”，不跳转
          ElMessage.info('请完成支付后再查看订单详情');
        });
  } catch (error) {
    console.error(error);
    ElMessage.error(`提交失败: '未知错误'`);
  } finally {
    loading.close();
    submitting.value = false;
  }
};

// 表单验证
const validateForm = (): boolean => {
  if (selectedItems.value.length === 0) {
    ElMessage.error('请先选择要购买的商品')
    return false
  }
  if (!shoppingForm.value.name.trim()) {
    ElMessage.error('请输入收货人姓名')
    return false
  }
  if (!shoppingForm.value.phone.trim()) {
    ElMessage.error('请输入联系电话')
    return false
  }
  if (!shoppingForm.value.address.trim()) {
    ElMessage.error('请输入详细地址')
    return false
  }
  return true
}
// 初始化检查
onMounted(() => {
  if (selectedItems.value.length === 0) {
    ElMessage.warning('没有选中商品，即将返回购物车')
    setTimeout(() => router.push('/cart'), 1500)
    return
  }
  loadSelectedItems()
})

</script>

<template>
  <div class="checkout-container">
    <h2 class="page-title">订单结算</h2>

    <div class="checkout-card">
      <!-- 收货信息 -->
      <div class="section">
        <h3 class="section-title">收货信息</h3>
        <el-form label-width="100px">
          <el-form-item label="收货人" required>
            <el-input
                v-model="shoppingForm.name"
                placeholder="请输入收货人姓名"
            />
          </el-form-item>
          <el-form-item label="联系电话" required>
            <el-input
                v-model="shoppingForm.phone"
                placeholder="请输入联系电话"
            />
          </el-form-item>
          <el-form-item label="详细地址" required>
            <el-input
                v-model="shoppingForm.address"
                type="textarea"
                :rows="2"
                placeholder="请输入详细地址"
            />
          </el-form-item>
          <el-form-item label="邮政编码">
            <el-input
                v-model="shoppingForm.postalCode"
                placeholder="请输入邮政编码"
            />
          </el-form-item>
        </el-form>
      </div>

      <!-- 支付方式 -->
      <div class="section">
        <h3 class="section-title">支付方式</h3>
        <el-radio-group v-model="paymentMethod">
          <el-radio value="ALIPAY">支付宝</el-radio>
        </el-radio-group>
      </div>

      <!-- 商品清单 -->
      <div class="section">
        <h3 class="section-title">商品清单</h3>
        <el-table :data="selectedCartItems" border style="width: 100%">
          <!-- 商品信息列 -->
          <el-table-column label="商品信息" width="350">
            <template #default="{ row }">
              <div class="product-info">
                <el-image
                    :src="row.cover || '/default-product.jpg'"
                    fit="cover"
                    style="width: 60px; height: 60px; margin-right: 10px;"
                />
                <div class="product-details">
                  <div class="product-title">{{ row.title }}</div>
                </div>
              </div>
            </template>
          </el-table-column>

          <!-- 单价列 -->
          <el-table-column label="单价" width="120" align="center">
            <template #default="{ row }">
              ¥{{ row.price.toFixed(2) }}
            </template>
          </el-table-column>

          <!-- 数量列 -->
          <el-table-column label="数量" width="100" align="center">
            <template #default="{ row }">
              {{ row.quantity }}
            </template>
          </el-table-column>

          <!-- 小计列 -->
          <el-table-column label="小计" width="120" align="right">
            <template #default="{ row }">
              ¥{{ (row.price * row.quantity).toFixed(2) }}
            </template>
          </el-table-column>
        </el-table>
      </div>
      <!-- 结算栏 -->
      <div class="checkout-footer">
        <div class="total-amount">
          应付总额：<span class="amount">¥{{ totalAmount }}</span>
        </div>
        <el-button
            type="primary"
            size="large"
            @click="submitOrder"
        >
          提交订单
        </el-button>
      </div>
    </div>
  </div>

</template>

<style scoped>
.checkout-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px;
}

.page-title {
  font-size: 24px;
  margin-bottom: 20px;
  color: #333;
}

.checkout-card {
  background: #fff;
  padding: 20px;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
}

.section {
  margin-bottom: 30px;
  padding: 20px;
  border: 1px solid #eee;
  border-radius: 4px;
}

.section-title {
  font-size: 18px;
  margin-bottom: 20px;
  color: #666;
  padding-bottom: 10px;
  border-bottom: 1px solid #eee;
}

.product-info {
  display: flex;
  align-items: center;
}

.product-title {
  font-weight: 500;
  margin-bottom: 5px;
}

.product-id {
  font-size: 12px;
  color: #999;
}

.checkout-footer {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  margin-top: 30px;
  padding-top: 20px;
  border-top: 1px solid #eee;
}

.total-amount {
  margin-right: 30px;
  font-size: 16px;
}

.amount {
  font-size: 24px;
  color: #f56c6c;
  font-weight: bold;
}

:deep(.el-form-item__label) {
  font-weight: 500;
}
</style>