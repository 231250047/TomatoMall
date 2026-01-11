<script setup>
import { ref, onMounted } from 'vue'
import { getTotalRank, getTagRank } from '@/api/product'
import ProductCard from '@/components/ProductCard.vue'
import NavigationBar from "@/components/NavigationBar.vue";

const currentTag = ref('total')
const rankList = ref([])
const categoryMap = {
  total: '总榜',
  education: '教辅',
  literature: '文学',
  art: '艺术',
  management: '管理',
  history: '历史',
  philosophy: '哲学宗教',
  health: '保健养生',
  science: '科技'
}

// 加载榜单数据
const loadRankData = async () => {
  try {
    const res = currentTag.value === 'total'
        ? await getTotalRank()
        : await getTagRank(currentTag.value)
    rankList.value = res.data.slice(0, 10) // 确保只取前10
  } catch (error) {
    console.error(error)
  }
}

// 切换分类处理
const handleTagChange = () => {
  loadRankData()
}

onMounted(loadRankData)
</script>

<template>
  <div class="rank-page-wrapper">
  <div class="rank-container">
    <NavigationBar></NavigationBar>
    <el-select
        v-model="currentTag"
        @change="handleTagChange"
        class="rank-selector"
    >
      <el-option
          v-for="(name, key) in categoryMap"
          :key="key"
          :label="name"
          :value="key"
      />
    </el-select>

    <el-table :data="rankList" stripe>
      <el-table-column prop="rank" label="排名" width="80">
        <template #default="{ $index }">
          {{ $index + 1 }}
        </template>
      </el-table-column>
      <el-table-column label="商品信息">
        <template #default="{ row }">
          <ProductCard :product="row" mode="horizontal" />
        </template>
      </el-table-column>
      <el-table-column prop="rate" label="评分" width="120">
        <template #default="{ row }">
          ⭐ {{ row.rate.toFixed(1) }}
        </template>
      </el-table-column>
    </el-table>
  </div>
  </div>>
</template>

<style scoped>
.rank-page-wrapper {
  min-height: 100vh;
  background: repeating-linear-gradient(
      0deg,
      #faf8f3 0px,
      #faf8f3 2px,
      #f5f1e8 2px,
      #f5f1e8 4px
  ),
  linear-gradient(
      180deg,
      rgba(255, 250, 240, 0.55) 0%,
      rgba(246, 239, 229, 0.45) 50%,
      rgba(239, 230, 219, 0.5) 100%
  );
  background-blend-mode: multiply;
  position: relative;
}

.rank-container {
  padding-top: 50px; /* 新增：为导航栏留出空间 */
  width: 80%; /* 添加自适应宽度 */
  margin: 0 auto;
  font-size: 20px;
}

.rank-selector {
  margin-bottom: 20px;
  width: 300px;
}

.el-table {
  width: 100% !important; /* 强制表格撑满容器 */
  font-size: 20px;
}

/* 设置列宽 */
.el-table__header th {
  background: #f5f7fa;
}

.el-table__column--selection .cell {
  min-width: 120px;
}

</style>