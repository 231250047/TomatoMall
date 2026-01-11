import { axios } from '../utils/request'
import { ElMessage } from "element-plus";

//加入到购物车
export const addToCart =(productId: string, quantity: number) => {
    return axios.post("http://localhost:8080/api/cart",{productId:productId, quantity:quantity})
        .then(res=>res.data)
        .catch(err =>{
            ElMessage.error(err.response?.data?.msg || '添加购物车失败');
            throw err;
        })

}

//从购物车删除商品
export const deleteFromCart = (cartItemId:string) => {
    return axios.delete(`http://localhost:8080/api/cart/${cartItemId}`)
        .then(res => res.data)
        .catch(err =>{
            ElMessage.error(err.response?.data?.msg || '删除失败');
            throw err;
        })
}


//修改购物车商品数量
export const changeAmount = (cartItemId: string, quantity: number) => {
    return axios.patch(`http://localhost:8080/api/cart/${cartItemId}`,quantity,{
        headers: {
            'Content-Type': 'application/json',  // 设置请求体格式为 JSON
        }
    })
        .then(res => res.data)
        .catch(err =>{
            ElMessage.error(err.response?.data?.msg || '调整失败');
            throw err;
    })
}

//获取购物车商品列表
export const getCart = () => {
    return axios.get(`http://localhost:8080/api/cart/`)
        .then(res => res.data)
        .catch(err =>{
            ElMessage.error(err.response?.data?.msg || '获取失败');
            throw err;
    })
}