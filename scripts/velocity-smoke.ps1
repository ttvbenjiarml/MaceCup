param(
  [string]$Name,
  [string]$WorkDir,
  [string]$Jar,
  [int]$TimeoutSeconds = 60
)
$ErrorActionPreference='Stop'
$java=(Get-ChildItem .tools -Directory | Where-Object { $_.Name -like 'jdk-21*' } | Select-Object -First 1).FullName + '\bin\java.exe'
$wd=(Resolve-Path $WorkDir).Path
$out="shared/docs/test-logs/$Name.log"
$err="shared/docs/test-logs/$Name.stderr.log"
Remove-Item $out,$err -Force -ErrorAction SilentlyContinue
$p=Start-Process -FilePath $java -ArgumentList @('-Xms256M','-Xmx512M','-jar',$Jar) -WorkingDirectory $wd -PassThru -RedirectStandardOutput $out -RedirectStandardError $err -WindowStyle Hidden
$deadline=(Get-Date).AddSeconds($TimeoutSeconds)
$ready=$false; $failed=$false
while((Get-Date) -lt $deadline -and -not $p.HasExited){
  Start-Sleep -Milliseconds 500
  if(Test-Path $out){
    $log=Get-Content $out -Raw
    if($log -match 'Done \(|Listening on|MaceCupProxyBridge ready'){$ready=$true;break}
    if($log -match 'ERROR|Exception|Failed|Could not load'){$failed=$true;break}
  }
}
if(-not $p.HasExited){Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue; Start-Sleep -Seconds 1}
$result=if($failed){'FAILED'}elseif($ready){'READY'}else{'TIMEOUT'}
Write-Output "$Name $result log=$out"
if($failed -or -not $ready){exit 1}
