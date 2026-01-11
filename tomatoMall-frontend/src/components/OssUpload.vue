<template>
  <div>
    <el-upload
        class="avatar-uploader"
        action="#"
        :show-file-list="false"
        :before-upload="beforeUpload"
        :http-request="handleUpload"
    >
      <!-- 头像展示区域 -->
      <img v-if="imageUrl" :src="imageUrl" class="avatar"
           @error="handleImageError" />
      <el-icon v-else class="avatar-uploader-icon">
        <Plus />
      </el-icon>
    </el-upload>
  </div>
</template>

<script setup lang="ts">
import { ref,watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import type { UploadProps } from 'element-plus'
import { uploadOssFile } from '../api/oss' // 导入上传API
import { uploadFileAsBase64 } from '../utils/base64Upload' // 导入 Base64 上传（备用方案）

// 是否使用 Base64 模式（当 OSS 不可用时）
const USE_BASE64_MODE = false // 设置为 true 使用 Base64，false 使用 OSS

// 添加props接收初始图片
const props = defineProps({
  initialImage: {
    type: String,
    default: ''
  }
});
// 头像的预览 URL（用于展示）
const imageUrl = ref(props.initialImage || '')

// 监听initialImage变化
watch(() => props.initialImage, (newVal) => {
  imageUrl.value =  newVal ? newVal : ''; // 过滤无效值
});

// 上传前的校验
const beforeUpload: UploadProps['beforeUpload'] = (file) => {
  const isImage = file.type.startsWith('image/')
  const isLt5MB = file.size / 1024 / 1024 < 5

  if (!isImage) {
    ElMessage.error('只能上传图片文件！')
    return false
  }
  if (!isLt5MB) {
    ElMessage.error('图片大小不能超过5MB！')
    return false
  }
  return true
}

const handleImageError = (e: Event) => {
  const img = e.target as HTMLImageElement;
  // 只有当有有效URL时才报错（避免空URL误报）
  if (img.src) {
    console.error('图片加载失败:', e);
    console.error('失败的图片 URL:', img.src);
    console.error('请在新标签页打开此 URL 检查是否能访问:', img.src);
    
    // 尝试在新标签页打开图片，帮助调试
    console.log('点击这里在新标签页查看图片:', img.src);
    
    ElMessage.error({
      message: '头像加载失败！可能原因：1) OSS Bucket权限未设置为公共读 2) CORS配置问题 3) URL错误',
      duration: 5000
    });
    
    // 不清空 URL，让用户可以看到问题
    // imageUrl.value = ''; 
  }
};


// 自定义上传方法
/*
当用户选择文件后，Element Plus 的 <el-upload> 组件默认会通过 <form> 提交文件到指定的 action URL。
自定义需求：如果你需要手动控制上传逻辑（例如使用 axios 发送请求、添加自定义参数），就需要覆盖默认行为，此时要用 http-request

<el-upload :http-request="handleUpload">
  <!-- ... -->
</el-upload>
当用户选择文件后，Element Plus 会调用 handleUpload，并传入一个对象参数，其结构大致如下：
  {
    file: File,            // 用户选择的文件对象
    onProgress: (event: ProgressEvent) => void,  // 上传进度回调
    onSuccess: (response: any) => void,          // 上传成功回调
    onError: (error: Error) => void              // 上传失败回调
  }
  所以要通过解构获取真正的 File 对象：
    const handleUpload = async ({ file }: { file: File }) => { //  解构出 file
    formData.append('file', file); // 附加真实的文件
};
 */
const handleUpload = async (options: { file: File }) => {
    const formData = new FormData()
  formData.append('file', options.file);
  
  // 检查文件大小，如果太大就提示压缩
  const fileSizeMB = options.file.size / 1024 / 1024;
  console.log(`原始文件大小: ${fileSizeMB.toFixed(2)} MB`);
  
  if (fileSizeMB > 2) {
    ElMessage.warning('图片较大，正在压缩...');
  }
  
  try {
    // 根据配置选择上传方式
    const res = USE_BASE64_MODE 
      ? await uploadFileAsBase64(formData)  // Base64 模式（本地存储）
      : await uploadOssFile(formData)       // OSS 模式（云存储）
    
    console.log('后端返回数据:', res.data);
    if (res.data.code === '200') {
      imageUrl.value = res.data.data
      
      // 检查生成的 Base64 大小
      if (USE_BASE64_MODE && res.data.data) {
        const base64SizeKB = (res.data.data.length * 0.75 / 1024).toFixed(2);
        console.log(`Base64 大小: ${base64SizeKB} KB`);
        
        // 如果 Base64 超过 1MB，警告用户
        if (parseFloat(base64SizeKB) > 1024) {
          ElMessage.warning(`头像较大 (${(parseFloat(base64SizeKB) / 1024).toFixed(2)} MB)，可能影响加载速度`);
        }
      }
      
      console.log('头像 URL:', imageUrl.value.substring(0, 100) + '...');
      
      const mode = USE_BASE64_MODE ? 'Base64 本地存储' : 'OSS 云存储'
      ElMessage.success(res.data.message || `上传成功（${mode}）`)
      return res.data.data
    } else {
      ElMessage.error(res.data.message || '上传失败')
      return null
    }
  }catch (error) {
    console.error('上传错误:', error);
    
    // 如果 OSS 失败，自动尝试 Base64 模式
    if (!USE_BASE64_MODE) {
      console.log('OSS 上传失败，尝试使用 Base64 模式...')
      try {
        const fallbackRes = await uploadFileAsBase64(formData)
        if (fallbackRes.data.code === '200') {
          imageUrl.value = fallbackRes.data.data
          ElMessage.success('已自动切换到本地存储模式')
          return fallbackRes.data.data
        }
      } catch (fallbackError) {
        console.error('Base64 备用方案也失败了:', fallbackError)
      }
    }
    
    ElMessage.error('上传失败，请检查网络或联系管理员');
  }
}

// 暴露方法（供父组件获取当前头像 URL）
defineExpose({
  getImageUrl: () => imageUrl.value,
  handleUpload
})
</script>

<style scoped>
.avatar-uploader .avatar {
  width: 120px;
  height: 120px;
  border-radius: 50%;  /* 圆形头像 */
  object-fit: cover;   /* 关键：保持比例填充 */
  display: block;      /* 避免行内元素间距问题 */
}
</style>