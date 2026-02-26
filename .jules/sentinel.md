## 2024-05-23 - JSON Unescaping Vulnerability in URL Resolution
**Vulnerability:** `tryBatchExecute` used `replace(/\\/g, '')` to unescape a JSON string, which corrupted escaped characters like `\u0026` (&) and could potentially facilitate obfuscation or functional bugs.
**Learning:** Relying on regex for JSON string unescaping is dangerous and incorrect. `JSON.parse` is the standard and safe way.
**Prevention:** Always use `JSON.parse` to decode JSON-encoded strings, even if extracting them manually from a larger payload.
