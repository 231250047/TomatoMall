<script setup>
import { ref } from 'vue'
import {ElForm, ElFormItem, ElInput, ElButton, ElSelect, ElOption, ElDialog, ElMessage} from "element-plus";
import router from "../router";
import {userRegister} from "@/api/user";
import OssUpload from "@/components/OssUpload.vue";

// 表单数据模型
const form = ref({
  username: "",
  password: "",
  name: "",
  avatar: "",
  role: "",
  telephone: "",
  email: "",
  location: "",
});

// 上传头像引用
const uploadRef = ref();

// 表单校验规则
const rules = {
  username: [
    { required: true, message: "用户名不能为空", trigger: "blur" },
    { min: 3, max: 20, message: "用户名长度在 3 到 20 个字符之间", trigger: "blur" },
  ],
  password: [
    { required: true, message: "密码不能为空", trigger: "blur" },
    { min: 6, message: "密码长度至少为 6 个字符", trigger: "blur" },
  ],
  name: [
    { required: true, message: "真实姓名不能为空", trigger: "blur" },
  ],
  role: [
    { required: true, message: "请选择用户身份", trigger: "change" },
  ],
  telephone: [
    { required: false, pattern: /^1\d{10}$/, message: "手机号格式不正确", trigger: "blur" },
  ],
  email: [
    { required: false, type: "email", message: "邮箱格式不正确", trigger: "blur" },
  ],
  location: [
    { required: false, message: "请输入位置", trigger: "blur" },
  ],
};

// 控制弹窗显示
const dialogVisible = ref(false);
const message = ref("");

const formRef = ref(); // 获取表单引用
// 提交表单的方法
function handleSubmit() {

  // 手动触发表单验证
  formRef.value.validate((valid) => {
    if (!valid) {
      ElMessage({
        message: "请检查表单填写是否正确",
        type: 'error',
        center: true,
      });
      return; // 验证不通过则停止执行
    }

    // 验证通过后才执行注册逻辑
    const newAvatar = uploadRef.value.getImageUrl();
    if (newAvatar) {
      form.value.avatar = newAvatar;
    }

    userRegister({
      username: form.value.username,
      password: form.value.password,
      name: form.value.name,
      avatar: form.value.avatar,
      role: form.value.role,
      telephone: form.value.telephone,
      email: form.value.email,
      location: form.value.location,
    }).then(res => {
      if(res.data.code==='200'){
        // 存储角色信息到 sessionStorage
        sessionStorage.setItem("role", form.value.role);

        ElMessage({
          message: "注册成功",
          type: 'success',
          center: true,
        });
        router.push({path:"/login"});
      } else if(res.data.code==='400'){
        ElMessage({
          message: "用户名已存在",
          type: 'error',
          center: true,
        });
      }
    }).catch(err => {
      ElMessage({
        message: "注册失败：" + err.message,
        type: 'error',
        center: true,
      });
    });
  });
}
</script>



<template>
  <el-container direction="vertical" class="register-container">
    <div class="avatar-container">
      <OssUpload ref="uploadRef" :initialImage="form.avatar" />
      <p>头像</p>
    </div>
    <el-form
        :model="form"
        :rules="rules"
        ref="formRef"
        label-width="120px"
        class="register-form"
        @submit.prevent="handleSubmit"
    >
      <el-form-item label="用户名" prop="username">
        <el-input v-model="form.username" placeholder="请输入用户名" />
      </el-form-item>

      <el-form-item label="密码" prop="password">
        <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
        />
      </el-form-item>

      <el-form-item label="真实姓名" prop="name">
        <el-input v-model="form.name" placeholder="请输入真实姓名" />
      </el-form-item>

<!--      <el-form-item label="头像 URL" prop="avatar">-->
<!--        <el-input v-model="form.avatar" placeholder="请输入头像 URL" />-->
<!--      </el-form-item>-->

      <el-form-item label="用户身份" prop="role">
        <el-select v-model="form.role" placeholder="请选择用户身份">
          <el-option label="管理员" value="admin" />
          <el-option label="普通用户" value="user" />
        </el-select>
      </el-form-item>

      <el-form-item label="手机号" prop="telephone">
        <el-input
            v-model="form.telephone"
            placeholder="请输入手机号"
            maxlength="11"
        />
      </el-form-item>

      <el-form-item label="邮箱" prop="email">
        <el-input v-model="form.email" placeholder="请输入邮箱" />
      </el-form-item>

      <el-form-item label="位置" prop="location">
        <el-input v-model="form.location" placeholder="请输入位置" />
      </el-form-item>

      <el-form-item>
        <el-button type="primary" native-type="submit">注册</el-button>
      </el-form-item>
    </el-form>

    <!-- 显示注册结果提示 -->
    <el-dialog :visible.sync="dialogVisible" title="注册结果">
      <p>{{ message }}</p>
      <template #footer>
        <el-button @click="dialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </el-container>
</template>


<style scoped>
.register-container {
  width: 400px;
  margin: 0 auto;
  padding: 20px;
  background-color: #fff;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  border-radius: 8px;
}

.register-form {
  margin-top: 20px;
}

.el-form-item {
  margin-bottom: 20px;
}

.el-button {
  width: 100%;
}
</style>