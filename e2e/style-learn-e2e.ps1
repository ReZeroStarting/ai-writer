#!/usr/bin/env pwsh
# 风格学习端到端 v2：JSON 提交素材（LearnRequest: title + content）→ 轮询任务 → 回读风格指令
$ErrorActionPreference = 'Continue'
$base = 'http://localhost:8080'
$u = 'st' + (Get-Random -Maximum 999999)
$reg = Invoke-RestMethod "$base/api/auth/register" -Method Post -ContentType 'application/json' `
  -Body (@{ username = $u; email = "$u@t.local"; password = 'Test123456' } | ConvertTo-Json)
$hdr = @{ Authorization = "Bearer $($reg.accessToken)" }
Write-Host "1) 注册 $u"

$style = Invoke-RestMethod "$base/api/styles" -Method Post -Headers $hdr -ContentType 'application/json' `
  -Body (@{ name = '冷硬派短句'; description = '素材学习后由 AI 生成风格指令' } | ConvertTo-Json)
Write-Host "2) 风格 id=$($style.id)"

$sample = @'
雨下了一夜。城门口没有灯。他站着，等一个人。
狗叫了三声，又停了。远处有马蹄，不快。
他把手按在刀上，指节发白。风从后面过来，带着河水的腥味。
'@
$job = Invoke-RestMethod "$base/api/styles/$($style.id)/learn" -Method Post -Headers $hdr -ContentType 'application/json' `
  -Body (@{ title = '冷硬派样本'; content = $sample } | ConvertTo-Json)
Write-Host "3) 学习任务 id=$($job.id) 状态=$($job.status)"

$deadline = (Get-Date).AddSeconds(180)
$final = $job
while ((Get-Date) -lt $deadline) {
  $final = Invoke-RestMethod "$base/api/jobs/$($job.id)" -Headers $hdr
  Write-Host "   状态=$($final.status)"
  if ($final.status -in @('DONE','FAILED','COMPLETED','ERROR','SUCCESS')) { break }
  Start-Sleep -Seconds 5
}

Write-Host "4) 任务最终状态=$($final.status) 错误=$($final.errorMsg)"
if ($final.resultPrompt) { "   任务产出的风格指令（前 120 字）: $($final.resultPrompt.Substring(0,[Math]::Min(120,$final.resultPrompt.Length)))" }

Write-Host "5) 回读风格模板"
$after = Invoke-RestMethod "$base/api/styles/$($style.id)" -Headers $hdr
$sp = $after.systemPrompt
if ($sp) {
  "   systemPrompt 长度=$($sp.Length)"
  "   前 120 字: $($sp.Substring(0,[Math]::Min(120,$sp.Length)))"
} else {
  "   ⚠️ systemPrompt 仍为空（任务可能异步未落库）"
}
