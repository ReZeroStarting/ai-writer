<template>
  <div class="style-page">
    <div class="style-topbar">
      <el-button text @click="$router.back()">← 返回</el-button>
    </div>

    <div class="style-header">
      <h2>写作风格管理</h2>
      <el-button type="primary" @click="showCreate = true">+ 新建风格</el-button>
    </div>

    <el-card class="guide-card">
      <template #header><span class="guide-title">使用教程</span></template>
      <div class="guide-body">
        <div class="guide-step">
          <span class="step-num">1</span>
          <div>
            <strong>创建风格</strong>
            <p>点击"+ 新建风格"，填写：</p>
            <ul>
              <li><strong>名称</strong> — 给风格起个名字，如"鲁迅风""古龙体"</li>
              <li><strong>描述</strong> — 可选，简单说明这个风格的特点</li>
              <li><strong>System Prompt</strong> — AI 写作时的行为指令。可以直接手写，也可以留空，通过下面的上传素材让 AI 自动生成</li>
            </ul>
          </div>
        </div>
        <div class="guide-step">
          <span class="step-num">2</span>
          <div>
            <strong>上传素材学习</strong>
            <p>创建完成后，在风格卡片中点击"📖 上传素材学习"：</p>
            <ul>
              <li>粘贴你想模仿的小说原文或文本片段</li>
              <li>AI 会分析文本特征，自动生成 System Prompt</li>
              <li>可以多次上传，不断优化风格效果</li>
            </ul>
          </div>
        </div>
        <div class="guide-step">
          <span class="step-num">3</span>
          <div>
            <strong>在写作中使用</strong>
            <p>回到写作界面，右侧 AI 助手面板顶部的下拉框选择你创建的风格，然后点击"续写""润色"等按钮即可。</p>
          </div>
        </div>
        <div class="guide-example">
          <strong>System Prompt 示例（手写参考）：</strong>
          <pre>你是一位小说作家。你的写作风格简洁有力，善用短句和白描手法。避免冗长的心理描写和华丽辞藻，多用动作和对话推动剧情。文字节奏明快，段落简短。</pre>
        </div>
      </div>
    </el-card>

    <StyleList />
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
.style-page { min-height: 100vh; padding: 24px; max-width: 900px; margin: 0 auto; }
.style-topbar { margin-bottom: 16px; }
.style-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; }
.guide-card { margin-bottom: 24px; background: var(--el-color-primary-light-9, #ecf5ff); }
.guide-title { font-weight: 700; font-size: 15px; }
.guide-body { display: flex; flex-direction: column; gap: 16px; }
.guide-step { display: flex; gap: 12px; }
.step-num { flex-shrink: 0; width: 24px; height: 24px; border-radius: 50%; background: var(--el-color-primary, #409eff); color: #fff; display: flex; align-items: center; justify-content: center; font-size: 13px; font-weight: 700; }
.guide-step strong { display: block; margin-bottom: 2px; }
.guide-step p { margin: 4px 0; color: var(--el-text-color-regular, #606266); }
.guide-step ul { margin: 4px 0; padding-left: 18px; color: var(--el-text-color-regular, #606266); font-size: 13px; }
.guide-step li { margin: 2px 0; }
.guide-example { border-top: 1px solid var(--el-border-color-light, #e0dbd0); padding-top: 12px; }
.guide-example pre { background: var(--el-bg-color-page, #f5f7fa); padding: 10px; border-radius: 4px; font-size: 13px; line-height: 1.7; white-space: pre-wrap; color: var(--el-text-color-regular, #606266); margin: 6px 0 0; }
</style>
