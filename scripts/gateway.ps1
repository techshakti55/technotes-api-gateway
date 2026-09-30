param(
    [ValidateSet('Run', 'Test', 'Package')]
    [string]$Action = 'Run',
    [string]$EnvFile = '.env'
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Push-Location $projectRoot
try {
    if ($Action -ne 'Test') {
        if (-not (Test-Path $EnvFile)) {
            throw "Environment file missing. Copy .env.example to .env and set your values."
        }
        foreach ($line in Get-Content $EnvFile) {
            $line = $line.Trim()
            if (-not $line -or $line.StartsWith('#')) { continue }
            $parts = $line -split '=', 2
            if ($parts.Count -ne 2 -or $parts[0] -notmatch '^[A-Z][A-Z0-9_]*$') {
                throw "Invalid environment entry; use KEY=value without shell expressions."
            }
            [Environment]::SetEnvironmentVariable($parts[0], $parts[1], 'Process')
        }
        $required = @('SERVER_PORT', 'NOTES_SERVICE_URL', 'USER_OAUTH_SERVICE_URL',
            'UI_ORIGIN', 'GATEWAY_CONNECT_TIMEOUT_MS', 'GATEWAY_RESPONSE_TIMEOUT',
            'CORS_MAX_AGE_SECONDS', 'EUREKA_CLIENT_ENABLED', 'EUREKA_URL')
        foreach ($key in $required) {
            if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($key, 'Process'))) {
                throw "Required environment variable missing: $key"
            }
        }
        if ($env:UI_ORIGIN.Contains('*')) { throw 'UI_ORIGIN must be one exact origin, without a wildcard.' }
    }
    switch ($Action) {
        'Run' { & .\mvnw.cmd spring-boot:run }
        'Test' { & .\mvnw.cmd test }
        'Package' { & .\mvnw.cmd clean verify }
    }
    if ($LASTEXITCODE -ne 0) { throw "Maven exited with code $LASTEXITCODE" }
} finally {
    Pop-Location
}
