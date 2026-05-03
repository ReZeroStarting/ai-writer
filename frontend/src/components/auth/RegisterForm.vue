<template>
  <el-form ref="formRef" :model="form" :rules="rules">
    <el-form-item prop="username">
      <el-input v-model="form.username" placeholder="用户名" size="large" />
    </el-form-item>
    <el-form-item prop="email">
      <el-input v-model="form.email" placeholder="邮箱" size="large" />
    </el-form-item>
    <el-form-item prop="phone">
      <el-input v-model="form.phone" placeholder="手机号（选填）" size="large" />
    </el-form-item>
    <el-form-item prop="password">
      <el-input v-model="form.password" type="password" placeholder="密码（6位以上）" size="large" show-password />
    </el-form-item>
    <el-form-item>
      <el-button type="primary" size="large" :loading="loading" @click="handleSubmit" style="width:100%">注册</el-button>
    </el-form-item>
  </el-form>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'

const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({ username: '', email: '', phone: '', password: '' })
const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  email: [{ required: true, type: 'email', message: '请输入有效邮箱', trigger: 'blur' }],
  password: [{ required: true, min: 6, message: '密码至少6位', trigger: 'blur' }],
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await auth.register({ username: form.username, email: form.email, phone: form.phone || undefined, password: form.password })
    ElMessage.success('注册成功')
    router.push('/')
  } catch (e: any) {
    ElMessage.error(e.response?.data?.error || '注册失败')
  } finally { loading.value = false }
}
</script>
