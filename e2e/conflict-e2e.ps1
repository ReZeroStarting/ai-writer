#!/usr/bin/env pwsh
# 冲突检测端到端 v2：用 Start-Job 发认证请求，避免 Start-Process 拆坏 header
$ErrorActionPreference = 'Continue'
$base = 'http://localhost:8080'

$u = 'cf' + (Get-Random -Maximum 999999)
$reg = Invoke-RestMethod "$base/api/auth/register" -Method Post -ContentType 'application/json' `
  -Body (@{ username = $u; email = "$u@t.local"; password = 'Test123456' } | ConvertTo-Json)
$hdr = @{ Authorization = "Bearer $($reg.accessToken)" }
$token = $reg.accessToken
Write-Host "1) 注册 $u"

# 先确认 token 可用
$projects = Invoke-RestMethod "$base/api/projects" -Headers $hdr
Write-Host "2) token 可用，现有项目 $($projects.Count) 个"

$proj = Invoke-RestMethod "$base/api/projects" -Method Post -Headers $hdr -ContentType 'application/json' `
  -Body (@{ name = '冲突验证用小说'; description = 'e2e' } | ConvertTo-Json)
Write-Host "3) 项目 id=$($proj.id)"

$fw = Invoke-RestMethod "$base/api/v1/projects/$($proj.id)/framework" -Method Post -Headers $hdr
Write-Host "4) 框架 id=$($fw.id)"

$el = Invoke-RestMethod "$base/api/v1/frameworks/$($fw.id)/elements" -Method Post -Headers $hdr -ContentType 'application/json' `
  -Body (@{ type = 'character'; name = '沈砚'; content = '盲人剑客，双目失明，靠听觉辨位。' } | ConvertTo-Json)
Write-Host "5) 设定元素 版本=$($el.version)"

# 后台开始生成
$url = "$base/api/v1/frameworks/$($fw.id)/write/continue-chapter"
$job = Start-Job -ScriptBlock {
  param($u2, $t)
  try {
    $r = Invoke-WebRequest -Uri $u2 -Method Post -Headers @{ Authorization = "Bearer $t" } -TimeoutSec 240
    return @{ ok = $true; size = $r.Content.Length }
  } catch { return @{ ok = $false; err = $_.Exception.Message } }
} -ArgumentList $url, $token
Write-Host "6) 已开始生成（job $($job.Id)），等 8 秒后修改设定…"

Start-Sleep -Seconds 8

$el2 = Invoke-RestMethod "$base/api/v1/frameworks/$($fw.id)/elements/$($el.id)" -Method Put -Headers $hdr -ContentType 'application/json' `
  -Body (@{ type = 'character'; name = '沈砚'; content = '盲人剑客，双目失明，靠听觉辨位，左手使剑，寡言。' } | ConvertTo-Json)
Write-Host "7) 设定更新到版本 $($el2.version)"

# 等生成结束
$deadline = (Get-Date).AddSeconds(260)
while ($job.State -eq 'Running' -and (Get-Date) -lt $deadline) { Start-Sleep -Seconds 5 }
$res = Receive-Job $job -Wait
Remove-Job $job -Force
Write-Host "8) 生成结果: $($res | ConvertTo-Json -Compress)"

Start-Sleep -Seconds 8
$conf = Invoke-RestMethod "$base/api/v1/frameworks/$($fw.id)/conflicts" -Headers $hdr
Write-Host "9) 冲突条数: $($conf.Count)"
$conf | Select-Object -First 5 | ForEach-Object { "   [$($_.severity)] $($_.description)" }

$ch = Invoke-RestMethod "$base/api/v1/frameworks/$($fw.id)/write/latest-chapter" -Headers $hdr
Write-Host "10) 最新章节: id=$($ch.id) index=$($ch.chapterIndex) 状态=$($ch.status)"
