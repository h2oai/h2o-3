from __future__ import absolute_import

import contextlib
import threading

_THREAD_CONTEXT = '_h2o_context_'


@contextlib.contextmanager
def thread_context(**kwargs):
    """
    Attach some key-value pairs to the current thread for the execution lifetime of the context block.
    Attached keys can then be retrieved with the function ``thread_env``.
    
    :param kwargs: key-value pairs to be attached to the thread.
    """
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
    """
    Look up and return the value associated with the given key in the thread context.
    
    :param key: the key to look up in the thread context.
    :param default: the default value of key was not found in the thread context (defaults to None).
    :return: the value for the given key attached to the thread context 
             or the default value if the key was not present in the thread context.
    """
    ct = threading.current_thread()
    storage = getattr(ct, _THREAD_CONTEXT, {})
    return storage.get(key, default)
