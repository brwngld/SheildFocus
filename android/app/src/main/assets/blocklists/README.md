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

The `adult-search-results` category is reserved for SafeSearch-related host rules. SafeSearch DNS rewriting will be implemented separately; adding a search engine's primary domain here would block the engine instead of enforcing SafeSearch.
