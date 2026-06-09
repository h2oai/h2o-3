# -*- encoding: utf-8 -*-
"""
Product attribution for telemetry — hardcoded per repo at build time.

This is the OSS repo, so ``_PRODUCT`` is ``"h2o-3-oss"``. The Enterprise
build overrides this constant via its own build flavor (same path,
different value). It is deliberately NOT env-detected or runtime-discovered:
both the OSS and Enterprise wheels register the same ``h2o`` package name
and can co-exist in one venv, so the only unambiguous source of truth is
the repo the artifact was built FROM.
"""
_PRODUCT = "h2o-3-oss"
