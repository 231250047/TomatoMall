import { axios } from '../utils/request'
import { ElMessage } from "element-plus";

// 收藏商品接口
export interface FavoriteVO {
    favoriteId?: number;
    accountId?: number;
    productId: number;
    productTitle?: string;
    productCover?: string;
    price?: number;
    description?: string;
    rate?: number;
    detail?: string;
    tag?: string;
    sellerId?: number;
    productStatus?: number | string;
    productCondition?: string;
    favoriteTime?: string;
}

// 添加收藏
export const addFavorite = (productId: number) => {
    return axios.post("http://localhost:8080/api/favorite/add", null, {
        params: { productId }
    })
        .then(res => res.data)
        .catch(err => {
            throw err;
        });
}

// 取消收藏
export const removeFavorite = (productId: number) => {
    return axios.delete(`http://localhost:8080/api/favorite/remove`, {
        params: { productId }
    })
        .then(res => res.data)
        .catch(err => {
            throw err;
        });
}

// 获取收藏列表
export const getFavoriteList = () => {
    return axios.get("http://localhost:8080/api/favorite/list")
        .then(res => {
            console.log('API 响应:', res.data.data);
            return res.data;
        })
        .catch(err => {
            ElMessage.error(err.response?.data?.msg || '获取收藏列表失败');
            throw err;
        });
}

// 检查是否已收藏
export const checkFavorite = (productId: number) => {
    return axios.get("http://localhost:8080/api/favorite/check", {
        params: { productId }
    })
        .then(res => res.data)
        .catch(err => {
            // 静默失败，不显示错误提示
            console.error('检查收藏状态失败:', err);
            return { code: '500', data: false };
        });
}

// 获取收藏数量
export const getFavoriteCount = () => {
    return axios.get("http://localhost:8080/api/favorite/count")
        .then(res => res.data)
        .catch(err => {
            ElMessage.error(err.response?.data?.msg || '获取收藏数量失败');
            throw err;
        });
}