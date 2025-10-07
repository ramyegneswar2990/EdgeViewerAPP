$baseDate = Get-Date "2025-10-07T12:00:00"
function New-TimestampedCommit {
    param ([string]$message, [string[]]$files, [int]$minutesOffset)
    $commitDate = $baseDate.AddMinutes($minutesOffset)
    $env:GIT_AUTHOR_DATE = $commitDate.ToString("yyyy-MM-ddTHH:mm:ss")
    $env:GIT_COMMITTER_DATE = $env:GIT_AUTHOR_DATE
    foreach ($file in $files) { git add $file -f }
    git commit -m $message
    Write-Host "Committed '$message' at $($commitDate.ToString('yyyy-MM-dd HH:mm:ss'))"
}
New-TimestampedCommit -message "chore(build): add project configuration" -files @("build.gradle.kts", "settings.gradle.kts", "gradle.properties") -minutesOffset 0
New-TimestampedCommit -message "chore(app): add app module configuration" -files @("app/build.gradle.kts", "app/proguard-rules.pro") -minutesOffset 2
New-TimestampedCommit -message "feat(app): add Android source code" -files @("app/src/main/AndroidManifest.xml", "app/src/main/java/") -minutesOffset 4
New-TimestampedCommit -message "chore(res): add Android resources" -files @("app/src/main/res/") -minutesOffset 6
New-TimestampedCommit -message "chore(deps): add OpenCV Android SDK" -files @("opencv-android-sdk/") -minutesOffset 8
New-TimestampedCommit -message "docs: add project documentation" -files @("*.md") -minutesOffset 10
