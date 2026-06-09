#!/usr/bin/env python3
"""rst2mdx.py — pilot RST -> Docusaurus MDX converter for the H2O-3 docs migration.

Pipeline:
  1. Extract Sphinx ".. tabs::" / ".. code-tab::" blocks, replace with placeholders.
  2. Run pandoc (rst -> gfm) on the remaining content (handles prose, lists,
     tables, links, and inline :math: -> $...$).
  3. Re-insert the tab blocks as Docusaurus <Tabs>/<TabItem> MDX.
  4. Prepend MDX frontmatter (title from the first H1, which is then removed
     from the body to avoid a duplicate heading).

Usage: python3 rst2mdx.py <input.rst> <output.mdx>

Scope note: this pilot handles the highest-frequency Sphinx constructs
(code-tab blocks + math + standard prose). :ref:/:doc: cross-refs,
|substitutions|, .. prompt::, and image path rewrites are TODO for the
full run.
"""
import os
import re
import subprocess
import sys

LANG_LABELS = {
    "r": "R", "python": "Python", "scala": "Scala", "java": "Java",
    "bash": "Bash", "sh": "Shell", "shell": "Shell",
}

ADMONITIONS = {"NOTE": "note", "TIP": "tip", "IMPORTANT": "info",
               "WARNING": "warning", "CAUTION": "danger"}


def convert_admonitions(md):
    """Convert pandoc GFM callouts (> [!NOTE]) into Docusaurus admonitions (:::note)."""
    lines = md.split("\n")
    out, i = [], 0
    while i < len(lines):
        m = re.match(r"^>\s*\[!(\w+)\]\s*$", lines[i])
        if m and m.group(1).upper() in ADMONITIONS:
            kind = ADMONITIONS[m.group(1).upper()]
            i += 1
            block = []
            while i < len(lines) and lines[i].lstrip().startswith(">"):
                block.append(re.sub(r"^\s*>\s?", "", lines[i]))
                i += 1
            while block and not block[0].strip():
                block.pop(0)
            while block and not block[-1].strip():
                block.pop()
            out.append(f":::{kind}")
            out.extend(block)
            out.append(":::")
        else:
            out.append(lines[i])
            i += 1
    return "\n".join(out)


def render_tabs(body):
    """Render the body of a `.. tabs::` block into <Tabs>/<TabItem> MDX."""
    tabs = []
    i, n = 0, len(body)
    while i < n:
        m = re.match(r"^(\s*)\.\. code-tab::\s*(\S+)(?:\s+(.*))?\s*$", body[i])
        if not m:
            i += 1
            continue
        sub_indent = len(m.group(1))
        lang = m.group(2).strip()
        label = (m.group(3) or LANG_LABELS.get(lang, lang.capitalize())).strip()
        i += 1
        code = []
        while i < n:
            ln = body[i]
            if ln.strip() == "":
                code.append("")
                i += 1
                continue
            if (len(ln) - len(ln.lstrip())) <= sub_indent:
                break
            code.append(ln)
            i += 1
        nonblank = [c for c in code if c.strip()]
        if nonblank:
            minind = min(len(c) - len(c.lstrip()) for c in nonblank)
            code = [c[minind:] if c.strip() else "" for c in code]
        while code and not code[0].strip():
            code.pop(0)
        while code and not code[-1].strip():
            code.pop()
        tabs.append((lang, label, "\n".join(code)))

    out = ['<Tabs groupId="lang">']
    for idx, (lang, label, code) in enumerate(tabs):
        default = " default" if idx == 0 else ""
        out.append(f'<TabItem value="{lang}" label="{label}"{default}>')
        out.append("")
        out.append(f"```{lang}")
        out.append(code)
        out.append("```")
        out.append("")
        out.append("</TabItem>")
    out.append("</Tabs>")
    return "\n".join(out)


def extract_tabs(lines):
    """Replace each `.. tabs::` block with an @@TABSn@@ placeholder."""
    out, blocks = [], []
    i, n = 0, len(lines)
    while i < n:
        m = re.match(r"^(\s*)\.\. tabs::\s*$", lines[i])
        if not m:
            out.append(lines[i])
            i += 1
            continue
        base = len(m.group(1))
        i += 1
        body = []
        while i < n:
            ln = lines[i]
            if ln.strip() == "":
                body.append(ln)
                i += 1
                continue
            if (len(ln) - len(ln.lstrip())) <= base:
                break
            body.append(ln)
            i += 1
        # Blank lines around the placeholder so it stays its own block — the
        # tab body absorbs trailing blanks, which would otherwise glue the
        # placeholder to a following heading/paragraph and break MDX.
        out.append("")
        out.append(f"@@TABS{len(blocks)}@@")
        out.append("")
        blocks.append(render_tabs(body))
    return out, blocks


def main():
    inp, outp = sys.argv[1], sys.argv[2]
    with open(inp, encoding="utf-8") as f:
        # Expand tabs (RST's 8-space convention) so indentation comparisons are
        # consistent even when a page mixes tabs and spaces.
        lines = f.read().expandtabs(8).split("\n")

    new_lines, blocks = extract_tabs(lines)
    res = subprocess.run(
        ["pandoc", "-f", "rst", "-t", "gfm", "--wrap=none"],
        input="\n".join(new_lines), capture_output=True, text=True,
    )
    if res.returncode != 0:
        sys.stderr.write(res.stderr)
        sys.exit(res.returncode)
    md = res.stdout

    for idx, mdx in enumerate(blocks):
        md = md.replace(f"@@TABS{idx}@@", mdx)

    # Drop the .html suffix from internal (relative) links so they resolve as
    # Docusaurus routes (e.g. algo-params/foo.html -> algo-params/foo).
    md = re.sub(r"\]\((?!https?://)([^)\s]+?)\.html(#[^)]*)?\)", r"](\1\2)", md)

    # Convert GFM callouts to Docusaurus admonitions.
    md = convert_admonitions(md)

    # Pull the first H1 into frontmatter and drop it from the body.
    title = None
    body_lines = md.split("\n")
    for j, ln in enumerate(body_lines):
        hm = re.match(r"^#\s+(.*)", ln)
        if hm:
            title = hm.group(1).strip()
            del body_lines[j]
            break
    md = "\n".join(body_lines).lstrip("\n")
    title = title or os.path.splitext(os.path.basename(inp))[0]

    header = ["---", f"title: {title}", "---", ""]
    if blocks:
        header += ["import Tabs from '@theme/Tabs';",
                   "import TabItem from '@theme/TabItem';", ""]
    with open(outp, "w", encoding="utf-8") as f:
        f.write("\n".join(header) + "\n" + md)
    sys.stderr.write(f"converted {inp} -> {outp} ({len(blocks)} tab block(s))\n")


if __name__ == "__main__":
    main()
