<script setup>

import NavigationBar from "@/components/NavigationBar.vue";
import { ref } from 'vue';
import { ElButton, ElForm, ElFormItem, ElInput, ElMessage } from 'element-plus';
import {updateProduct} from "@/api/product.ts";
import OssUpload from "@/components/OssUpload.vue";
import router from "@/router/index.js";
import {useRoute} from "vue-router";

// 响应式数据
const formData = ref({
  id: '',
  title: '',
  price: 0,
  rate: 0,
  description: '',
  cover: '',
  detail: '',
  specifications: []
});

const route = useRoute(); // 获取当前路由对象
const productId = route.params.id; // 从路由参数中获取 id

const uploadRef = ref();
const submitProduct = () =>{
  formData.value.id = productId;


  const newCover = uploadRef.value.getImageUrl();
  if (newCover) {
    formData.value.cover = newCover;
  }
  if(!formData.value.cover) {
    formData.value.cover = null;
  }


  console.log(formData.value);
  updateProduct(formData.value).then(res => {
    if(res.code === "200") {
      ElMessage({
        message: "更新信息成功！",
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

  router.push(`/products/${productId}`).then(()=>{
    window.location.reload();
  })

}




// 添加一组商品规格
const addSpecification = () => {
  formData.value.specifications.push({ item: '', value: '' }); // 添加新的规格项
};

// 移除一组商品规格
const removeSpecification = (index) => {
  formData.value.specifications.splice(index, 1); // 移除指定索引的规格项
};


</script>

<template>
  <h1>更新商品信息</h1>
  <NavigationBar></NavigationBar>
  <div class="warehouse-container">

    <el-form :model="formData" ref="formRef" label-width="100px">

      <!-- 商品封面 -->
      <el-form-item label="商品封面" prop="cover">
        <div class="cover-container">
          <OssUpload ref="uploadRef" :initialImage="formData.cover" />
        </div>
      </el-form-item>

      <!-- 商品名称 -->
      <el-form-item label="商品名称" prop="title" :rules="[{ required: true, message: '请输入商品名称', trigger: 'blur' }]">
        <el-input v-model="formData.title" placeholder="请输入商品名称" />
      </el-form-item>

      <!-- 商品价格 -->
      <el-form-item label="商品价格" prop="price" :rules="[{ required: true, message: '请输入商品价格', trigger: 'blur' }]">
        <el-input v-model="formData.price" placeholder="请输入商品价格" />
      </el-form-item>

      <!-- 商品评分 -->
      <el-form-item label="商品评分" prop="rate" :rules="[{ required: true, message: '请输入商品评分', trigger: 'blur' }]">
        <el-input v-model="formData.rate" type="number" placeholder="请输入商品评分" />
      </el-form-item>

      <!-- 商品描述 -->
      <el-form-item label="商品描述">
        <el-input v-model="formData.description" placeholder="请输入商品描述" />
      </el-form-item>

      <!-- 商品详细说明 -->
      <el-form-item label="商品详细说明">
        <el-input v-model="formData.detail" placeholder="请输入商品详细说明" />
      </el-form-item>

      <!-- 商品规格 -->
      <el-form-item label="商品规格">
        <div v-for="(spec, index) in formData.specifications" :key="index" class="spec-item">
          <el-input v-model="spec.item" placeholder="规格名称" style="width: 200px; margin-right: 10px;" />
          <el-input v-model="spec.value" placeholder="规格内容" style="width: 200px; margin-right: 10px;" />
          <el-button type="danger" icon="el-icon-delete" @click="removeSpecification(index)">删除</el-button>
        </div>
        <el-button type="primary" icon="el-icon-plus" @click="addSpecification">添加规格</el-button>
      </el-form-item>
    </el-form>

    <div slot="footer" class="dialog-footer">
      <el-button type="primary" @click="submitProduct">确定</el-button>
    </div>
  </div>

</template>

<style scoped>
.warehouse-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 20px;
}

.add-product-btn {
  position: fixed;
  bottom: 20px;
  right: 20px;
  z-index: 100;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
}

/* 商品封面上传框样式 */
.cover-container {
  display: flex;
  justify-content: center;
  align-items: center;
  border: 1px dashed #dcdfe6;
  border-radius: 5px;
  padding: 10px;
  margin-bottom: 20px;
  background-color: #f9f9f9;
  min-height: 80px; /* 确保上传框有一定高度 */
}

.cover-container .el-upload {
  width: 100%;
}

.cover-container .el-upload .el-upload__text {
  color: #8B5A2B;
  font-size: 14px;
}

/* 使按钮和表单项的对齐和间距更加一致 */
.el-form-item {
  margin-bottom: 20px; /* 提高表单项之间的间距 */
}

.el-form-item label {
  font-weight: bold; /* 强调标签文字 */
}

.el-dialog {
  width: 600px; /* 弹框宽度 */
}
.spec-item {
  margin-bottom: 10px;
}

</style>