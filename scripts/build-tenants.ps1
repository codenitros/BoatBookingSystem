$ErrorActionPreference = "Stop"

$tenants = @("honnavar", "kasarkod")
$artifactNames = @{
    honnavar = "HonnavarBoatTicketSystem"
    kasarkod = "KasarkodBoatTicketSystem"
}

New-Item -ItemType Directory -Force -Path dist | Out-Null

foreach ($tenant in $tenants) {
    mvn -B clean package "-P$tenant"
    $source = Join-Path target "$($artifactNames[$tenant]).jar"
    $destination = Join-Path dist "$($artifactNames[$tenant]).jar"
    Copy-Item -LiteralPath $source -Destination $destination -Force
}

Write-Host "Created tenant artifacts in dist/."
