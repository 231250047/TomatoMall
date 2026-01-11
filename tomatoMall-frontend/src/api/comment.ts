import { axios } from '../utils/request'
import { ElMessage } from "element-plus";

//添加评论
export const addComment =(productId: string, commentStr:string) => {
    return axios.post(`http://localhost:8080/api/comment/${productId}`,{productId:productId, commentStr:commentStr})
        .then(res=>res.data)
        .catch(err =>{
            ElMessage.error(err.response?.data?.msg || '添加评论失败');
            throw err;
        })

}

//删除评论
export const deleteComment = (commentId:string) => {
    return axios.delete(`http://localhost:8080/api/comment/${commentId}`)
        .then(res => res.data)
        .catch(err =>{
            ElMessage.error(err.response?.data?.msg || '删除失败');
            throw err;
        })
}



//获取评论
export const getComment = (productId:string) => {
    return axios.get(`http://localhost:8080/api/comment/${productId}`)
        .then(res => res.data)
        .catch(err =>{
            ElMessage.error(err.response?.data?.msg || '获取失败');
            throw err;
        })
}