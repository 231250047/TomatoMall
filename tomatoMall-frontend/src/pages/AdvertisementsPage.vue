<script setup>
import {ref, onMounted} from 'vue';
import { ElMessage,  ElFormItem, ElDialog, ElForm} from 'element-plus';
// import type { advertisement, advertisementNew } from '@/api/advertisements';
import {
  getAdvertisements,
  createAdvertisement,
  updateAdvertisement,
  deleteAdvertisement
} from '@/api/advertisements';
import NavigationBar from "@/components/NavigationBar.vue";
import OssUpload from "@/components/OssUpload.vue";

// 数据
//  TypeScript 会强制校验数据格式
// const advertisements = ref<advertisement[]>([]);
// const currentAd = ref<Partial<advertisement>>({});
const dialogVisible = ref(false);
const advertisements = ref([]);
const currentAd = ref({});
const isEditMode = ref(false);
const uploadRef = ref(); // OSS上传组件引用
const adForm = ref(); // 添加这行

// 表单验证规则
const formRules = {
  title: [{ required: true, message: '请输入广告标题', trigger: 'blur' }],
  content: [{ required: true, message: '请输入广告内容', trigger: 'blur' }],
  imgUrl: [{ required: true, message: '请输入图片地址', trigger: 'blur' }]
  // relatedUrl 可选，不强制校验
};

const loading = ref(false);

// 加载广告列表
const loadAdvertisements = async () => {
  try {
    advertisements.value = await getAdvertisements();
  } catch (error) {
    ElMessage.error('广告加载失败');
  }
};

// 打开创建对话框
const openCreateDialog = () => {
  currentAd.value = { imgUrl: '' }; // 明确初始化（清空图片缓存）
  isEditMode.value = false;
  dialogVisible.value = true;
};

// 打开编辑对话框
const openEditDialog = (row) => {
  currentAd.value = { ...row };
  isEditMode.value = true;
  dialogVisible.value = true;
};

// 处理删除
const handleDelete = async (id) => {
  try {
    await deleteAdvertisement(id);
    ElMessage.success('广告删除成功');
    await loadAdvertisements();
  } catch (error) {
    console.error('删除失败:', error);
    ElMessage.error('删除失败');
  }
};

// 提交表单（先从上传组件取图，再校验表单）
const submitForm = async () => {
  try {
    // 先从上传组件获取最新图片URL并写回表单，避免校验时 imgUrl 为空
    const newImage = uploadRef.value?.getImageUrl?.();
    if (newImage) {
      currentAd.value.imgUrl = newImage;
    }

    console.log("提交的广告数据：", currentAd.value);

    await adForm.value.validate();

    if (isEditMode.value) {
      await updateAdvertisement(currentAd.value);
      ElMessage.success('广告更新成功');
    } else {
      await createAdvertisement(currentAd.value);
      ElMessage.success('广告创建成功');
    }
    dialogVisible.value = false;
    await loadAdvertisements();
  } catch (error) {
    console.error('操作失败:', error);
    ElMessage.error('操作失败');
  }
};

//初始化加载
onMounted(() => {
  loadAdvertisements();
});

// onMounted(() => {
//   advertisements.value = [
//     {
//       id: 1,
//       title: "限时优惠",
//       content: "全场商品满200减50，快来抢购吧！",
//       imgUrl: require('@/assets/ad/ad1.png'), // 使用 require 引入图片
//
//     },
//     {
//       id: 2,
//       title: "新品推荐",
//       content: "最新上市的智能手机，性能强劲，价格实惠！",
//       imgUrl: require('@/assets/new-arrivals.jpg'), // 使用 require 引入图片
//       relatedUrl: "https://example.com/new-arrivals",
//     },
//     {
//       id: 3,
//       title: "会员专享",
//       content: "会员专享折扣，加入会员享更多优惠！",
//       imgUrl: require('@/assets/membership.jpg'), // 使用 require 引入图片
//       relatedUrl: "https://example.com/membership",
//     },
//   ];
// });


</script>

<template>
  <NavigationBar></NavigationBar>
  <div class="main-content">
    <div class="advertisements-page">
      <!-- 操作栏 -->
      <div class="action-bar">
        <el-button type="primary" @click="openCreateDialog">新增广告</el-button>
      </div>

      <!-- 广告列表 -->
      <el-table :data="advertisements" border>
        <el-table-column prop="title" label="标题" width="200" />
        <el-table-column prop="content" label="内容" />
        <el-table-column label="图片" width="180">
          <template #default="{ row }">
            <el-image :src="row.imgUrl" style="width: 150px; height: 100px" fit="cover" />
          </template>
        </el-table-column>
        <el-table-column prop="relatedUrl" label="跳转链接" width="220">
          <template #default="{ row }">
            <div v-if="row.relatedUrl">
              <a :href="row.relatedUrl" target="_blank" rel="noopener noreferrer">{{ row.relatedUrl }}</a>
            </div>
            <div v-else>—</div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="openEditDialog(row)">编辑</el-button>
            <el-button type="danger" size="small" @click="handleDelete(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 创建/编辑对话框 -->
      <el-dialog
          v-model="dialogVisible"
          :title="isEditMode ? '编辑广告' : '新建广告'"
          width="800px"
      >
        <el-form
            :model="currentAd"
            :rules="formRules"
            label-width="80px"
            ref="adForm"
        >
          <el-form-item label="标题" prop="title">
            <el-input v-model="currentAd.title" placeholder="请输入广告标题" />
          </el-form-item>

          <el-form-item label="内容" prop="content">
            <el-input
                v-model="currentAd.content"
                type="textarea"
                :rows="3"
                placeholder="请输入广告内容"
            />
          </el-form-item>

          <el-form-item label="图片" prop="imgUrl">
            <OssUpload
                ref="uploadRef"
                :key="isEditMode ? `edit-${currentAd.id || ''}` : 'create'"
                :initialImage="currentAd.imgUrl"
            />
          </el-form-item>
          <el-form-item label="跳转链接" prop="relatedUrl">
            <el-input v-model="currentAd.relatedUrl" placeholder="填写跳转 URL（可选）" />
          </el-form-item>
        </el-form>

        <template #footer>
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitForm">确认</el-button>
        </template>
      </el-dialog>
    </div>
  </div>
</template>

<style scoped>
/* 新增样式 */
.main-content {
  padding-top: 60px; /* 与导航栏高度一致 */
  min-height: calc(100vh - 60px); /* 可选：确保内容撑满剩余高度 */
}

.advertisements-page {
  padding: 20px;
  max-width: 1200px;
  margin: 0 auto;
  /* 移除原有的 margin-top */
}

.action-bar {
  margin-bottom: 20px;
}

.product-selector {
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 10px;
}

.selected-product {
  padding: 15px;
  background-color: #f5f7fa;
  border-radius: 4px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.product-list-container {
  max-height: 400px;
  overflow-y: auto;
}

.el-table {
  margin-top: 20px;
}

/* 统一删除按钮为枫叶红（包含 text 模式与 icon 继承） */
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
</style>