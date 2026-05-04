<template>
  <div class="style-page">
    <div class="style-topbar">
      <el-button text @click="$router.back()">← 返回</el-button>
    </div>

    <div class="style-layout">
      <aside class="guide-sidebar">
        <div class="guide-title">使用教程</div>
        <div class="guide-body">
          <div class="guide-step">
            <span class="step-num">1</span>
            <div>
              <strong>创建风格</strong>
              <p>点击"+ 新建风格"，填写：</p>
              <ul>
                <li><strong>名称</strong> — 如"鲁迅风""古龙体"</li>
                <li><strong>描述</strong> — 可选，简单说明特点</li>
                <li><strong>System Prompt</strong> — 可直接手写，也可留空后通过上传素材让 AI 自动生成</li>
              </ul>
            </div>
          </div>
          <div class="guide-step">
            <span class="step-num">2</span>
            <div>
              <strong>上传素材学习</strong>
              <p>在风格卡片中点击"📖 上传素材学习"：</p>
              <ul>
                <li>粘贴小说原文或文本片段</li>
                <li>AI 分析文本特征，自动生成 System Prompt</li>
                <li>可多次上传，不断优化效果</li>
              </ul>
            </div>
          </div>
          <div class="guide-step">
            <span class="step-num">3</span>
            <div>
              <strong>在写作中使用</strong>
              <p>回到写作界面，在右侧 AI 助手面板的下拉框选择风格即可。</p>
            </div>
          </div>
          <div class="guide-example">
            <strong>手写 System Prompt 参考：</strong>
            <pre>你是一位小说作家。你的写作风格简洁有力，善用短句和白描手法。避免冗长的心理描写和华丽辞藻，多用动作和对话推动剧情。</pre>
          </div>
        </div>
      </aside>

      <main class="style-main">
        <div class="style-header">
          <h2>写作风格</h2>
          <el-button type="primary" @click="showCreate = true">+ 新建风格</el-button>
        </div>
        <StyleList />
      </main>
    </div>

    <StyleCreateDialog v-model="showCreate" />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import StyleList from '@/components/styles/StyleList.vue'
import StyleCreateDialog from '@/components/styles/StyleCreateDialog.vue'

const showCreate = ref(false)
</script>

<style scoped>
.style-page { min-height: 100vh; padding: 24px; }
.style-topbar { margin-bottom: 16px; }

.style-layout { display: flex; gap: 24px; }

.guide-sidebar {
  width: 280px; flex-shrink: 0;
  background: var(--el-color-primary-light-9, #ecf5ff);
  border-radius: 8px; padding: 20px;
  align-self: flex-start; position: sticky; top: 24px;
  max-height: calc(100vh - 48px); overflow-y: auto;
}
.guide-title { font-weight: 700; font-size: 15px; margin-bottom: 16px; }
.guide-body { display: flex; flex-direction: column; gap: 14px; }
.guide-step { display: flex; gap: 10px; }
.step-num { flex-shrink: 0; width: 22px; height: 22px; border-radius: 50%; background: var(--el-color-primary, #409eff); color: #fff; display: flex; align-items: center; justify-content: center; font-size: 12px; font-weight: 700; }
.guide-step strong { font-size: 13px; }
.guide-step p { margin: 2px 0; color: var(--el-text-color-regular, #606266); font-size: 12px; }
.guide-step ul { margin: 2px 0; padding-left: 16px; color: var(--el-text-color-regular, #606266); font-size: 12px; }
.guide-step li { margin: 1px 0; }
.guide-example { border-top: 1px solid var(--el-border-color-light, #e0dbd0); padding-top: 10px; }
.guide-example strong { font-size: 12px; }
.guide-example pre { background: var(--el-bg-color-page, #f5f7fa); padding: 8px; border-radius: 4px; font-size: 12px; line-height: 1.6; white-space: pre-wrap; color: var(--el-text-color-regular, #606266); margin: 4px 0 0; }

.style-main { flex: 1; min-width: 0; }
.style-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; }
.style-header h2 { margin: 0; }
</style>
