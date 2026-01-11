import axios from 'axios'
import {ref} from "vue";

//创建一个axios的实例service
const service = axios.create()

//判断是否登录
function hasToken() {
    return !(sessionStorage.getItem('token') == '')
}

//当前实例的拦截器，对所有要发送给后端的请求进行处理，在其中加入token
service.interceptors.request.use(
    config => {
        if(hasToken()) {
            config.headers['token'] = sessionStorage.getItem('token')
        }
        return config
    },
    error => {
        console.log(error);
        return Promise.reject(error);
    }
)

//当前实例的拦截器，对所有从后端收到的请求进行处理，检验http的状态码
service.interceptors.response.use(
    response => {
        if (response.status === 200) {
            return response;
        } else {
            const err: any = new Error('Response status not OK');
            err.response = response;
            return Promise.reject(err);
        }
    },
    error => {
        console.log(error);
        return Promise.reject(error);
    }
)

//设置为全局变量
export {
    service as axios
}

export const roleUpdated = ref(false)
