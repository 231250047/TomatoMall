<script setup>
import {ElMenu, ElMenuItem, ElMessage} from "element-plus";
import router from "@/router/index.js";
import {ref, onMounted, watchEffect} from "vue";
import {roleUpdated} from "@/utils/request.js";

// 使用响应式变量存储角色
const role = ref('');

watchEffect(() => {
  // 依赖 roleUpdated 触发更新
  role.value = sessionStorage.getItem('role') || ''
  return roleUpdated.value // 实际依赖值
})
// 组件挂载时从sessionStorage获取角色
onMounted(() => {
  role.value = sessionStorage.getItem('role') || '';
  console.log('当前角色:', role.value); // 调试用
});

const handleWareHouse = () => {
  if(role.value === 'admin'){
    router.push('/warehouse');
  }else{
    ElMessage({
      message: "非法访问！",
      type: 'error',
      center: true,
    })
  }
}

const handleAdvertisements = () => {
  if(role.value === 'admin'){
    router.push('/advertisements');
  }else{
    ElMessage({
      message: "非法访问！",
      type: 'error',
      center: true,
    })
  }
}

</script>
<template>
  <div class="nav-bar-container">
    <el-menu class="nav-bar" mode="horizontal" :router="true">
      <el-menu-item index="/home">拾光书坊</el-menu-item>
      <el-menu-item index="/cart">购物车</el-menu-item>
      <el-menu-item   index="/tag/education"
                      :route="{ name: 'tag', params: { tag: 'education' }}"
      >分类</el-menu-item>
      <el-menu-item index="/rank">榜单</el-menu-item>
      <el-menu-item index="/warehouse" v-if="role === 'admin'" @click="handleWareHouse">库存管理</el-menu-item>
      <el-menu-item index="/advertisements" v-if="role === 'admin'" @click="handleAdvertisements">广告管理</el-menu-item>
      <el-menu-item index="/information">个人信息</el-menu-item>
    </el-menu>
  </div>
</template>

<style scoped>
.nav-bar-container {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  z-index: 1000;
  height: 60px;
  background: #fff;
  box-shadow: 0 2px 12px rgba(0,0,0,0.1);
  overflow-x: auto; /* 允许横向滚动 */
}
/* 修改element-plus组件样式 */
:deep(.nav-bar) {
  display: flex;
  justify-content: space-around;
  max-width: 1440px;
  margin: 0 auto;
  height: 100%;
  /* 新增以下两行 */
  flex-wrap: nowrap; /* 禁止换行 */
  overflow: visible !important; /* 强制显示溢出内容 */
}

:deep(.el-menu-item) {
  flex: none !important; /* 禁止flex压缩 */
  display: flex;
  justify-content: center;
  align-items: center;
  font-size: 16px;
  min-width: 150px; /* 增大最小宽度 */
  width: auto !important; /* 覆盖默认宽度 */
  white-space: nowrap;
  padding: 0 25px !important; /* 增大间距 */
  transition: all 0.3s ease;
  /* 强制覆盖element-plus样式 */
  overflow: visible !important;
  text-overflow: clip !important;
}

:deep(.el-menu-item) .el-tooltip__trigger {
  overflow: visible !important;
  text-overflow: clip !important;
  width: auto !important;
}

/* 若需要更精确控制（根据DOM结构调整） */
:deep(.el-menu-item) :deep(.el-tooltip__trigger) {
  overflow: visible !important;
  text-overflow: clip !important;
}

/* 响应式调整 */
@media (max-width: 1200px) {
  :deep(.el-menu-item) {
    min-width: 130px;
    padding: 0 20px !important;
  }
}

@media (max-width: 992px) {
  :deep(.el-menu-item) {
    min-width: 110px;
    font-size: 14px;
    padding: 0 15px !important;
  }
}

@media (max-width: 768px) {
  :deep(.el-menu-item) {
    min-width: 90px;
    font-size: 13px;
    padding: 0 10px !important;
  }
}
</style>