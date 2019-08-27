# dialects.py - backport unix_dialect

from __future__ import unicode_literals

import csv

from ._common import PY2

__all__ = ['unix_dialect']

if PY2:
    def register(name):
        def decorate(cls):
            csv.register_dialect(name, cls)
            return cls

        return decorate


    @register(b'unix')
    class unix_dialect(csv.Dialect):
        """Describe the usual properties of Unix-generated CSV files."""

        delimiter = b','
        quotechar = b'"'
        doublequote = True
        skipinitialspace = False
        lineterminator = b'\n'
        quoting = csv.QUOTE_ALL


else:
    unix_dialect = csv.unix_dialect
