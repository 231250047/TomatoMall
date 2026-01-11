<!--<script setup lang="ts">-->
<!--import { ref, onMounted } from 'vue';-->
<!--import { useRoute } from 'vue-router';-->
<!--import {  initiatePayment  } from '@/api/order';-->
<!--import { ElLoading, ElMessage } from 'element-plus';-->

<!--const route = useRoute();-->
<!--const paymentFormHtml = ref('');-->

<!--// 加载支付表单-->
<!--const loadPaymentForm = async () => {-->
<!--  const orderId = route.params.orderId as string;-->
<!--  const loading = ElLoading.service({ text: '加载支付中...' });-->

<!--  try {-->
<!--    const data = await  initiatePayment(orderId);-->
<!--    paymentFormHtml.value = data.paymentForm;-->
<!--  } catch (error) {-->
<!--    ElMessage.error(`支付加载失败`);-->
<!--  } finally {-->
<!--    loading.close();-->
<!--  }-->
<!--};-->

<!--onMounted(() => {-->
<!--  loadPaymentForm();-->
<!--});-->
<!--</script>-->

<!--<template>-->
<!--  <div class="payment-container">-->
<!--    <h2>订单支付</h2>-->
<!--    &lt;!&ndash; 渲染支付宝表单 &ndash;&gt;-->
<!--    <div v-html="paymentFormHtml">-->
<!--    </div>-->

<!--  </div>-->

<!--</template>-->

<!--<style scoped>-->
<!--.payment-container {-->
<!--  max-width: 800px;-->
<!--  margin: 0 auto;-->
<!--  padding: 20px;-->
<!--}-->
<!--</style>-->
<script setup>
import { ref, onMounted, nextTick } from 'vue';
import { useRoute } from 'vue-router';
import { initiatePayment } from '@/api/order';
import { ElLoading, ElMessage } from 'element-plus';


const route = useRoute();
const paymentFormHtml = ref('');

const loadPaymentForm = async () => {
  // 优惠券功能已移除
  const orderId = route.params.orderId;
  const loading = ElLoading.service({ text: '加载支付中...' });

  try {
    const data = await initiatePayment(orderId);
    paymentFormHtml.value = data.paymentForm;

    // 关键步骤：DOM更新后手动提交表单
    nextTick(() => {
      const form = document.forms.namedItem('punchout_form');
      if (form) {
        form.submit(); // 手动提交表单
      } else {
        ElMessage.error('支付表单加载失败');
      }
    });

  } catch (error) {
    ElMessage.error('支付加载失败');
  } finally {
    loading.close();
  }

};

onMounted(() => {

  loadPaymentForm();
});
</script>

<template>
  <div class="payment-container">
    <h2>订单支付</h2>
    <!-- 隐藏渲染支付宝表单 -->
    <div v-html="paymentFormHtml"></div>
  </div>
</template>