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
        默认用服务端配置的模型。你也可以填自己的 API Key —— 主流厂商基本都提供 OpenAI 兼容接口，
        选一个、填上 Key 即可；换成别家或自建服务时选「自定义」，填接口地址和模型名。
        Key 加密后保存，接口只返回掩码。
      </p>

      <el-form label-position="top" class="form">
        <el-form-item label="使用哪个上游">
          <el-select v-model="form.provider" placeholder="选择厂商" style="width: 100%">
            <el-option v-for="p in providers" :key="p.id" :label="p.label" :value="p.id" />
            <el-option label="自定义（自建 / 中转 / 其它厂商）" value="custom" />
          </el-select>
          <div class="hint">
            服务端默认是 {{ setting?.defaultProvider || '—' }}，不配置时就走它。
            <template v-if="currentPreset?.note"> · {{ currentPreset.note }}</template>
            <template v-if="currentPreset?.consoleUrl">
              · <a :href="currentPreset.consoleUrl" target="_blank" rel="noreferrer">去申请 Key ↗</a>
            </template>
          </div>
        </el-form-item>

        <el-form-item label="API Key">
          <el-input
            v-model="form.apiKey"
            type="password"
            show-password
            clearable
            :placeholder="setting?.hasKey ? `已保存 ${setting.maskedKey}，留空表示不修改` : '粘贴你自己的 API Key'"
          />
          <div class="hint">留空表示沿用已保存的 Key；换厂商时建议一并换 Key。</div>
        </el-form-item>

        <el-form-item :label="isCustom ? '模型名（必填）' : '模型（可选）'">
          <el-input v-model="form.model" :placeholder="currentPreset?.defaultModel || '例如 gpt-4o-mini'" />
          <div class="hint">留空则用该厂商的默认模型：{{ currentPreset?.defaultModel || '—' }}</div>
        </el-form-item>

        <el-form-item :label="isCustom ? '接口地址（必填，需以 /v1 之类结尾）' : '接口地址（可选）'">
          <el-input v-model="form.baseUrl" :placeholder="currentPreset?.baseUrl || 'https://example.com/v1'" />
          <div class="hint">用中转 / 代理 / 自建服务时填这里；留空用官方地址。</div>
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
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { aiSettingsApi, type AiProviderPreset, type AiSetting } from '@/api/aiSettings'

const setting = ref<AiSetting | null>(null)
const providers = ref<AiProviderPreset[]>([])
const form = reactive({ provider: 'deepseek', apiKey: '', model: '', baseUrl: '' })
const saving = ref(false)
const testing = ref(false)
const testResult = ref('')
const testOk = ref(false)

const isCustom = computed(() => form.provider === 'custom')
const currentPreset = computed(() => providers.value.find(p => p.id === form.provider) || null)

async function load() {
  const [provRes, setRes] = await Promise.all([aiSettingsApi.providers(), aiSettingsApi.get()])
  providers.value = provRes.data
  setting.value = setRes.data
  form.provider = setRes.data.provider || (setRes.data.defaultProvider || 'deepseek').toLowerCase()
  form.model = setRes.data.model || ''
  form.baseUrl = setRes.data.baseUrl || ''
  form.apiKey = ''
}

onMounted(load)

async function save() {
  if (isCustom.value && (!form.baseUrl.trim() || !form.model.trim())) {
    ElMessage.warning('自定义上游需要填接口地址和模型名')
    return
  }
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
    ElMessage.success(res.data.hasKey ? `已保存，后续调用使用 ${res.data.providerLabel || ''} 的 Key` : '已恢复为服务端默认')
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.response?.data?.error || '保存失败')
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
.hint a { color: var(--el-color-primary, #409eff); text-decoration: none; }
.actions { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.updated { font-size: 12px; color: var(--el-text-color-secondary, #909399); }
.test-result { margin-top: 16px; }
</style>
