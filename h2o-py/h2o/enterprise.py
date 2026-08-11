# -*- encoding: utf-8 -*-
"""
H2O-3 Secure messaging shown by the open-source Python client.

Single source for the "two paths, one product" notice (H2O-3 OSS vs. H2O-3
Secure) so the wording and the box style stay consistent across
``h2o.init()``, ``h2o.connect()`` and the MOJO entry points. Kept dependency-free
(only ``sys`` + ``h2o.exceptions``) to avoid import cycles.
"""
from __future__ import absolute_import, division, print_function, unicode_literals

import sys

from h2o.exceptions import H2OValueError

ENTERPRISE_EMAIL = "enterprise@h2o.ai"


def _box(lines):
    """Render the given lines inside an ASCII frame.

    Deliberately ASCII-only: this banner prints on every h2o.init(), and box-drawing
    characters raise UnicodeEncodeError when stdout cannot represent them (Windows with
    a redirected or piped stdout uses the ANSI code page). In block() the write happens
    before the raise, so that would mask the H2OValueError we actually want to surface.
    """
    width = max(len(s) for s in lines)
    rule = u"+" + u"-" * (width + 2) + u"+"
    body = [u"| " + s.ljust(width) + u" |" for s in lines]
    return u"\n".join([rule] + body + [rule])


def show_cluster_banner():
    """Print the OSS-vs-Secure notice shown by h2o.init() / h2o.connect()."""
    sys.stdout.write(u"\n" + _box([
        u"You are running the community edition of H2O-3 OSS.",
        u"",
        u"For commercial use, H2O-3 Secure is now recommended.",
        u"This includes production support, CVE fixes, multi-node scaling,",
        u"model artifact extraction, and more.",
        u"See h2o.ai/h2o-3/oss-vs-secure for additional details.",
        u"Contact " + ENTERPRISE_EMAIL + u" to upgrade.",
    ]) + u"\n")
    sys.stdout.flush()


def block(operation):
    """Print the H2O-3 Secure 'blocked' notice, then raise: MOJO is Secure-only."""
    sys.stdout.write(u"\n" + _box([
        u"H2O-3 SECURE REQUIRED  -  THIS ACTION IS BLOCKED",
        u"",
        operation + u" is a production capability available only in",
        u"H2O-3 Secure, the commercially supported tier of H2O-3.",
        u"",
        u"You are running self-managed H2O-3 OSS.",
        u"",
        u"You must upgrade to H2O-3 Secure for:",
        u"  - Hadoop and Kubernetes enterprise packages",
        u"  - Audit-supporting capabilities (SOC 2, ISO 27001, ISO 42001)",
        u"  - Commercial CVE patching & long-term support",
        u"  - Premium support with SLAs",
        u"",
        u"If you need MOJO, we provide a free license for non-commercial",
        u"use. See h2o.ai/h2o-3/oss-vs-secure to compare the two tiers.",
        u"",
        u"Request a license or upgrade:  " + ENTERPRISE_EMAIL,
    ]) + u"\n")
    sys.stdout.flush()
    raise H2OValueError(operation + " requires H2O-3 Secure. Contact " + ENTERPRISE_EMAIL)
