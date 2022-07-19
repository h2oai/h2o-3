from __future__ import absolute_import

from contextlib import contextmanager
import threading

try:
    # not ideal, but as we're going to add various keys to this local context,
    # then simply initialize it as a dictionary that we're going to manipulate.
    # This also allows a simplified shared interface (dict) for context vars and thread-locals.
    from contextvars import ContextVar, copy_context  # Py 3.7+
    local = ContextVar('h2o_context', default={})
    
    def _get_local(copy=True):
        loc = local.get()
        return loc.copy() if copy else loc
        
    def _set_local(loc):
        local.set(loc)
except ImportError:
    local = threading.local()
    local.context = {}
    
    def _get_local(copy=True):
        loc = local.context
        return loc.copy() if copy else loc
    
    def _set_local(loc):
        local.context = loc

__no_export = set(dir())  # all variables defined above this are not exported


@contextmanager
def local_context(**kwargs):
    """
    Attach some key-value pairs to a local context on the current thread for the execution lifetime of the context block.
    Attached keys can then be retrieved with the function ``local_env``.
    
    :param kwargs: key-value pairs to be attached to this context.
    """
    to_restore = {}
    try:
        context = _get_local()
        for k, v in kwargs.items():
            if k in context:
                to_restore[k] = context[k]
            context[k] = v
        _set_local(context)
        yield
    finally:
        context = _get_local()  # getting a new copy as the context could have been modified in async event loop.
        for k, v in kwargs.items():
            if k in to_restore:
                context[k] = to_restore[k]
            else:
                del context[k]
        _set_local(context)
        
        
@contextmanager
def local_context_safe(key=None, value=None, force=False):
    """
    sets the value on the local context key iff the value is not None and there's no value already set for that key 
    (overriding existing value must be requested explicitly by setting `force` param to True).
    :param str key:
    :param value:
    :param bool force: set to True to possibly override existing value on the local context.
    """
    if value is not None and (force or local_env(key) is None):
        with local_context(**{key: value}):
            yield local_env(key)
    else:
        yield local_env(key)


def local_env(key, default=None, use_default_if_none=False):
    """
    Look up and return the value associated with the given key in the thread context.
    
    :param key: the key to look up in the thread context.
    :param default: the default value of key was not found in the thread context (defaults to None).
    :param use_default_if_none: also return the default value if the key exists with a None value (defaults to False).
    :return: the value for the given key attached to the thread context 
             or the default value if the key was not present in the thread context.
    """
    context = _get_local(copy=False)
    value = context.get(key, default)
    return default if use_default_if_none and value is None else value


__all__ = [s for s in dir() if not s.startswith('_') and s not in __no_export]
