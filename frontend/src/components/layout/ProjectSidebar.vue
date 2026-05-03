<template>
  <div class="sidebar">
    <div class="sidebar-header">
      <span>我的项目</span>
      <el-button size="small" @click="showCreate = true">+ 新建</el-button>
    </div>
    <div class="project-list">
      <div v-for="p in store.projects" :key="p.id"
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

    <el-dialog v-model="showCreate" title="新建项目" width="400px">
      <el-input v-model="newName" placeholder="项目名称" />
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
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useProjectStore } from '@/stores/projects'
import { ElMessage } from 'element-plus'
import { MoreFilled } from '@element-plus/icons-vue'
import type { Project } from '@/types'

const router = useRouter()
const store = useProjectStore()
const showCreate = ref(false); const newName = ref('')
const showRename = ref(false); const renameText = ref(''); let renameId = 0

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

async function handleDelete(id: number) { await store.remove(id); ElMessage.success('项目已删除') }
</script>

<style scoped>
.sidebar { width: 240px; border-right: 1px solid #e4e7ed; display: flex; flex-direction: column; background: #fafafa; }
.sidebar-header { padding: 12px; display: flex; justify-content: space-between; align-items: center; font-weight: 600; }
.project-list { flex: 1; overflow-y: auto; }
.project-item { padding: 10px 12px; cursor: pointer; display: flex; justify-content: space-between; align-items: center; }
.project-item:hover { background: #ecf5ff; }
.project-item.active { background: #d9ecff; color: #409eff; font-weight: 600; }
</style>
