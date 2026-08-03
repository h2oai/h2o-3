# -*- encoding: utf-8 -*-
"""
H2O-3 Enterprise messaging shown by the open-source Python client.

Single source for the "two paths, one product" notice (H2O-3 OSS vs. H2O-3
Enterprise) so the wording and the box style stay consistent across
``h2o.init()`` and ``h2o.connect()``. Kept dependency-free (only ``sys``) to
avoid import cycles.
"""
from __future__ import absolute_import, division, print_function, unicode_literals

import sys

ENTERPRISE_EMAIL = "enterprise@h2o.ai"


def _box(lines):
    """Render the given lines inside an ASCII frame.

    Deliberately ASCII-only: this banner prints on every h2o.init(), and box-drawing
    characters raise UnicodeEncodeError when stdout cannot represent them (Windows with
    a redirected or piped stdout uses the ANSI code page).
    """
    width = max(len(s) for s in lines)
    rule = u"+" + u"-" * (width + 2) + u"+"
    body = [u"| " + s.ljust(width) + u" |" for s in lines]
    return u"\n".join([rule] + body + [rule])


def show_cluster_banner():
    """Print the OSS-vs-Enterprise notice shown by h2o.init() / h2o.connect()."""
    sys.stdout.write(u"\n" + _box([
        u"You are running the community edition of H2O-3 OSS.",
        u"",
        u"For commercial use, H2O-3 Enterprise is now recommended.",
        u"This includes production support, CVE fixes, multi-node scaling,",
        u"model artifact extraction, and more.",
        u"See h2o.ai/h2o-3/oss-vs-enterprise for additional details.",
        u"Contact " + ENTERPRISE_EMAIL + u" to upgrade.",
    ]) + u"\n")
    sys.stdout.flush()
