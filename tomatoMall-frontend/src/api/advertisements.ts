import { axios } from '../utils/request'
import { ElMessage } from "element-plus";

export interface advertisement {
    id: string;
    title: string;
    content: string;
    imgUrl: string;
    relatedUrl?: string; // 新增：广告跳转链接（可为空）
}

export interface advertisementNew {
    title: string;
    content: string;
    imgUrl: string;
    relatedUrl?: string; // 可选跳转链接
}


//获取所有广告信息
export const getAdvertisements = () => {
    return axios.get("http://localhost:8080/api/advertisements")
        .then(res=> {
            if(res.data.code === "200"){
                return res.data.data as advertisement[];
            } else {
                throw new Error('展示广告列表失败');
            }
        })
        .catch(err =>{
            ElMessage.error(err.message || err.response?.data?.msg || '获取广告所有信息响应失败');
            throw err;
        })
}

//更新广告信息
export function updateAdvertisement(advertisement: advertisement) {
  // 更新广告：不再包含 productId 字段
  return axios.put('http://localhost:8080/api/advertisements', advertisement)
     .then(res => {
     if (res.data.code === "200") {
     return res.data.data; //"更新成功"
     } else if (res.data.code === "400") {
     throw new Error(res.data.msg); // "商品不存在"
     } else {
     throw new Error("更新广告信息失败");
     }
 })
 .catch(err => {
 ElMessage.error(err.message || err.response?.data?.msg || '更新广告信息响应失败');
 throw err;
 });
 }


//创建广告
export const createAdvertisement = (advertisement: advertisementNew) => {
    // 新建广告，不再关联商品
    return axios.post('http://localhost:8080/api/advertisements', advertisement)
        .then(res => {
            if(res.data.code === "200"){
                return res.data.data as advertisement;
            } else if(res.data.code === "400") {
                throw new Error(res.data.msg)
            } else {
                throw new Error("创建广告失败")
            }
        })
        .catch(err =>{
            ElMessage.error(err.message || err.response?.data?.msg || '创建广告响应失败');
            throw err;
        })
}

//删除广告
export const deleteAdvertisement = (id:string) => {
    return axios.delete(`http://localhost:8080/api/advertisements/${id}`)
        .then(res => {
            if(res.data.code === "200"){
                return res.data.data;
            }else{
                throw new Error("删除广告失败")
            }
        })
        .catch(err =>{
            ElMessage.error(err.message || err.response?.data?.msg || '删除广告响应失败');
            throw err;
        })
}