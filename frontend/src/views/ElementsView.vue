<template>
  <div class="elements-view">
    <div class="elements-toolbar">
      <el-select v-model="filterType" placeholder="按类型筛选" clearable style="width: 180px" @change="fetchElements">
        <el-option v-for="t in ELEMENT_TYPES" :key="t.value" :label="t.label" :value="t.value" />
      </el-select>
      <el-input v-model="searchText" placeholder="搜索名称..." style="width: 240px" clearable />
      <el-button type="primary" @click="showCreate = true"><el-icon><Plus /></el-icon>添加</el-button>
      <el-button @click="showAiGen = true"><el-icon><MagicStick /></el-icon>AI 生成</el-button>
    </div>

    <div v-if="filteredElements.length === 0" class="empty-state">
      <p>暂无设定元素，点击"添加"或"AI 生成"创建第一个设定</p>
    </div>

    <div class="element-grid">
      <el-card v-for="el in filteredElements" :key="`${el.id}-${el.version}`" class="element-card" shadow="hover" @click="openDetail(el)">
        <template #header>
          <div class="card-header">
            <el-tag :type="getTypeTagColor(el.type)" size="small">{{ getTypeLabel(el.type) }}</el-tag>
            <span class="card-name">{{ el.name }}</span>
            <span class="card-actions" @click.stop>
              <el-button size="small" text @click.stop="openEdit(el)">编辑</el-button>
              <el-button size="small" text type="danger" @click.stop="handleDelete(el)">弃用</el-button>
            </span>
          </div>
        </template>
        <p class="card-content">{{ el.content.substring(0, 100) }}{{ el.content.length > 100 ? '...' : '' }}</p>
      </el-card>
    </div>

    <!-- Create Dialog -->
    <el-dialog v-model="showCreate" title="添加设定元素" width="500px">
      <el-form :model="createForm" label-position="top">
        <el-form-item label="类型">
          <el-select v-model="createForm.type" placeholder="选择类型">
            <el-option v-for="t in ELEMENT_TYPES" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="createForm.name" maxlength="200" />
        </el-form-item>
        <el-form-item label="内容">
          <el-input v-model="createForm.content" type="textarea" :rows="6" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" @click="handleCreate">确定</el-button>
      </template>
    </el-dialog>

    <!-- AI Generate Dialog -->
    <el-dialog v-model="showAiGen" title="AI 生成设定" width="500px">
      <el-form label-position="top">
        <el-form-item label="类型">
          <el-select v-model="aiGenForm.type" placeholder="选择要生成的类型">
            <el-option v-for="t in ELEMENT_TYPES" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="提示（可选）">
          <el-input v-model="aiGenForm.hint" placeholder="例如：一个冷漠的剑客..." />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAiGen = false">取消</el-button>
        <el-button type="primary" @click="handleAiGen" :loading="aiGenerating">生成</el-button>
      </template>
    </el-dialog>

    <!-- Detail Dialog -->
    <el-dialog v-model="showDetail" title="元素详情" width="600px">
      <template v-if="selectedElement">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="ID">{{ selectedElement.id }}</el-descriptions-item>
          <el-descriptions-item label="版本">v{{ selectedElement.version }}</el-descriptions-item>
          <el-descriptions-item label="类型">
            <el-tag :type="getTypeTagColor(selectedElement.type)" size="small">{{ getTypeLabel(selectedElement.type) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="名称">{{ selectedElement.name }}</el-descriptions-item>
        </el-descriptions>
        <div class="detail-content">{{ selectedElement.content }}</div>

        <!-- 版本历史：每次修改都会追加一个版本，历史不覆盖 -->
        <div v-if="versions.length > 1" class="version-block">
          <div class="section-title">版本历史</div>
          <div v-for="v in versions" :key="v.version" class="version-row">
            <el-tag size="small" :type="v.version === selectedElement.version ? 'success' : 'info'">v{{ v.version }}</el-tag>
            <span class="version-time">{{ (v.createdAt || '').slice(0, 19).replace('T', ' ') }}</span>
            <span class="version-name">{{ v.name }}</span>
          </div>
        </div>
      </template>
      <template #footer>
        <el-button @click="showDetail = false">关闭</el-button>
        <el-button v-if="selectedElement" @click="openEdit(selectedElement)">编辑</el-button>
      </template>
    </el-dialog>

    <!-- Edit Dialog -->
    <el-dialog v-model="showEdit" title="编辑设定元素" width="500px">
      <el-form :model="editForm" label-position="top">
        <el-form-item label="类型">
          <el-select v-model="editForm.type" placeholder="选择类型">
            <el-option v-for="t in ELEMENT_TYPES" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="editForm.name" maxlength="200" />
        </el-form-item>
        <el-form-item label="内容">
          <el-input v-model="editForm.content" type="textarea" :rows="6" />
        </el-form-item>
        <p class="edit-hint">保存后不会覆盖旧内容，而是追加一个新版本，章节里引用的旧版本号仍然可查。</p>
      </el-form>
      <template #footer>
        <el-button @click="showEdit = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleEdit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useFrameworkStore } from '@/stores/framework'
import { ELEMENT_TYPES, elementTypeLabel, type FrameworkElement } from '@/types'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, MagicStick } from '@element-plus/icons-vue'
import { frameworkApi } from '@/api/framework'

const route = useRoute()
const fwStore = useFrameworkStore()

const elements = computed(() => fwStore.elements)
const filterType = ref('')
const searchText = ref('')
const showCreate = ref(false)
const showAiGen = ref(false)
const showDetail = ref(false)
const showEdit = ref(false)
const saving = ref(false)
const aiGenerating = ref(false)
const selectedElement = ref<FrameworkElement | null>(null)
const versions = ref<any[]>([])

const createForm = ref({ type: 'CHARACTER', name: '', content: '' })
const editForm = ref({ id: '', type: 'CHARACTER', name: '', content: '' })
const aiGenForm = ref({ type: 'CHARACTER', hint: '' })

const filteredElements = computed(() => {
  let list = elements.value
  if (searchText.value) {
    list = list.filter(e => e.name.includes(searchText.value))
  }
  return list
})

function getTypeLabel(type: string) { return ELEMENT_TYPES.find(t => t.value === type)?.label ?? type }
function getTypeTagColor(type: string): '' | 'success' | 'warning' | 'danger' | 'info' | 'primary' {
  const map: Record<string, '' | 'success' | 'warning' | 'danger' | 'info' | 'primary'> = {
    CHARACTER: '', LOCATION: 'success', ITEM: 'warning', ORGANIZATION: 'danger', EVENT: 'info', WORLD_SETTING: 'primary',
  }
  return map[type] ?? ''
}

async function fetchElements() {
  const fwId = fwStore.framework?.id
  if (!fwId) return
  const type = filterType.value || undefined
  const { frameworkApi } = await import('@/api/framework')
  const res = await frameworkApi.listElements(fwId, type)
  fwStore.elements = res.data
}

async function handleCreate() {
  const fwId = fwStore.framework?.id
  if (!fwId) return
  if (!createForm.value.name || !createForm.value.content) {
    ElMessage.warning('请填写名称和内容')
    return
  }
  try {
    await fwStore.createElement(fwId, createForm.value)
    showCreate.value = false
    ElMessage.success('设定已添加')
    createForm.value = { type: 'CHARACTER', name: '', content: '' }
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '创建失败')
  }
}

async function handleAiGen() {
  aiGenerating.value = true
  try {
    const fwId = fwStore.framework?.id
    if (!fwId) return
    const { frameworkApi } = await import('@/api/framework')
    const res = await frameworkApi.aiGenerateElements(fwId, aiGenForm.value)
    fwStore.elements = [...res.data, ...fwStore.elements]
    await fwStore.refreshSummary(fwId)
    ElMessage.success('AI 生成完成')
    showAiGen.value = false
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '生成失败')
  } finally { aiGenerating.value = false }
}

function openDetail(el: FrameworkElement) {
  selectedElement.value = el
  showDetail.value = true
  versions.value = []
  loadVersions(el)
}

/** 版本历史：只读展示，让「改过什么」有迹可循 */
async function loadVersions(el: FrameworkElement) {
  const fwId = fwStore.framework?.id
  if (!fwId) return
  try {
    versions.value = (await frameworkApi.getElementVersions(fwId, el.id)).data
  } catch { /* 非致命 */ }
}

function openEdit(el: FrameworkElement) {
  editForm.value = { id: el.id, type: el.type, name: el.name, content: el.content }
  showDetail.value = false
  showEdit.value = true
}

async function handleEdit() {
  const fwId = fwStore.framework?.id
  if (!fwId) return
  if (!editForm.value.name.trim() || !editForm.value.content.trim()) {
    ElMessage.warning('名称和内容不能为空')
    return
  }
  saving.value = true
  try {
    const updated = await fwStore.updateElement(fwId, editForm.value.id, {
      type: editForm.value.type,
      name: editForm.value.name.trim(),
      content: editForm.value.content.trim(),
    })
    ElMessage.success(`已保存为 v${updated.version}`)
    showEdit.value = false
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function handleDelete(el: FrameworkElement) {
  const fwId = fwStore.framework?.id
  if (!fwId) return
  try {
    await ElMessageBox.confirm(
      `弃用「${el.name}」后，它不会再进入 AI 的写作上下文，但历史版本仍保留在库里。确定弃用？`,
      '弃用设定元素',
      { confirmButtonText: '弃用', cancelButtonText: '取消', type: 'warning' },
    )
  } catch { return }
  try {
    await fwStore.deleteElement(fwId, el.id)
    ElMessage.success('已弃用')
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '弃用失败')
  }
}
</script>

<style scoped>
.elements-view { }
.elements-toolbar { display: flex; gap: 12px; margin-bottom: 20px; align-items: center; }
.empty-state { text-align: center; padding: 48px; color: var(--el-text-color-secondary); }
.element-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 16px; }
.element-card { cursor: pointer; }
.card-header { display: flex; align-items: center; gap: 8px; }
.card-actions { margin-left: auto; display: flex; gap: 2px; opacity: 0; transition: opacity .15s; }
.element-card:hover .card-actions { opacity: 1; }
.version-block { margin-top: 16px; }
.section-title { font-size: 13px; color: var(--el-text-color-secondary); margin-bottom: 6px; }
.version-row { display: flex; align-items: center; gap: 8px; font-size: 12px; padding: 4px 0; border-bottom: 1px dashed var(--el-border-color-lighter); }
.version-time { color: var(--el-text-color-secondary); }
.edit-hint { font-size: 12px; color: var(--el-text-color-secondary); line-height: 1.6; margin: 0; }
.card-name { font-weight: 600; }
.card-content { color: var(--el-text-color-secondary); font-size: 13px; line-height: 1.5; margin: 0; }
.detail-content { margin-top: 16px; white-space: pre-wrap; line-height: 1.8; }
</style>
