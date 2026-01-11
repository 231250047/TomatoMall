<script setup>

import NavigationBar from "@/components/NavigationBar.vue";
import ProductList from "@/components/ProductList.vue";
import { ElButton} from 'element-plus';
import router from "@/router/index.js";

const AddProduct=()=>{
  router.push('/createProduct');
}
//存储是否处于仓库管理模式，用于组件中按钮的显示
sessionStorage.setItem('change','true');



</script>

<template>
  <div class="content">
    <h1>库存管理</h1>
    <NavigationBar></NavigationBar>
    <ProductList></ProductList>
    <div class="warehouse-container">
      <!-- 其他内容可以放在这里 -->
      <div class="content"></div>

      <!-- 固定的增加商品按钮 -->
      <el-button
          class="add-product-btn"
          type="primary"
          icon="el-icon-plus"
          @click="AddProduct">
        添加商品
      </el-button>
    </div>
  </div>

</template>

<style scoped>
.content{
  padding-top: 50px;
  max-width: 1000px;
  margin: 0 auto;
}
.warehouse-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 50px;
}

.add-product-btn {
  position: fixed;
  bottom: 20px;
  right: 20px;
  z-index: 100;
}

</style>