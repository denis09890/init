<#
.SYNOPSIS
    Finds out which public ATS job boards exist for a list of companies and how many of their
    postings are located in Romania.

.DESCRIPTION
    For every company in the candidates file the script tries the usual board identifiers on the
    public APIs Init already uses (Greenhouse, Lever, SmartRecruiters) and reports, for each board
    that exists, its name (when the API gives one), how many postings are in Romania and how many
    of those titles look early-career.

    The script only reads public APIs. It writes nothing to the database and adds nothing to the
    catalog: a person reads the table, checks that each board really belongs to the company, and
    only then adds it to backend/src/main/resources/companies.yml.

    Requests are sequential, paused between calls and sent with the InitBot User-Agent. A run of
    about a hundred companies takes roughly ten minutes. Works in Windows PowerShell 5.1 and in
    PowerShell 7. The file is ASCII only on purpose (5.1 reads scripts without a BOM as ANSI).

.PARAMETER CandidatesFile
    One company per line: "Name", or "Name = token1, token2" to try specific identifiers as well.
    Lines starting with # are comments.

.PARAMETER OutFile
    CSV with every board found. The default is inside backend/target, which git ignores.

.PARAMETER DelayMs
    Pause before every request, in milliseconds.

.EXAMPLE
    .\scripts\check-ats-sources.ps1
#>
param(
    [string]$CandidatesFile = (Join-Path $PSScriptRoot 'ats-candidates.txt'),
    [string]$OutFile = (Join-Path $PSScriptRoot '..\backend\target\ats-check.csv'),
    [int]$DelayMs = 250
)

[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$UserAgent = 'InitBot/0.1 (+https://github.com/denis09890/init)'

# Cities and the country name, compared after lower-casing and removing diacritics.
$RoPattern = '\b(romania|bucharest|bucuresti|cluj|iasi|timisoara|brasov|sibiu|oradea|craiova|constanta|galati|ploiesti|targu mures|arad|pitesti)\b'

# A rough hint for ranking boards only; the real classification happens in the backend.
$EarlyPattern = '\b(intern|interns|internship|trainee|graduate|junior|entry level|student|stagiar|stagiu|practica|apprentice|apprenticeship)\b'

function ConvertTo-PlainText([string]$Text) {
    if ([string]::IsNullOrEmpty($Text)) { return '' }
    $decomposed = $Text.Normalize([System.Text.NormalizationForm]::FormD)
    $builder = New-Object System.Text.StringBuilder
    foreach ($char in $decomposed.ToCharArray()) {
        $category = [System.Globalization.CharUnicodeInfo]::GetUnicodeCategory($char)
        if ($category -ne [System.Globalization.UnicodeCategory]::NonSpacingMark) {
            [void]$builder.Append($char)
        }
    }
    return $builder.ToString().ToLowerInvariant()
}

function ConvertTo-Array($Value) {
    if ($null -eq $Value) { return , @() }
    return , @($Value)
}

function Get-Json([string]$Uri) {
    Start-Sleep -Milliseconds $DelayMs
    try {
        $response = Invoke-WebRequest -Uri $Uri -UseBasicParsing -TimeoutSec 20 -UserAgent $UserAgent
        # Decode the bytes as UTF-8 ourselves: Windows PowerShell 5.1 would guess Latin-1.
        $text = [System.Text.Encoding]::UTF8.GetString($response.RawContentStream.ToArray())
        return [pscustomobject]@{ Status = [int]$response.StatusCode; Data = (ConvertFrom-Json -InputObject $text) }
    }
    catch {
        $status = 0
        if ($_.Exception.Response) { $status = [int]$_.Exception.Response.StatusCode }
        return [pscustomobject]@{ Status = $status; Data = $null }
    }
}

function Test-InRomania([string]$Text) {
    return (ConvertTo-PlainText $Text) -match $RoPattern
}

function New-Result($Company, $Ats, $Identifier, $Region, $BoardName, $Total, $RomaniaTitles) {
    $titles = @($RomaniaTitles)
    $early = @($titles | Where-Object { (ConvertTo-PlainText $_) -match $EarlyPattern })
    return [pscustomobject]@{
        Company    = $Company
        Ats        = $Ats
        Identifier = $Identifier
        Region     = $Region
        BoardName  = $BoardName
        Total      = $Total
        Romania    = $titles.Count
        EarlyHints = $early.Count
        Sample     = (($titles | Select-Object -First 2) -join ' | ')
    }
}

function Test-Greenhouse([string]$Company, [string]$Token) {
    $board = Get-Json "https://boards-api.greenhouse.io/v1/boards/$Token"
    if ($board.Status -ne 200 -or $null -eq $board.Data) { return $null }
    $jobs = Get-Json "https://boards-api.greenhouse.io/v1/boards/$Token/jobs"
    if ($jobs.Status -ne 200 -or $null -eq $jobs.Data) { return $null }
    $list = ConvertTo-Array $jobs.Data.jobs
    $titles = @($list | Where-Object { $_.location -and (Test-InRomania $_.location.name) } | ForEach-Object { $_.title })
    return New-Result $Company 'GREENHOUSE' $Token 'GLOBAL' $board.Data.name $list.Count $titles
}

function Get-LeverPostings([string]$Site, [string]$BaseUrl) {
    $all = @()
    $skip = 0
    $pageSize = 50
    for ($page = 0; $page -lt 20; $page++) {
        $response = Get-Json "$BaseUrl/${Site}?mode=json&skip=$skip&limit=$pageSize"
        if ($response.Status -ne 200) {
            if ($page -eq 0) { return $null }
            break
        }
        $items = ConvertTo-Array $response.Data
        $all += $items
        if ($items.Count -lt $pageSize) { break }
        $skip += $pageSize
    }
    return , $all
}

function Test-Lever([string]$Company, [string]$Site) {
    $region = 'GLOBAL'
    $postings = Get-LeverPostings $Site 'https://api.lever.co/v0/postings'
    if ($null -eq $postings) {
        $region = 'EU'
        $postings = Get-LeverPostings $Site 'https://api.eu.lever.co/v0/postings'
    }
    if ($null -eq $postings) { return $null }
    $titles = @($postings | Where-Object {
            $_.country -eq 'RO' -or
            ($_.categories -and (Test-InRomania (($_.categories.location, ($_.categories.allLocations -join ' ')) -join ' ')))
        } | ForEach-Object { $_.text })
    # Lever does not return the company name; check the identifier and the sample titles by hand.
    return New-Result $Company 'LEVER' $Site $region '' $postings.Count $titles
}

function Test-SmartRecruiters([string]$Company, [string]$Id) {
    $response = Get-Json "https://api.smartrecruiters.com/v1/companies/${Id}/postings?country=ro&limit=100"
    if ($response.Status -ne 200 -or $null -eq $response.Data) { return $null }
    $content = ConvertTo-Array $response.Data.content
    if ($content.Count -eq 0) { return $null }
    $name = ''
    if ($content[0].company) { $name = $content[0].company.name }
    $titles = @($content | ForEach-Object { $_.name })
    # The list is already filtered to Romania, so the total of the whole company is not requested.
    return New-Result $Company 'SMARTRECRUITERS' $Id 'GLOBAL' $name '' $titles
}

function Get-Variants([string]$Name, [string[]]$Extra) {
    $lower = $Name.ToLowerInvariant()
    $joined = $lower -replace '[^a-z0-9]', ''
    $hyphenated = ($lower -replace '[^a-z0-9 -]', '').Trim() -replace '\s+', '-'
    $plain = @($joined, $hyphenated) + $Extra | Where-Object { $_ } | Select-Object -Unique
    # SmartRecruiters identifiers are case-insensitive, so the lower-case form is enough.
    $smart = @($joined) + $Extra | Where-Object { $_ } | Select-Object -Unique
    return [pscustomobject]@{ Plain = @($plain); Smart = @($smart) }
}

if (-not (Test-Path -LiteralPath $CandidatesFile)) {
    throw "Candidates file not found: $CandidatesFile"
}

$entries = Get-Content -LiteralPath $CandidatesFile |
    ForEach-Object { $_.Trim() } |
    Where-Object { $_ -and -not $_.StartsWith('#') }

$results = New-Object System.Collections.Generic.List[object]
$index = 0
foreach ($entry in $entries) {
    $index++
    $parts = $entry -split '=', 2
    $name = $parts[0].Trim()
    $extra = @()
    if ($parts.Count -gt 1) { $extra = @($parts[1] -split ',' | ForEach-Object { $_.Trim() } | Where-Object { $_ }) }
    $variants = Get-Variants $name $extra
    Write-Host ("[{0}/{1}] {2}" -f $index, @($entries).Count, $name)

    foreach ($token in $variants.Plain) {
        $hit = Test-Greenhouse $name $token
        if ($hit) { $results.Add($hit) }
        $hit = Test-Lever $name $token
        if ($hit) { $results.Add($hit) }
    }
    foreach ($id in $variants.Smart) {
        $hit = Test-SmartRecruiters $name $id
        if ($hit) { $results.Add($hit) }
    }
}

$outDir = Split-Path -Parent $OutFile
if ($outDir -and -not (Test-Path -LiteralPath $outDir)) {
    New-Item -ItemType Directory -Path $outDir | Out-Null
}
$results | Export-Csv -LiteralPath $OutFile -NoTypeInformation -Encoding UTF8

$withRomania = @($results | Where-Object { $_.Romania -gt 0 } | Sort-Object Romania -Descending)
$withoutRomania = @($results | Where-Object { $_.Romania -eq 0 })

Write-Host ''
Write-Host "Boards with postings in Romania: $($withRomania.Count)"
($withRomania | Format-Table Company, Ats, Identifier, Region, BoardName, Total, Romania, EarlyHints, Sample -AutoSize -Wrap | Out-String -Width 250)
Write-Host "Boards that exist but have nothing in Romania right now: $($withoutRomania.Count)"
($withoutRomania | Format-Table Company, Ats, Identifier, Region, BoardName, Total -AutoSize | Out-String -Width 250)
Write-Host "CSV: $OutFile"
