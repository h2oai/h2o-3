# _workarounds.py

import io
import csv
import warnings

from ._common import PY2

ESCAPECHAR = '\\'


def issue12178(escapechar=ESCAPECHAR):
    with (io.BytesIO() if PY2 else io.StringIO(newline='')) as stream:
        csv.writer(stream, escapechar=escapechar).writerow([escapechar])
        line = stream.getvalue()
    return (escapechar * 2) not in line


def issue31590(line='spam%s\neggs,spam\r\n' % ESCAPECHAR, escapechar=ESCAPECHAR):
    with (io.BytesIO(line) if PY2 else io.StringIO(line, newline='')) as stream:
        reader = csv.reader(stream, quoting=csv.QUOTE_NONE, escapechar=escapechar)
        row = next(reader)
    return len(row) != 2


def has_issue12178(dialect, affected=issue12178()):
    return affected and dialect.escapechar and dialect.quoting != csv.QUOTE_NONE


def has_issue31590(dialect, affected=issue31590()):
    return affected and dialect.escapechar and dialect.quoting == csv.QUOTE_NONE


def warn_if_issue31590(reader):
    if has_issue31590(reader.dialect):
        warnings.warn('%r cannot parse embedded newlines correctly, '
                      'see https://bugs.python.org/issue31590'
                      'for details' % reader)
