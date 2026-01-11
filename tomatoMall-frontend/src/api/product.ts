import { axios } from '../utils/request'
import { ElMessage } from "element-plus";

// 商品规格接口
export interface Specification {
    id: string;
    item: string;
    value: string;
    productId: string;
}

// 库存接口
export interface Stockpile {
    id: string;
    amount: number; // 库存数量(可以销售)
    frozen: number; // 冻结数量（不可销售）
    // （有点奇怪的定义我就把库存总量算为：amount + frozen 了）
    productId: string;
}

// 商品基础接口
export interface Product {
    id: string;
    title: string;
    price: number;
    rate: number;
    description?: string;
    cover?: string;
    detail?: string;
    specifications?: Specification[];
    tag: string;
    condition: string; // 新增：成色枚举字符串，如 10_NEW,8_NEW,5_NEW,4_NEW,OLD
}


// 商品VO接口
/*  Product的ts类型安全约束
    作用：区分「创建」和「更新」操作
        新增商品时：不需要传递 id（由后端自动生成）
        修改商品时：必须传递 id（用于标识要修改的商品）
 */
export interface ProductVO extends Omit<Product, 'id'> {
    id?: string;
}

// 获取所有商品
export const getAllProducts = () => {
    return axios.get("http://localhost:8080/api/products")
        .then(res => res.data)
        .catch(err => {
            ElMessage.error(err.response?.data?.msg || '获取商品失败');
            throw err;
        });
}

// 获取单个商品
export const getProduct = (id: string) => {
    return axios.get(`http://localhost:8080/api/products/${id}`)
        .then(res => res.data)
        .catch(err => {
            ElMessage.error(err.response?.data?.msg || '获取商品详情失败');
            throw err;
        });
}

// 更新商品
export const updateProduct = (product: ProductVO) => {
    return axios.put("http://localhost:8080/api/products", product)
        .then(res => {
            ElMessage.success(res.data.msg || '更新成功');
            return res.data;
        })
        .catch(err => {
            ElMessage.error(err.msg || '更新失败');
            throw err;
        });
}

// 新增商品
export const addProduct = (product: ProductVO) => {
    console.log('API addProduct 被调用');
    console.log('product 参数:', product);
    console.log('product.sellerId:', product.sellerId);
    console.log('请求 URL:', "http://localhost:8080/api/products");
    console.log('请求体 JSON:', JSON.stringify(product, null, 2));
    return axios.post("http://localhost:8080/api/products", product)
        .then(res => {
            console.log('API 响应:', res);
            ElMessage.success(res.data.msg || '添加成功');
            return res.data;
        })
        .catch((err: any) => {
            // 更详细地打印错误信息
            console.error('API 错误 原始:', err);
            console.error('API 错误 response:', err?.response);
            console.error('API 错误 response.data:', err?.response?.data);

            const serverMsg = err?.response?.data?.msg || err?.response?.data?.message || err?.response?.data || null;
            const displayMsg = typeof serverMsg === 'string' ? serverMsg : (err?.message || '添加失败');
            ElMessage.error(displayMsg);
            throw err;
        });
}

// 删除商品
export const deleteProduct = (id: string) => {
    return axios.delete(`http://localhost:8080/api/products/${id}`)
        .then(res => {
            ElMessage.success(res.data.msg || '删除成功');
            return res.data;
        })
        .catch(err => {
            ElMessage.error(err.response?.data?.msg || '删除失败');
            throw err;
        });
}

// 调整库存
export const updateStockpile = (productId: string, amount: number) => {
    return axios.patch(`http://localhost:8080/api/products/stockpile/${productId}`, { amount })
        .then(res => {
            ElMessage.success(res.data.msg || '库存更新成功');
            return res.data;
        })
        .catch(err => {
            ElMessage.error(err.response?.data?.msg || '库存更新失败');
            throw err;
        });
}

// 查询库存
export const getStockpile = (productId: string) => {
    return axios.get(`http://localhost:8080/api/products/stockpile/${productId}`)
        .then(res => res.data)
        .catch(err => {
            ElMessage.error(err.response?.data?.msg || '获取库存失败');
            throw err;
        });
}

// 获取分类商品
export const getTagProducts = (tag: string) => {
    return axios.get(`http://localhost:8080/api/products/tag/${tag}`)
        .then(res => res.data)
        .catch(err => {
            ElMessage.error(err.response?.data?.msg || '获取分类商品失败');
            throw err;
        });
}

// 获取总榜单
export const getTotalRank = () => {
    return axios.get("http://localhost:8080/api/products/totalRank")
        .then(res => res.data)
        .catch(err => {
            ElMessage.error(err.response?.data?.msg || '获取总榜单失败');
            throw err;
        });
}

// 获取分类榜单
export const getTagRank = (tag: string) => {
    return axios.get(`http://localhost:8080/api/products/rank/${tag}`)
        .then(res => res.data)
        .catch(err => {
            ElMessage.error(err.response?.data?.msg || '获取分类榜单失败');
            throw err;
        });
}

// 获取用户发布的商品
export const getProductsBySellerId = (sellerId: number) => {
    console.log('getProductsBySellerId 被调用，sellerId:', sellerId);
    if (!sellerId) {
        console.error('错误：sellerId 为空');
        ElMessage.error('用户ID无效');
        return Promise.reject(new Error('sellerId is required'));
    }
    return axios.get(`http://localhost:8080/api/products/seller/${sellerId}`)
        .then(res => {
            console.log('getProductsBySellerId 响应:', res);
            return res.data;
        })
        .catch(err => {
            console.error('getProductsBySellerId 错误:', err);
            const errorMsg = err.response?.data?.msg || err.message || '获取商品失败';
            ElMessage.error(errorMsg);
            throw err;
        });
}


