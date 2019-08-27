# _dispatch.py

from __future__ import unicode_literals

import functools
import itertools

__all__ = ['register_reader', 'register_writer', 'get_reader', 'get_writer']

REGISTRY = {}

KIND = ('reader', 'writer')
ROWTYPE = ('list', 'dict', 'namedtuple')
LINETYPE = ('bytes', 'text')

KEYS = set(itertools.product(KIND, ROWTYPE, LINETYPE))


def register(kind, rowtype, linetype, second_linetype=None):
    keys = [(kind, rowtype, linetype)]
    if second_linetype is not None:
        keys.append((kind, rowtype, second_linetype))

    def decorate(cls):
        for k in keys:
            assert k in KEYS
            assert k not in REGISTRY
            REGISTRY[k] = cls
        return cls

    return decorate


register_reader, register_writer = (functools.partial(register, k) for k in KIND)


def get(kind, rowtype, linetype):
    key = kind, rowtype, linetype
    try:
        return REGISTRY[key]
    except (KeyError, TypeError):
        assert kind in KIND and linetype in LINETYPE
        raise ValueError('invalid/unsupported rowtype: %r' % rowtype)


get_reader, get_writer = (functools.partial(get, k) for k in KIND)
