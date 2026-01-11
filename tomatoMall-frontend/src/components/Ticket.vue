<script setup>
import {onMounted, ref} from 'vue'
import {ElMessage} from "element-plus";

// 优惠券数据
const coupons = [
  {threshold: 50, discount: 5, text: "满50减5"},
  {threshold: 200, discount: 30, text: "满200减30"},
  {threshold: 300, discount: 50, text: "满300减50"}
]

// 当前显示的优惠券
const currentCoupon = ref(null)
const showCouponDialog = ref(false)

// 随机选择优惠券
const showRandomCoupon = () => {
  sessionStorage.setItem('ticketShow', 'false');
  const randomIndex = Math.floor(Math.random() * coupons.length)
  currentCoupon.value = coupons[randomIndex]
  showCouponDialog.value = true
}

// 使用优惠券
const useCoupon = () => {
  ElMessage.success(`已使用${currentCoupon.value.text}优惠券`)
  sessionStorage.setItem('discount', currentCoupon.value.discount)
  console.log('折扣',currentCoupon.value.discount)
  showCouponDialog.value = false
  // 这里可以添加实际使用优惠券的逻辑
}

<script setup>
// 优惠券组件已移除 — 保留空组件以免意外引用时报错
</script>

<template>
  <!-- 已移除 -->
  <div style="display:none"></div>
</template>

<style scoped>
</style>
.coupon-actions {
  display: flex;
  justify-content: space-around;
  margin-top: 20px;
}
</style>