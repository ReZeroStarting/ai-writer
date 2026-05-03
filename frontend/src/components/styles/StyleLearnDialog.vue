<template>
  <el-dialog v-model="visible" :title="'上传素材学习 — ' + styleName" width="600px">
    <el-form>
      <el-form-item label="素材标题"><el-input v-model="title" placeholder="例如：《百年孤独》节选" /></el-form-item>
      <el-form-item label="文本内容">
        <el-input v-model="content" type="textarea" :rows="10" placeholder="粘贴小说原文或文本片段..." />
      </el-form-item>
    </el-form>
    <div v-if="job" class="job-status">
      <el-tag v-if="job.status === 'PENDING'" type="info">等待中...</el-tag>
      <el-tag v-else-if="job.status === 'PROCESSING'" type="warning">AI 正在分析...</el-tag>
      <el-tag v-else-if="job.status === 'COMPLETED'" type="success">学习完成！</el-tag>
      <el-tag v-else-if="job.status === 'FAILED'" type="danger">失败：{{ job.errorMsg }}</el-tag>
    </div>
    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
      <el-button type="primary" :loading="loading" @click="handleLearn">开始学习</el-button>
    </template>
  </el-dialog>

  <el-button size="small" @click="visible = true" style="margin-top: 8px;">📖 上传素材学习</el-button>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { stylesApi } from '@/api/styles'
import type { LearningJob } from '@/types'

const props = defineProps<{ styleId: number; styleName: string }>()

const visible = ref(false); const loading = ref(false)
const title = ref(''); const content = ref('')
const job = ref<LearningJob | null>(null)

async function handleLearn() {
  if (!title.value.trim() || !content.value.trim()) { ElMessage.warning('请填写标题和内容'); return }
  loading.value = true
  try {
    const res = await stylesApi.learn(props.styleId, { title: title.value, content: content.value })
    job.value = res.data
    const timer = setInterval(async () => {
      const jobs = await stylesApi.getJobs(props.styleId)
      const latest = jobs.data[0]
      if (latest) {
        job.value = latest
        if (latest.status === 'COMPLETED' || latest.status === 'FAILED') {
          clearInterval(timer)
          if (latest.status === 'COMPLETED') ElMessage.success('风格学习完成！')
        }
      }
    }, 2000)
  } catch (e: any) { ElMessage.error('学习请求失败') }
  finally { loading.value = false }
}
</script>

<style scoped>
.job-status { margin-top: 12px; }
</style>
