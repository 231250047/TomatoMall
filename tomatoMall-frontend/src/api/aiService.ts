import { axios } from '../utils/request'
import {ElMessage} from "element-plus";

// 发送消息给AI助手
export const sendMessageToAI = (message: string) => {
  return axios.post("http://localhost:8080/api/ai/chat", {message: message})
      .then(res => {
          // 从嵌套结构中提取AI回复内容
          return res.data.output.choices[0].message.content;
      })
      .catch(err =>{
          ElMessage.error(err.response?.data?.msg || 'ai对话失败');
          throw err;
      })
};







