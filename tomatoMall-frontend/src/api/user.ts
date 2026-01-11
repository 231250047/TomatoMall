import { axios } from '../utils/request'
import { USER_MODULE } from "./_prefix";
import { ElMessage } from "element-plus";
// 用户信息接口
export interface UserInfo {
    id?: number;
    username?: string;
    name?: string;
    role?: string;
    avatar?: string;
    telephone?: string;
    email?: string;
    location?: string;
}

//注册信息
type RegisterInfo = {
    username: string,
    password: string,
    name: string,
    avatar?: string,
    role: string,
    telephone?: string,
    email?: string,
    location?: string,
}

//登录信息

type LoginInfo = {
    username: string,
    password: string,
}

// 用户注册
export const userRegister = (registerInfo: RegisterInfo) => {
    return axios.post("http://localhost:8080/api/accounts", registerInfo,
        { headers: { 'Content-Type': 'application/json' } })
        .then(res => {
            return res
        })
}

//用户登录
export const userLogin = (loginInfo: LoginInfo) => {
    return axios.post("http://localhost:8080/api/accounts/login", loginInfo)
        .then(res => {
            return res
        })
}

// 获取用户信息
export const getUserInfo = () => {
    const username = sessionStorage.getItem('username') || '';
    console.log(username);
    return axios.get(`http://localhost:8080/api/accounts`)
        .then(res => {
            return res
        })
}

// 根据 username 获取用户信息（用于获取其他用户信息，如卖家）
export const getUserInfoByUsername = (username: string) => {
    return axios.get(`http://localhost:8080/api/accounts/${username}`)
        .then(res => {
            return res
        })
}

export const updateUserInfo = (updateInfo: UserInfo) => {
    console.log('发送的用户信息:', updateInfo);
    console.log(sessionStorage.getItem('token'))
    return axios.put("http://localhost:8080/api/accounts", updateInfo)
        .then(res => {
            console.log('更新用户信息响应:', res);
            return res.data; // 返回 res.data 而不是整个 res
        })
        .catch((error: any) => {
            console.error('更新用户信息失败:', error);
            if (error?.response) {
                console.error('错误状态码:', error.response.status);
                console.error('错误详情:', error.response.data);
            } else {
                console.error('错误信息:', error?.message);
            }
            throw error; // 重新抛出错误以便调用方捕获
        });
}


