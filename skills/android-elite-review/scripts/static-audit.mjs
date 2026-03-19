#!/usr/bin/env node

import fs from 'node:fs';
import path from 'node:path';
import process from 'node:process';

const SKIP_DIRS = new Set([
  '.git',
  'node_modules',
  'build',
  '.gradle',
  '.idea',
  '.beads',
  '.agent',
  '.agents',
  'dist',
  'out',
  'tmp',
  'skills',
  'screenshots'
]);

const TEXT_EXTENSIONS = new Set([
  '.kt', '.java', '.xml', '.kts', '.gradle', '.md', '.json', '.yaml', '.yml', '.properties',
  '.ts', '.tsx', '.js', '.jsx', '.mjs', '.cjs', '.sh', '.ps1', '.txt'
]);

const SECRET_PATTERNS = [
  {
    id: 'SEC-PRIVKEY',
    severity: 'critical',
    category: 'secrets',
    regex: /-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----/,
    message: 'Private key material detected in source.'
  },
  {
    id: 'SEC-GOOGLE-API-KEY',
    severity: 'high',
    category: 'secrets',
    regex: /AIza[0-9A-Za-z\-_]{35}/,
    message: 'Potential Google API key detected in source.'
  },
  {
    id: 'SEC-HARDCODED-CRED',
    severity: 'high',
    category: 'secrets',
    regex: /\b(api[_-]?key|token|secret|password|client[_-]?secret)\b\s*[:=]\s*["'][^"'\n]{8,}["']/i,
    message: 'Potential hardcoded credential detected.'
  },
  {
    id: 'SEC-HARDCODED-API-HEADER',
    severity: 'high',
    category: 'secrets',
    regex: /X-API-Key["']?\s*,\s*["'][^"'\n]{8,}["']/i,
    message: 'Potential hardcoded API header credential detected.'
  }
];

const WEBVIEW_PATTERNS = [
  {
    id: 'WEBVIEW-JS-ENABLED',
    severity: 'medium',
    regex: /\.setJavaScriptEnabled\s*\(\s*true\s*\)/,
    message: 'WebView JavaScript is enabled; verify strict content and origin controls.'
  },
  {
    id: 'WEBVIEW-FILE-ACCESS',
    severity: 'high',
    regex: /\.setAllowFileAccess\s*\(\s*true\s*\)/,
    message: 'WebView file access is enabled; this can expand local attack surface.'
  },
  {
    id: 'WEBVIEW-UNIVERSAL-FILE-ACCESS',
    severity: 'high',
    regex: /\.setAllowUniversalAccessFromFileURLs\s*\(\s*true\s*\)/,
    message: 'WebView universal access from file URLs is enabled; this is high risk.'
  },
  {
    id: 'WEBVIEW-MIXED-CONTENT',
    severity: 'high',
    regex: /MIXED_CONTENT_ALWAYS_ALLOW/,
    message: 'WebView mixed content is fully allowed; enforce strict HTTPS content loading.'
  },
  {
    id: 'WEBVIEW-JS-INTERFACE',
    severity: 'medium',
    regex: /\.addJavascriptInterface\s*\(/,
    message: 'JavaScript interface is exposed; verify minimal interface and API-level protections.'
  }
];

const HIGH_RISK_TEST_EXPECTATIONS = [
  {
    source: /app[\\/]src[\\/]main[\\/]java[\/].*[\\/]RssNewsRepository\.kt$/i,
    expectedTests: [/RssNewsRepositoryTest\.kt$/i]
  },
  {
    source: /app[\\/]src[\\/]main[\\/]java[\/].*[\\/]EmbeddingEngine\.kt$/i,
    expectedTests: [/EmbeddingEngineTest\.kt$/i]
  },
  {
    source: /app[\\/]src[\\/]main[\\/]java[\/].*[\\/]EmbeddingModelManager\.kt$/i,
    expectedTests: [/EmbeddingModelManagerTest\.kt$/i]
  },
  {
    source: /app[\\/]src[\\/]main[\\/]java[\/].*[\\/]ArticleDetailScreen\.kt$/i,
    expectedTests: [/ArticleDetailScreenTest\.kt$/i, /ArticleDetail.*androidTest/i]
  },
  {
    source: /worker[\\/]src[\\/]index\.ts$/i,
    expectedTests: [/security_headers\.test\.ts$/i, /resolver_security\.test\.ts$/i]
  }
];

function parseArgs(argv) {
  const args = {
    repo: process.cwd(),
    outDir: null
  };

  for (let i = 0; i < argv.length; i += 1) {
    const arg = argv[i];
    if (arg === '--repo' && argv[i + 1]) {
      args.repo = argv[i + 1];
      i += 1;
      continue;
    }
    if (arg === '--out-dir' && argv[i + 1]) {
      args.outDir = argv[i + 1];
      i += 1;
      continue;
    }
  }

  const repo = path.resolve(args.repo);
  const outDir = args.outDir
    ? path.resolve(args.outDir)
    : path.join(repo, 'tmp', 'android-elite-review');

  return { repo, outDir };
}

function walkFiles(rootDir) {
  const files = [];
  const stack = [rootDir];

  while (stack.length > 0) {
    const current = stack.pop();
    const entries = fs.readdirSync(current, { withFileTypes: true });

    for (const entry of entries) {
      const full = path.join(current, entry.name);
      if (entry.isDirectory()) {
        if (!SKIP_DIRS.has(entry.name)) {
          stack.push(full);
        }
      } else if (entry.isFile()) {
        files.push(full);
      }
    }
  }

  return files;
}

function toRelative(repo, absolutePath) {
  return path.relative(repo, absolutePath).replace(/\\/g, '/');
}

function isTextFile(filePath) {
  const ext = path.extname(filePath).toLowerCase();
  return TEXT_EXTENSIONS.has(ext) || path.basename(filePath).toLowerCase() === 'androidmanifest.xml';
}

function readLines(filePath) {
  const raw = fs.readFileSync(filePath, 'utf8');
  return raw.split(/\r?\n/);
}

function addFinding(findings, finding) {
  findings.push(finding);
}

function scanSecrets(repo, filePath, findings) {
  if (!isTextFile(filePath)) return;

  const rel = toRelative(repo, filePath);
  const lines = readLines(filePath);

  for (let idx = 0; idx < lines.length; idx += 1) {
    const line = lines[idx];
    const lineLower = line.toLowerCase();

    if (lineLower.includes('template') || lineLower.includes('example') || lineLower.includes('sample')) {
      continue;
    }

    for (const pattern of SECRET_PATTERNS) {
      if (pattern.regex.test(line)) {
        addFinding(findings, {
          id: pattern.id,
          severity: pattern.severity,
          category: pattern.category,
          file: rel,
          line: idx + 1,
          message: pattern.message,
          snippet: line.trim(),
          recommendation: 'Move secrets to secure runtime configuration and rotate exposed credentials.'
        });
      }
    }
  }
}

function scanWebView(repo, filePath, findings) {
  const ext = path.extname(filePath).toLowerCase();
  if (ext !== '.kt' && ext !== '.java') return;

  const rel = toRelative(repo, filePath);
  const lines = readLines(filePath);

  for (let idx = 0; idx < lines.length; idx += 1) {
    const line = lines[idx];
    for (const pattern of WEBVIEW_PATTERNS) {
      if (pattern.regex.test(line)) {
        addFinding(findings, {
          id: pattern.id,
          severity: pattern.severity,
          category: 'webview',
          file: rel,
          line: idx + 1,
          message: pattern.message,
          snippet: line.trim(),
          recommendation: 'Apply least-privilege WebView settings and document threat-model justification.'
        });
      }
    }
  }
}

function scanTransportConfig(repo, files, findings) {
  const networkConfig = files.find((f) => /network_security_config\.xml$/i.test(f));
  if (networkConfig) {
    const rel = toRelative(repo, networkConfig);
    const lines = readLines(networkConfig);
    for (let idx = 0; idx < lines.length; idx += 1) {
      if (/cleartextTrafficPermitted\s*=\s*"true"/i.test(lines[idx])) {
        addFinding(findings, {
          id: 'NET-CLEARTEXT-ENABLED',
          severity: 'high',
          category: 'transport',
          file: rel,
          line: idx + 1,
          message: 'Cleartext traffic is permitted in network security config.',
          snippet: lines[idx].trim(),
          recommendation: 'Set cleartextTrafficPermitted="false" and explicitly scope debug overrides.'
        });
      }
    }
  }

  const manifest = files.find((f) => /AndroidManifest\.xml$/i.test(f));
  if (manifest) {
    const rel = toRelative(repo, manifest);
    const lines = readLines(manifest);
    for (let idx = 0; idx < lines.length; idx += 1) {
      if (/usesCleartextTraffic\s*=\s*"true"/i.test(lines[idx])) {
        addFinding(findings, {
          id: 'MANIFEST-CLEARTEXT-ENABLED',
          severity: 'high',
          category: 'transport',
          file: rel,
          line: idx + 1,
          message: 'Manifest allows cleartext traffic.',
          snippet: lines[idx].trim(),
          recommendation: 'Disable cleartext traffic in production manifest.'
        });
      }
      if (/android:debuggable\s*=\s*"true"/i.test(lines[idx])) {
        addFinding(findings, {
          id: 'MANIFEST-DEBUGGABLE',
          severity: 'high',
          category: 'release-hardening',
          file: rel,
          line: idx + 1,
          message: 'Manifest explicitly sets debuggable=true.',
          snippet: lines[idx].trim(),
          recommendation: 'Ensure release builds are non-debuggable and remove explicit debuggable=true.'
        });
      }
    }
  }
}

function scanTestCoverage(repo, files, findings) {
  const relFiles = files.map((f) => toRelative(repo, f));
  const tests = relFiles.filter((f) => {
    const lower = f.toLowerCase();
    return (
      lower.includes('/src/test/') ||
      lower.includes('/src/androidtest/') ||
      lower.endsWith('.test.ts') ||
      lower.endsWith('.spec.ts')
    );
  });

  for (const expectation of HIGH_RISK_TEST_EXPECTATIONS) {
    const source = relFiles.find((f) => expectation.source.test(f));
    if (!source) continue;

    const hasExpectedTest = expectation.expectedTests.some((pattern) => tests.some((testPath) => pattern.test(testPath)));

    if (!hasExpectedTest) {
      addFinding(findings, {
        id: 'TEST-HIGH-RISK-GAP',
        severity: 'medium',
        category: 'test-coverage',
        file: source,
        line: 1,
        message: 'High-risk file lacks an obvious dedicated test target.',
        snippet: source,
        recommendation: `Add focused tests covering ${path.basename(source)} behavior and edge cases.`
      });
    }
  }
}

function summarize(findings) {
  const summary = {
    critical: 0,
    high: 0,
    medium: 0,
    low: 0,
    total: findings.length
  };

  for (const finding of findings) {
    if (finding.severity === 'critical') summary.critical += 1;
    if (finding.severity === 'high') summary.high += 1;
    if (finding.severity === 'medium') summary.medium += 1;
    if (finding.severity === 'low') summary.low += 1;
  }

  return summary;
}

function rankSeverity(severity) {
  if (severity === 'critical') return 0;
  if (severity === 'high') return 1;
  if (severity === 'medium') return 2;
  return 3;
}

function writeOutputs(outDir, payload) {
  fs.mkdirSync(outDir, { recursive: true });

  const jsonPath = path.join(outDir, 'static-audit.json');
  const mdPath = path.join(outDir, 'static-audit.md');

  fs.writeFileSync(jsonPath, JSON.stringify(payload, null, 2), 'utf8');

  const lines = [];
  lines.push('# Static Audit Report');
  lines.push('');
  lines.push(`- Generated: ${payload.generatedAt}`);
  lines.push(`- Repo: ${payload.repo}`);
  lines.push(`- Findings: ${payload.summary.total} total (${payload.summary.critical} critical, ${payload.summary.high} high, ${payload.summary.medium} medium, ${payload.summary.low} low)`);
  lines.push('');

  if (payload.findings.length === 0) {
    lines.push('No findings from static checks.');
  } else {
    lines.push('## Findings');
    lines.push('');
    for (const finding of payload.findings) {
      lines.push(`- [${finding.severity.toUpperCase()}] ${finding.id} ${finding.file}:${finding.line}`);
      lines.push(`  - Category: ${finding.category}`);
      lines.push(`  - Problem: ${finding.message}`);
      lines.push(`  - Snippet: \`${finding.snippet}\``);
      lines.push(`  - Recommendation: ${finding.recommendation}`);
    }
  }

  fs.writeFileSync(mdPath, `${lines.join('\n')}\n`, 'utf8');

  return { jsonPath, mdPath };
}

function main() {
  const { repo, outDir } = parseArgs(process.argv.slice(2));

  if (!fs.existsSync(repo) || !fs.statSync(repo).isDirectory()) {
    console.error(`Repository path does not exist or is not a directory: ${repo}`);
    process.exit(1);
  }

  const absoluteFiles = walkFiles(repo);
  const findings = [];

  for (const filePath of absoluteFiles) {
    scanSecrets(repo, filePath, findings);
    scanWebView(repo, filePath, findings);
  }

  scanTransportConfig(repo, absoluteFiles, findings);
  scanTestCoverage(repo, absoluteFiles, findings);

  findings.sort((a, b) => {
    const sev = rankSeverity(a.severity) - rankSeverity(b.severity);
    if (sev !== 0) return sev;
    if (a.file !== b.file) return a.file.localeCompare(b.file);
    return a.line - b.line;
  });

  const payload = {
    generatedAt: new Date().toISOString(),
    repo,
    summary: summarize(findings),
    findings
  };

  const outputs = writeOutputs(outDir, payload);

  console.log(`Static audit complete: ${payload.summary.total} finding(s)`);
  console.log(`- JSON: ${outputs.jsonPath}`);
  console.log(`- Markdown: ${outputs.mdPath}`);
}

main();
