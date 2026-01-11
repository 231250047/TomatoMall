import {axios} from '../utils/request'

export const uploadOssFile = async (formData: FormData) => {
    return axios.post('http://localhost:8080/oss/fileoss', formData)
        .then(res => {
            console.log('响应数据:', res.data);
            return res;
        });
};

/* Content-Type 头告诉服务器请求体的数据格式
    * application/json → 表示请求体是 JSON 字符串
    * multipart/form-data → 表示请求体是分段数据（常用于文件上传）
        multipart/form-data 的特殊性：
        当上传文件时，浏览器会将请求体分成多个“段”（parts），每个段对应一个字段（如文件、文本参数）。
            boundary 参数的作用：它是一个唯一字符串，用于分隔不同的段。
        手动覆盖的后果：如果你手动设置 Content-Type: multipart/form-data，但没有指定 boundary，请求体会缺失分隔符，导致后端无法解析分段数据。
 */