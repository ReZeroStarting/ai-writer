<template>
  <div class="sidebar">
    <div class="sidebar-header">
      <span>小说列表</span>
      <el-button size="small" @click="showCreate = true">+ 新建</el-button>
    </div>
    <div class="search-box">
      <el-input v-model="search" placeholder="搜索..." size="small" clearable />
    </div>
    <div class="project-list">
      <div v-for="p in filteredProjects" :key="p.id"
        :class="['project-item', { active: p.id === store.currentId }]"
        @click="selectProject(p.id)">
        <span>{{ p.name }}</span>
        <el-dropdown trigger="click" @click.stop>
          <el-icon><MoreFilled /></el-icon>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="startRename(p)">重命名</el-dropdown-item>
              <el-dropdown-item @click="handleDelete(p.id)">删除</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>

    <el-dialog v-model="showCreate" title="新建小说" width="400px">
      <el-input v-model="newName" placeholder="小说名称" />
      <template #footer>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" @click="handleCreate">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showRename" title="重命名" width="400px">
      <el-input v-model="renameText" placeholder="新名称" />
      <template #footer>
        <el-button @click="showRename = false">取消</el-button>
        <el-button type="primary" @click="handleRename">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useProjectStore } from '@/stores/projects'
import { ElMessage } from 'element-plus'
import { MoreFilled } from '@element-plus/icons-vue'
import type { Project } from '@/types'

const router = useRouter()
const store = useProjectStore()
const showCreate = ref(false); const newName = ref('')
const showRename = ref(false); const renameText = ref(''); let renameId = 0
const search = ref('')

const filteredProjects = computed(() => {
  if (!search.value.trim()) return store.projects
  const q = search.value.trim().toLowerCase()
  return store.projects.filter(p => p.name.toLowerCase().includes(q))
})

function selectProject(id: number) { store.setCurrent(id); router.push(`/project/${id}`) }

async function handleCreate() {
  if (!newName.value.trim()) return
  const p = await store.create(newName.value.trim())
  showCreate.value = false; newName.value = ''
  store.setCurrent(p.id); router.push(`/project/${p.id}`)
}

function startRename(p: Project) { renameId = p.id; renameText.value = p.name; showRename.value = true }

async function handleRename() {
  if (!renameText.value.trim()) return
  await store.update(renameId, renameText.value.trim())
  showRename.value = false
}

async function handleDelete(id: number) { await store.remove(id); ElMessage.success('小说已删除') }
</script>

<style scoped>
.sidebar { width: 180px; border-right: 1px solid var(--el-border-color, #e4e7ed); display: flex; flex-direction: column; background: var(--el-bg-color-page, #fafafa); }
.sidebar-header { padding: 12px; display: flex; justify-content: space-between; align-items: center; font-weight: 600; font-size: 14px; }
.search-box { padding: 0 8px 8px; }
.project-list { flex: 1; overflow-y: auto; }
.project-item { padding: 8px 12px; cursor: pointer; display: flex; justify-content: space-between; align-items: center; font-size: 13px; }
.project-item:hover { background: var(--el-color-primary-light-9, #ecf5ff); }
.project-item.active { background: var(--el-color-primary-light-8, #d9ecff); color: var(--el-color-primary, #409eff); font-weight: 600; }
</style>
