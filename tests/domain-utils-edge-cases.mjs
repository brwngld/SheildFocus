import assert from "node:assert/strict";

const { normalizeHostname, normalizeDomainInput, matchesDomainRule } = await import("../shared/domain-utils.js");

assert.equal(normalizeHostname("https://www.Example.com/path"), "example.com");
assert.equal(normalizeHostname("sub.www.example.com"), "sub.www.example.com");
assert.equal(normalizeDomainInput("https://www.Example.com/path"), "example.com");
assert.equal(normalizeDomainInput("sub.example.com"), "sub.example.com");
assert.equal(normalizeDomainInput("not a domain"), "");
assert.equal(normalizeDomainInput("localhost"), "localhost");
assert.equal(matchesDomainRule("news.example.com", "example.com"), true);
assert.equal(matchesDomainRule("example.com", "example.com"), true);
assert.equal(matchesDomainRule("example.com", "invalid input"), false);

console.log("domain-utils-edge-cases: ok");
