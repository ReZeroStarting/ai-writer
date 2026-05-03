<template>
  <el-dialog :model-value="modelValue" @update:model-value="emit('update:modelValue', $event)" title="新建风格" width="500px">
    <el-form>
      <el-form-item label="名称"><el-input v-model="form.name" placeholder="例如：鲁迅风" /></el-form-item>
      <el-form-item label="描述"><el-input v-model="form.description" placeholder="简短描述" /></el-form-item>
      <el-form-item label="System Prompt">
        <el-input v-model="form.systemPrompt" type="textarea" :rows="6"
          placeholder="直接填写写作风格指令，或留空后通过上传素材让 AI 自动生成" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" @click="handleCreate">创建</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { reactive } from 'vue'
import { useStyleStore } from '@/stores/styles'
import { ElMessage } from 'element-plus'

defineProps<{ modelValue: boolean }>()
const emit = defineEmits(['update:modelValue'])
const store = useStyleStore()
const form = reactive({ name: '', systemPrompt: '', description: '' })

async function handleCreate() {
  if (!form.name.trim() || !form.systemPrompt.trim()) { ElMessage.warning('名称和 System Prompt 为必填'); return }
  await store.create(form.name, form.systemPrompt, form.description)
  ElMessage.success('风格创建成功')
  form.name = ''; form.systemPrompt = ''; form.description = ''
  emit('update:modelValue', false)
}
</script>
