const fs = require('fs');
const path = require('path');

const ISSUES_FILE = 'C:\\Users\\lweis\\Documents\\newsthread\\.beads\\issues.jsonl';

const newIssues = [
    {
        title: "Tracking page: last updated time disappears after navigation",
        description: "After leaving and returning to the Tracking page, the last updated time disappears and no longer displays.",
        priority: 2,
        issue_type: "bug"
    },
    {
        title: "Tracking page: original story link missing for some stories",
        description: "The link to the original story on the tracking page has disappeared for some stories.",
        priority: 2,
        issue_type: "bug"
    },
    {
        title: "Tracking page: stories show no updates",
        description: "None of the tracked stories are showing any updates, even when updates should exist. This suggests a failure in the background matching or grouping logic.",
        priority: 1, // Critical
        issue_type: "bug"
    },
    {
        title: "Feed quality: Unrated sources over-represented",
        description: "The main feed contains too many stories from unrated sources (e.g., Slashdot) compared to rated/trusted sources. Need to check if we are filtering or prioritizing sources correctly.",
        priority: 2,
        issue_type: "bug"
    },
    {
        title: "Feed quality: Trusted sources missing from main feed",
        description: "Main story feed shows many similar stories but very few from the most trusted sources. User asks: 'from the newsAPI top stories feed?'. Need to verify API endpoint usage (top-headlines vs everything) and source weightings.",
        priority: 2,
        issue_type: "bug"
    },
    {
        title: "UI: Source rating badges missing",
        description: "Source rating badges (reliability shields/bias icons) no longer display for sources. This is a regression in the UI.",
        priority: 1, // Critical UI
        issue_type: "bug"
    }
];

// Simple ID generator (not perfect but sufficient for recovery)
function generateId() {
    return 'newsthread-' + Math.random().toString(36).substring(2, 5);
}

const now = new Date().toISOString();

const issueLines = newIssues.map(issue => {
    const newIssue = {
        id: generateId(),
        title: issue.title,
        description: issue.description,
        status: 'open',
        priority: issue.priority,
        issue_type: issue.issue_type,
        owner: 'lweiss01@users.noreply.github.com',
        created_at: now,
        created_by: 'Lisa',
        updated_at: now
    };
    return JSON.stringify(newIssue);
}).join('\n');

fs.appendFileSync(ISSUES_FILE, '\n' + issueLines);

console.log(`Added ${newIssues.length} issues to ${ISSUES_FILE}`);
