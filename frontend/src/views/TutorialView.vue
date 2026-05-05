<template>
  <div class="tutorial-view">
    <el-timeline>
      <el-timeline-item icon="FolderOpened" type="primary" size="large" :hollow="true">
        <h3 class="step-title">开启框架模式</h3>
        <div class="step-body">
          <p>进入任意写作项目，点击侧边栏的"写作框架"菜单。如果项目尚未开启框架模式，系统会自动创建。创建后会看到框架主页：</p>
          <el-row :gutter="16">
            <el-col :span="8"><el-card shadow="hover"><strong>设定元素数</strong><br/>当前有多少个角色、地点等</el-card></el-col>
            <el-col :span="8"><el-card shadow="hover"><strong>章节数</strong><br/>AI 已写了多少章</el-card></el-col>
            <el-col :span="8"><el-card shadow="hover"><strong>未处理冲突</strong><br/>需要关注的不一致问题</el-card></el-col>
          </el-row>
        </div>
      </el-timeline-item>

      <el-timeline-item icon="Grid" type="success" size="large" :hollow="true">
        <h3 class="step-title">搭建设定</h3>
        <div class="step-body">
          <p>点击顶部导航的<strong>"设定元素"</strong>，进入元素管理页面。系统预设了 <strong>6 种类型</strong>，也支持自定义类型：</p>
          <el-table :data="elementTypes" size="small" stripe style="margin-bottom: 16px">
            <el-table-column prop="type" label="类型" width="200" />
            <el-table-column prop="desc" label="说明" />
            <el-table-column prop="example" label="示例" />
          </el-table>
          <el-divider />
          <h4>手动添加</h4>
          <p>点击 <el-tag>添加</el-tag> 按钮 → 选择类型 → 填写名称和详细描述 → 确定。写得越详细，AI 写作时越能准确把握。</p>
          <h4>AI 辅助生成</h4>
          <p>点击 <el-tag type="warning">AI 生成</el-tag> 按钮 → 选择类型 → 可选填提示（如"一个冷酷的女刺客"）→ 点击生成。AI 会自动避开已有设定，不会和现有角色冲突。</p>
          <h4>版本化管理</h4>
          <p>点击任意元素卡片可查看详情。修改设定时系统不覆盖旧版本，而是<strong>追加新版本</strong>。每一章都知道它是基于哪个版本写的，修改设定后系统自动找出受影响的章节。</p>
        </div>
      </el-timeline-item>

      <el-timeline-item icon="Edit" type="warning" size="large" :hollow="true">
        <h3 class="step-title">开始 AI 写作</h3>
        <div class="step-body">
          <p>点击<strong>"写作工作台"</strong>，进入核心写作界面。界面分为三部分：</p>
          <ul>
            <li><strong>左侧</strong>：章节列表，展示每章状态</li>
            <li><strong>中央</strong>：内容展示区，实时流式输出 AI 生成的文字</li>
            <li><strong>底部</strong>：控制栏，开始/继续/暂停/插入/完结</li>
          </ul>
          <p>点击<strong>"开始写作"</strong>，AI 根据你搭建的设定开始生成第一章。生成是<strong>流式输出</strong>——你能看到文字逐字出现，就像有人在实时打字。</p>
          <p>AI 写完一章后会自动暂停，等待你的指令。</p>
        </div>
      </el-timeline-item>

      <el-timeline-item icon="Connection" type="danger" size="large" :hollow="true">
        <h3 class="step-title">引导写作走向</h3>
        <div class="step-body">
          <p>AI 暂停后，你有三个选择：</p>
          <el-card shadow="hover" class="action-card">
            <h4>继续下一章</h4>
            <p>直接让 AI 接着写下一章。AI 会参考前面所有章节的内容和摘要。</p>
          </el-card>
          <el-card shadow="hover" class="action-card">
            <h4>插入内容</h4>
            <p>输入你想说的内容，可以是：一段你亲自写的文字、给 AI 的指令（如"接下来让主角遇到一个神秘老人"）、新增设定的描述。</p>
            <p>AI 会从你插入的内容之后接续写作。</p>
          </el-card>
          <el-card shadow="hover" class="action-card">
            <h4>完结框架</h4>
            <p>觉得故事可以结束了，点击完结按钮。框架状态变更为"已完结"，AI 停止写作。</p>
          </el-card>
        </div>
      </el-timeline-item>

      <el-timeline-item icon="WarningFilled" type="info" size="large" :hollow="true">
        <h3 class="step-title">处理冲突</h3>
        <div class="step-body">
          <p>写作过程中如果修改了设定（比如把主角年龄从 25 改成 30），而前文是基于旧设定写的，系统会检测到冲突。</p>
          <el-table :data="conflictSeverities" size="small" stripe style="margin-bottom: 16px">
            <el-table-column prop="level" label="级别" width="120" />
            <el-table-column prop="desc" label="说明" />
            <el-table-column prop="behavior" label="系统行为" />
          </el-table>
          <p>处理方式：</p>
          <el-row :gutter="12">
            <el-col :span="8"><el-card shadow="hover"><strong>忽略</strong><br/>矛盾无关紧要，不做处理</el-card></el-col>
            <el-col :span="8"><el-card shadow="hover"><strong>打补丁</strong><br/>手动修改内容，应用到受影响章节</el-card></el-col>
            <el-col :span="8"><el-card shadow="hover"><strong>重写章节</strong><br/>标记该章需基于新设定重新生成</el-card></el-col>
          </el-row>
        </div>
      </el-timeline-item>

      <el-timeline-item icon="Clock" type="primary" size="large" :hollow="true">
        <h3 class="step-title">查看历史</h3>
        <div class="step-body">
          <p>点击<strong>"历史事件"</strong>标签页，可以看到框架的完整操作历史：谁在什么时候创建/修改了哪个元素、AI 生成了哪一章、用户插入了什么内容、冲突何时被检测到及如何处理。</p>
        </div>
      </el-timeline-item>
    </el-timeline>

    <el-divider />

    <div class="tips-section">
      <h3>写作建议</h3>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-alert title="设定先行" type="success" :closable="false" show-icon>
            在让 AI 开始写之前，尽量把核心设定搭完整。设定越丰富，AI 写出来的内容越有深度。
          </el-alert>
        </el-col>
        <el-col :span="12">
          <el-alert title="逐步扩展" type="info" :closable="false" show-icon>
            不需要一开始就完美。先建几个核心角色和关键地点，写了几章之后自然会需要补充新设定。
          </el-alert>
        </el-col>
      </el-row>
      <el-row :gutter="16" style="margin-top: 12px">
        <el-col :span="12">
          <el-alert title="善用插入" type="warning" :closable="false" show-icon>
            AI 不擅长写高潮和转折，关键时刻你自己写几段，AI 跟进续写效果最好。
          </el-alert>
        </el-col>
        <el-col :span="12">
          <el-alert title="别怕冲突" type="error" :closable="false" show-icon>
            修改前文设定产生冲突是正常的，补丁系统就是为此设计的。大胆改，系统会帮你找出需要调整的地方。
          </el-alert>
        </el-col>
      </el-row>
    </div>
  </div>
</template>

<script setup lang="ts">
const elementTypes = [
  { type: '角色 CHARACTER', desc: '人物设定', example: '张三，25 岁剑客，沉默寡言，左臂有刀疤' },
  { type: '地点 LOCATION', desc: '场景/地域', example: '青云山，终年云雾缭绕，山顶有废弃道观' },
  { type: '物品 ITEM', desc: '关键道具', example: '霜月剑，通体银白，剑柄镶嵌蓝宝石' },
  { type: '组织 ORGANIZATION', desc: '帮派/门派', example: '天机阁，情报组织，势力遍布五国' },
  { type: '事件 EVENT', desc: '历史事件', example: '赤月之夜，三十年前魔族入侵的夜晚' },
  { type: '世界观 WORLD_SETTING', desc: '世界法则', example: '灵力分为金木水火土五系，相生相克' },
]

const conflictSeverities = [
  { level: '大冲突 MAJOR', desc: '设定严重矛盾', behavior: 'AI 暂停写作，等待用户决策' },
  { level: '小冲突 MINOR', desc: '设定有出入但不影响主线', behavior: 'AI 自动生成补丁建议，通知用户可修改' },
]
</script>

<style scoped>
.tutorial-view {
  padding: 24px 0;
  max-width: 840px;
}
h3.step-title { margin: 0 0 8px; font-size: 18px; }
.step-body { margin-bottom: 8px; }
.step-body p { margin: 0 0 8px; line-height: 1.8; color: var(--el-text-color-regular); }
.step-body ul { margin: 0 0 8px; padding-left: 20px; }
.step-body ul li { line-height: 1.8; }
.step-body h4 { margin: 12px 0 4px; font-size: 15px; }
.action-card { margin-bottom: 8px; }
.action-card h4 { margin: 0 0 4px; font-size: 14px; }
.action-card p { margin: 0; color: var(--el-text-color-secondary); }
.tips-section { }
</style>
