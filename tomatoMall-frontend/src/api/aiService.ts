import { axios } from '../utils/request'
import {ElMessage} from "element-plus";

// RAG图书推荐 - 主要接口，使用完整的RAG系统
export const recommendBooks = (query: string) => {
  return axios.post("http://localhost:8080/api/chat/recommend", {query: query})
      .then(res => {
          return res.data.data;
      })
      .catch(err =>{
          ElMessage.error(err.response?.data?.message || 'RAG推荐失败');
          throw err;
      })
};

// 简单对话 - 仅用于测试，不使用RAG
export const simpleChat = (message: string) => {
  return axios.post("http://localhost:8080/api/chat/simple", {message: message})
      .then(res => {
          return res.data.data;
      })
      .catch(err =>{
          ElMessage.error(err.response?.data?.message || '对话失败');
          throw err;
      })
};

// 测试向量数据库状态
export const testVectorStore = () => {
  return axios.get("http://localhost:8080/api/test/vectorstore/status")
      .then(res => res.data)
      .catch(err => {
          ElMessage.error('向量数据库连接失败');
          throw err;
      })
};

// 构建向量知识库
export const buildVectorKnowledgeBase = () => {
  return axios.post("http://localhost:8080/api/test/vectorstore/build")
      .then(res => res.data)
      .catch(err => {
          ElMessage.error('构建向量知识库失败');
          throw err;
      })
};


