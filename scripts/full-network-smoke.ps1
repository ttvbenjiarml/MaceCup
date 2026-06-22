$ErrorActionPreference='Stop'
New-Item -ItemType Directory -Force shared\docs\test-logs\full-network | Out-Null
$java=(Get-ChildItem .tools -Directory | Where-Object { $_.Name -like 'jdk-21*' } | Select-Object -First 1).FullName + '\bin\java.exe'
$servers=@(
  @{Name='lobby-practice';Dir='lobby-practice';Jar='paper-1.21.11.jar';Ready='Done \('},
  @{Name='event-1';Dir='event-1';Jar='paper-1.21.11.jar';Ready='Done \('},
  @{Name='velocity-na';Dir='velocity-na';Jar='velocity.jar';Ready='Done|Listening on|MaceCupProxyBridge ready'},
  @{Name='velocity-eu';Dir='velocity-eu';Jar='velocity.jar';Ready='Done|Listening on|MaceCupProxyBridge ready'}
)
$procs=@()
function Start-One($s){
  $wd=(Resolve-Path $s.Dir).Path
  $out="shared/docs/test-logs/full-network/$($s.Name).stdout.log"
  $err="shared/docs/test-logs/full-network/$($s.Name).stderr.log"
  Remove-Item $out,$err,"$wd\logs\latest.log" -Force -ErrorAction SilentlyContinue
  $p=Start-Process -FilePath $java -ArgumentList @('-Xms384M','-Xmx1024M','-jar',$s.Jar,'nogui') -WorkingDirectory $wd -PassThru -RedirectStandardOutput $out -RedirectStandardError $err -WindowStyle Hidden
  $script:procs += $p
  $deadline=(Get-Date).AddSeconds(240)
  while((Get-Date) -lt $deadline -and -not $p.HasExited){
    Start-Sleep -Seconds 1
    $text=''
    if(Test-Path "$wd\logs\latest.log"){$text += Get-Content "$wd\logs\latest.log" -Raw}
    if(Test-Path $out){$text += Get-Content $out -Raw}
    if($text -match $s.Ready){ Write-Output "$($s.Name) READY pid=$($p.Id)"; return }
    if($text -match 'SEVERE|ERROR|Exception|Could not load|Failed to start'){ throw "$($s.Name) failed during startup" }
  }
  throw "$($s.Name) did not become ready"
}
try {
  foreach($s in $servers){ Start-One $s }
  node scripts\mc-ping.mjs 127.0.0.1 25565 | Set-Content -Encoding UTF8 shared\docs\test-logs\full-network\velocity-na-ping.json
  node scripts\mc-ping.mjs 127.0.0.1 25565 | Set-Content -Encoding UTF8 shared\docs\test-logs\full-network\velocity-eu-ping.json
  Write-Output 'PING_NA='; Get-Content shared\docs\test-logs\full-network\velocity-na-ping.json
  Write-Output 'PING_EU='; Get-Content shared\docs\test-logs\full-network\velocity-eu-ping.json
} finally {
  foreach($p in $procs){ if($p -and -not $p.HasExited){ Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue } }
}
