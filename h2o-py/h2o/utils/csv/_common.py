# _common.py

from __future__ import unicode_literals

import sys
import codecs
import locale

PY2 = sys.version_info.major == 2

ENCODING = 'utf-8'

DIALECT = 'excel'

ROWTYPE = 'list'

EIGHT_BIT_CLEAN = {
    'ascii',
    'cp437', 'cp720', 'cp737', 'cp775',
    'cp850', 'cp852', 'cp855', 'cp856', 'cp857', 'cp858',
    'cp860', 'cp861', 'cp862', 'cp863', 'cp864', 'cp865', 'cp866', 'cp869',
    'cp1006', 'cp1125',
    'cp1250', 'cp1251', 'cp1252', 'cp1253', 'cp1254', 'cp1255', 'cp1256', 'cp1257', 'cp1258',
    'cp65001',
    'iso8859-1', 'iso8859-2', 'iso8859-3', 'iso8859-4', 'iso8859-5', 'iso8859-6',
    'iso8859-7', 'iso8859-8', 'iso8859-9', 'iso8859-10', 'iso8859-11', 'iso8859-13',
    'iso8859-14', 'iso8859-15', 'iso8859-16',
    'mac-cyrillic', 'mac-greek', 'mac-iceland', 'mac-latin2', 'mac-roman', 'mac-turkish',
    'utf-8',
}

PY2_BYTEARGS = {'delimiter', 'lineterminator', 'quotechar', 'escapechar'}


def none_encoding():
    return locale.getpreferredencoding()


def is_8bit_clean(encoding):
    return codecs.lookup(encoding).name in EIGHT_BIT_CLEAN


if PY2:
    def csv_args(kwargs, _bytekeys=PY2_BYTEARGS):
        """Cast csv.reader/writer kwargs values from unicode to str."""
        for k in (kwargs.viewkeys() & _bytekeys):
            v = kwargs[k]
            if isinstance(v, unicode):
                kwargs[k] = str(v)
        return kwargs

else:
    def csv_args(kwargs):
        raise NotImplementedError


class lazyproperty(object):
    """Non-data descriptor caching the computed result as instance attribute.

    >>> import itertools
    >>> class Spam(object):
    ...     @lazyproperty
    ...     def eggs(self, _ints=itertools.count()):
    ...         return next(_ints)

    >>> spam = Spam(); (spam.eggs, spam.eggs)
    (0, 0)
    >>> spam.eggs = 'eggs'; str(spam.eggs)
    'eggs'
    >>> del spam.eggs; (spam.eggs, spam.eggs)
    (1, 1)
    >>> Spam().eggs
    2
    >>> Spam.eggs  # doctest: +ELLIPSIS
    <...lazyproperty object at 0x...>
    """

    def __init__(self, fget):
        self.fget = fget
        for attr in ('__module__', '__name__', '__doc__'):
            setattr(self, attr, getattr(fget, attr))

    def __get__(self, instance, owner):
        if instance is None:
            return self
        result = instance.__dict__[self.__name__] = self.fget(instance)
        return result
