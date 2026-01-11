<script setup>
import NavigationBar from "@/components/NavigationBar.vue";
import { ref, onMounted } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import { getOrderDetail, initiatePayment, deleteOrder } from "@/api/order"; // 修改导入

const route = useRoute();
const router = useRouter();
const loading = ref(false);
const order = ref(null);

const fetchOrderDetail = async () => {
  const orderId = route.params.orderId;
  if (!orderId) {
    ElMessage.error("订单号缺失");
    router.push("/orders");
    return;
  }
  loading.value = true;
  try {
    const res = await getOrderDetail(orderId);
    order.value = res?.data?.data ?? res?.data ?? res;
    console.log("Order Detail:", order.value);
  } catch (e) {
    ElMessage.error("获取订单详情失败");
    router.push("/orders");
  } finally {
    loading.value = false;
  }
};

const goBack = () => router.push("/orders");

const handlePay = async () => {
  try {
    // 使用 initiatePayment 替代 createAliPayOrder
    const paymentForm = await initiatePayment(order.value.orderId);
    if (!paymentForm || !paymentForm.paymentForm) {
      ElMessage.error('支付表单获取失败');
      return;
    }
    const w = window.open("", "_blank");
    if (!w) {
      ElMessage.error('浏览器阻止弹窗，请允许弹窗');
      return;
    }
    w.document.open();
    w.document.write(paymentForm.paymentForm);
    w.document.close();

    // 等待支付完成后刷新订单详情
    setTimeout(async () => {
      await fetchOrderDetail(); // 刷新订单详情
    }, 2000); // 延迟2秒等待支付完成
  } catch (err) {
    ElMessage.error('发起支付失败');
    console.error(err);
  }
};

const handleDelete = async () => {
  try {
    await ElMessageBox.confirm('确认删除该订单？', '提示', {
      confirmButtonText: '确认',
      cancelButtonText: '取消'
    });
    await deleteOrder(order.value.orderId); // 调用后端删除订单接口
    ElMessage.success('订单删除成功');
    router.push('/orders'); // 删除成功后跳转到订单列表页
  } catch (e) {
    // 用户取消操作或请求失败
  }
};

onMounted(fetchOrderDetail);
</script>

<template>
  <div class="order-detail-page">
    <NavigationBar />

    <div class="order-detail-container" v-loading="loading">
      <div class="order-header">
        <h1>订单详情</h1>
        <el-button @click="goBack">返回订单列表</el-button>
      </div>

      <div v-if="order" class="order-content">
        <!-- 订单信息 -->
        <div class="info-section">
          <div class="section-title">订单信息</div>
          <div class="info-grid">
            <div class="info-item">
              <span class="label">订单号</span>
              <span class="value">{{ order.orderId }}</span>
            </div>
            <div class="info-item">
              <span class="label">订单状态</span>
              <el-tag :type="order.status === 'SUCCESS' ? 'success' : 'warning'">
                {{ order.status === 'SUCCESS' ? '已支付' : '待支付' }}
              </el-tag>
            </div>
            <div class="info-item">
              <span class="label">支付方式</span>
              <span class="value">{{ order.paymentMethod }}</span>
            </div>
            <div class="info-item">
              <span class="label">创建时间</span>
              <span class="value">{{ order.createTime }}</span>
            </div>
            <div class="info-item">
              <span class="label">支付时间</span>
              <span class="value">{{ order.paymentTime || '-' }}</span> <!-- 新增支付时间 -->
            </div>
            <div class="info-item">
              <span class="label">订单金额</span>
              <span class="price-value">¥{{ order.totalAmount }}</span>
            </div>
          </div>
        </div>

        <!-- 收货信息 -->
        <div class="info-section" v-if="order.receiverName">
          <div class="section-title">收货信息</div>
          <div class="info-grid">
            <div class="info-item">
              <span class="label">收货人</span>
              <span class="value">{{ order.receiverName }}</span>
            </div>
            <div class="info-item">
              <span class="label">联系电话</span>
              <span class="value">{{ order.receiverPhone }}</span>
            </div>
            <div class="info-item full-width">
              <span class="label">收货地址</span>
              <span class="value">{{ order.receiverAddress }}</span>
            </div>
            <div class="info-item" v-if="order.receiverPostalCode">
              <span class="label">邮政编码</span>
              <span class="value">{{ order.receiverPostalCode }}</span>
            </div>
          </div>
        </div>

        <!-- 商品信息 -->
        <div class="info-section">
          <div class="section-title">商品信息</div>
          <el-table :data="order.items || []" stripe style="width: 100%">
            <el-table-column label="商品" min-width="200">
              <template #default="{ row }">
                <div class="product-info">
                  <img v-if="row.cover" :src="row.cover" class="product-image" />
                  <span>{{ row.title }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="price" label="单价" width="120">
              <template #default="{ row }">
                <span class="price-value">¥{{ row.price }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="quantity" label="数量" width="100" align="center" />
            <el-table-column label="小计" width="120">
              <template #default="{ row }">
                <span class="price-value">¥{{ (row.price * row.quantity).toFixed(2) }}</span>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <!-- 操作按钮 -->
        <div class="action-buttons" v-if="order.status !== 'SUCCESS'">
          <el-button type="danger" @click="handlePay">去支付</el-button>
          <el-button type="danger" @click="handleDelete">删除订单</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.order-detail-page {
  min-height: 100vh;
  background:
      linear-gradient(90deg, transparent 0%, rgba(139, 69, 19, 0.03) 50%, transparent 100%),
      repeating-linear-gradient(0deg, #faf8f3 0px, #faf8f3 2px, #f5f1e8 2px, #f5f1e8 4px);
  position: relative;
}

.order-detail-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 24px 24px 60px;
  position: relative;
  z-index: 1;
}

.order-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #d8aa84;
  padding: 24px;
  border-radius: 12px;
  margin-bottom: 20px;
  box-shadow: 0 2px 8px rgba(101,67,33,0.08);
}

.order-header h1 {
  font-size: 24px;
  font-weight: 600;
  color: #fffaf0;
  margin: 0;
}

.order-content {
  background: white;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
}

.info-section {
  margin-bottom: 32px;
}

.info-section:last-of-type {
  margin-bottom: 0;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  padding-left: 12px;
  border-left: 4px solid #d8aa84;
  margin-bottom: 16px;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
  background: #fafafa;
  padding: 20px;
  border-radius: 8px;
}

.info-item {
  display: flex;
  align-items: center;
  gap: 12px;
}

.info-item.full-width {
  grid-column: 1 / -1;
}

.label {
  color: #909399;
  font-size: 14px;
  min-width: 80px;
}

.value {
  color: #303133;
  font-size: 14px;
}

.price-value {
  font-size: 18px;
  font-weight: 700;
  color: #E04E3A;
}

.product-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.product-image {
  width: 60px;
  height: 60px;
  object-fit: cover;
  border-radius: 4px;
}

.action-buttons {
  display: flex;
  justify-content: center;
  gap: 16px;
  margin-top: 32px;
  padding-top: 24px;
  border-top: 1px solid #e4e7ed;
}

:deep(.el-table) {
  font-size: 14px;
}

:deep(.el-table th) {
  background-color: #f5f7fa;
  color: #606266;
  font-weight: 600;
}

:deep(.el-table td) {
  padding: 16px 0;
}
</style>