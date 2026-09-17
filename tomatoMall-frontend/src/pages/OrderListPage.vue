<script setup>
import { orderStatusLabel, cancelOrder } from "@/api/order";
import NavigationBar from "@/components/NavigationBar.vue";
import { ref, onMounted, computed } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { getOrderList } from '@/api/order';
import { deleteOrder } from '@/api/order'; // 导入删除订单 API 方法

const router = useRouter();
const isAdmin = computed(() => sessionStorage.getItem('role') === 'admin');

const loading = ref(false);
const list = ref([]);

const fetchOrders = async () => {
  loading.value = true;
  try {
    const res = await getOrderList();
    const data = res?.data?.data ?? res?.data ?? res;
    list.value = Array.isArray(data) ? data : (data || []);
    console.log("Order List Response:", res);
    console.log("Parsed Data:", list.value);
  } catch (e) {
    ElMessage.error('获取订单失败');
  } finally {
    loading.value = false;
  }
};

const handleShowDetails = (orderId) => {
  router.push(`/orders/${orderId}`);
};

// const handlePay = async (orderId) => {
//   try {
//     const res = await createAliPayOrder(orderId);
//     const ali = res?.data?.data ?? res?.data ?? res;
//     if (!ali || !ali.paymentForm) {
//       ElMessage.error('支付表单获取失败');
//       return;
//     }
//     const w = window.open("", "_blank");
//     if (!w) {
//       ElMessage.error('浏览器阻止弹窗，请允许弹窗');
//       return;
//     }
//     w.document.open();
//     w.document.write(ali.paymentForm);
//     w.document.close();
//   } catch (err) {
//     ElMessage.error('发起支付失败');
//     console.error(err);
//   }
// };

const handleCancel = async (id) => {
  try {
    await ElMessageBox.confirm('确认取消订单？如正在支付，将先核实支付结果。', '取消订单');
    await cancelOrder(id);
    ElMessage.success('取消申请已提交');
    fetchOrders();
  } catch (error) { if (error !== 'cancel') ElMessage.error('取消未完成，请刷新订单状态后重试'); }
};

const handleDelete = async (orderId) => {
  try {
    await ElMessageBox.confirm('确认隐藏该订单？交易记录会保留。', '提示', {
      confirmButtonText: '确认',
      cancelButtonText: '取消'
    });
    await deleteOrder(orderId); // 调用后端删除订单接口
    ElMessage.success('订单已隐藏');
    fetchOrders(); // 刷新订单列表
  } catch (e) {
    // 用户取消操作或请求失败
  }
};

onMounted(fetchOrders);
</script>

<template>
  <div class="order-page">
    <NavigationBar v-if="isAdmin" />

    <div class="order-container">
      <div class="order-header">
        <h1>订单列表</h1>
        <p class="order-count">共 {{ list.length }} 个订单</p>
      </div>

      <div v-if="list.length === 0 && !loading" class="empty-order">
        <el-empty description="暂无订单">
          <el-button type="primary" @click="$router.push('/home')">去逛逛</el-button>
        </el-empty>
      </div>

      <div v-else class="order-content" v-loading="loading">
        <el-table :data="list" stripe style="width: 100%">
          <el-table-column prop="orderId" label="订单号" width="120" />

          <el-table-column label="金额" width="150">
            <template #default="{ row }">
              <span class="price-value">¥{{ row.totalAmount }}</span>
            </template>
          </el-table-column>

          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              <el-tag :type="row.status === 'SUCCESS' ? 'success' : 'warning'">
                {{ orderStatusLabel(row.status) }}
              </el-tag>
            </template>
          </el-table-column>

          <el-table-column prop="createTime" label="创建时间" width="200" />

          <el-table-column label="支付时间" width="200">
            <template #default="{ row }">
              {{ row.paymentTime || '-' }}
            </template>
          </el-table-column>

          <el-table-column label="操作" align="right">
            <template #default="{ row }">
              <el-button
                  type="primary"
                  size="small"
                  @click="handleShowDetails(row.orderId)"
              >
                查看详情
              </el-button>
              <el-button v-if="row.status === 'PENDING'" size="small" @click="handleCancel(row.orderId)">取消订单</el-button>
<!--              <el-button-->
<!--                  v-if="['SUCCESS', 'TIMEOUT', 'FAILED', 'CANCELLED'].includes(row.status)"-->
<!--                  type="danger"-->
<!--                  size="small"-->
<!--                  @click="handlePay(row.orderId)"-->
<!--                  style="margin-left: 8px"-->
<!--              >-->
<!--                去支付-->
<!--              </el-button>-->
              <el-button
                  v-if="['SUCCESS', 'TIMEOUT', 'FAILED', 'CANCELLED'].includes(row.status)"
                  size="small"
                  @click="handleDelete(row.orderId)"
                  style="margin-left: 8px"
              >
                隐藏订单
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 背景样式保持不变 */
.order-page {
  min-height: 100vh;
  background:
      linear-gradient(90deg, transparent 0%, rgba(139, 69, 19, 0.03) 50%, transparent 100%),
      repeating-linear-gradient(0deg, #faf8f3 0px, #faf8f3 2px, #f5f1e8 2px, #f5f1e8 4px);
  position: relative;
}

.order-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 24px 24px 60px;
  position: relative;
  z-index: 1;
}

.order-header {
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
  margin: 0 0 8px 0;
}

.order-count {
  color: #ffefdd;
  font-size: 14px;
  margin: 0;
}

.empty-order {
  background: white;
  padding: 80px 24px;
  border-radius: 12px;
  text-align: center;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
}

.order-content {
  background: white;
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
}

.price-value {
  font-size: 18px;
  font-weight: 700;
  color: #E04E3A;
}

/* 表格样式优化 */
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

:deep(.el-table__row:hover) {
  background-color: #fafafa;
}
</style>