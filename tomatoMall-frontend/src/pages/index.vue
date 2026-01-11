<script setup>
import { ref } from 'vue'
import NavigationBar from "@/components/NavigationBar.vue";
import {getUserInfo} from "@/api/user.js";
import {ElMessage} from "element-plus";
import {roleUpdated} from "@/utils/request.js";
import HomePage from "@/pages/HomePage.vue";

const isAdmin = ref(false)

getUserInfo().then((res) => {
  if (res.data.code === '200') {
    const role = res.data.data.role
    isAdmin.value = role === 'admin'
    roleUpdated.value = !roleUpdated.value // 触发更新
    console.log('获取到的用户数据:', role); // 添加日志
    sessionStorage.setItem('role', role);
    sessionStorage.setItem('change','false');
    // 移除优惠券相关状态（已隐藏优惠券功能）
    sessionStorage.removeItem('ticketShow');
    sessionStorage.removeItem('discount');
  }else{
    console.error('获取用户信息失败:', error);
    ElMessage.error('获取用户信息失败');
  }
})



</script>

<template>
  <NavigationBar v-if="isAdmin" />
  <HomePage/>
</template>

<style scoped>

</style>
