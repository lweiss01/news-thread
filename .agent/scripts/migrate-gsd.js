const fs = require('fs');
const path = require('path');

const SOURCE_DIR = 'C:\\Users\\lweis\\Documents\\newsthread\\.gemini\\get-shit-done\\workflows';
const DEST_DIR = 'C:\\Users\\lweis\\Documents\\newsthread\\.agent\\workflows';

if (!fs.existsSync(DEST_DIR)) {
  fs.mkdirSync(DEST_DIR, { recursive: true });
}

const allFiles = fs.readdirSync(SOURCE_DIR);
const tomlFiles = allFiles.filter(file => file.endsWith('.toml'));
const mdFiles = allFiles.filter(file => file.endsWith('.md'));

console.log(`Found ${tomlFiles.length} TOML files and ${mdFiles.length} MD files in ${SOURCE_DIR}`);

const processedStems = new Set();

function injectMigrationStep(content, stem) {
  if (stem === 'update' || stem === 'gsd-update') {
    if (!content.includes('migrate_workflows')) {
      console.log(`Injecting migrate_workflows step into ${stem} workflow...`);
      const migrationStep = `
<step name="migrate_workflows">
Run the migration script to convert the new TOML/MD workflows to the agent format.

\`\`\`bash
node .agent/scripts/migrate-gsd.js
\`\`\`
</step>
`;
      if (content.includes('<step name="display_result">')) {
        const parts = content.split('<step name="display_result">');
        return parts[0] + migrationStep + '\n<step name="display_result">' + parts[1];
      } else {
        const parts = content.split('</process>');
        if (parts.length > 1) {
          return parts[0] + migrationStep + '\n</process>' + parts[1];
        }
      }
    }
  }
  return content;
}

function extractDescription(content) {
  const purposeMatch = content.match(/<purpose>\s*([\s\S]*?)\s*<\/purpose>/i);
  return purposeMatch
    ? purposeMatch[1].replace(/\n/g, ' ').trim()
    : 'GSD Workflow';
}

function writeMarkdown(filenameStem, promptContent, description) {
  const destFile = path.join(DEST_DIR, `gsd-${filenameStem}.md`);
  const shortDesc = description.length > 200 ? description.substring(0, 197) + '...' : description;
  const safeDesc = shortDesc.replace(/:/g, '');

  const markdownContent = `---
name: gsd:${filenameStem}
description: ${safeDesc}
allowed-tools:
  - Read
  - Write
  - Edit
  - Bash
  - Grep
  - Glob
  - Task
  - AskUserQuestion
  - SlashCommand
---

${promptContent}
`;

  fs.writeFileSync(destFile, markdownContent);
  console.log(`Converted ${filenameStem} -> gsd-${filenameStem}.md`);
}

// 1. Process TOML files
tomlFiles.forEach(file => {
  const stem = path.basename(file, '.toml');
  processedStems.add(stem);

  const content = fs.readFileSync(path.join(SOURCE_DIR, file), 'utf8');
  let promptMatch = content.match(/prompt\s*=\s*"""([\s\S]*?)"""/);

  if (promptMatch) {
    let promptContent = promptMatch[1];
    const description = extractDescription(promptContent);
    promptContent = injectMigrationStep(promptContent, stem);
    writeMarkdown(stem, promptContent, description);
  } else {
    const complexMatch = content.match(/prompt\s*=\s*"((?:[^"\\]|\\.)*)"/);
    if (complexMatch) {
      let rawContent = complexMatch[1];
      try {
        let promptContent = JSON.parse(`"${rawContent}"`);
        const description = extractDescription(promptContent);
        promptContent = injectMigrationStep(promptContent, stem);
        writeMarkdown(stem, promptContent, description);
      } catch (e) {
        console.error(`Failed to parse string content in ${file}: ${e.message}`);
      }
    } else {
      console.warn(`Could not parse prompt from ${file}`);
    }
  }
});

// 2. Process MD files
mdFiles.forEach(file => {
  const stem = path.basename(file, '.md');
  if (processedStems.has(stem)) return;

  let content = fs.readFileSync(path.join(SOURCE_DIR, file), 'utf8');
  const description = extractDescription(content);
  content = injectMigrationStep(content, stem);
  writeMarkdown(stem, content, description);
});

console.log('Migration complete.');
