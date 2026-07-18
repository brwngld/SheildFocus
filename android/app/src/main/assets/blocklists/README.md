# ShieldFocus Adult Blocklist

Add domain names to `adult_domains.json` under the most appropriate category.

Use domain names only:

```json
"adult-videos": [
  "example.com",
  "videos.example.net"
]
```

Do not include:

- `https://` or `http://`
- paths such as `/page/video`
- query strings
- duplicate domains
- descriptions, titles, or explicit text

Use lowercase domains where possible. Subdomains can be included when blocking the parent domain would be too broad.

Record the source page or dataset URL in the top-level `sources` array. Only include data that can legally be used and redistributed by ShieldFocus.

`stevenblack_adult_hosts.txt` is a normalized domain-only import of the adult-extension portion of StevenBlack's hosts file. The base advertising/malware portion is deliberately excluded to avoid categorizing unrelated tracking hosts as adult content. Regenerate it with `tools/import_hosts_blocklist.py` and review gambling-like matches separately before assigning any gambling category.

The same importer separates the base portion into advertising, tracking, malware, and high-confidence gambling assets. Malware uses explicit risk/spam/URLHaus source sections; tracking uses the dedicated 2o7 source plus tracking endpoint labels; remaining base blocking sources are assigned to advertising. Gambling enforcement requires strong casino/poker/operator signals. Broader gambling-like names remain review candidates because this source has no authoritative gambling section.

The `adult-search-results` category is reserved for SafeSearch-related host rules. SafeSearch DNS rewriting will be implemented separately; adding a search engine's primary domain here would block the engine instead of enforcing SafeSearch.
