<template>
  <div class="ai-settings">
    <div class="topbar">
      <el-button text @click="$router.back()">← 返回</el-button>
    </div>

    <el-card class="card">
      <template #header>
        <div class="card-head">
          <h2>AI 上游设置</h2>
          <el-tag :type="setting?.hasKey ? 'success' : 'info'" size="small">
            {{ setting?.hasKey ? '使用我的 Key' : '使用服务端默认' }}
          </el-tag>
        </div>
      </template>

      <p class="intro">
        默认用服务端配置的模型。你也可以填自己的 API Key，之后所有 AI 写作、设定生成、风格学习
        都会走你的 Key（消耗你自己的额度）。Key 加密后保存，接口只返回掩码。
      </p>

      <el-form label-position="top" class="form">
        <el-form-item label="使用哪个上游">
          <el-radio-group v-model="form.provider">
            <el-radio-button value="DEEPSEEK">DeepSeek</el-radio-button>
            <el-radio-button value="ANTHROPIC">Claude (Anthropic)</el-radio-button>
          </el-radio-group>
          <div class="hint">服务端默认是 {{ setting?.defaultProvider || '—' }}，不配置时就走它。</div>
        </el-form-item>

        <el-form-item label="API Key">
          <el-input
            v-model="form.apiKey"
            type="password"
            show-password
            clearable
            :placeholder="setting?.hasKey ? `已保存 ${setting.maskedKey}，留空表示不修改` : '粘贴你自己的 API Key'"
          />
          <div class="hint">
            DeepSeek 的 key 在 platform.deepseek.com 申请，Claude 的在 console.anthropic.com。
          </div>
        </el-form-item>

        <el-form-item label="模型（可选）">
          <el-input v-model="form.model" :placeholder="form.provider === 'ANTHROPIC' ? 'claude-sonnet-4-6' : 'deepseek-flash'" />
        </el-form-item>

        <el-form-item label="接口地址（可选）">
          <el-input
            v-model="form.baseUrl"
            :placeholder="form.provider === 'ANTHROPIC' ? 'https://api.anthropic.com' : 'https://api.deepseek.com'"
          />
          <div class="hint">用中转/代理时填这里；留空用官方地址。</div>
        </el-form-item>
      </el-form>

      <div class="actions">
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
        <el-button :loading="testing" @click="test">测试连接</el-button>
        <el-button v-if="setting?.hasKey" type="danger" plain @click="clear">清除我的 Key</el-button>
        <span v-if="setting?.updatedAt" class="updated">上次更新：{{ setting.updatedAt.slice(0, 19).replace('T', ' ') }}</span>
      </div>

      <el-alert
        v-if="testResult"
        :title="testResult"
        :type="testOk ? 'success' : 'error'"
        :closable="false"
        show-icon
        class="test-result"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { aiSettingsApi, type AiSetting } from '@/api/aiSettings'

const setting = ref<AiSetting | null>(null)
const form = reactive({ provider: 'DEEPSEEK', apiKey: '', model: '', baseUrl: '' })
const saving = ref(false)
const testing = ref(false)
const testResult = ref('')
const testOk = ref(false)

async function load() {
  const res = await aiSettingsApi.get()
  setting.value = res.data
  form.provider = res.data.provider || (res.data.defaultProvider || 'deepseek').toUpperCase()
  form.model = res.data.model || ''
  form.baseUrl = res.data.baseUrl || ''
  form.apiKey = ''
}

onMounted(load)

async function save() {
  saving.value = true
  testResult.value = ''
  try {
    const res = await aiSettingsApi.save({
      provider: form.provider,
      apiKey: form.apiKey || undefined,
      model: form.model || undefined,
      baseUrl: form.baseUrl || undefined,
    })
    setting.value = res.data
    form.apiKey = ''
    ElMessage.success(res.data.hasKey ? '已保存，后续调用使用你的 Key' : '已恢复为服务端默认')
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function test() {
  testing.value = true
  testResult.value = ''
  try {
    const res = await aiSettingsApi.test()
    testOk.value = !!res.data.ok
    testResult.value = res.data.ok
      ? `连接成功（${res.data.provider}）：${res.data.reply || 'OK'}`
      : `连接失败（${res.data.provider}）：${res.data.message || '未知错误'}`
  } catch (e: any) {
    testOk.value = false
    testResult.value = e?.response?.data?.message || '测试请求失败'
  } finally {
    testing.value = false
  }
}

async function clear() {
  try {
    await ElMessageBox.confirm('清除后会用回服务端配置的 Key，确定吗？', '清除我的 Key', {
      confirmButtonText: '清除', cancelButtonText: '取消', type: 'warning',
    })
  } catch { return }
  await aiSettingsApi.clear()
  await load()
  testResult.value = ''
  ElMessage.success('已恢复为服务端默认')
}
</script>

<style scoped>
.ai-settings { padding: 24px; max-width: 720px; margin: 0 auto; }
.topbar { margin-bottom: 8px; }
.card-head { display: flex; align-items: center; gap: 12px; }
.card-head h2 { margin: 0; font-size: 18px; }
.intro { color: var(--el-text-color-regular, #606266); font-size: 13px; line-height: 1.8; margin: 0 0 16px; }
.form { max-width: 520px; }
.hint { font-size: 12px; color: var(--el-text-color-secondary, #909399); line-height: 1.6; margin-top: 4px; }
.actions { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.updated { font-size: 12px; color: var(--el-text-color-secondary, #909399); }
.test-result { margin-top: 16px; }
</style>
