# readers.py - re/decoding csv.reader wrappers, convenience context manager

from __future__ import unicode_literals

import csv

__all__ = [
    'reader', 'DictReader',
    'UnicodeTextReader', 'UnicodeBytesReader',
]

from ._common import PY2, ENCODING, DIALECT
from ._common import none_encoding, is_8bit_clean, csv_args
from ._dispatch import register_reader
from ._workarounds import warn_if_issue31590


def reader(stream, dialect=DIALECT, encoding=False, **fmtparams):
    r"""CSV reader yielding lists of ``unicode`` strings (PY3: ``str``).

    Args:
        stream: Iterable of text (``unicode``, PY3: ``str``) lines. If an
            ``encoding`` is given, iterable of encoded (``str``, PY3: ``bytes``)
            lines in the given (8-bit clean) ``encoding``.
        dialect: Dialect argument for the underlying :func:`py:csv.reader`.
        encoding: If not ``False`` (default): name of the encoding needed to
            decode the encoded (``str``, PY3: ``bytes``) lines from ``stream``.
        \**fmtparams: Keyword arguments (formatting parameters) for the
            underlying :func:`py:csv.reader`.

    Returns:
        A Python 3 :func:`py3:csv.reader` stand-in yielding a list of ``unicode`` strings
        (PY3: ``str``) for each row.

    >>> import io
    >>> text = u'Spam!,Spam!,Spam!\r\nSpam!,Lovely Spam!,Lovely Spam!\r\n'
    >>> with io.StringIO(text, newline='') as f:
    ...     for row in reader(f):
    ...         print(', '.join(row))
    Spam!, Spam!, Spam!
    Spam!, Lovely Spam!, Lovely Spam!

    Raises:
        NotImplementedError: If ``encoding`` is not 8-bit clean.
    """
    if encoding is False:
        return UnicodeTextReader(stream, dialect, **fmtparams)
    if encoding is None:
        encoding = none_encoding()
    if not is_8bit_clean(encoding):
        raise NotImplementedError
    return UnicodeBytesReader(stream, dialect, encoding, **fmtparams)


@register_reader('dict', 'bytes', 'text')
class DictReader(csv.DictReader):
    """:func:`csv23.reader` yielding dicts of ``unicode`` strings (PY3: ``str``)."""

    def __init__(self, f, fieldnames=None, restkey=None, restval=None, dialect=DIALECT, encoding=False, **kwds):
        # NOTE: csv.DictReader is an old-style class on PY2
        csv.DictReader.__init__(self, [], fieldnames, restkey, restval)
        self.reader = reader(f, dialect, encoding, **kwds)


class Reader(object):
    """Proxy for csv.reader."""

    def __init__(self, stream, dialect=DIALECT, **kwargs):
        self._reader = csv.reader(stream, dialect, **kwargs)
        warn_if_issue31590(self._reader)

    def __iter__(self):
        return self

    @property
    def dialect(self):
        return self._reader.dialect

    @property
    def line_num(self):
        return self._reader.line_num


class UnicodeReader(Reader):
    """CSV reader yielding lists of ``unicode`` strings (PY3: ``str``)."""

    if PY2:
        def __init__(self, stream, dialect=DIALECT, **kwargs):
            kwargs = csv_args(kwargs)
            super(UnicodeReader, self).__init__(stream, dialect, **kwargs)

        def next(self):
            return map(self._decode, self._reader.next())

    else:
        def __next__(self):
            return next(self._reader)


if PY2:
    @register_reader('list', 'text')
    class UnicodeTextReader(UnicodeReader):
        """Unicode CSV reader for iterables of text (``unicode``) lines."""

        def __init__(self, stream, dialect=DIALECT, **kwargs):
            bytes_stream = (line.encode('utf-8') for line in stream)
            super(UnicodeTextReader, self).__init__(bytes_stream, dialect, **kwargs)
            self._decode = lambda s: unicode(s, 'utf-8')


    @register_reader('list', 'bytes')
    class UnicodeBytesReader(UnicodeReader):
        """Unicode CSV reader for iterables of 8-bit clean encoded (``str``) lines."""

        def __init__(self, stream, dialect=DIALECT, encoding=ENCODING, **kwargs):
            super(UnicodeBytesReader, self).__init__(stream, dialect, **kwargs)
            self._decode = lambda s: unicode(s, encoding)

else:
    #: Unicode CSV reader for iterables of text (``str``) lines.
    UnicodeTextReader = csv.reader
    register_reader('list', 'text')(UnicodeTextReader)


    @register_reader('list', 'bytes')
    class UnicodeBytesReader(UnicodeReader):
        """Unicode CSV reader for iterables of 8-bit clean encoded (``bytes``) lines."""

        def __init__(self, stream, dialect=DIALECT, encoding=ENCODING, **kwargs):
            text_stream = (str(line, encoding) for line in stream)
            super(UnicodeBytesReader, self).__init__(text_stream, dialect, **kwargs)
