<script setup>

import { userLogin } from '../api/user';
import {computed, ref} from 'vue';
import {ElMessage} from "element-plus";
import router from '../router/index';

const username = ref('')
const password = ref('')

const hasUserInput = computed(() => username.value !== '')
const hasPasswordInput = computed(() => password.value !== '')

const loginDisabled = computed(() => {
  return !(hasUserInput.value&& hasPasswordInput.value)
})

function handleLogin(){
  userLogin({
    username: username.value,
    password: password.value,
  }).then(res =>{
    if (res.data.code === '200') {
      ElMessage({
        message: "登录成功！",
        type: 'success',
        center: true,
      })
      const token = res.data.data;
      sessionStorage.setItem('token', token)
      console.log(token);

      sessionStorage.setItem('username', username.value)
      console.log(username.value);

      // //  router.push('/home');
      // // @@
      // getUserInfo().then(userRes => {
      //   const userData = userRes.data.data;
      //   sessionStorage.setItem('userInfo', JSON.stringify(userData)); // 存储完整信息
      // });
      router.push('/home');

 /*     userInfo().then(res => {
        sessionStorage.setItem('username', res.data.data.username)
        sessionStorage.setItem('role', res.data.data.role)
        sessionStorage.setItem('name', res.data.data.name)
        router.push({path: "/home"})
      }  */

    } else if (res.data.code === '400') {
      ElMessage({
        message: res.data.msg,
        type: 'error',
        center: true,
      })
      password.value = ''
    }
  })
}


</script>



<template>

  <div class="login-container">

    <div class="login-box">
      <!-- 网站 Logo -->
<!--      <img src="@/assets/images/coffee.png" alt="网站 Logo" class="logo" />-->
      <h2>登录</h2>
      <el-input
          v-model="username"
          placeholder="请输入用户名"
          class="input-field"
      />
      <el-input
          v-model="password"
          type="password"
          placeholder="请输入密码"
          @keyup.enter="handleLogin"
          class="input-field"
      />
      <div class="button-group">
        <el-button @click.prevent="handleLogin" :disabled="loginDisabled"
                   type="primary" class="login-btn">登录</el-button>
      </div>
      <!-- 右下角小字注册入口 -->
      <div class="register-note">
        <router-link to="/register">还没有账号？去注册</router-link>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 主题背景：条纹 + 暖色渐变，与 HomePage 保持一致 */
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background:
    repeating-linear-gradient(0deg, #faf8f3 0px, #faf8f3 2px, #f5f1e8 2px, #f5f1e8 4px),
    linear-gradient(180deg, rgba(211,125,63,0.12) 0%, rgba(217,138,82,0.06) 50%, rgba(255,250,240,0.02) 100%);
  background-blend-mode: multiply;
  padding: 24px;
  box-sizing: border-box;
}

/* 登录卡片 */
.login-box {
  /* 使容器成为定位参照，以放置右下角注册小字 */
  position: relative;
  width: 420px;
  text-align: center;
  background: linear-gradient(180deg, #ffffff 0%, #fffaf0 100%);
  padding: 36px;
  border-radius: 12px;
  box-shadow: 0 10px 30px rgba(0,0,0,0.06);
  border: 1px solid rgba(226,214,196,0.6);
}

/* 标题更大 */
.login-box h2 {
  margin-bottom: 40px;
  color: #6B3E1E;
  font-size: 34px; /* 放大 */
  font-weight: 800;
}

/* 输入框更宽、更高、间距更大 — 容器为定位参照，inner 与 wrapper 完全重合 */
.input-field {
  position: relative;         /* 作为绝对定位参照 */
  margin: 0 auto 36px;        /* 居中并加大间距 */
  width: 360px;               /* 宽度 */
  height: 56px;               /* 固定高度，供内部元素填充 */
  box-sizing: border-box;
}

/* 使 el-input wrapper 本身也撑满容器（保留默认结构） */
.input-field :deep(.el-input) {
  height: 100% !important;
  box-sizing: border-box;
  padding: 0 !important; /* 消除 wrapper 的额外内边距，交由 inner 控制 */
}

/* 让 el-input__inner 与页面色调契合（暖米色背景、柔和边框、占位符灰） */
.input-field :deep(.el-input__inner) {
  position: absolute !important;
  inset: 0;
  width: 100%;
  height: 100%;
  padding: 14px 16px;
  box-sizing: border-box;
  border-radius: 12px;
  font-size: 17px;
  background: #fffaf0; /* 米色背景，和页面一致 */
  color: #1f2937;      /* 主文字色，与页面主体一致 */
  border: 1px solid rgba(226,214,196,0.6); /* 轻柔边框 */
  transition: box-shadow 0.18s ease, border-color 0.18s ease, background 0.18s ease;
}

/* placeholder 颜色 */
.input-field :deep(.el-input__inner)::placeholder {
  color: #9ca3af; /* 次要提示色 */
  opacity: 1;
}

/* focus 时的高亮效果（柔和橙色） */
.input-field :deep(.el-input__inner:focus) {
  outline: none;
  border-color: rgba(217,125,63,0.9);
  box-shadow: 0 4px 18px rgba(217,125,63,0.12);
  background: #fffaf0;
}

/* 同步 textarea 的 inner 行为 */
.input-field :deep(.el-textarea__inner) {
  position: absolute !important;
  inset: 0;
  width: 100%;
  height: 100%;
  padding: 14px 16px;
  box-sizing: border-box;
  border-radius: 12px;
  resize: none;
  background: #fffaf0;
  color: #1f2937;
  border: 1px solid rgba(226,214,196,0.6);
}

/* 按钮组：主按钮使用统一橘色，次要为棕色描边 */
.button-group {
  display: flex;
  justify-content: center; /* 居中按钮组 */
  gap: 12px;
  width: 100%;
}

/* 主操作按钮风格（稍微圆一点） */
.button-group :deep(.el-button--primary) {
  background-color: #c27935 !important;
  border-color: #c27935 !important;
  color: #ffffff !important;
  font-weight: 700;
  height: 44px;
  border-radius: 16px;
}

/* 悬停（深色） */
.button-group :deep(.el-button--primary:hover) {
  background-color: #9B341F !important;
  border-color: #9B341F !important;
}

/* 登录按钮更大（桌面） */
.login-btn {
  width: 200px; /* 放大宽度 */
  height: 50px;
  font-size: 17px;
  max-width: 100%;
}

/* 右下角注册小字 */
.register-note {
  position: absolute;
  right: 16px;
  bottom: 12px;
  font-size: 13px;
  color: #8B5A2B;
}
.register-note a {
  color: #8B5A2B;
  text-decoration: underline;
  cursor: pointer;
}

/* 移动端调整 */
@media (max-width: 768px) {
  .login-box {
    width: 100%;
    max-width: 420px;
    padding: 20px;
  }
  .login-box h2 {
    font-size: 26px;
  }
  .input-field {
    width: 100%;
    margin-bottom: 22px;
  }
  .input-field :deep(.el-input__inner) {
    padding: 16px 12px;
    font-size: 16px;
  }
  .button-group :deep(.el-button--primary),
  .button-group :deep(.el-button) {
    height: 44px;
  }
  .login-btn {
    width: 100%;
    height: 48px;
    font-size: 16px;
  }
}

.logo {
  width: 120px; /* 设置 Logo 宽度 */
  margin-bottom: 10px; /* 与标题之间的间距 */
}
</style>