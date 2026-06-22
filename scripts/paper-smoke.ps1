param(
  [string]$Name,
  [string]$WorkDir,
  [string]$Jar,
  [int]$TimeoutSeconds = 150
)
$ErrorActionPreference='Stop'
$java=(Get-ChildItem .tools -Directory | Where-Object { $_.Name -like 'jdk-21*' } | Select-Object -First 1).FullName + '\bin\java.exe'
$wd=(Resolve-Path $WorkDir).Path
Remove-Item "$wd\logs\latest.log" -Force -ErrorAction SilentlyContinue
$out="shared/docs/test-logs/$Name.stdout.log"
$err="shared/docs/test-logs/$Name.stderr.log"
$p=Start-Process -FilePath $java -ArgumentList @('-Xms512M','-Xmx1024M','-jar',$Jar,'nogui') -WorkingDirectory $wd -PassThru -RedirectStandardOutput $out -RedirectStandardError $err -WindowStyle Hidden
$deadline=(Get-Date).AddSeconds($TimeoutSeconds)
$ready=$false; $failed=$false
while((Get-Date) -lt $deadline -and -not $p.HasExited){
  Start-Sleep -Milliseconds 500
  $logPath="$wd\logs\latest.log"
  if(Test-Path $logPath){
    $log=Get-Content $logPath -Raw
    if($log -match 'Done \('){$ready=$true;break}
    if($log -match 'SEVERE|ERROR|Exception|Could not load|Failed to start'){$failed=$true;break}
  }
}
if(-not $p.HasExited){Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue; Start-Sleep -Seconds 2}
$dest="shared/docs/test-logs/$Name.log"
if(Test-Path "$wd\logs\latest.log"){Copy-Item "$wd\logs\latest.log" $dest -Force}
$result=if($failed){'FAILED'}elseif($ready){'READY'}else{'TIMEOUT'}
Write-Output "$Name $result log=$dest"
if($failed -or -not $ready){exit 1}
