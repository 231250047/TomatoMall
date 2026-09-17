import { axios } from '../utils/request'
import { ElMessage } from "element-plus";

// 订单状态类型
export type OrderStatus = 'PENDING' | 'SUCCESS' | 'FAILED' | 'TIMEOUT' | 'CLOSING' | 'CANCELLED';



// 订单信息类型
export interface OrderInfo {
    orderId: string;
    username: string;
    totalAmount: string;
    paymentMethod: string;
    createTime: string;
    expiresAt: string;
    status: OrderStatus;
}

export interface PaymentForm {
    paymentForm: string;
    orderId: string;
    totalAmount: string;
    paymentMethod: string;
}

// 提交参数格式
// ShoppingAddress
export interface ShoppingAddress {
    name: string,
    phone: string,
    postalCode?: string,
    address: string
}
// 提交订单参数
export interface CheckoutRequest {
    requestId: string;
    cartItemIds: string[];
    shoppingAddress: ShoppingAddress; // 保持原拼写
    paymentMethod: string;
    discount: string;
}

// 展示选中购物车物品
export interface cartItem {
    cartItemId: string,
    productId: string,
    title: string,
    price: number,
    description: string,
    cover?: string
    quantity: number
}


// 提交订单
export const checkout = (data: CheckoutRequest) => {
    return axios.post("http://localhost:8080/api/cart/checkout", data)
        .then(res => {
            if (res.data.code === "200") {
                return res.data.data as OrderInfo;
            } else {
                throw new Error(res.data.msg || '提交订单失败');
            }
        })
        .catch(err => {
            ElMessage.error(err.response?.data?.msg || '提交订单响应失败');
            throw err;
        })
}


// 发起支付
export const initiatePayment = (
    orderId: string
) => {
    return axios.post(`http://localhost:8080/api/orders/${orderId}/pay`)
        .then(res => {
            if (res.data.code === "200") {
                return res.data.data as PaymentForm;
            } else {
                throw new Error(res.data.msg || '发起支付失败');
            }
        })
        .catch(err => {
            ElMessage.error(err.response?.data?.msg || '发起支付响应失败');
            throw err;
        })
}

// 获取用户购买的商品
export const getPurchasedProducts = (userId: number) => {
    return axios.get(`http://localhost:8080/api/orders/purchased/${userId}`)
        .then(res => res.data)
        .catch(err => {
            ElMessage.error(err.response?.data?.msg || '获取购买记录失败');
            throw err;
        })
}

// 获取用户订单列表
export const getOrderList = () => {
    return axios.get("http://localhost:8080/api/orders/list")
        .then(res => res.data)
        .catch(err => {
            ElMessage.error(err.response?.data?.msg || '获取订单列表失败');
            throw err;
        });
};

// 获取订单详情
export const getOrderDetail = (orderId: string) => {
    return axios.get(`http://localhost:8080/api/orders/${orderId}`)
        .then(res => res.data)
        .catch(err => {
            ElMessage.error(err.response?.data?.msg || '获取订单详情失败');
            throw err;
        });
};

// 删除订单
export const deleteOrder = (orderId: string) => {
    return axios.delete(`http://localhost:8080/api/orders/${orderId}`)
        .then(res => res.data)
        .catch(err => {
            ElMessage.error(err.response?.data?.msg || '删除订单失败');
            throw err;
        });
};


export const orderStatusLabel = (status: string) => ({
    PENDING: '待支付', SUCCESS: '已支付', CLOSING: '关单确认中', TIMEOUT: '已超时', FAILED: '已关闭', CANCELLED: '已取消'
}[status] || status);

export const cancelOrder = (orderId: string) => axios.post(`http://localhost:8080/api/orders/${orderId}/cancel`);
