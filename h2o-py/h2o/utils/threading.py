from __future__ import absolute_import

import contextlib
import threading

_THREAD_CONTEXT = '_h2o_context_'


@contextlib.contextmanager
def thread_context(**kwargs):
    ct = threading.current_thread()
    if not hasattr(ct, _THREAD_CONTEXT):
        setattr(ct, _THREAD_CONTEXT, {})
    context = getattr(ct, _THREAD_CONTEXT)
    to_restore = {}
    try:
        for k, v in kwargs.items():
            if k in context:
                to_restore[k] = context[k]
            context[k] = v
        yield
    finally:
        for k, v in kwargs.items():
            if k in to_restore:
                context[k] = to_restore[k]
            else:
                del context[k]


def thread_env(key, default=None):
    ct = threading.current_thread()
    storage = getattr(ct, _THREAD_CONTEXT, {})
    return storage.get(key, default)
