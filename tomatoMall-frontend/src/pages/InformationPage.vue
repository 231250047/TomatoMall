<script setup>
import { ref, onMounted,computed } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Picture, Edit, Delete } from '@element-plus/icons-vue';
import { getUserInfo, updateUserInfo } from '../api/user';
import { getProductsBySellerId, deleteProduct } from '../api/product';
import { getPurchasedProducts } from '../api/order';
import NavigationBar from "@/components/NavigationBar.vue";
import OssUpload from "@/components/OssUpload.vue";
import ProductCard from "@/components/ProductCard.vue";

// 是否为管理员
const isAdmin = computed(() => {
  return sessionStorage.getItem('role') === 'admin'
})

// 用户信息数据
const userInfo = ref({
    username: '',
    name: '',
    role: '',
    avatar: '',
    telephone: '',
    email: '',
    location: '',
    id: null
});

// 我的宝贝（发布的商品）
const soldProducts = ref([]);
// 我买到的商品
const purchasedProducts = ref([]);

// 编辑状态控制
const isEditing = ref(false);
// 表单数据
const form = ref({});
// 加载状态
const loading = ref(false);
// 上传头像引用
const uploadRef = ref();
const router = useRouter();

// 退出登录：清理会话并跳转到登录页
const logout = () => {
    // 清理常见的会话字段（可根据项目扩展）
    sessionStorage.removeItem('token');
    sessionStorage.removeItem('username');
    sessionStorage.removeItem('role');
    sessionStorage.removeItem('id');
    sessionStorage.removeItem('ticketShow');
    sessionStorage.removeItem('discount');
    sessionStorage.removeItem('selectedItems');
    // 如需完全清理：sessionStorage.clear();
    ElMessage.success('已退出登录');
    router.push('/login');
}

// 获取用户信息
const fetchUserInfo = async () => {
    loading.value = true;
    try {
        const res = await getUserInfo();
        console.log('getUserInfo 完整响应:', res);
        if (res.data.code === '200') {
          console.log('获取到的用户数据:', res.data.data);
          userInfo.value.username=res.data.data.username;
          userInfo.value.name=res.data.data.name;
          userInfo.value.role=res.data.data.role;
          userInfo.value.avatar=res.data.data.avatar;
          userInfo.value.telephone=res.data.data.telephone;
          userInfo.value.location=res.data.data.location;
          userInfo.value.email = res.data.data.email;
          userInfo.value.id = res.data.data.id;
          
          console.log('userInfo.value.id 设置为:', userInfo.value.id);

          // 确保 id 存在后再获取商品
          if (userInfo.value.id) {
            fetchSoldProducts();
            fetchPurchasedProducts();
          } else {
            console.error('错误：用户ID为空');
            ElMessage.error('无法获取用户ID');
          }
        }
    } catch (error) {
        console.error('获取用户信息失败:', error);
        ElMessage.error('获取用户信息失败');
    } finally {
        loading.value = false;
    }
};

// 获取用户发布的商品
const fetchSoldProducts = async () => {
  try {
    console.log('fetchSoldProducts 被调用，userInfo.value.id:', userInfo.value.id);
    if (!userInfo.value.id) {
      console.error('错误：userInfo.value.id 为空，无法获取卖出商品');
      return;
    }
    const res = await getProductsBySellerId(userInfo.value.id);
    console.log('获取到的卖出商品数据:', res);
    // 处理API响应：res.data 是实际的商品数组
    if (res && res.data) {
      soldProducts.value = Array.isArray(res.data) ? res.data : [];
    } else if (Array.isArray(res)) {
      soldProducts.value = res;
    } else {
      soldProducts.value = [];
    }
  } catch (error) {
    console.error('获取卖出商品失败:', error);
    soldProducts.value = [];
  }
};

const fetchPurchasedProducts = async () => {
  try {
    console.log("调用获取购买商品接口，用户ID：", userInfo.value.id);
    const res = await getPurchasedProducts(userInfo.value.id);
    console.log("获取购买商品接口响应：", res);

    if (res.code === '200') {
      purchasedProducts.value = res.data;
      console.log("购买商品列表：", purchasedProducts.value);
    } else {
      console.error("获取购买商品失败，错误信息：", res.msg || res.message);
    }
  } catch (error) {
    console.error("获取购买商品失败：", error);
  }
};

// 编辑商品
const editProduct = (productId, event) => {
  event.stopPropagation(); // 阻止事件冒泡
  router.push(`/update-product/${productId}`);
};

// 删除商品
const handleDeleteProduct = async (productId, event) => {
  event.stopPropagation(); // 阻止事件冒泡
  try {
    await ElMessageBox.confirm(
      '确定要删除这个商品吗？删除后无法恢复。',
      '警告',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
      }
    );
    
    const res = await deleteProduct(productId);
    if (res.code === '200') {
      ElMessage.success('删除成功');
      // 重新获取商品列表
      fetchSoldProducts();
    } else {
      ElMessage.error(res.msg || '删除失败');
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除商品失败:', error);
      ElMessage.error('删除失败');
    }
  }
};

// 开始编辑
const startEdit = () => {
    form.value = { ...userInfo.value };
    isEditing.value = true;
};

// 取消编辑
const cancelEdit = () => {
    isEditing.value = false;
};

// 保存编辑
const saveEdit = async () => {
    // 这里应该添加表单验证逻辑
    loading.value = true;
    try {
      // 从上传组件获取最新头像URL
        const newAvatar = uploadRef.value.getImageUrl();
        console.log('获取到的新头像 URL:', newAvatar);
        
        if (newAvatar) {
          form.value.avatar = newAvatar;
        }

      // 如果用户未上传新头像，保留原始头像
      if (!form.value.avatar || form.value.avatar === 'null') {
        form.value.avatar = userInfo.value.avatar;
      }

      // 创建要提交的数据，不包含 role 字段（后端会忽略 role 的修改）
      const submitData = {
        id: form.value.id,
        username: form.value.username,
        name: form.value.name,
        avatar: form.value.avatar,
        telephone: form.value.telephone,
        email: form.value.email,
        location: form.value.location
        // 不发送 role 和 password 字段
      };

      console.log('准备提交的表单数据:', submitData);

      // 调用后端保存接口
      const response = await updateUserInfo(submitData);
      console.log('保存响应:', response);
      
      // 检查响应状态
      if (response && (response.code === '200' || response.code === 200)) {
        // 更新本地用户信息
        Object.assign(userInfo.value, submitData);
        if (newAvatar) {
          userInfo.value.avatar = newAvatar;
        }

        ElMessage.success('保存成功');
        isEditing.value = false;
      } else {
        throw new Error(response?.message || response?.msg || '保存失败');
      }

    } catch (error) {
        console.error('保存失败:', error);
        console.error('错误类型:', typeof error);
        if (error) {
          console.error('错误对象详情:', JSON.stringify(error, Object.getOwnPropertyNames(error)));
        }
        
        let errorMsg = '保存失败，请重试';
        
        if (error?.response) {
            console.error('HTTP 错误状态:', error.response.status);
            console.error('HTTP 错误数据:', error.response.data);
            errorMsg = error.response.data?.message 
              || error.response.data?.msg 
              || `服务器错误 (${error.response.status})`;
        } else if (error && error.message) {
            errorMsg = error.message;
        }
        
        ElMessage.error(errorMsg);
    } finally {
        loading.value = false;
    }
};

// 添加角色翻译映射
const roleTranslations = {
  'admin': '管理者',
  'user': '普通用户',
  // 添加其他需要的角色翻译
};

// 添加计算属性来获取翻译后的角色
const translatedRole = computed(() => {
  return roleTranslations[userInfo.value.role] || userInfo.value.role;
})

// 添加一个计算属性用于头像显示（带时间戳防缓存）
const avatarUrl = computed(() => {
  // 如果正在编辑且表单有 avatar，优先使用表单的值（上传后）
  const src = isEditing.value && form.value && form.value.avatar 
    ? form.value.avatar 
    : userInfo.value.avatar;
  
  // 如果没有头像或为null字符串，返回空
  if (!src || src === 'null' || src === 'undefined') {
    return '';
  }
  
  // 移除已存在的时间戳参数，避免多重时间戳
  const cleanSrc = src.split('?')[0];
  
  // 添加时间戳防缓存
  return `${cleanSrc}?t=${Math.floor(Date.now()/1000)}`;
});

// 页面加载时获取用户信息
onMounted(() => {
    fetchUserInfo();
});
</script>

<template>
  <NavigationBar v-if="isAdmin" />
    <div class="information-page-wrapper">
        <div class="information-container">
            <el-card class="box-card" :element-loading-text="'加载中...'" v-if="true">
            <template #header>
                <div class="header">
                    <div class="header-left">
                        <div class="title">个人信息</div>
                        <div class="subtitle">管理您的帐号信息与联系方式</div>
                    </div>
                                        <div class="header-actions">
                                                <el-button v-if="!isEditing" type="primary" plain @click="startEdit" class="edit-info-btn">
                                                    编辑信息
                                                </el-button>
                                                <el-button v-else type="warning" @click="cancelEdit">取消</el-button>
                                                <el-button type="danger" plain style="margin-left:12px" @click="logout" class="logout-btn">退出登录</el-button>
                                        </div>
                </div>
            </template>

            <!-- 查看模式 -->
            <div v-if="!isEditing" class="content view-mode">
                <div class="user-top">
                    <div class="avatar-area">
                        <img 
                          :src="avatarUrl" 
                          alt="用户头像"
                          class="big-avatar"
                          @error="(e) => e.target.src = 'data:image/svg+xml,%3Csvg xmlns=%22http://www.w3.org/2000/svg%22 width=%22200%22 height=%22200%22%3E%3Crect fill=%22%23cccccc%22 width=%22200%22 height=%22200%22/%3E%3Ctext fill=%22%23ffffff%22 font-family=%22Arial%22 font-size=%22100%22 font-weight=%22bold%22 x=%2250%25%22 y=%2250%25%22 text-anchor=%22middle%22 dy=%220.35em%22%3E?%3C/text%3E%3C/svg%3E'"
                        />
                    </div>
                    <div class="basic-info">
                        <div class="name-row">
                            <div class="display-name">{{ userInfo.name || userInfo.username }}</div>
                        </div>
                        <div class="username">@{{ userInfo.username }}</div>

                        <div class="contact-box">
                            <div class="detail-item"><span class="label">电话：</span><span class="value">{{ userInfo.telephone || '-' }}</span></div>
                            <div class="detail-item"><span class="label">邮箱：</span><span class="value">{{ userInfo.email || '-' }}</span></div>
                            <div class="detail-item"><span class="label">地址：</span><span class="value">{{ userInfo.location || '-' }}</span></div>
                        </div>
                    </div>
                </div>
            </div>

            <!-- 编辑模式 -->
            <div v-else class="content edit-mode">
                <el-form :model="form" label-width="90px" class="edit-form">
                    <el-row :gutter="20" class="edit-grid">
                        <el-col :xs="24" :sm="8" class="avatar-col">
                            <div class="avatar-edit">
                                <OssUpload ref="uploadRef" :initialImage="form.avatar" />
                                <div class="hint">建议：正方形图片，大小&lt;2MB</div>
                            </div>
                        </el-col>

                        <el-col :xs="24" :sm="16">
                            <el-form-item label="用户名">
                                <el-input v-model="form.username" disabled></el-input>
                            </el-form-item>
                            <el-form-item label="姓名">
                                <el-input v-model="form.name"></el-input>
                            </el-form-item>
                            <el-form-item label="电话">
                                <el-input v-model="form.telephone"></el-input>
                            </el-form-item>
                            <el-form-item label="邮箱">
                                <el-input v-model="form.email"></el-input>
                            </el-form-item>
                            <el-form-item label="地址">
                                <el-input v-model="form.location"></el-input>
                            </el-form-item>
                        </el-col>
                    </el-row>

                    <div class="form-actions">
                        <el-button @click="cancelEdit">取消</el-button>
                        <el-button type="primary" @click="saveEdit" :loading="loading">保存更改</el-button>
                    </div>
                </el-form>
            </div>
        </el-card>

        <!-- 我的宝贝 -->
        <el-card v-if="isAdmin"class="box-card" style="margin-top: 20px;">
            <template #header>
                <div class="header">
                    <div class="header-left">
                        <div class="title">我发布的商品</div>
                        <div class="subtitle">您发布的所有商品</div>
                    </div>
                </div>
            </template>
            <div class="products-grid">
                <div v-if="soldProducts.length === 0" class="empty-state">
                    <el-empty description="您还没有发布任何商品" />
                </div>
                <div v-else class="product-list">
                    <div 
                        v-for="product in soldProducts" 
                        :key="product.id" 
                        class="product-item"
                        @click="$router.push(`/products/${product.id}`)"
                    >
                        <div class="product-image-wrapper">
                            <el-image 
                                :src="product.cover" 
                                fit="cover" 
                                class="product-image"
                            >
                                <template #error>
                                    <div class="image-slot">
                                        <el-icon><Picture /></el-icon>
                                    </div>
                                </template>
                            </el-image>
                            <!-- 已售标签 -->
                            <el-tag v-if="product.status === 'sold'" type="info" class="sold-tag">已售出</el-tag>
                        </div>
                        <div class="product-info">
                            <div class="product-title">{{ product.title }}</div>
                            <div class="product-price">¥{{ product.price }}</div>
                        </div>
                        <!-- 商品管理按钮 -->
                        <div class="product-actions">
                            <el-button 
                                type="primary" 
                                size="small" 
                                :icon="Edit"
                                @click="editProduct(product.id, $event)"
                                circle
                            />
                            <el-button 
                                type="danger" 
                                size="small" 
                                :icon="Delete"
                                @click="handleDeleteProduct(product.id, $event)"
                                circle
                            />
                        </div>
                    </div>
                </div>
            </div>
        </el-card>

        <!-- 我买到的商品 -->
        <el-card class="box-card" style="margin-top: 20px;">
            <template #header>
                <div class="header">
                    <div class="header-left">
                        <div class="title">我买到的</div>
                        <div class="subtitle">您购买的所有商品</div>
                    </div>
                </div>
            </template>
            <div class="products-grid">
                <div v-if="purchasedProducts.length === 0" class="empty-state">
                    <el-empty description="您还没有购买任何商品" />
                </div>
                <div v-else class="product-list">
                    <div
                        v-for="product in purchasedProducts"
                        :key="product.id"
                        class="product-item"
                        @click="$router.push(`/products/${product.id}`)"
                    >
                        <el-image
                            :src="product.cover"
                            fit="cover"
                            class="product-image"
                        >
                            <template #error>
                                <div class="image-slot">
                                    <el-icon><Picture /></el-icon>
                                </div>
                            </template>
                        </el-image>
                        <div class="product-info">
                            <div class="product-title">{{ product.title }}</div>
                            <div class="product-price">¥{{ product.price }}</div>
                        </div>
                    </div>
                </div>
            </div>
        </el-card>
    </div>
    </div>
</template>

<style scoped>
.information-page-wrapper {
    min-height: 100vh;
    background: repeating-linear-gradient(0deg, #faf8f3 0px, #faf8f3 2px, #f5f1e8 2px, #f5f1e8 4px);
    position: relative;
}

.information-page-wrapper::before {
    content: '📖';
    position: fixed;
    top: 150px;
    right: 80px;
    font-size: 100px;
    opacity: 0.05;
    transform: rotate(-20deg);
    z-index: 0;
}

.information-page-wrapper::after {
    content: '☕';
    position: fixed;
    bottom: 120px;
    left: 60px;
    font-size: 90px;
    opacity: 0.05;
    transform: rotate(15deg);
    z-index: 0;
}

.information-container {
    width: 100%;
    max-width: 1200px;
    margin: 0 auto;
    padding: 24px 24px 120px;
    position: relative;
    z-index: 1;
}

/* 卡片增强 */
:deep(.box-card) {
    border-radius: 8px;
    box-shadow: 0 6px 18px rgba(245, 158, 11, 0.12);
    overflow: visible;
    background: linear-gradient(135deg, #ffffff 0%, #f6efe5 100%);
    position: relative;
    z-index: 1;
}

/* header */
.header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 8px 0;
}
.header-left .title {
    font-size: 20px;
    font-weight: 700;
    color: #222;
}
.header-left .subtitle {
    font-size: 12px;
    color: #666;
    margin-top: 4px;
}
.header-actions .el-button {
    min-width: 120px;
}

/* 视图模式顶部 */
.user-top {
    display: flex;
    gap: 20px;
    align-items: center;
    padding: 16px 0;
}
.avatar-area { flex: 0 0 160px; display:flex; justify-content:center; }
.big-avatar {
    width: 200px;
    height: 200px;
    object-fit: cover;
    border: 4px solid #fff;
    box-shadow: 0 4px 12px rgba(0,0,0,0.12);
    border-radius: 8px;
    background: linear-gradient(135deg,#fff,#f8f8f8);
}
.contact-box { margin-top: 12px; }
.contact-box .detail-item { padding: 4px 0; }
.basic-info { flex:1; }
.display-name {
    font-size: 22px;
    font-weight: 700;
    color: #111;
    display:inline-block;
    margin-right: 12px;
}
.username { color:#777; margin-top:6px; }
.role-tag { vertical-align:middle; }

/* 详情网格 */
.details-grid { padding: 12px 0 6px; }
.detail-item { padding: 8px 0; }
.label { font-weight:600; color:#444; display:inline-block; width:72px; }
.value { color:#333; }

/* 编辑模式 */
.edit-mode .edit-grid { align-items: start; }
.avatar-col { display:flex; justify-content:center; }
.avatar-edit .hint { margin-top:8px; font-size:12px; color:#999; text-align:center; }

/* 表单按钮 */
.form-actions {
    margin-top: 20px;
    text-align: right;
}

/* 响应式调整 */
@media (max-width: 600px) {
    .user-top { flex-direction: column; align-items: center; text-align:center; }
    .basic-info { margin-top: 8px; }
    .label { width: 72px; }
}

/* 商品列表样式 */
.edit-info-btn {
    background-color: #d97706 !important;
    border-color: #d97706 !important;
    color: white !important;
}
.edit-info-btn:hover {
    background-color: #b45309 !important;
    border-color: #b45309 !important;
}
.logout-btn {
    background-color: #fef3c7 !important;
    border-color: #fef3c7 !important;
    color: #d97706 !important;
}
.logout-btn:hover {
    background-color: #fde68a !important;
    border-color: #fde68a !important;
}

.products-grid {
    padding: 12px 0;
}

.product-list {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
    gap: 16px;
}

.product-item {
    border: 1px solid #fde68a;
    border-radius: 8px;
    overflow: hidden;
    cursor: pointer;
    transition: all 0.3s ease;
    background: linear-gradient(135deg, #ffffff 0%, #f6efe5 100%);
    position: relative;
    width: 180px; /* 调整卡片宽度 */
    height: 240px; /* 调整卡片高度 */
}

.product-item:hover {
    transform: translateY(-4px);
    box-shadow: 0 4px 12px rgba(245, 158, 11, 0.2);
    border-color: #fbbf24;
}

.product-item:hover .product-actions {
    opacity: 1;
}

.product-image-wrapper {
    position: relative;
    width: 100%;
    height: 200px;
}

.product-image {
    width: 100%;
    height: 100%;
}

.sold-tag {
    position: absolute;
    top: 8px;
    right: 8px;
    font-size: 12px;
    font-weight: 600;
}

.product-actions {
    position: absolute;
    top: 8px;
    left: 8px;
    display: flex;
    gap: 4px;
    opacity: 0;
    transition: opacity 0.3s ease;
}

.product-actions .el-button {
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
    padding: 10px 16px;
    font-size: 14px;
}

.image-slot {
    display: flex;
    justify-content: center;
    align-items: center;
    width: 100%;
    height: 100%;
    background: #f5f5f5;
    color: #909399;
}

.product-info {
    padding: 12px;
}

.product-title {
    font-size: 14px;
    font-weight: 600;
    color: #333;
    margin-bottom: 8px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.product-price {
    font-size: 16px;
    font-weight: 700;
    color: #ea580c;
}

.empty-state {
    padding: 40px 0;
}

@media (max-width: 768px) {
    .product-list {
        grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
        gap: 12px;
    }
    
    .product-image {
        height: 150px;
    }
}
</style>
