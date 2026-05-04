<template>
  <div class="framework-view">
    <div class="fw-header">
      <div class="fw-title">
        <h2>写作框架</h2>
        <el-tag :type="fw?.status === 'ACTIVE' ? 'success' : 'info'">
          {{ fw?.status === 'ACTIVE' ? '创作中' : '已完结' }}
        </el-tag>
      </div>
      <div class="fw-actions">
        <el-button type="primary" @click="goWrite">进入写作</el-button>
        <el-button @click="goElements">管理设定</el-button>
        <el-button type="danger" plain @click="handleFinish" :disabled="fw?.status !== 'ACTIVE'">
          完结框架
        </el-button>
      </div>
    </div>

    <el-row :gutter="20" class="fw-stats">
      <el-col :span="6">
        <el-card shadow="hover"><el-statistic title="设定元素" :value="fw?.elementCount ?? 0" /></el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover"><el-statistic title="章节数" :value="fw?.chapterCount ?? 0" /></el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <el-statistic title="未处理冲突" :value="fw?.unresolvedConflictCount ?? 0">
            <template #suffix>
              <el-tag v-if="(fw?.unresolvedConflictCount ?? 0) > 0" type="danger" size="small">需处理</el-tag>
            </template>
          </el-statistic>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <el-statistic title="已完结" :value="fw?.status === 'FINISHED' ? 1 : 0">
            <template #suffix>
              <el-tag :type="fw?.status === 'FINISHED' ? 'success' : 'info'" size="small">
                {{ fw?.status === 'FINISHED' ? '是' : '否' }}
              </el-tag>
            </template>
          </el-statistic>
        </el-card>
      </el-col>
    </el-row>

    <el-card class="fw-nav">
      <el-menu :default-active="route.name as string" mode="horizontal" @select="handleMenuSelect">
        <el-menu-item index="framework">概览</el-menu-item>
        <el-menu-item index="framework-elements">设定元素</el-menu-item>
        <el-menu-item index="framework-write">写作工作台</el-menu-item>
        <el-menu-item index="framework-conflicts">
          冲突管理
          <el-badge v-if="(fw?.unresolvedConflictCount ?? 0) > 0" :value="fw?.unresolvedConflictCount" style="margin-left: 8px" />
        </el-menu-item>
        <el-menu-item index="framework-history">历史事件</el-menu-item>
      </el-menu>
    </el-card>

    <router-view />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useFrameworkStore } from '@/stores/framework'
import { frameworkApi } from '@/api/framework'
import { ElMessage, ElMessageBox } from 'element-plus'

const route = useRoute()
const router = useRouter()
const fwStore = useFrameworkStore()
const fw = computed(() => fwStore.framework)

onMounted(async () => {
  const projectId = Number(route.params.id)
  try {
    const res = await frameworkApi.getByProjectId(projectId)
    fwStore.framework = res.data
    await fwStore.fetchFramework(res.data.id)
  } catch {
    try {
      await fwStore.openFramework(projectId)
    } catch (e: any) {
      ElMessage.error(e?.response?.data?.message || '无法进入框架模式')
      router.push(`/project/${projectId}`)
    }
  }
})

function goWrite() { router.push({ name: 'framework-write', params: { id: route.params.id } }) }
function goElements() { router.push({ name: 'framework-elements', params: { id: route.params.id } }) }

async function handleFinish() {
  try {
    await ElMessageBox.confirm('确定要完结此框架吗？完结后 AI 将停止写作。', '确认完结', { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' })
    if (fw.value) await fwStore.finishFramework(fw.value.id)
    ElMessage.success('框架已完结')
  } catch { /* cancelled */ }
}

function handleMenuSelect(index: string) {
  router.push({ name: index, params: { id: route.params.id } })
}
</script>

<style scoped>
.framework-view { padding: 24px; max-width: 1200px; margin: 0 auto; }
.fw-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; }
.fw-title { display: flex; align-items: center; gap: 12px; }
.fw-title h2 { margin: 0; }
.fw-stats { margin-bottom: 24px; }
.fw-nav { margin-bottom: 24px; }
</style>
