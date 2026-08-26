[CmdletBinding()]
param(
    [string]$ApiUrl = "http://localhost:8080",
    [ValidateRange(1, 5000)]
    [int]$Count = 100,
    [ValidateRange(0, 600000)]
    [int]$DurationMs = 100,
    [ValidateSet("NONE", "FAIL_ONCE", "ALWAYS_FAIL")]
    [string]$FailureMode = "NONE",
    [ValidateSet("HIGH", "MEDIUM", "LOW")]
    [string]$Priority = "MEDIUM",
    [ValidateRange(0, 10)]
    [int]$MaxRetries = 3,
    [switch]$UseIdempotencyKeys,
    [switch]$WaitForCompletion,
    [ValidateRange(5, 3600)]
    [int]$TimeoutSeconds = 300,
    [ValidateRange(1, 60)]
    [int]$PollIntervalSeconds = 2
)

$ErrorActionPreference = "Stop"
$jobsUrl = "$($ApiUrl.TrimEnd('/'))/api/v1/jobs"
$jobIds = [System.Collections.Generic.List[string]]::new()
$submissionStopwatch = [System.Diagnostics.Stopwatch]::StartNew()

Write-Host "Submitting $Count jobs to $jobsUrl"

for ($index = 1; $index -le $Count; $index++) {
    $payload = @{
        jobType = "SIMULATED"
        payload = @{
            durationMs = $DurationMs
            failureMode = $FailureMode
        }
        priority = $Priority
        maxRetries = $MaxRetries
    } | ConvertTo-Json -Depth 4

    $headers = @{}
    if ($UseIdempotencyKeys) {
        $headers["Idempotency-Key"] = [guid]::NewGuid().ToString()
    }

    try {
        $response = Invoke-RestMethod -Method Post -Uri $jobsUrl -ContentType "application/json" -Headers $headers -Body $payload
        $jobIds.Add($response.jobId)
    } catch {
        throw "Submission failed at job ${index} of ${Count}: $($_.Exception.Message)"
    }

    Write-Progress -Activity "Submitting Chronos jobs" -Status "$index of $Count" -PercentComplete (($index / $Count) * 100)
}

$submissionStopwatch.Stop()
$submissionRate = [math]::Round($Count / [math]::Max($submissionStopwatch.Elapsed.TotalSeconds, 0.001), 2)
Write-Host "Submitted $Count jobs in $([math]::Round($submissionStopwatch.Elapsed.TotalSeconds, 2)) seconds ($submissionRate jobs/sec)."

if (-not $WaitForCompletion) {
    Write-Host "Completion was not polled. Inspect queue depth and worker metrics in Prometheus/Grafana for this run."
    return
}

$completionStopwatch = [System.Diagnostics.Stopwatch]::StartNew()
$remaining = [System.Collections.Generic.HashSet[string]]::new([string[]]$jobIds)
$outcomes = @{}

while ($remaining.Count -gt 0 -and $completionStopwatch.Elapsed.TotalSeconds -lt $TimeoutSeconds) {
    foreach ($jobId in @($remaining)) {
        try {
            $job = Invoke-RestMethod -Method Get -Uri "$jobsUrl/$jobId"
            if ($job.status -in @("SUCCESS", "DLQ", "FAILED", "CANCELLED")) {
                if ($outcomes.ContainsKey($job.status)) {
                    $outcomes[$job.status]++
                } else {
                    $outcomes[$job.status] = 1
                }
                $remaining.Remove($jobId) | Out-Null
            }
        } catch {
            Write-Warning "Could not read job ${jobId}: $($_.Exception.Message)"
        }
    }

    Write-Progress -Activity "Waiting for Chronos jobs" -Status "$($remaining.Count) remaining" -PercentComplete ((($Count - $remaining.Count) / $Count) * 100)
    if ($remaining.Count -gt 0) {
        Start-Sleep -Seconds $PollIntervalSeconds
    }
}

$completionStopwatch.Stop()
Write-Host "Observed $($Count - $remaining.Count) terminal jobs in $([math]::Round($completionStopwatch.Elapsed.TotalSeconds, 2)) seconds."
foreach ($status in @("SUCCESS", "DLQ", "FAILED", "CANCELLED")) {
    $value = if ($outcomes.ContainsKey($status)) { $outcomes[$status] } else { 0 }
    Write-Host "${status}: $value"
}
if ($remaining.Count -gt 0) {
    Write-Warning "Timed out with $($remaining.Count) non-terminal jobs. They may still be queued, retrying, or running."
}
