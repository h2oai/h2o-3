#!/usr/bin/env python3
# -*- encoding: utf-8 -*-
"""
Guard against drift in the declared supported-Python-version range.

The range is hand-replicated across many files with no single source of truth,
so a version bump that updates one place but forgets another is silent today.
This test walks every place the range is declared and asserts they agree.

Canonical source: ``BuildConfig.PYTHON_VERSIONS`` in
``scripts/jenkins/groovy/buildConfig.groovy`` (the explicit, enumerated CI list).
Everything else is checked against it:

  * ``h2o/__init__.py``                 -- the runtime guard tuple + the
                                           "Tested versions are 3.x - 3.y" message
  * ``h2o-py/setup.py``                 -- classifiers + python_requires
  * ``h2o-py-cloud-extensions/setup.py``-- classifiers + python_requires
  * ``h2o-py-mlflow-flavor/setup.py``   -- classifiers + python_requires
  * ``h2o-py/README.md``                -- enumerated "3.x.x" support line
  * ``h2o-docs/.../welcome.rst``        -- enumerated support line(s)
  * ``h2o-docs/.../flow/SiteIntro.md``  -- "3.7.x through 3.14.x" support line

Floors are allowed to differ per package (h2o-py-mlflow-flavor intentionally
requires >=3.8), so sibling packages are only required to share the *ceiling*
and to be contiguous up to it; the primary h2o-py package must match the
canonical floor exactly.

These tests do not require an H2O server connection. They need the full source
checkout (build config, packaging files, docs); when run from a packaged/stripped
test tree where those files are absent (e.g. the "Changed Only" CI stage) the
test skips cleanly rather than failing.
"""
import os
import re

_here = os.path.dirname(os.path.abspath(__file__))

BUILDCONFIG = "scripts/jenkins/groovy/buildConfig.groovy"
INIT_FILE = "h2o-py/h2o/__init__.py"
SETUP_FILES = {
    "h2o-py": "h2o-py/setup.py",
    "h2o-py-cloud-extensions": "h2o-py-cloud-extensions/setup.py",
    "h2o-py-mlflow-flavor": "h2o-py-mlflow-flavor/setup.py",
}
DOC_FILES = {
    "README.md": "h2o-py/README.md",
    "welcome.rst": "h2o-docs/src/product/welcome.rst",
    "SiteIntro.md": "h2o-docs/src/product/flow/SiteIntro.md",
}

_CANON_LIST_RE = re.compile(r"List PYTHON_VERSIONS\s*=\s*\[([^\]]*)\]")
_VER_TOKEN_RE = re.compile(r"3\.(\d+)")
_CLASSIFIER_RE = re.compile(r"Programming Language :: Python :: 3\.(\d+)")
_PYREQ_RE = re.compile(r"""python_requires\s*=\s*['"]([^'"]+)['"]""")
_PYREQ_FLOOR_RE = re.compile(r">=\s*3\.(\d+)")
_PYREQ_CEIL_RE = re.compile(r"<\s*3\.(\d+)")
_GUARD_MIN_RE = re.compile(r"version_info\[:2\]\s*<\s*\(\s*3\s*,\s*(\d+)\s*\)")
_GUARD_MAX_RE = re.compile(r"version_info\[:2\]\s*>\s*\(\s*3\s*,\s*(\d+)\s*\)")
_MSG_RE = re.compile(r"Tested versions are 3\.(\d+)\.x\s*-\s*3\.(\d+)\.x")
# A documentation "support line" enumerates versions as 3.x.x; require the
# trailing ".x" so we don't pick up numpy notes like "numpy<2 on Python 3.7-3.11".
_DOC_VER_RE = re.compile(r"3\.(\d+)\.x")


def _candidate_roots():
    """Directories that might be the repo root: ancestors of this test file and
    of the installed h2o package (covers both source checkouts and editable installs)."""
    seen = []

    def _walk_up(start):
        d = start
        while True:
            if d not in seen:
                seen.append(d)
            parent = os.path.dirname(d)
            if parent == d:
                break
            d = parent

    _walk_up(_here)
    try:
        import h2o
        _walk_up(os.path.dirname(os.path.abspath(h2o.__file__)))
    except Exception:
        pass
    return seen


def _find_repo_root():
    # buildConfig.groovy is the canonical source; its presence marks a full checkout.
    for d in _candidate_roots():
        if os.path.isfile(os.path.join(d, BUILDCONFIG)):
            return d
    return None


REPO_ROOT = _find_repo_root()
# All files the consistency check needs; if any is missing we are running from a
# packaged/stripped test tree and skip rather than fail.
_REQUIRED_FILES = [BUILDCONFIG, INIT_FILE] + list(SETUP_FILES.values()) + list(DOC_FILES.values())
SOURCE_AVAILABLE = REPO_ROOT is not None and all(
    os.path.isfile(os.path.join(REPO_ROOT, rel)) for rel in _REQUIRED_FILES
)


def _skip_if_no_source():
    """Return True (and print why) when the full source tree is not reachable."""
    if not SOURCE_AVAILABLE:
        print("SKIPPED: full source tree not available in this test environment "
              "(need %s + packaging files + docs); the version-consistency check "
              "only runs from a source checkout." % BUILDCONFIG)
        return True
    return False


def _read(rel_path):
    with open(os.path.join(REPO_ROOT, rel_path), encoding="utf-8") as f:
        return f.read()


def _canonical_minors():
    """The authoritative, contiguous set of supported minor versions from CI config."""
    m = _CANON_LIST_RE.search(_read(BUILDCONFIG))
    assert m, "Could not find 'List PYTHON_VERSIONS = [...]' in %s" % BUILDCONFIG
    minors = sorted(int(x) for x in _VER_TOKEN_RE.findall(m.group(1)))
    assert minors, "PYTHON_VERSIONS is empty"
    assert minors == list(range(minors[0], minors[-1] + 1)), \
        "PYTHON_VERSIONS is not contiguous: %r" % minors
    return minors


def test_runtime_guard_matches_canonical():
    if _skip_if_no_source():
        return
    minors = _canonical_minors()
    lo, hi = minors[0], minors[-1]
    init = _read(INIT_FILE)

    gmin = _GUARD_MIN_RE.search(init)
    gmax = _GUARD_MAX_RE.search(init)
    assert gmin and gmax, "Could not parse the version_info guard in %s" % INIT_FILE
    assert int(gmin.group(1)) == lo, \
        "%s guard lower bound is 3.%s but canonical floor is 3.%d" % (INIT_FILE, gmin.group(1), lo)
    assert int(gmax.group(1)) == hi, \
        "%s guard upper bound is 3.%s but canonical ceiling is 3.%d" % (INIT_FILE, gmax.group(1), hi)

    msg = _MSG_RE.search(init)
    assert msg, "Could not find the 'Tested versions are 3.x.x - 3.y.x' message in %s" % INIT_FILE
    assert (int(msg.group(1)), int(msg.group(2))) == (lo, hi), \
        "%s message says 3.%s.x - 3.%s.x but canonical range is 3.%d - 3.%d" \
        % (INIT_FILE, msg.group(1), msg.group(2), lo, hi)


def test_setup_files_match_canonical():
    if _skip_if_no_source():
        return
    minors = _canonical_minors()
    lo, hi = minors[0], minors[-1]

    for pkg, rel_path in SETUP_FILES.items():
        text = _read(rel_path)
        cls = sorted(int(x) for x in _CLASSIFIER_RE.findall(text))
        assert cls, "%s declares no 'Python :: 3.x' classifiers" % pkg

        # Ceiling must match everywhere; classifiers must be contiguous up to it.
        assert max(cls) == hi, \
            "%s advertises ceiling 3.%d but canonical ceiling is 3.%d" % (pkg, max(cls), hi)
        assert cls == list(range(cls[0], hi + 1)), \
            "%s classifiers are not contiguous up to 3.%d: %r" % (pkg, hi, cls)

        # The primary package must also match the canonical floor; siblings may be higher.
        if pkg == "h2o-py":
            assert min(cls) == lo, \
                "h2o-py floor classifier is 3.%d but canonical floor is 3.%d" % (min(cls), lo)
        else:
            assert min(cls) >= lo, \
                "%s floor classifier 3.%d is below the canonical floor 3.%d" % (pkg, min(cls), lo)

        pyreq = _PYREQ_RE.search(text)
        assert pyreq, "%s has no python_requires" % pkg
        floor = _PYREQ_FLOOR_RE.search(pyreq.group(1))
        ceil = _PYREQ_CEIL_RE.search(pyreq.group(1))
        assert floor, "%s python_requires %r has no '>=3.x' floor" % (pkg, pyreq.group(1))
        assert ceil, "%s python_requires %r has no '<3.x' ceiling" % (pkg, pyreq.group(1))
        assert int(floor.group(1)) == min(cls), \
            "%s python_requires floor >=3.%s != lowest classifier 3.%d" % (pkg, floor.group(1), min(cls))
        assert int(ceil.group(1)) == hi + 1, \
            "%s python_requires ceiling <3.%s should be <3.%d (canonical ceiling 3.%d + 1)" \
            % (pkg, ceil.group(1), hi + 1, hi)


def test_docs_match_canonical():
    if _skip_if_no_source():
        return
    minors = _canonical_minors()
    lo, hi = minors[0], minors[-1]
    canon_set = set(minors)

    for name, rel_path in DOC_FILES.items():
        support_lines = []
        for line in _read(rel_path).splitlines():
            found = sorted({int(x) for x in _DOC_VER_RE.findall(line)})
            if len(found) >= 2:  # a version-range / enumeration line, not an incidental mention
                support_lines.append(found)
        assert support_lines, \
            "%s (%s) has no recognizable 'Python 3.x.x' support line" % (name, rel_path)

        for found in support_lines:
            assert (found[0], found[-1]) == (lo, hi), \
                "%s support line spans 3.%d - 3.%d but canonical range is 3.%d - 3.%d" \
                % (name, found[0], found[-1], lo, hi)
            # Fully-enumerated lines (same count as canonical) must match exactly,
            # so a gap like skipping 3.10 is caught; "X through Y" lines only pin the ends.
            if len(found) == len(minors):
                assert set(found) == canon_set, \
                    "%s enumerates %r but canonical set is %r" % (name, found, sorted(canon_set))


if __name__ == "__main__":
    test_runtime_guard_matches_canonical()
    print("PASS: test_runtime_guard_matches_canonical")
    test_setup_files_match_canonical()
    print("PASS: test_setup_files_match_canonical")
    test_docs_match_canonical()
    print("PASS: test_docs_match_canonical")
    print("All tests passed.")
