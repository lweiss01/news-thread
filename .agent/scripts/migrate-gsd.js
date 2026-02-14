const fs = require('fs');
const path = require('path');

const SOURCE_DIR = 'C:\\Users\\lweis\\Documents\\newsthread\\.gemini\\get-shit-done\\workflows';
const DEST_DIR = 'C:\\Users\\lweis\\Documents\\newsthread\\.agent\\workflows';

if (!fs.existsSync(DEST_DIR)) {
  fs.mkdirSync(DEST_DIR, { recursive: true });
}

const files = fs.readdirSync(SOURCE_DIR).filter(file => file.endsWith('.toml'));

console.log(`Found ${files.length} TOML files in ${SOURCE_DIR}`);

files.forEach(file => {
  const content = fs.readFileSync(path.join(SOURCE_DIR, file), 'utf8');
  
  // Extract prompt content - handles multiline strings with """
  let promptMatch = content.match(/prompt\s*=\s*"""([\s\S]*?)"""/);
  
  if (promptMatch) {
    let promptContent = promptMatch[1];
    
    // Extract description
    const purposeMatch = promptContent.match(/<purpose>\s*([\s\S]*?)\s*<\/purpose>/i);
    const description = purposeMatch 
      ? purposeMatch[1].replace(/\n/g, ' ').trim() 
      : 'GSD Workflow';
      
    writeMarkdown(file, promptContent, description);
  } else {
    // Handle basic strings with double quotes, accounting for escaped quotes
    // Match prompt = " then capture until the matching closing quote
    const complexMatch = content.match(/prompt\s*=\s*"((?:[^"\\]|\\.)*)"/);
    
    if (complexMatch) {
       let rawContent = complexMatch[1];
       try {
           // JSON.parse can unescape the string (ignoring the surrounding quotes we need to add)
           let promptContent = JSON.parse(`"${rawContent}"`);
           
           // Extract description
           const purposeMatch = promptContent.match(/<purpose>\s*([\s\S]*?)\s*<\/purpose>/i);
           const description = purposeMatch 
             ? purposeMatch[1].replace(/\n/g, ' ').trim() 
             : 'GSD Workflow';
             
           writeMarkdown(file, promptContent, description);
       } catch (e) {
           console.error(`Failed to parse string content in ${file}: ${e.message}`);
       }
    } else {
       console.warn(`Could not parse prompt from ${file}`);
    }
  }
});

function writeMarkdown(file, promptContent, description) {
    const filenameStem = path.basename(file, '.toml');
    const destFile = path.join(DEST_DIR, `gsd-${filenameStem}.md`);

    // Limit description length
    const shortDesc = description.length > 200 ? description.substring(0, 197) + '...' : description;
    
    // Remove colons from description to avoid YAML parsing issues
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
    console.log(`Converted ${file} -> gsd-${filenameStem}.md`);
}

console.log('Migration complete.');
