$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$jarName = 'railways-0.3.0-beta.2+neoforge-mc1.21.1.jar'
$expectedHash = '64B74DDAD661DEAE1CFE7E8334CFA3D39BB8EF7B761F83595BA0476C4A9BB1D5'
$destination = Join-Path $projectRoot ('libs/' + $jarName)
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $destination) | Out-Null
if ((Test-Path -LiteralPath $destination) -and (Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash -eq $expectedHash) {
    Write-Output 'Pinned Steam n Rails Beta already verified'
    exit 0
}
$temporaryJar = Join-Path $projectRoot 'libs/railways-beta-download.tmp'
Invoke-WebRequest 'https://edge.forgecdn.net/files/8522/154/railways-0.3.0-beta.2%2Bneoforge-mc1.21.1.jar' -OutFile $temporaryJar
if ((Get-FileHash -LiteralPath $temporaryJar -Algorithm SHA256).Hash -ne $expectedHash) {
    throw 'Downloaded Steam n Rails jar does not match the pinned release hash'
}
Move-Item -LiteralPath $temporaryJar -Destination $destination -Force
Write-Output 'Pinned Steam n Rails Beta downloaded and verified'
