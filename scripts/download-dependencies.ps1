$ErrorActionPreference='Stop'
$slugs = @('luckperms','placeholderapi','tab-was-taken','vaultunlocked','worldedit','worldguard','decentholograms')
$dest = 'shared/dependencies'
New-Item -ItemType Directory -Force $dest | Out-Null
$headers=@{'User-Agent'='MaceCupSetup/1.0 (macecup.xyz)'}
foreach($slug in $slugs){
  $versions = Invoke-RestMethod -Headers $headers "https://api.modrinth.com/v2/project/$slug/version"
  $candidate = $versions | Where-Object { ($_.loaders -contains 'paper' -or $_.loaders -contains 'spigot' -or $_.loaders -contains 'bukkit') -and (($_.game_versions -contains '1.21.11') -or ($_.game_versions | Where-Object { $_ -like '1.21*' })) } | Sort-Object date_published -Descending | Select-Object -First 1
  if(-not $candidate){ $candidate = $versions | Sort-Object date_published -Descending | Select-Object -First 1 }
  $file = $candidate.files | Where-Object { $_.primary } | Select-Object -First 1
  if(-not $file){ $file = $candidate.files | Select-Object -First 1 }
  $safeName = "$slug-$($candidate.version_number).jar" -replace '[\\/:*?"<>|]','-'
  $out = Join-Path $dest $safeName
  Invoke-WebRequest -Headers $headers $file.url -OutFile $out
  [pscustomobject]@{slug=$slug; version=$candidate.version_number; file=$safeName; game_versions=($candidate.game_versions -join ',')}
}
