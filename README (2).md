# Confluence MCP Server for KIRO

MCP server providing full CRUD access to Confluence — designed for [KIRO](https://kiro.dev) (AWS AI IDE).

Full Unicode support: pages, comments, labels, and attachments work with **English**, **Hebrew (עברית)**, or any mix.

---

## Tools (27 total)

### Spaces (5)

| Tool           | Description                  |
|----------------|------------------------------|
| `list_spaces`  | List all spaces              |
| `get_space`    | Get space details by key     |
| `create_space` | Create a new space           |
| `update_space` | Update name / description    |
| `delete_space` | Delete a space               |

### Pages — standard CRUD (7)

| Tool                | Description                                      |
|---------------------|--------------------------------------------------|
| `list_pages`        | List pages in a space                            |
| `get_page`          | Get page by ID (full body)                       |
| `get_page_metadata` | Get page title, version, space (no body)         |
| `create_page`       | Create a page                                    |
| `update_page`       | Replace entire page body                         |
| `delete_page`       | Move page to trash                               |
| `search_pages`      | Search via CQL (supports Hebrew queries)         |

### Pages — large page helpers (5)

These tools edit pages **without loading the full body into the LLM context window**. The server fetches the page, applies the change server-side, and writes it back. Only the delta passes through context.

| Tool                     | Description                                              |
|--------------------------|----------------------------------------------------------|
| `get_page_excerpt`       | Plain-text preview (default 2000 chars)                  |
| `append_to_page`         | Add HTML to the end of a page                            |
| `prepend_to_page`        | Add HTML to the beginning of a page                      |
| `replace_in_page`        | Find & replace text/HTML in page body (like sed)         |
| `replace_section_in_page`| Replace an entire section by heading                     |

### Comments (5)

| Tool             | Description            |
|------------------|------------------------|
| `list_comments`  | List comments on page  |
| `get_comment`    | Get comment by ID      |
| `create_comment` | Add a comment          |
| `update_comment` | Update a comment       |
| `delete_comment` | Delete a comment       |

### Labels (3)

| Tool           | Description            |
|----------------|------------------------|
| `list_labels`  | List labels on page    |
| `add_labels`   | Add labels             |
| `remove_label` | Remove a label         |

### Attachments (3)

| Tool                | Description                 |
|---------------------|-----------------------------|
| `list_attachments`  | List attachments on page    |
| `upload_attachment` | Upload file (base64)        |
| `delete_attachment` | Delete attachment           |

---

## Installation

```bash
git clone <repo-url> confluence-mcp
cd confluence-mcp
uv venv && source .venv/bin/activate
uv pip install -e .
```

## Atlassian API Token

1. Go to https://id.atlassian.com/manage-profile/security/api-tokens
2. Click **Create API token**
3. Copy the token

## KIRO Configuration

Add to `.kiro/settings/mcp.json` in your project root:

```json
{
  "mcpServers": {
    "confluence": {
      "command": "uv",
      "args": ["run", "/absolute/path/to/confluence-mcp/server.py"],
      "env": {
        "CONFLUENCE_URL": "https://your-domain.atlassian.net/wiki",
        "CONFLUENCE_USERNAME": "your-email@example.com",
        "CONFLUENCE_API_TOKEN": "your-token"
      }
    }
  }
}
```

Restart KIRO after editing the config. The Confluence tools will appear in the tool list.

---

## Usage Examples in KIRO

### Create a page

```
Create a page "API Reference" in the ENG space with a table of endpoints
```

KIRO calls: `create_page(space_key="ENG", title="API Reference", body_html="<table>...")`

### Update a small page

```
Update page 12345 — change the title to "Updated Guide" and fix the typo in the first paragraph
```

KIRO calls: `get_page("12345")` → `update_page("12345", ...)`

### Edit a large page (>50 KB) — context-friendly

```
Add a "Monitoring" section at the end of page 12345
```

KIRO calls: `append_to_page("12345", "<h2>Monitoring</h2><p>...</p>")`

The full page body **never enters the context window**.

```
Replace the "Deployment" section in page 12345 with updated steps
```

KIRO calls: `replace_section_in_page("12345", "Deployment", "<h2>Deployment</h2><p>new steps...</p>")`

### Search in Hebrew

```
Find all pages in DEV space that mention "תיעוד"
```

KIRO calls: `search_pages('type=page AND space=DEV AND text~"תיעוד"')`

---

## How Large Page Helpers Work

Traditional flow (problematic for big pages):

```
get_page(id)         →  full 80 KB HTML into context
  ... LLM processes ...
update_page(id, ...) →  full 80 KB HTML back to API
```

With large page helpers:

```
append_to_page(id, "<h2>New</h2><p>...</p>")
```

Only ~200 bytes in context. The server handles the rest.

| Scenario                     | Tool                     | Context usage |
|------------------------------|--------------------------|---------------|
| Add content at the end       | `append_to_page`         | Delta only    |
| Add content at the beginning | `prepend_to_page`        | Delta only    |
| Fix a typo / swap a word     | `replace_in_page`        | Search+replace strings |
| Rewrite one section          | `replace_section_in_page`| New section only |
| Preview before editing       | `get_page_excerpt`       | 2 KB max      |
| Check version number         | `get_page_metadata`      | ~500 bytes    |

---

## Confluence Storage Format

Page and comment bodies use Confluence Storage Format (XHTML-like). Hebrew works natively:

```html
<p>English paragraph</p>
<p dir="rtl">פסקה בעברית</p>
<h2>Heading</h2>
<ul>
  <li>Item one</li>
  <li dir="rtl">פריט בעברית</li>
</ul>
<ac:structured-macro ac:name="code">
  <ac:parameter ac:name="language">python</ac:parameter>
  <ac:plain-text-body><![CDATA[print("hello")]]></ac:plain-text-body>
</ac:structured-macro>
```

> **Tip:** Add `dir="rtl"` to elements containing Hebrew text for proper right-to-left rendering.

---

## CQL Quick Reference (for `search_pages`)

```
type=page AND space=DEV
type=page AND title="Deployment Guide"
type=page AND title~"deploy"
type=page AND text~"API"
type=page AND label="docs"
type=page AND creator=currentUser()
type=page AND lastModified > "2025-01-01"
type=page AND space=DEV ORDER BY lastModified DESC
```
