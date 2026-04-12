"""
Confluence MCP Server for KIRO — full CRUD + large-page helpers.

27 tools total:
  Spaces (5):      list, get, create, update, delete
  Pages (7):       list, get, create, update, delete, search, get_page_metadata
  Large pages (5): get_page_excerpt, append, prepend, replace_in_page, replace_section
  Comments (5):    list, get, create, update, delete
  Labels (3):      list, add, remove
  Attachments (3): list, upload, delete

Full Unicode support — English, Hebrew (עברית), and mixed content.

Configuration (environment variables):
    CONFLUENCE_URL        — Base URL (e.g. https://your-domain.atlassian.net/wiki)
    CONFLUENCE_USERNAME   — User email (Atlassian Cloud)
    CONFLUENCE_API_TOKEN  — API token

Running:
    uv run server.py   (stdio transport — default for KIRO)
"""

from __future__ import annotations

import json
import logging
import os
import re
import sys
from base64 import b64decode
from typing import Any

import httpx
from mcp.server.fastmcp import FastMCP

# ---------------------------------------------------------------------------
# Logging
# ---------------------------------------------------------------------------
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
)
logger = logging.getLogger("confluence-mcp")

# ---------------------------------------------------------------------------
# MCP Server
# ---------------------------------------------------------------------------
mcp = FastMCP(
    "Confluence",
    dependencies=["httpx"],
)

# ---------------------------------------------------------------------------
# Internal helpers
# ---------------------------------------------------------------------------

def _json(obj: Any) -> str:
    """Serialize to JSON preserving Hebrew and other non-ASCII."""
    return json.dumps(obj, ensure_ascii=False, indent=2)


def _get_config() -> tuple[str, str, str]:
    """Return (base_url, username, token) from env vars."""
    base_url = os.environ.get("CONFLUENCE_URL", "").rstrip("/")
    username = os.environ.get("CONFLUENCE_USERNAME", "")
    token = os.environ.get("CONFLUENCE_API_TOKEN", "")
    if not all([base_url, username, token]):
        raise RuntimeError(
            "Missing environment variables. "
            "Set CONFLUENCE_URL, CONFLUENCE_USERNAME, CONFLUENCE_API_TOKEN."
        )
    return base_url, username, token


def _client() -> httpx.Client:
    base_url, username, token = _get_config()
    return httpx.Client(
        base_url=base_url,
        auth=(username, token),
        headers={"Accept": "application/json"},
        timeout=30.0,
    )


def _request(
    method: str,
    path: str,
    *,
    params: dict | None = None,
    json_body: dict | list | None = None,
    files: dict | None = None,
    extra_headers: dict | None = None,
) -> dict | list | str:
    """Execute an HTTP request against the Confluence REST API."""
    with _client() as client:
        headers = dict(extra_headers or {})
        kwargs: dict[str, Any] = {"params": params, "headers": headers}
        if json_body is not None:
            kwargs["json"] = json_body
        if files is not None:
            kwargs["files"] = files
            headers["X-Atlassian-Token"] = "nocheck"

        resp = client.request(method, path, **kwargs)
        resp.raise_for_status()
        if resp.status_code == 204:
            return {"status": "ok"}
        try:
            return resp.json()
        except Exception:
            return resp.text


def _get_page_internal(page_id: str) -> dict:
    """Fetch a page with body + version (internal, not exposed as tool)."""
    return _request(
        "GET",
        f"/rest/api/content/{page_id}",
        params={"expand": "body.storage,version,space"},
    )


def _save_page(
    page_id: str, title: str, body: str, version: int, message: str,
) -> Any:
    """Write a page back to Confluence (internal helper)."""
    payload = {
        "type": "page",
        "title": title,
        "body": {"storage": {"value": body, "representation": "storage"}},
        "version": {"number": version, "message": message},
    }
    return _request("PUT", f"/rest/api/content/{page_id}", json_body=payload)


# ═══════════════════════════════════════════════════════════════════════════
#  SPACES
# ═══════════════════════════════════════════════════════════════════════════

@mcp.tool()
def list_spaces(
    limit: int = 25,
    start: int = 0,
    space_type: str | None = None,
) -> str:
    """List all Confluence spaces.

    Args:
        limit: Maximum number of results (default 25).
        start: Pagination offset.
        space_type: Filter by type — 'global' or 'personal'.
    """
    params: dict[str, Any] = {"limit": limit, "start": start}
    if space_type:
        params["type"] = space_type
    return _json(_request("GET", "/rest/api/space", params=params))


@mcp.tool()
def get_space(space_key: str) -> str:
    """Get details of a single space by its key.

    Args:
        space_key: The space key (e.g. 'DEV', 'HR', 'ENG').
    """
    return _json(
        _request(
            "GET",
            f"/rest/api/space/{space_key}",
            params={"expand": "description.plain,homepage"},
        )
    )


@mcp.tool()
def create_space(key: str, name: str, description: str = "") -> str:
    """Create a new space.

    Args:
        key: Unique space key (uppercase letters, e.g. 'PROJ').
        name: Display name of the space.
        description: Plain-text description.
    """
    body: dict[str, Any] = {
        "key": key,
        "name": name,
        "description": {
            "plain": {"value": description, "representation": "plain"}
        },
    }
    return _json(_request("POST", "/rest/api/space", json_body=body))


@mcp.tool()
def update_space(
    space_key: str,
    name: str | None = None,
    description: str | None = None,
) -> str:
    """Update a space's name and/or description.

    Args:
        space_key: The space key.
        name: New display name (optional).
        description: New plain-text description (optional).
    """
    body: dict[str, Any] = {"key": space_key}
    if name:
        body["name"] = name
    if description is not None:
        body["description"] = {
            "plain": {"value": description, "representation": "plain"}
        }
    return _json(_request("PUT", f"/rest/api/space/{space_key}", json_body=body))


@mcp.tool()
def delete_space(space_key: str) -> str:
    """Delete a space (async long-running operation).

    Args:
        space_key: The space key to delete.
    """
    return _json(_request("DELETE", f"/rest/api/space/{space_key}"))


# ═══════════════════════════════════════════════════════════════════════════
#  PAGES — standard CRUD
# ═══════════════════════════════════════════════════════════════════════════

@mcp.tool()
def list_pages(
    space_key: str,
    limit: int = 25,
    start: int = 0,
    title: str | None = None,
) -> str:
    """List pages in a space. Supports English and Hebrew titles.

    Args:
        space_key: The space key.
        limit: Maximum number of results.
        start: Pagination offset.
        title: Filter by exact page title (optional).
    """
    params: dict[str, Any] = {
        "spaceKey": space_key,
        "limit": limit,
        "start": start,
        "expand": "version,space",
        "type": "page",
    }
    if title:
        params["title"] = title
    return _json(_request("GET", "/rest/api/content", params=params))


@mcp.tool()
def get_page(
    page_id: str,
    expand: str = "body.storage,version,space,ancestors",
) -> str:
    """Get a page by its ID. Returns full body in Confluence storage format.

    WARNING: For large pages (>50 KB) prefer get_page_metadata or
    get_page_excerpt to avoid filling the context window.

    Args:
        page_id: The content ID of the page.
        expand: Comma-separated fields to expand.
    """
    return _json(
        _request("GET", f"/rest/api/content/{page_id}", params={"expand": expand})
    )


@mcp.tool()
def get_page_metadata(page_id: str) -> str:
    """Get page metadata WITHOUT the body. Returns title, version, space,
    and ancestors. Use this to check version before editing large pages.

    Args:
        page_id: The content ID of the page.
    """
    return _json(
        _request(
            "GET",
            f"/rest/api/content/{page_id}",
            params={"expand": "version,space,ancestors"},
        )
    )


@mcp.tool()
def create_page(
    space_key: str,
    title: str,
    body_html: str,
    parent_page_id: str | None = None,
) -> str:
    """Create a new page. Title and body support English, Hebrew, or mixed.

    Args:
        space_key: Space key where the page will be created.
        title: Page title.
        body_html: Page body in Confluence storage format (XHTML).
        parent_page_id: ID of the parent page to nest under (optional).
    """
    payload: dict[str, Any] = {
        "type": "page",
        "title": title,
        "space": {"key": space_key},
        "body": {
            "storage": {
                "value": body_html,
                "representation": "storage",
            }
        },
    }
    if parent_page_id:
        payload["ancestors"] = [{"id": parent_page_id}]
    return _json(_request("POST", "/rest/api/content", json_body=payload))


@mcp.tool()
def update_page(
    page_id: str,
    title: str,
    body_html: str,
    version_number: int,
    version_message: str = "",
) -> str:
    """Update an existing page by replacing the entire body.

    For large pages prefer append_to_page, prepend_to_page,
    replace_in_page, or replace_section_in_page instead.

    Args:
        page_id: The content ID of the page.
        title: New title.
        body_html: Complete new body in Confluence storage format.
        version_number: Current version + 1 (get via get_page_metadata).
        version_message: Optional change description.
    """
    return _json(
        _save_page(page_id, title, body_html, version_number, version_message)
    )


@mcp.tool()
def delete_page(page_id: str) -> str:
    """Delete a page (moves it to the trash).

    Args:
        page_id: The content ID of the page.
    """
    return _json(_request("DELETE", f"/rest/api/content/{page_id}"))


@mcp.tool()
def search_pages(cql: str, limit: int = 25, start: int = 0) -> str:
    """Search for pages using CQL (Confluence Query Language).

    Supports Hebrew text in queries.

    Example CQL:
        type=page AND space=DEV AND text~"API"
        type=page AND title~"deployment"
        type=page AND label="docs" ORDER BY lastModified DESC

    Args:
        cql: CQL query string.
        limit: Maximum number of results.
        start: Pagination offset.
    """
    params = {
        "cql": cql,
        "limit": limit,
        "start": start,
        "expand": "space,version",
    }
    return _json(_request("GET", "/rest/api/content/search", params=params))


# ═══════════════════════════════════════════════════════════════════════════
#  PAGES — large-page helpers (context-window friendly)
#
#  These tools modify pages WITHOUT passing the full body through
#  the LLM context window. The server fetches the body, applies the
#  change server-side, and writes it back. The LLM only sends/receives
#  the delta.
# ═══════════════════════════════════════════════════════════════════════════

@mcp.tool()
def get_page_excerpt(page_id: str, max_length: int = 2000) -> str:
    """Get a truncated plain-text excerpt of a page. Useful for previewing
    large pages without loading the full HTML body into context.

    Args:
        page_id: The content ID of the page.
        max_length: Maximum character length of the excerpt (default 2000).
    """
    page = _get_page_internal(page_id)
    body_html = page.get("body", {}).get("storage", {}).get("value", "")

    text = re.sub(r"<[^>]+>", " ", body_html)
    text = re.sub(r"\s+", " ", text).strip()

    truncated = len(text) > max_length
    return _json({
        "id": page.get("id"),
        "title": page.get("title"),
        "version": page.get("version", {}).get("number"),
        "body_length_chars": len(body_html),
        "excerpt": text[:max_length],
        "truncated": truncated,
    })


@mcp.tool()
def append_to_page(
    page_id: str,
    html_to_append: str,
    version_message: str = "",
) -> str:
    """Append HTML to the END of a page. Only the new content needs to fit
    in context — the existing body is handled server-side.

    Args:
        page_id: The content ID of the page.
        html_to_append: HTML content to append (Confluence storage format).
        version_message: Optional version comment.
    """
    page = _get_page_internal(page_id)
    title = page["title"]
    old_body = page["body"]["storage"]["value"]
    version = page["version"]["number"] + 1

    new_body = old_body + html_to_append
    _save_page(page_id, title, new_body, version, version_message or "Appended content")
    return _json({
        "status": "ok",
        "id": page_id,
        "version": version,
        "body_length_before": len(old_body),
        "body_length_after": len(new_body),
    })


@mcp.tool()
def prepend_to_page(
    page_id: str,
    html_to_prepend: str,
    version_message: str = "",
) -> str:
    """Prepend HTML to the BEGINNING of a page. The existing body is
    handled server-side.

    Args:
        page_id: The content ID of the page.
        html_to_prepend: HTML content to prepend (Confluence storage format).
        version_message: Optional version comment.
    """
    page = _get_page_internal(page_id)
    title = page["title"]
    old_body = page["body"]["storage"]["value"]
    version = page["version"]["number"] + 1

    new_body = html_to_prepend + old_body
    _save_page(page_id, title, new_body, version, version_message or "Prepended content")
    return _json({
        "status": "ok",
        "id": page_id,
        "version": version,
        "body_length_before": len(old_body),
        "body_length_after": len(new_body),
    })


@mcp.tool()
def replace_in_page(
    page_id: str,
    search_text: str,
    replace_text: str,
    version_message: str = "",
) -> str:
    """Find and replace text/HTML inside a page body without loading the
    full body into context. Works like sed on the raw storage-format HTML.

    Args:
        page_id: The content ID of the page.
        search_text: Exact string to find (can be HTML or plain text).
        replace_text: Replacement string.
        version_message: Optional version comment.
    """
    page = _get_page_internal(page_id)
    title = page["title"]
    old_body = page["body"]["storage"]["value"]
    version = page["version"]["number"] + 1

    count = old_body.count(search_text)
    if count == 0:
        return _json({
            "status": "not_found",
            "id": page_id,
            "message": f"Search text not found in page body ({len(old_body)} chars).",
            "replacements": 0,
        })

    new_body = old_body.replace(search_text, replace_text)
    _save_page(
        page_id, title, new_body, version,
        version_message or f"Replaced {count} occurrence(s)",
    )
    return _json({
        "status": "ok",
        "id": page_id,
        "version": version,
        "replacements": count,
        "body_length_before": len(old_body),
        "body_length_after": len(new_body),
    })


@mcp.tool()
def replace_section_in_page(
    page_id: str,
    section_heading: str,
    new_section_html: str,
    heading_level: int = 2,
    version_message: str = "",
) -> str:
    """Replace an entire section (heading + all content until the next
    heading of the same or higher level) with new HTML.

    Only the new section HTML needs to fit in context.

    Args:
        page_id: The content ID of the page.
        section_heading: Exact heading text to find (e.g. 'Rollback Procedures').
        new_section_html: Complete replacement HTML including the heading tag.
        heading_level: Heading level 1-6 (default 2 for <h2>).
        version_message: Optional version comment.
    """
    page = _get_page_internal(page_id)
    title = page["title"]
    old_body = page["body"]["storage"]["value"]
    version = page["version"]["number"] + 1

    esc_heading = re.escape(section_heading)
    levels = "|".join(f"h{l}" for l in range(1, heading_level + 1))
    pattern = re.compile(
        rf"(<h{heading_level}[^>]*>\s*{esc_heading}\s*</h{heading_level}>)"
        rf"(.*?)"
        rf"(?=<(?:{levels})[^>]*>|$)",
        re.DOTALL | re.IGNORECASE,
    )

    match = pattern.search(old_body)
    if not match:
        return _json({
            "status": "not_found",
            "id": page_id,
            "message": f"Section '{section_heading}' (h{heading_level}) not found.",
        })

    new_body = old_body[: match.start()] + new_section_html + old_body[match.end() :]
    _save_page(
        page_id, title, new_body, version,
        version_message or f"Replaced section: {section_heading}",
    )
    return _json({
        "status": "ok",
        "id": page_id,
        "version": version,
        "section_found": section_heading,
        "body_length_before": len(old_body),
        "body_length_after": len(new_body),
    })


# ═══════════════════════════════════════════════════════════════════════════
#  COMMENTS
# ═══════════════════════════════════════════════════════════════════════════

@mcp.tool()
def list_comments(page_id: str, limit: int = 25, start: int = 0) -> str:
    """List comments on a page.

    Args:
        page_id: The content ID of the page.
        limit: Maximum number of results.
        start: Pagination offset.
    """
    params = {
        "limit": limit,
        "start": start,
        "expand": "body.storage,version",
    }
    return _json(
        _request("GET", f"/rest/api/content/{page_id}/child/comment", params=params)
    )


@mcp.tool()
def get_comment(comment_id: str) -> str:
    """Get a single comment by its ID.

    Args:
        comment_id: The content ID of the comment.
    """
    return _json(
        _request(
            "GET",
            f"/rest/api/content/{comment_id}",
            params={"expand": "body.storage,version"},
        )
    )


@mcp.tool()
def create_comment(page_id: str, body_html: str) -> str:
    """Add a comment to a page. Body supports Hebrew and English.

    Args:
        page_id: The content ID of the page.
        body_html: Comment body in Confluence storage format (XHTML).
    """
    payload = {
        "type": "comment",
        "container": {"id": page_id, "type": "page"},
        "body": {
            "storage": {
                "value": body_html,
                "representation": "storage",
            }
        },
    }
    return _json(_request("POST", "/rest/api/content", json_body=payload))


@mcp.tool()
def update_comment(comment_id: str, body_html: str, version_number: int) -> str:
    """Update an existing comment.

    Args:
        comment_id: The content ID of the comment.
        body_html: New comment body in Confluence storage format.
        version_number: Current version + 1.
    """
    payload = {
        "type": "comment",
        "body": {
            "storage": {
                "value": body_html,
                "representation": "storage",
            }
        },
        "version": {"number": version_number},
    }
    return _json(
        _request("PUT", f"/rest/api/content/{comment_id}", json_body=payload)
    )


@mcp.tool()
def delete_comment(comment_id: str) -> str:
    """Delete a comment.

    Args:
        comment_id: The content ID of the comment.
    """
    return _json(_request("DELETE", f"/rest/api/content/{comment_id}"))


# ═══════════════════════════════════════════════════════════════════════════
#  LABELS
# ═══════════════════════════════════════════════════════════════════════════

@mcp.tool()
def list_labels(page_id: str) -> str:
    """List all labels attached to a page.

    Args:
        page_id: The content ID of the page.
    """
    return _json(_request("GET", f"/rest/api/content/{page_id}/label"))


@mcp.tool()
def add_labels(page_id: str, labels: list[str]) -> str:
    """Add one or more labels to a page.

    Args:
        page_id: The content ID of the page.
        labels: List of label names (e.g. ['api', 'docs']).
    """
    body = [{"prefix": "global", "name": lbl} for lbl in labels]
    return _json(
        _request("POST", f"/rest/api/content/{page_id}/label", json_body=body)
    )


@mcp.tool()
def remove_label(page_id: str, label: str) -> str:
    """Remove a label from a page.

    Args:
        page_id: The content ID of the page.
        label: The label name to remove.
    """
    return _json(
        _request("DELETE", f"/rest/api/content/{page_id}/label/{label}")
    )


# ═══════════════════════════════════════════════════════════════════════════
#  ATTACHMENTS
# ═══════════════════════════════════════════════════════════════════════════

@mcp.tool()
def list_attachments(page_id: str, limit: int = 25, start: int = 0) -> str:
    """List attachments on a page.

    Args:
        page_id: The content ID of the page.
        limit: Maximum number of results.
        start: Pagination offset.
    """
    params = {"limit": limit, "start": start}
    return _json(
        _request(
            "GET",
            f"/rest/api/content/{page_id}/child/attachment",
            params=params,
        )
    )


@mcp.tool()
def upload_attachment(
    page_id: str,
    filename: str,
    file_base64: str,
    content_type: str = "application/octet-stream",
    comment: str = "",
) -> str:
    """Upload a file attachment to a page.

    Args:
        page_id: The content ID of the page.
        filename: File name (Unicode supported).
        file_base64: File content encoded as base64.
        content_type: MIME type (default: application/octet-stream).
        comment: Optional description.
    """
    file_bytes = b64decode(file_base64)
    files = {"file": (filename, file_bytes, content_type)}
    params: dict[str, str] = {}
    if comment:
        params["comment"] = comment
    return _json(
        _request(
            "POST",
            f"/rest/api/content/{page_id}/child/attachment",
            params=params,
            files=files,
        )
    )


@mcp.tool()
def delete_attachment(attachment_id: str) -> str:
    """Delete an attachment.

    Args:
        attachment_id: The content ID of the attachment.
    """
    return _json(_request("DELETE", f"/rest/api/content/{attachment_id}"))


# ---------------------------------------------------------------------------
# Entrypoint
# ---------------------------------------------------------------------------

if __name__ == "__main__":
    transport = "stdio"
    port = 8000

    args = sys.argv[1:]
    i = 0
    while i < len(args):
        if args[i] == "--transport" and i + 1 < len(args):
            transport = args[i + 1]
            i += 2
        elif args[i] == "--port" and i + 1 < len(args):
            port = int(args[i + 1])
            i += 2
        else:
            i += 1

    logger.info("Starting Confluence MCP server (transport=%s)", transport)

    if transport == "stdio":
        mcp.run(transport="stdio")
    else:
        mcp.run(transport=transport, port=port)
