#!/usr/bin/env python3
"""rst2mdx.py — RST -> Docusaurus MDX converter for the H2O-3 docs migration (v2).

Pipeline per page:
  1. Preprocess: rst_prolog substitutions, substitution-code-block ->
     code-block, :ref: resolution (via a corpus-wide label index), label
     anchor injection, absolute /images/ path rewriting.
  2. Extract Sphinx ".. tabs::" blocks (code-tab, tab, group-tab), replace
     with placeholders.
  3. pandoc rst -> gfm (math comes out as $...$/$$...$$ natively; KaTeX
     rendering is enabled site-side via makersaurus markdown.math).
  4. Re-insert tab blocks as <Tabs>/<TabItem> MDX (content-tab bodies are
     themselves pandoc-converted).
  5. Postprocess: .html link stripping, GFM callouts -> :::admonitions,
     class= -> className= in raw HTML, frontmatter from the first H1.
  6. Copy referenced images into the mirrored output tree.

Usage:
  python3 rst2mdx.py [--src-root DIR] [--out-root DIR] page.rst [page2.rst ...]

Pages are given relative to --src-root (default: h2o-docs/src/product);
output mirrors the relative path under --out-root (default: documentation/docs)
with an .mdx extension.
"""
import argparse
import os
import re
import shutil
import subprocess
import sys

SRC_ROOT_DEFAULT = "h2o-docs/src/product"
OUT_ROOT_DEFAULT = "documentation/docs"

# Mirrors conf.py rst_prolog so |version|-style substitutions resolve.
RST_PROLOG = {"version": "3.42.0.2"}

# One-off typo fixes for the upstream RST source, applied before conversion
# so converter re-runs stay idempotent.
SOURCE_FIXES = {
    # glm.rst: broken link, missing "html" (anova_glm.# -> anova_glm.html#)
    "anova_glm.#defining-an-anova-glm-model": "anova_glm.html#defining-an-anova-glm-model",
    # glm.rst: literal "{th}" typo in prose ("p-th power" intended)
    "-{th} power of the mean": "-th power of the mean",
}

LANG_LABELS = {
    "r": "R", "python": "Python", "scala": "Scala", "java": "Java",
    "bash": "Bash", "sh": "Shell", "shell": "Shell",
}

ADMONITIONS = {"NOTE": "note", "TIP": "tip", "IMPORTANT": "info",
               "WARNING": "warning", "CAUTION": "danger",
               "ATTENTION": "warning", "HINT": "tip"}

warnings = []


def warn(msg):
    warnings.append(msg)
    sys.stderr.write(f"  WARN: {msg}\n")


def slugify(text):
    """Approximate Docusaurus/github-slugger heading anchors."""
    s = text.strip().lower()
    s = re.sub(r"[^\w\- ]", "", s)
    return s.replace(" ", "-")


# --------------------------------------------------------------------------
# Label index: corpus-wide map of `.. _label:` -> (page relpath, anchor, text)
# --------------------------------------------------------------------------

HEADING_UNDERLINE = re.compile(r"^[=\-~^\"'`#*+.]{3,}\s*$")
LABEL_DEF = re.compile(r"^\.\. _([^:]+):\s*$")


def build_label_index(src_root):
    index = {}
    for dirpath, _dirnames, filenames in os.walk(src_root):
        for fn in filenames:
            if not fn.endswith(".rst"):
                continue
            path = os.path.join(dirpath, fn)
            rel = os.path.splitext(os.path.relpath(path, src_root))[0]
            with open(path, encoding="utf-8") as f:
                lines = f.read().expandtabs(8).split("\n")
            for i, ln in enumerate(lines):
                m = LABEL_DEF.match(ln)
                if not m:
                    continue
                label = m.group(1).strip().lower()
                # Look ahead past blanks/other labels for a section heading
                # (text line followed by an underline) to use as the anchor.
                anchor, text = "", ""
                j = i + 1
                while j < len(lines) and (not lines[j].strip()
                                          or LABEL_DEF.match(lines[j])):
                    j += 1
                if (j + 1 < len(lines) and lines[j].strip()
                        and HEADING_UNDERLINE.match(lines[j + 1])
                        and len(lines[j + 1].strip()) >= len(lines[j].strip())):
                    text = lines[j].strip()
                    anchor = slugify(text)
                index.setdefault(label, (rel, anchor, text))
    return index


# --------------------------------------------------------------------------
# Preprocessing (on raw RST, before pandoc)
# --------------------------------------------------------------------------

def apply_prolog_substitutions(text):
    for name, value in RST_PROLOG.items():
        text = text.replace(f"|{name}|", value)
    return text


def convert_substitution_code_blocks(lines):
    """`.. substitution-code-block::` is a custom Sphinx directive pandoc
    drops silently — rename it to a plain code-block (substitutions in its
    body were already applied textually by apply_prolog_substitutions)."""
    return [re.sub(r"^(\s*)\.\. substitution-code-block::", r"\1.. code-block::", ln)
            for ln in lines]


def resolve_refs(lines, label_index, page_rel):
    """Replace :ref:`Text <label>` / :ref:`label` with RST hyperlinks."""
    page_dir = os.path.dirname(page_rel)

    def href_for(label):
        entry = label_index.get(label.strip().lower())
        if not entry:
            return None, None
        target_rel, anchor, text = entry
        if target_rel == page_rel:
            href = f"#{anchor or label.strip().lower()}"
        else:
            href = os.path.relpath(target_rel, page_dir or ".")
            if anchor:
                href += f"#{anchor}"
        return href, text

    def repl(m):
        inner = m.group(1)
        em = re.match(r"^(.*)<([^<>]+)>\s*$", inner)
        if em:
            text, label = em.group(1).strip(), em.group(2).strip()
        else:
            text, label = "", inner.strip()
        href, heading = href_for(label)
        if href is None:
            warn(f"unresolved :ref:`{label}` — emitting plain text")
            return text or label
        return f"`{text or heading or label} <{href}>`__"

    return [re.sub(r":ref:`([^`]+)`", repl, ln) for ln in lines]


def inject_label_anchors(lines, label_index):
    """Drop `.. _label:` lines that precede headings (the heading slug is the
    anchor); turn the rest into raw-HTML anchors so in-page refs (citation
    lists etc.) still have a target."""
    out = []
    for i, ln in enumerate(lines):
        m = LABEL_DEF.match(ln)
        if not m:
            out.append(ln)
            continue
        label = m.group(1).strip().lower()
        entry = label_index.get(label)
        if entry and entry[1]:  # anchored to a heading -> drop the line
            continue
        out.extend(["", ".. raw:: html", "",
                    f'   <a id="{label}"></a>', ""])
    return out


def rewrite_absolute_image_paths(lines, page_rel):
    """Sphinx `/images/foo.png` is source-root absolute; make it relative to
    the page so the mirrored output tree resolves it."""
    depth = len([p for p in os.path.dirname(page_rel).split(os.sep) if p])
    prefix = "../" * depth if depth else ""
    return [re.sub(r"^(\s*\.\. (?:image|figure)::\s+)/", rf"\g<1>{prefix}", ln)
            for ln in lines]


# --------------------------------------------------------------------------
# Tabs (.. tabs:: with code-tab / tab / group-tab items)
# --------------------------------------------------------------------------

TAB_ITEM = re.compile(r"^(\s*)\.\. (code-tab|tab|group-tab)::\s*(\S+)?(?:\s+(.*))?\s*$")


def run_pandoc(rst_text, cwd):
    res = subprocess.run(
        ["pandoc", "-f", "rst", "-t", "gfm-indented_code_blocks", "--wrap=none"],
        input=rst_text, capture_output=True, text=True, cwd=cwd,
    )
    if res.returncode != 0:
        sys.stderr.write(res.stderr)
        sys.exit(res.returncode)
    return res.stdout


def dedent_block(code):
    nonblank = [c for c in code if c.strip()]
    if nonblank:
        minind = min(len(c) - len(c.lstrip()) for c in nonblank)
        code = [c[minind:] if c.strip() else "" for c in code]
    while code and not code[0].strip():
        code.pop(0)
    while code and not code[-1].strip():
        code.pop()
    return code


def render_tabs(body, cwd):
    """Render the body of a `.. tabs::` block into <Tabs>/<TabItem> MDX."""
    tabs = []
    i, n = 0, len(body)
    while i < n:
        m = TAB_ITEM.match(body[i])
        if not m:
            if body[i].strip():
                warn(f"unrecognized line inside tabs block: {body[i].strip()[:60]}")
            i += 1
            continue
        sub_indent = len(m.group(1))
        kind = m.group(2)
        arg = (m.group(3) or "").strip()
        rest = (m.group(4) or "").strip()
        i += 1
        content = []
        while i < n:
            ln = body[i]
            if ln.strip() == "":
                content.append("")
                i += 1
                continue
            if (len(ln) - len(ln.lstrip())) <= sub_indent:
                break
            content.append(ln)
            i += 1
        content = dedent_block(content)
        if kind == "code-tab":
            lang = arg
            label = rest or LANG_LABELS.get(lang.lower(), lang.capitalize())
            tabs.append(("code", lang, label, "\n".join(content)))
        else:  # tab / group-tab: arbitrary RST content
            label = (arg + " " + rest).strip()
            tabs.append(("content", slugify(label), label, "\n".join(content)))

    out = ['<Tabs groupId="lang">']
    for idx, (kind, value, label, payload) in enumerate(tabs):
        default = " default" if idx == 0 else ""
        out.append(f'<TabItem value="{value}" label="{label}"{default}>')
        out.append("")
        if kind == "code":
            out.append(f"```{value.lower()}")
            out.append(payload)
            out.append("```")
        else:
            out.append(postprocess_md(run_pandoc(payload, cwd)).strip())
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
        blocks.append(body)
    return out, blocks


# --------------------------------------------------------------------------
# Postprocessing (on pandoc's GFM output)
# --------------------------------------------------------------------------

def convert_admonitions(md):
    """Convert pandoc GFM callouts (> [!NOTE]) into Docusaurus admonitions."""
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


def postprocess_md(md):
    # Drop the .html suffix from internal (relative) links so they resolve as
    # Docusaurus routes (e.g. algo-params/foo.html -> algo-params/foo).
    md = re.sub(r"\]\((?!https?://)([^)\s]+?)\.html(#[^)]*)?\)", r"](\1\2)", md)
    md = convert_admonitions(md)
    # Raw HTML attributes must be JSX-safe in MDX.
    md = md.replace(' class="', ' className="')
    # GFM autolinks (<http://...>) are JSX syntax errors in MDX.
    md = re.sub(r"<(https?://[^>\s]+)>", r"[\1](\1)", md)
    md = escape_prose_braces(md)
    return md


# Regions where braces are safe (or owned by another parser): code fences,
# inline code, display/inline math (remark-math), and HTML/JSX tags.
PROTECTED = re.compile(
    r"```[\s\S]*?```"        # fenced code
    r"|`[^`\n]*`"            # inline code
    r"|\$\$[\s\S]*?\$\$"     # display math
    r"|\$[^$\n]+?\$"         # inline math
    r"|</?[a-zA-Z][^>]*>"    # HTML/JSX tags (incl. attributes)
)


def escape_prose_braces(md):
    """Literal { } in prose are JSX expressions in MDX — escape them.
    Idempotent: already-escaped braces are left alone."""
    out, last = [], 0
    for m in PROTECTED.finditer(md):
        out.append(re.sub(r"(?<!\\)([{}])", r"\\\1", md[last:m.start()]))
        out.append(m.group(0))
        last = m.end()
    out.append(re.sub(r"(?<!\\)([{}])", r"\\\1", md[last:]))
    return "".join(out)


IMG_REFS = [re.compile(r"!\[[^\]]*\]\(([^)\s]+)\)"),
            re.compile(r'<img[^>]+src="([^"]+)"')]


def copy_referenced_images(md, src_file, out_file):
    copied = 0
    for pat in IMG_REFS:
        for path in pat.findall(md):
            if path.startswith(("http://", "https://", "data:")):
                continue
            src = os.path.normpath(os.path.join(os.path.dirname(src_file), path))
            dst = os.path.normpath(os.path.join(os.path.dirname(out_file), path))
            if not os.path.exists(src):
                warn(f"image not found: {src} (referenced as {path})")
                continue
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            if not os.path.exists(dst):
                shutil.copy2(src, dst)
                copied += 1
    return copied


# --------------------------------------------------------------------------
# Page conversion
# --------------------------------------------------------------------------

def convert_page(page_rel, src_root, out_root, label_index):
    src_file = os.path.join(src_root, page_rel)
    page_noext = os.path.splitext(page_rel)[0]
    out_file = os.path.join(out_root, page_noext + ".mdx")
    cwd = os.path.dirname(src_file) or "."

    with open(src_file, encoding="utf-8") as f:
        # Expand tabs (RST's 8-space convention) so indentation comparisons
        # are consistent even when a page mixes tabs and spaces.
        text = f.read().expandtabs(8)

    for old, new in SOURCE_FIXES.items():
        text = text.replace(old, new)
    text = apply_prolog_substitutions(text)
    lines = text.split("\n")
    lines = convert_substitution_code_blocks(lines)
    lines = resolve_refs(lines, label_index, page_noext)
    lines = inject_label_anchors(lines, label_index)
    lines = rewrite_absolute_image_paths(lines, page_noext)

    new_lines, blocks = extract_tabs(lines)
    md = run_pandoc("\n".join(new_lines), cwd)

    for idx, body in enumerate(blocks):
        md = md.replace(f"@@TABS{idx}@@", render_tabs(body, cwd))

    md = postprocess_md(md)

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
    title = title or os.path.basename(page_noext)

    header = ["---", f'title: "{title}"', "---", ""]
    if blocks:
        header += ["import Tabs from '@theme/Tabs';",
                   "import TabItem from '@theme/TabItem';", ""]
    os.makedirs(os.path.dirname(out_file) or ".", exist_ok=True)
    with open(out_file, "w", encoding="utf-8") as f:
        f.write("\n".join(header) + "\n" + md)

    n_img = copy_referenced_images(md, src_file, out_file)
    sys.stderr.write(f"converted {page_rel} -> {out_file} "
                     f"({len(blocks)} tab block(s), {n_img} image(s) copied)\n")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--src-root", default=SRC_ROOT_DEFAULT)
    ap.add_argument("--out-root", default=OUT_ROOT_DEFAULT)
    ap.add_argument("pages", nargs="+",
                    help=".rst paths relative to --src-root")
    args = ap.parse_args()

    label_index = build_label_index(args.src_root)
    sys.stderr.write(f"label index: {len(label_index)} labels\n")

    for page in args.pages:
        rel = os.path.relpath(page, args.src_root) if os.path.isabs(page) else page
        convert_page(rel, args.src_root, args.out_root, label_index)

    if warnings:
        sys.stderr.write(f"\n{len(warnings)} warning(s) total\n")


if __name__ == "__main__":
    main()
