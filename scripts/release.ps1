<#
.SYNOPSIS
    Local one-command release automation for Kesje Tume / kTume (org.chuvash.ktume).

.DESCRIPTION
    Builds, verifies, tags, and publishes a reproducible signed APK release to GitHub Releases
    without ever uploading the private signing keystore (.jks) to cloud CI servers.
    Compatible with F-Droid Reproducible Builds (UpdateCheckMode: Tags, Binaries: ktume-%v.apk).

.EXAMPLE
    .\scripts\release.ps1 -DryRun
    Runs tests, lint, release build, APK signature/reproducibility checks, and previews release notes.

.EXAMPLE
    .\scripts\release.ps1
    Pushes main, creates & pushes tag v<versionName>, and publishes ktume-<versionName>.apk via gh CLI.
#>
[CmdletBinding()]
param(
    [switch]$DryRun,
    [switch]$SkipChecks,
    [switch]$BuildBundle,
    [switch]$AllowNonMain,
    [string]$ExpectedCertSha256 = "3fd72afafa41e973b820e0c9ee1cd9e818f95a3431fbf4f9722ed9fc4b301312"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Write-Step([string]$Message) {
    Write-Host "`n==> $Message" -ForegroundColor Cyan
}

function Write-Warn([string]$Message) {
    Write-Host "[WARN] $Message" -ForegroundColor Yellow
}

function Fail([string]$Message) {
    throw $Message
}

function Resolve-Jdk21Home {
    $userGradleProps = Join-Path $HOME ".gradle\gradle.properties"
    if (Test-Path $userGradleProps) {
        foreach ($line in [System.IO.File]::ReadAllLines($userGradleProps, [System.Text.Encoding]::UTF8)) {
            if ($line -match '^\s*org\.gradle\.java\.home\s*=\s*(.+)$') {
                $candidate = ($Matches[1].Trim() -replace '\\:', ':') -replace '\\\\', '\'
                if (Test-Path (Join-Path $candidate "bin\java.exe")) {
                    return $candidate
                }
            }
        }
    }

    $fallbackJdk = "C:/Program Files/COMSOL/COMSOL64/Multiphysics/java/win64/jre"
    if (Test-Path (Join-Path $fallbackJdk "bin\java.exe")) {
        return $fallbackJdk
    }

    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\java.exe"))) {
        return $env:JAVA_HOME
    }

    Fail "JDK 21 not found. Set org.gradle.java.home in ~/.gradle/gradle.properties or JAVA_HOME."
}

function Resolve-Apksigner([string]$ProjectRoot) {
    $sdkCandidates = New-Object System.Collections.Generic.List[string]

    $localProps = Join-Path $ProjectRoot "local.properties"
    if (Test-Path $localProps) {
        foreach ($line in [System.IO.File]::ReadAllLines($localProps, [System.Text.Encoding]::UTF8)) {
            if ($line -match '^\s*sdk\.dir\s*=\s*(.+)$') {
                $sdkPath = ($Matches[1].Trim() -replace '\\:', ':') -replace '\\\\', '\'
                $sdkCandidates.Add($sdkPath)
            }
        }
    }
    if ($env:ANDROID_HOME) { $sdkCandidates.Add($env:ANDROID_HOME) }
    if ($env:ANDROID_SDK_ROOT) { $sdkCandidates.Add($env:ANDROID_SDK_ROOT) }
    if ($env:LOCALAPPDATA) { $sdkCandidates.Add((Join-Path $env:LOCALAPPDATA "Android\Sdk")) }

    foreach ($sdk in $sdkCandidates) {
        if (-not $sdk -or -not (Test-Path $sdk)) { continue }
        $buildToolsDir = Join-Path $sdk "build-tools"
        if (-not (Test-Path $buildToolsDir)) { continue }

        $tools = Get-ChildItem -Path $buildToolsDir -Directory |
            Sort-Object {
                $ver = $_.Name -replace '-.*$', ''
                try { [version]$ver } catch { [version]"0.0.0" }
            } -Descending

        foreach ($dir in $tools) {
            $bat = Join-Path $dir.FullName "apksigner.bat"
            if (Test-Path $bat) { return $bat }
        }
    }

    Fail "apksigner.bat not found in Android SDK build-tools."
}

function Resolve-GhCli {
    $cmd = Get-Command gh -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }

    $candidates = @(
        "C:\Program Files\GitHub CLI\gh.exe",
        (Join-Path $env:LOCALAPPDATA "Programs\GitHub CLI\gh.exe")
    )
    foreach ($path in $candidates) {
        if ($path -and (Test-Path $path)) { return $path }
    }
    return $null
}

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Push-Location $projectRoot
try {
    [Console]::OutputEncoding = [System.Text.Encoding]::UTF8

    # 1. Read versionCode & versionName from app/build.gradle.kts
    Write-Step "Reading version metadata from app/build.gradle.kts"
    $gradleBuildPath = Join-Path $projectRoot "app\build.gradle.kts"
    $gradleContent = [System.IO.File]::ReadAllText($gradleBuildPath, [System.Text.Encoding]::UTF8)

    if ($gradleContent -notmatch 'versionCode\s*=\s*(\d+)') {
        Fail "Could not parse versionCode from app/build.gradle.kts"
    }
    $versionCode = [int]$Matches[1]

    if ($gradleContent -notmatch 'versionName\s*=\s*"([^"]+)"') {
        Fail "Could not parse versionName from app/build.gradle.kts"
    }
    $versionName = $Matches[1]
    $tagName = "v$versionName"
    $apkFileName = "ktume-$versionName.apk"

    # Pure-ASCII Unicode escapes keep this script safe in both Windows PowerShell 5.1 and PowerShell 7
    $appDisplayTitle = [regex]::Unescape("\u041a\u04d7\u0441\u044c\u0435 \u0422\u04f3\u043c\u0435 (kT\u00fcme)")
    $releaseTitle = "$appDisplayTitle $tagName"

    Write-Host "Version      : $versionName (versionCode = $versionCode)"
    Write-Host "Git Tag      : $tagName"
    Write-Host "Release Asset: $apkFileName"
    Write-Host "Mode         : $(if ($DryRun) { 'DRY-RUN (no git push / GitHub upload)' } else { 'LIVE RELEASE' })"

    # 2. Pre-flight checks: signing, Fastlane changelogs, Git cleanliness, tag, gh CLI
    Write-Step "Running pre-flight checks"

    $keystorePropsPath = Join-Path $projectRoot "keystore.properties"
    if (-not (Test-Path $keystorePropsPath) -and -not $env:KESJE_KEYSTORE_PATH) {
        Fail "Missing keystore.properties (and KESJE_KEYSTORE_PATH is not set). Copy keystore.properties.example to keystore.properties first."
    }

    $ruChangelogPath = Join-Path $projectRoot "fastlane\metadata\android\ru-RU\changelogs\$versionCode.txt"
    $enChangelogPath = Join-Path $projectRoot "fastlane\metadata\android\en-US\changelogs\$versionCode.txt"

    if (-not (Test-Path $ruChangelogPath)) {
        Fail "Missing Fastlane changelog for versionCode ${versionCode}: fastlane/metadata/android/ru-RU/changelogs/$versionCode.txt"
    }
    $ruChangelog = [System.IO.File]::ReadAllText($ruChangelogPath, [System.Text.Encoding]::UTF8).Trim()
    $ruBytes = [System.Text.Encoding]::UTF8.GetByteCount($ruChangelog)
    if ($ruBytes -eq 0 -or $ruBytes -gt 500) {
        Fail "ru-RU changelog ($versionCode.txt) must be 1..500 UTF-8 bytes (current: $ruBytes bytes)."
    }

    $enChangelog = ""
    if (Test-Path $enChangelogPath) {
        $enChangelog = [System.IO.File]::ReadAllText($enChangelogPath, [System.Text.Encoding]::UTF8).Trim()
        $enBytes = [System.Text.Encoding]::UTF8.GetByteCount($enChangelog)
        if ($enBytes -gt 500) {
            Fail "en-US changelog ($versionCode.txt) exceeds 500 UTF-8 bytes (current: $enBytes bytes)."
        }
    } else {
        Write-Warn "Optional English changelog fastlane/metadata/android/en-US/changelogs/$versionCode.txt not found."
    }

    $gitStatus = (& git status --porcelain)
    if ($LASTEXITCODE -ne 0) { Fail "git status failed." }
    if ($gitStatus) {
        $msg = "Git working tree has uncommitted changes. F-Droid Reproducible Builds require building from the exact committed tree."
        if ($DryRun) { Write-Warn "$msg (allowed in -DryRun)" } else { Fail $msg }
    }

    $currentBranch = (& git branch --show-current).Trim()
    if ($currentBranch -ne "main" -and -not $AllowNonMain) {
        $msg = "Current branch is '$currentBranch' (expected 'main'). Pass -AllowNonMain to override."
        if ($DryRun) { Write-Warn "$msg (allowed in -DryRun)" } else { Fail $msg }
    }

    $existingLocalTag = (& git tag -l $tagName)
    if ($existingLocalTag) {
        $msg = "Git tag '$tagName' already exists locally. Bump versionCode and versionName in app/build.gradle.kts before releasing."
        if ($DryRun) { Write-Warn "$msg (allowed in -DryRun)" } else { Fail $msg }
    }

    if (-not $DryRun) {
        $existingRemoteTag = (& git ls-remote --tags origin "refs/tags/$tagName")
        if ($LASTEXITCODE -ne 0) { Fail "Failed to query remote tags on 'origin'." }
        if ($existingRemoteTag) {
            Fail "Git tag '$tagName' already exists on origin. Bump versionCode and versionName in app/build.gradle.kts."
        }
    }

    $jdkHome = Resolve-Jdk21Home
    $env:JAVA_HOME = $jdkHome
    $apksignerExe = Resolve-Apksigner -ProjectRoot $projectRoot
    $ghExe = Resolve-GhCli

    Write-Host "JAVA_HOME    : $jdkHome"
    Write-Host "apksigner    : $apksignerExe"

    if (-not $ghExe) {
        $ghInstallHint = "GitHub CLI (gh) is not installed. Run once:`n  winget install --id GitHub.cli -e`n  gh auth login"
        if ($DryRun) {
            Write-Warn "$ghInstallHint"
        } else {
            Fail $ghInstallHint
        }
    } else {
        Write-Host "GitHub CLI   : $ghExe"
        if (-not $DryRun) {
            & $ghExe auth status *> $null
            if ($LASTEXITCODE -ne 0) {
                Fail "GitHub CLI is not authenticated. Run 'gh auth login' once in your terminal."
            }
        }
    }

    # 3. Clean build, unit tests, lint, and signed release APK
    $gradleTasks = New-Object System.Collections.Generic.List[string]
    $gradleTasks.Add("clean")
    if (-not $SkipChecks) {
        $gradleTasks.Add("testDebugUnitTest")
        $gradleTasks.Add("lintDebug")
    }
    $gradleTasks.Add("assembleRelease")
    if ($BuildBundle) {
        $gradleTasks.Add("bundleRelease")
    }
    $gradleTasks.Add("--no-daemon")

    Write-Step "Running Gradle: .\gradlew.bat $($gradleTasks -join ' ')"
    & (Join-Path $projectRoot "gradlew.bat") @gradleTasks
    if ($LASTEXITCODE -ne 0) {
        Fail "Gradle build failed with exit code $LASTEXITCODE."
    }

    $builtApkPath = Join-Path $projectRoot "app\build\outputs\apk\release\app-release.apk"
    if (-not (Test-Path $builtApkPath)) {
        Fail "Signed APK not found at $builtApkPath (check keystore.properties so Gradle does not emit an unsigned APK)."
    }

    if ($DryRun) {
        $targetApkPath = Join-Path $projectRoot "app\build\outputs\apk\release\$apkFileName"
    } else {
        $targetApkPath = Join-Path $projectRoot $apkFileName
    }
    Copy-Item -Path $builtApkPath -Destination $targetApkPath -Force

    # 4. Verify F-Droid Reproducibility & APK Signature
    Write-Step "Verifying APK reproducibility and cryptographic signature"
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [System.IO.Compression.ZipFile]::OpenRead($targetApkPath)
    try {
        $vcsEntry = $zip.Entries | Where-Object { $_.FullName -eq "META-INF/version-control-info.textproto" }
        if ($vcsEntry) {
            Fail "APK contains META-INF/version-control-info.textproto! This breaks F-Droid Reproducible Builds."
        }
    } finally {
        $zip.Dispose()
    }

    $certOutput = (& $apksignerExe verify --print-certs $targetApkPath 2>&1 | Out-String)
    if ($LASTEXITCODE -ne 0) {
        Fail "apksigner verification failed:`n$certOutput"
    }

    if ($certOutput -notmatch 'Signer #1 certificate SHA-256 digest:\s*([0-9a-fA-F]{64})') {
        Fail "Could not extract Signer #1 SHA-256 certificate digest from apksigner output:`n$certOutput"
    }
    $actualCertSha256 = $Matches[1].ToLowerInvariant()

    if ($ExpectedCertSha256 -and ($actualCertSha256 -ne $ExpectedCertSha256.ToLowerInvariant())) {
        Fail "APK signing certificate mismatch!`n  Expected (F-Droid AllowedAPKSigningKeys): $ExpectedCertSha256`n  Actual APK signer                     : $actualCertSha256"
    }

    $apkSha256 = (Get-FileHash -Path $targetApkPath -Algorithm SHA256).Hash.ToLowerInvariant()
    $apkSizeMb = ([math]::Round((Get-Item $targetApkPath).Length / 1MB, 2)).ToString([System.Globalization.CultureInfo]::InvariantCulture)

    # 5. Compose UTF-8 (no BOM) Release Notes from Fastlane changelogs
    $hChangesRu = [regex]::Unescape("### \u0427\u0442\u043e \u043d\u043e\u0432\u043e\u0433\u043e")
    $hVerify = [regex]::Unescape("### \u041f\u0440\u043e\u0432\u0435\u0440\u043a\u0430 \u043f\u043e\u0434\u043b\u0438\u043d\u043d\u043e\u0441\u0442\u0438 (`SHA-256`)")
    $lblFile = [regex]::Unescape("\u0424\u0430\u0439\u043b")
    $lblSize = [regex]::Unescape("\u041c\u0411")
    $lblCert = [regex]::Unescape("\u0421\u0435\u0440\u0442\u0438\u0444\u0438\u043a\u0430\u0442 \u043f\u043e\u0434\u043f\u0438\u0441\u0438 APK")

    $notesLines = New-Object System.Collections.Generic.List[string]
    $notesLines.Add($hChangesRu)
    $notesLines.Add($ruChangelog)
    if ($enChangelog) {
        $notesLines.Add("")
        $notesLines.Add("### What's New")
        $notesLines.Add($enChangelog)
    }
    $notesLines.Add("")
    $notesLines.Add($hVerify)
    $notesLines.Add("- $lblFile ``$apkFileName`` ($apkSizeMb $lblSize): ``$apkSha256``")
    $notesLines.Add("- ${lblCert}: ``$actualCertSha256``")

    $releaseNotes = ($notesLines -join "`n") + "`n"
    $notesPath = Join-Path $projectRoot "app\build\outputs\apk\release\release-notes-$tagName.md"
    [System.IO.File]::WriteAllText($notesPath, $releaseNotes, [System.Text.UTF8Encoding]::new($false))

    Write-Host "APK Path     : $targetApkPath" -ForegroundColor Green
    Write-Host "APK Size     : $apkSizeMb MB" -ForegroundColor Green
    Write-Host "APK SHA-256  : $apkSha256" -ForegroundColor Green
    Write-Host "Cert SHA-256 : $actualCertSha256 (matches F-Droid AllowedAPKSigningKeys)" -ForegroundColor Green
    Write-Host "`n--- Release Notes ($notesPath) ---`n$releaseNotes-----------------------------------"

    if ($DryRun) {
        Write-Step "DRY-RUN complete. No Git tags pushed and no GitHub Release created."
        Write-Host "Commands that will run in live mode:"
        Write-Host "  git push origin HEAD"
        Write-Host "  git tag -a $tagName -m `"$releaseTitle`""
        Write-Host "  git push origin $tagName"
        Write-Host "  gh release create $tagName `"$apkFileName`" --verify-tag --title `"$releaseTitle`" --notes-file `"$notesPath`""
        return
    }

    # 6. Push branch, create & push annotated tag, publish GitHub Release
    Write-Step "Pushing commit and annotated tag '$tagName' to origin"
    & git push origin HEAD
    if ($LASTEXITCODE -ne 0) { Fail "git push origin HEAD failed." }

    & git tag -a $tagName -m $releaseTitle
    if ($LASTEXITCODE -ne 0) { Fail "git tag creation failed." }

    & git push origin $tagName
    if ($LASTEXITCODE -ne 0) { Fail "git push origin $tagName failed." }

    Write-Step "Publishing GitHub Release '$releaseTitle' with $apkFileName"
    & $ghExe release create $tagName $targetApkPath `
        --verify-tag `
        --title $releaseTitle `
        --notes-file $notesPath
    if ($LASTEXITCODE -ne 0) { Fail "gh release create failed." }

    Write-Step "Release $tagName published successfully!"
    Write-Host "F-Droid (UpdateCheckMode: Tags, AutoUpdateMode: Version) will automatically pick up $tagName and verify $apkFileName." -ForegroundColor Green
} finally {
    Pop-Location
}
