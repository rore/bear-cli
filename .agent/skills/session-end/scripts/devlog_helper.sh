#!/bin/bash

# Dev Log Helper Script for Fourth Table
# Gathers environmental information for creating per-session dev log entries
# Non-interactive: outputs data for Agent to use in constructing filename and loading context

# Get the project root directory (where this script is located, minus .agent/skills/session-end/scripts/)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# Go up 5 levels to reach project root from .agent/skills/session-end/scripts/
PROJECT_ROOT="$(dirname "$(dirname "$(dirname "$(dirname "$(dirname "$SCRIPT_DIR")")")")")"

# 1. Get current timestamp in ISO format
TIMESTAMP=$(date "+%Y-%m-%dT%H:%M%z")
echo "TIMESTAMP: $TIMESTAMP"

# 2. Get current week number and year
WEEK=$(date "+%V")
YEAR=$(date "+%Y")
echo "WEEK: $WEEK"
echo "YEAR: $YEAR"

# 3. Extract date and time components for filename
FILE_DATE=$(date "+%Y-%m-%d")
FILE_TIME=$(date "+%H%M")
echo "FILE_DATE: $FILE_DATE"
echo "FILE_TIME: $FILE_TIME"

# 4. Define path to current week's directory (relative to project)
LOG_DIR="$PROJECT_ROOT/docs/devlogs"
WEEK_DIR="$LOG_DIR/${YEAR}_w${WEEK}"
echo "PROJECT_ROOT: $PROJECT_ROOT"
echo "LOG_DIR: $LOG_DIR"
echo "WEEK_DIR: $WEEK_DIR"

# 5. Create directories if they don't exist
if [ ! -d "$LOG_DIR" ]; then
    mkdir -p "$LOG_DIR"
    echo "LOG_DIR_CREATED: true"
fi

if [ ! -d "$WEEK_DIR" ]; then
    mkdir -p "$WEEK_DIR"
    echo "WEEK_DIR_CREATED: true"
else
    echo "WEEK_DIR_EXISTS: true"
fi

# 6. List existing sessions in current week chronologically
echo "CURRENT_WEEK_SESSIONS:"
if [ -d "$WEEK_DIR" ]; then
    sessions=$(ls -1 "$WEEK_DIR" 2>/dev/null)
    if [ -n "$sessions" ]; then
        echo "$sessions" | while read -r session; do
            echo "  - $session"
        done
    else
        echo "  (none)"
    fi
fi

# 7. Find most recent previous session (may be in previous week)
LATEST_SESSION=""
PREV_SESSION_PATH=""

# First check current week
if [ -d "$WEEK_DIR" ]; then
    LATEST_SESSION=$(ls -t "$WEEK_DIR" 2>/dev/null | head -1)
fi

# If current week has sessions, use latest; otherwise check previous weeks
if [ -n "$LATEST_SESSION" ]; then
    PREV_SESSION_PATH="$WEEK_DIR/$LATEST_SESSION"
else
    # Find all week directories, sort reverse chronologically
    for week_dir in $(ls -dt "$LOG_DIR"/????_w?? 2>/dev/null); do
        if [ -d "$week_dir" ] && [ "$week_dir" != "$WEEK_DIR" ]; then
            LATEST_SESSION=$(ls -t "$week_dir" 2>/dev/null | head -1)
            if [ -n "$LATEST_SESSION" ]; then
                PREV_SESSION_PATH="$week_dir/$LATEST_SESSION"
                break
            fi
        fi
    done
fi

if [ -n "$PREV_SESSION_PATH" ] && [ -f "$PREV_SESSION_PATH" ]; then
    echo "PREVIOUS_SESSION_PATH: $PREV_SESSION_PATH"
    echo "READ_PREVIOUS_SESSION: true"
else
    echo "PREVIOUS_SESSION_PATH: (none - this is the first session)"
    echo "READ_PREVIOUS_SESSION: false"
fi

# 8. Find same-day sessions (for loading conventions)
echo "SAME_DAY_SESSIONS:"
if [ -d "$WEEK_DIR" ]; then
    same_day=$(ls -1 "$WEEK_DIR" 2>/dev/null | grep "^${FILE_DATE}")
    if [ -n "$same_day" ]; then
        echo "$same_day" | while read -r session; do
            echo "  - $WEEK_DIR/$session"
        done
    else
        echo "  (none)"
    fi
fi

# 9. Get current git branch
GIT_BRANCH=$(git -C "$PROJECT_ROOT" branch --show-current 2>/dev/null || echo "unknown")
echo "GIT_BRANCH: $GIT_BRANCH"

# 10. Get recent commits (for context)
echo "RECENT_COMMITS:"
git -C "$PROJECT_ROOT" log --oneline -5 2>/dev/null | while read -r commit; do
    echo "  - $commit"
done

# 11. Output guidance for Agent to construct filename
echo ""
echo "=== Filename Construction ==="
echo "Agent should determine from conversation context:"
echo "  - DESCRIPTION: Brief 3-5 word description (lowercase, hyphens, e.g. 'setup-agents-directory')"
echo "  - FEATURE: Optional feature/epic name if applicable (e.g. 'authentication')"
echo ""
echo "Filename format:"
echo "  \${FILE_DATE}_\${FILE_TIME}_\${DESCRIPTION}.md"
echo ""
echo "Example: 2026-01-04_1430_setup-agents-directory.md"
echo ""
echo "SESSION_FILE_PATH: $WEEK_DIR/${FILE_DATE}_${FILE_TIME}_[DESCRIPTION].md"

echo ""
echo "=== Loading Recommendations ==="
echo "LOAD_PREVIOUS: true (always load immediately previous session if exists)"
if [ -n "$(ls -1 "$WEEK_DIR" 2>/dev/null | grep "^${FILE_DATE}")" ]; then
    echo "LOAD_SAME_DAY: true (found other sessions from today)"
else
    echo "LOAD_SAME_DAY: false"
fi

# 12. Partner model location
# UPDATED PATH: Pointing to the global partner model
PARTNER_MODEL="$PROJECT_ROOT/.agent/knowledge/partner_model.md"
echo ""
echo "=== Knowledge Files ==="
echo "PARTNER_MODEL: $PARTNER_MODEL"
if [ -f "$PARTNER_MODEL" ]; then
    echo "PARTNER_MODEL_EXISTS: true"
else
    echo "PARTNER_MODEL_EXISTS: false"
fi

echo ""
echo "DEVLOG_HELPER_COMPLETE"