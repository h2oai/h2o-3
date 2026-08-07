# -*- encoding: utf-8 -*-
"""
H2O-3 Enterprise messaging shown by the open-source Python client.

Single source for the OSS-vs-Enterprise notice so the wording and the box style
stay consistent across the MOJO entry points. Kept dependency-free (only ``sys``
+ ``h2o.exceptions``) to avoid import cycles.
"""
from __future__ import absolute_import, division, print_function, unicode_literals

import sys

from h2o.exceptions import H2OValueError

ENTERPRISE_EMAIL = "enterprise@h2o.ai"


def _box(lines):
    """Render the given lines inside an ASCII frame.

    Deliberately ASCII-only: box-drawing characters raise UnicodeEncodeError when stdout
    cannot represent them (Windows with a redirected or piped stdout uses the ANSI code
    page). In block() the write happens before the raise, so that would mask the
    H2OValueError we actually want to surface.
    """
    width = max(len(s) for s in lines)
    rule = u"+" + u"-" * (width + 2) + u"+"
    body = [u"| " + s.ljust(width) + u" |" for s in lines]
    return u"\n".join([rule] + body + [rule])


def block(operation):
    """Print the Enterprise 'blocked' notice, then raise: MOJO is Enterprise-only."""
    sys.stdout.write(u"\n" + _box([
        u"H2O-3 ENTERPRISE REQUIRED  -  THIS ACTION IS BLOCKED",
        u"",
        operation + u" is a production capability available only in",
        u"H2O-3 Enterprise, the commercially supported tier of H2O-3.",
        u"",
        u"You are running H2O-3 OSS: built for experimentation and",
        u"research, not production.",
        u"",
        u"You must upgrade to H2O-3 Enterprise for:",
        u"  - Multi-node production deployment (Hadoop, Spark, Kubernetes)",
        u"  - Audit-ready governance (SOC 2, ISO 27001, ISO 42001)",
        u"  - Prioritized CVE patching and long-term security maintenance",
        u"  - Premium support with SLAs",
        u"",
        u"H2O-3 Enterprise is a drop-in replacement for H2O-3 OSS -",
        u"your existing code, APIs, and pipelines run unchanged.",
        u"",
        u"Learn more: h2o.ai/h2o-3/oss-vs-enterprise",
        u"Contact:    " + ENTERPRISE_EMAIL,
    ]) + u"\n")
    sys.stdout.flush()
    raise H2OValueError(operation + " requires H2O-3 Enterprise. Contact " + ENTERPRISE_EMAIL)
