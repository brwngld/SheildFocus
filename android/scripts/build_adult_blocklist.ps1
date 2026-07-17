param(
    [Parameter(Mandatory = $true)] [string] $InputDirectory,
    [Parameter(Mandatory = $true)] [string] $OutputFile
)

$pageCategories = [ordered]@{
    'pornographic-websites' = @(
        'ai-porn-sites.html'
    )
    'adult-videos' = @(
        'asian-porn-premium-sites.html',
        'full-porn-movies-sites.html',
        'top-asian-porn-tube-sites.html',
        'top-porn-tube-sites.html',
        'top-premium-sites.html'
    )
    'webcam-live-chat' = @(
        'best-adult-chat-sites.html',
        'top-asian-sex-cams.html',
        'top-sex-cam-sites.html'
    )
    'escort-hookup-sites' = @(
        'best-dating-sites.html'
    )
    'explicit-image-galleries' = @(
        'ai-porn-generator-sites-reviews.html',
        'asian-ai-porn-sites-reviews.html',
        'free-onlyfans-accounts.html',
        'onlyfans-porn-sites.html',
        'premium-onlyfans-sites.html',
        'tiktok-porn-sites.html'
    )
    'nsfw-forums' = @()
    'adult-advertising' = @()
    'adult-search-results' = @()
}

$excludedHosts = @(
    'theporndude.com', 'www.theporndude.com', 'porndudeai.com', 'porndudecams.com',
    'porndudegirls.com', 'pdude.link', 'cdn.staticstack.net',
    'staticstack.net', 'tpd.deals', 'google.com', 'www.google.com',
    'googletagmanager.com', 'www.googletagmanager.com', 'google-analytics.com',
    'twitter.com', 'www.twitter.com', 'instagram.com', 'www.instagram.com',
    'reddit.com', 'www.reddit.com', 'schema.org', 'www.w3.org', 'ipinfo.be',
    'api.ipinfo.be', 'porndudecasting.com', 'porndudeshop.com'
)

function Normalize-Host([string] $Value) {
    if ([string]::IsNullOrWhiteSpace($Value)) { return $null }
    $candidate = [System.Net.WebUtility]::HtmlDecode($Value).Trim().ToLowerInvariant()
    if ($candidate -match '^https?://') {
        try { $candidate = ([Uri] $candidate).DnsSafeHost.ToLowerInvariant() } catch { return $null }
    }
    $candidate = $candidate.Trim('.')
    if ($candidate.StartsWith('www.')) { $candidate = $candidate.Substring(4) }
    if ($candidate -notmatch '^[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?\.[a-z]{2,63}$') { return $null }
    if ($candidate -in $excludedHosts -or "www.$candidate" -in $excludedHosts) { return $null }
    if ($candidate -match '\.(png|jpg|jpeg|gif|svg|webp|avif|woff|woff2)$') { return $null }
    return $candidate
}

function Get-SiteDomains([string] $Path) {
    $html = Get-Content -LiteralPath $Path -Raw
    $segments = [regex]::Split($html, '<div class="url_link_container"') | Select-Object -Skip 1
    $results = [System.Collections.Generic.List[string]]::new()

    foreach ($segment in $segments) {
        $slice = if ($segment.Length -gt 5000) { $segment.Substring(0, 5000) } else { $segment }
        $externalMatch = [regex]::Match($slice, 'data-external-link="([^"]+)"', 'IgnoreCase')
        $siteHost = if ($externalMatch.Success) { Normalize-Host $externalMatch.Groups[1].Value } else { $null }

        if (-not $siteHost) {
            $descriptionMatch = [regex]::Match($slice, '<div class="url_short_desc">(.*?)</div>', 'IgnoreCase,Singleline')
            if ($descriptionMatch.Success) {
                $description = [regex]::Replace([System.Net.WebUtility]::HtmlDecode($descriptionMatch.Groups[1].Value), '<[^>]+>', ' ')
                $domainMatches = [regex]::Matches($description, '(?i)\b(?:www\.)?[a-z0-9][a-z0-9.-]*\.[a-z]{2,63}\b')
                foreach ($domainMatch in $domainMatches) {
                    $siteHost = Normalize-Host $domainMatch.Value
                    if ($siteHost) { break }
                }
            }
        }

        if ($siteHost) { $results.Add($siteHost) }
    }

    return $results | Sort-Object -Unique
}

$seen = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
$categories = [ordered]@{}
$existingCategories = if (Test-Path -LiteralPath $OutputFile) {
    try { (Get-Content -LiteralPath $OutputFile -Raw | ConvertFrom-Json).categories } catch { $null }
} else {
    $null
}

foreach ($entry in $pageCategories.GetEnumerator()) {
    $domains = [System.Collections.Generic.List[string]]::new()
    if ($entry.Value.Count -eq 0 -and $null -ne $existingCategories) {
        $existingProperty = $existingCategories.psobject.Properties |
            Where-Object { $_.Name -eq $entry.Key } |
            Select-Object -First 1
        foreach ($domain in @($existingProperty.Value)) {
            $preservedEntry = [System.Net.WebUtility]::HtmlDecode([string] $domain).Trim().ToLowerInvariant()
            if (
                $preservedEntry -and
                $preservedEntry -notmatch '(?i)porndude|pdude|(^|[./])tpd\.' -and
                $seen.Add($preservedEntry)
            ) {
                $domains.Add($preservedEntry)
            }
        }
    }
    foreach ($fileName in $entry.Value) {
        $path = Join-Path $InputDirectory $fileName
        if (-not (Test-Path -LiteralPath $path)) { continue }
        foreach ($domain in (Get-SiteDomains $path)) {
            if ($seen.Add($domain)) { $domains.Add($domain) }
        }
    }
    $categories[$entry.Key] = @($domains | Sort-Object)
}

$document = [ordered]@{
    schemaVersion = 1
    listId = 'shieldfocus-adult-content'
    updatedAt = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')
    categories = $categories
}

$parent = Split-Path -Parent $OutputFile
if (-not (Test-Path -LiteralPath $parent)) { New-Item -ItemType Directory -Path $parent -Force | Out-Null }
$document | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $OutputFile -Encoding utf8

$categories.GetEnumerator() | ForEach-Object { "{0}: {1}" -f $_.Key, $_.Value.Count }
"total: $($seen.Count)"
