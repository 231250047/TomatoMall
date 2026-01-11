<script setup>

import NavigationBar from "@/components/NavigationBar.vue";
import deco3 from '@/assets/images/deco_3.jpg'
import sell2 from '@/assets/images/sell2.png'
import { ref, onMounted, computed } from 'vue';
import { ElButton, ElForm, ElFormItem, ElInput } from 'element-plus';
import { Plus, Delete, Check } from '@element-plus/icons-vue';
import {addProduct} from "@/api/product.ts";
import { getUserInfo } from "@/api/user.ts";
import OssUpload from "@/components/OssUpload.vue";
import router from "@/router/index.js";

const isAdmin = computed(() => sessionStorage.getItem('role') === 'admin')

// 响应式数据
const formData = ref({
  title: '',
  price: 0,
  rate: 0,
  description: '',
  cover: '',
  detail: '',
  specifications: [],
  tag: '', // 新增分类字段
  condition: '10_NEW', // 新增成色字段，默认 10_NEW
  sellerId: null
});

// 用户ID
const userId = ref(null);

// 获取当前用户信息
const fetchUserInfo = async () => {
  try {
    console.log('CreateProduct: 开始获取用户信息...');
    const res = await getUserInfo();
    console.log('CreateProduct: getUserInfo 完整响应:', res);
    console.log('CreateProduct: res.data:', res.data);
    console.log('CreateProduct: res.data.code:', res.data?.code);
    console.log('CreateProduct: res.data.data:', res.data?.data);
    
    if (res.data.code === '200') {
      const userIdValue = res.data.data.id;
      console.log('CreateProduct: 获取到的用户ID:', userIdValue);
      userId.value = userIdValue;
      formData.value.sellerId = userIdValue;
      console.log('CreateProduct: userId.value 设置为:', userId.value);
      console.log('CreateProduct: formData.value.sellerId 设置为:', formData.value.sellerId);
    } else {
      console.error('CreateProduct: 响应code不是200:', res.data.code);
    }
  } catch (error) {
    console.error('CreateProduct: 获取用户信息失败:', error);
  }
};

onMounted(() => {
  fetchUserInfo();
});

// 分类选项
const categoryOptions = [
  { label: '教辅', value: 'education' },
  { label: '文学', value: 'literature' },
  { label: '艺术', value: 'art' },
  { label: '管理', value: 'management' },
  { label: '历史', value: 'history' },
  { label: '哲学宗教', value: 'philosophy' },
  { label: '保健养生', value: 'health' },
  { label: '科技', value: 'science' }
];

// 成色选项（与后端 enum 对应）
const conditionOptions = [
  { label: '10成新', value: '10_NEW' },
  { label: '8成新', value: '8_NEW' },
  { label: '5成新', value: '5_NEW' },
  { label: '4成新', value: '4_NEW' },
  { label: '旧', value: 'OLD' }
];

// 上传封面引用
const uploadRef = ref();

const submitProduct = () =>{
  const newCover = uploadRef.value.getImageUrl();
  if (newCover) {
    formData.value.cover = newCover;
  }

  // 数据清理：确保类型正确
  const cleanedData = {
    ...formData.value,
    price: Number(formData.value.price) || 0,
    rate: Number(formData.value.rate) || 0,
    tag: formData.value.tag || 'education', // 如果为空，默认为 education
    condition: formData.value.condition || '10_NEW', // 保证传递成色
    sellerId: formData.value.sellerId
  };

  console.log('========== 提交商品 START ==========');
  console.log('原始表单数据:', JSON.stringify(formData.value, null, 2));
  console.log('清理后数据:', JSON.stringify(cleanedData, null, 2));
  console.log('sellerId 类型:', typeof cleanedData.sellerId);
  console.log('sellerId 值:', cleanedData.sellerId);
  console.log('price 类型:', typeof cleanedData.price, '值:', cleanedData.price);
  console.log('rate 类型:', typeof cleanedData.rate, '值:', cleanedData.rate);
  console.log('tag 值:', cleanedData.tag);
  
  // 确保 sellerId 存在
  if (!cleanedData.sellerId) {
    console.error('❗ 错误: sellerId 为空！');
    console.error('userId 值:', userId.value);
  } else {
    console.log('✅ sellerId 存在:', cleanedData.sellerId);
  }
  
  console.log('即将调用 addProduct API...');
  addProduct(cleanedData)
      .then(data => {
        console.log('✅ 商品添加成功，返回数据:', data);
        console.log('========== 提交商品 END ==========');
        
        // 延迟跳转以便查看控制台日志
        setTimeout(() => {
          router.push('/warehouse').then(()=>{
            window.location.reload();
          });
        }, 2000); // 2秒后跳转
      })
      .catch(error => {
        // 错误处理，显示用户友好的错误信息
        console.error("❌ 商品添加失败:", error);
        console.log('========== 提交商品 END (ERROR) ==========');
        
        // 错误时也延迟跳转
        setTimeout(() => {
          router.push('/warehouse').then(()=>{
            window.location.reload();
          });
        }, 2000);
      });

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
  <div class="create-product-page">
    <img :src="deco3" class="deco-img deco-3" alt="" />
    <NavigationBar v-if="isAdmin"></NavigationBar>
    <div class="page-container">
      <div class="page-header">
        <h1 class="page-title">
          <img :src="sell2" class="title-icon" alt="卖书" />
          添加商品
        </h1>
        <p class="page-subtitle">填写商品信息，让书籍找到新主人</p>
      </div>

      <div class="form-card">
        <el-form :model="formData" ref="formRef" label-width="120px" label-position="left" size="large">
        <!-- 新增分类选择 -->
        <el-form-item label="商品分类" prop="tag"
                      :rules="[{ required: true, message: '请选择商品分类', trigger: 'change' }]">
          <el-select
              v-model="formData.tag"
              placeholder="请选择商品分类"
              style="width: 100%"
              size="large"
          >
            <el-option
                v-for="item in categoryOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
            />
          </el-select>
        </el-form-item>

        <!-- 成色选择 -->
        <el-form-item label="成色" prop="condition"
                      :rules="[{ required: true, message: '请选择商品成色', trigger: 'change' }]">
          <el-select
              v-model="formData.condition"
              placeholder="请选择商品成色"
              style="width: 100%"
              size="large"
          >
            <el-option
                v-for="item in conditionOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
            />
          </el-select>
        </el-form-item>

        <!-- 商品封面 -->
        <el-form-item label="商品封面" prop="cover">
          <div class="upload-container">
            <OssUpload ref="uploadRef" :initialImage="formData.cover" />
            <p class="upload-hint">建议尺寸：800x800px，支持JPG、PNG格式</p>
          </div>
        </el-form-item>

        <!-- 商品名称 -->
        <el-form-item label="商品名称" prop="title" :rules="[{ required: true, message: '请输入商品名称', trigger: 'blur' }]">
          <el-input v-model="formData.title" placeholder="请输入商品名称" size="large" />
        </el-form-item>

        <!-- 商品价格 -->
        <el-form-item label="商品价格" prop="price" :rules="[{ required: true, message: '请输入商品价格', trigger: 'blur' }]">
          <el-input v-model="formData.price" placeholder="请输入商品价格" size="large">
            <template #prepend>￥</template>
          </el-input>
        </el-form-item>

        <!-- 商品评分 -->
        <el-form-item label="商品评分" prop="rate" :rules="[{ required: true, message: '请输入商品评分', trigger: 'blur' }]">
          <el-input v-model="formData.rate" type="number" placeholder="请输入10.0以内的评分" size="large">
            <template #append>⭐</template>
          </el-input>
        </el-form-item>

        <!-- 商品描述 -->
        <el-form-item label="商品描述">
          <el-input 
            v-model="formData.description" 
            type="textarea" 
            :rows="3" 
            placeholder="请输入商品简要描述" 
            maxlength="200" 
            show-word-limit
            size="large"
          />
        </el-form-item>

        <!-- 商品详细说明 -->
        <el-form-item label="详细说明">
          <el-input 
            v-model="formData.detail" 
            type="textarea" 
            :rows="5" 
            placeholder="请输入商品详细说明" 
            maxlength="500" 
            show-word-limit
            size="large"
          />
        </el-form-item>

        <!-- 商品规格 -->
        <el-form-item label="商品规格">
          <div class="spec-list">
            <div v-for="(spec, index) in formData.specifications" :key="index" class="spec-item">
              <el-input v-model="spec.item" placeholder="规格名称（如：ISBN、作者）" size="large" class="spec-input" />
              <el-input v-model="spec.value" placeholder="规格内容" size="large" class="spec-input" />
              <el-button type="danger" @click="removeSpecification(index)" size="large" circle>
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
            <el-button type="primary" plain @click="addSpecification" size="large" class="add-spec-btn">
              <el-icon style="margin-right: 8px;"><Plus /></el-icon>
              添加规格
            </el-button>
          </div>
        </el-form-item>
      </el-form>

      <div class="form-footer">
        <el-button @click="$router.back()" size="large" class="cancel-btn">取消</el-button>
        <el-button type="primary" @click="submitProduct" size="large" class="submit-btn">
          <el-icon style="margin-right: 8px;"><Check /></el-icon>
          提交商品
        </el-button>
      </div>
    </div>
  </div>
</div>

</template>

<style scoped>
.create-product-page {
  min-height: 100vh;
  background: #f6efe5;
  position: relative;
}

.create-product-page::before {
  content: '🍁';
  position: fixed;
  top: 150px;
  right: 80px;
  font-size: 100px;
  opacity: 0.08;
  transform: rotate(-30deg);
  z-index: 0;
}

/* 装饰图（参考首页样式） */
.deco-img {
  position: fixed;
  z-index: 0;
  opacity: 0.28;
  pointer-events: none;
  mix-blend-mode: multiply;
}

.deco-3 {
  top: 100px;
  right: 120px;
  width: 260px;
  transform: rotate(-10deg);
}

.page-container {
  max-width: 900px;
  margin: 0 auto;
  padding: 40px 24px;
}

/* 页面标题 */
.page-header {
  text-align: center;
  margin-bottom: 40px;
}

.page-title {
  font-size: 36px;
  font-weight: 800;
  color: #111827;
  margin: 0 0 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
}

.title-icon {
  width: 56px;
  height: 56px;
  object-fit: contain;
  display: inline-block;
  margin-right: 6px; /* 更靠近标题文字 */
  vertical-align: -8px;
}

.page-subtitle {
  font-size: 16px;
  color: #6b7280;
  margin: 0;
}

/* 表单卡片 */
.form-card {
  background: #ffffff;
  border-radius: 16px;
  padding: 40px;
  box-shadow: 0 4px 24px rgba(245, 158, 11, 0.1);
  margin-bottom: 24px;
  position: relative;
  z-index: 1;
}

/* 表单样式 */
:deep(.el-form-item__label) {
  font-size: 16px;
  font-weight: 600;
  color: #374151;
}

:deep(.el-input__inner),
:deep(.el-textarea__inner) {
  font-size: 15px;
  border-radius: 8px;
}

:deep(.el-select) {
  width: 100%;
}

/* 上传容器 */
.upload-container {
  width: 100%;
}

.upload-hint {
  margin-top: 12px;
  font-size: 14px;
  color: #9ca3af;
  text-align: center;
}

/* 规格列表 */
.spec-list {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.spec-item {
  display: flex;
  gap: 12px;
  align-items: center;
}

/* 把规格相关按钮和输入聚焦色统一为页面橙色 */
.spec-item :deep(.el-button--danger) {
  background-color: #c27935 !important; /* 橙色 */
  border-color: #c27935 !important;
  color: #ffffff !important;
}
.spec-item :deep(.el-button--danger:hover) {
  background-color: #9B341F !important;
  border-color: #9B341F !important;
}

/* 添加规格按钮改为橙色描边风格 */
.add-spec-btn {
  width: 100%;
  margin-top: 8px;
  border-style: dashed;
  border-color: #c27935 !important;
  color: #c27935 !important;
  background: transparent !important;
}
.add-spec-btn:hover {
  background-color: rgba(194,121,53,0.08) !important;
}

/* 输入获得焦点时的橙色高亮（仅规格区的输入） */
.spec-item :deep(.el-input__inner):focus,
.spec-item :deep(.el-textarea__inner):focus {
  box-shadow: 0 0 0 1px #c27935 inset !important;
  border-color: #c27935 !important;
}

/* 表单底部 */
.form-footer {
  display: flex;
  justify-content: center;
  gap: 16px;
  padding: 20px;
}

.cancel-btn,
.submit-btn {
  min-width: 140px;
  height: 48px;
  font-size: 16px;
  font-weight: 600;
  border-radius: 24px;
}

.submit-btn {
  background: #c27935ff;
  border: none;
}

.submit-btn:hover {
  background: #9B341F !important;
  transform: translateY(-2px);
  box-shadow: 0 8px 20px rgba(153, 90, 46, 0.28);
}

/* 将 danger 按钮调整为页面色调一致的枫叶红（包括 text 模式） */
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
:deep(.el-button--danger.is-text) {
  color: #E04E3A !important;
  background-color: transparent !important;
}
:deep(.el-button--danger.is-text:hover) {
  color: #9B341F !important;
  background-color: rgba(192,65,43,0.06) !important;
}
:deep(.el-button--danger .el-icon),
:deep(.el-button--danger.is-text .el-icon) {
  color: inherit !important;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .page-container {
    padding: 24px 16px;
  }
  
  .form-card {
    padding: 24px 20px;
    border-radius: 12px;
  }
  
  .page-title {
    font-size: 28px;
  }
  .title-icon {
    width: 40px;
    height: 40px;
    margin-right: 6px;
    vertical-align: -6px;
  }
  
  .spec-item {
    flex-direction: column;
  }
  
  .spec-item .el-button {
    width: 100%;
  }
  
  .form-footer {
    flex-direction: column;
  }
  
  .cancel-btn,
  .submit-btn {
    width: 100%;
  }
}

/* 删除旧样式 */
.warehouse-container {
  /* 保留以防其他地方引用 */
}

.add-product-btn {
  /* 删除，不再需要 */
}

.dialog-footer {
  /* 删除，不再需要 */
}

.cover-container {
  /* 已替换为 upload-container */
}
</style>