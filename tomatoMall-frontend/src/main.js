import './assets/main.css'
import ElementPlus from 'element-plus';
import 'element-plus/dist/index.css';
import './assets/css/theme.css' // 引入自定义主题文件

import { createApp } from 'vue'
import App from './App.vue'
import router from './router/index.js';
import axios from 'axios';

// 创建应用实例
const app = createApp(App)
axios.defaults.baseURL = ("http://localhost:8080")

// 统一在这里注册所有插件
app.use(ElementPlus);
app.use(router);

// 最后挂载
app.mount('#app');
