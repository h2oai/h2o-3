# -*- encoding: utf-8 -*-
"""
h2o -- module for using H2O services.

:copyright: (c) 2016 H2O.ai
:license:   Apache License Version 2.0 (see LICENSE for details)
"""
from __future__ import absolute_import, division, print_function, unicode_literals

import contextlib
import os
import sys

try:
    from StringIO import StringIO  # py2 (first as py2 also has io.StringIO, but only with unicode support)
except:
    from io import StringIO  # py3

import tabulate

# noinspection PyUnresolvedReferences
from .utils.compatibility import *  # NOQA
from .utils.shared_utils import can_use_pandas
from .utils.threading import local_context, local_env

__no_export = set(dir())  # all variables defined above this are not exported


def _attributes(obj, filtr='all'):
    attrs = vars(obj)
    if filtr is None or filtr == 'all':
        return attrs
    elif filtr == 'public':
        return {k: v for k, v in attrs.items() if not k.startswith('_')}
    elif filtr == 'private':
        return {k: v for k, v in attrs.items() if k.startswith('_')}
    elif isinstance(filtr, list):
        return {k: v for k, v in attrs.items() if k in filtr}
    else:
        assert callable(filtr)
        return {k: v for k, v in attrs.items() if filtr(k)}


def _classname(obj):
    return type(obj).__name__


def repr_def(obj, attributes='public'):
    return "{cls}({attrs!r})".format(
        cls=_classname(obj),
        attrs=_attributes(obj, attributes)
    )


@contextlib.contextmanager
def repr_context(ctxt=None, force=False):
    if ctxt is not None and (force or local_env('repr') is None):
        with local_context(repr=ctxt):
            yield local_env('repr') 
    else:
        yield local_env('repr')


def in_ipy():  # are we in ipy? then pretty print tables with _repr_html
    return get_builtin('__IPYTHON__') is not None


def in_zep():  # are we in zeppelin? then use zeppelin pretty print support
    return in_ipy() and "ZEPPELIN_RUNNER" in os.environ


@contextlib.contextmanager
def capture_output(out=None, err=None):
    tmp_out = out or StringIO()
    tmp_err = err or StringIO()
    ori_out = sys.stdout
    ori_err = sys.stderr
    try:
        sys.stdout = tmp_out
        sys.stderr = tmp_err
        yield tmp_out, tmp_err
    finally:
        sys.stdout = ori_out
        sys.stderr = ori_err


class ReplHook:

    def __init__(self):
        self.ori_displayhook = None

    def __enter__(self):
        self.ori_displayhook = sys.displayhook
        sys.displayhook = repl_displayhook

    def __exit__(self, *args):
        if self.ori_displayhook is not None:
            sys.displayhook = self.ori_displayhook


def repl_displayhook(value):
    if value is None:
        return
    set_builtin('_', None)
    if hasattr(value, '_repr_repl_'):
        s = value._repr_repl_()
    else:
        s = repr(value)
    print(s)
    set_builtin('_', value)


_user_tips_on_ = True


def toggle_user_tips(on=None):
    """
    When used with no param, toggles the display state for user tips.
    :param on: if set to True or False, enforce the display state for user tips.
    """
    global _user_tips_on_
    if on is None:
        _user_tips_on_ = not _user_tips_on_
    else:
        _user_tips_on_ = on
        
        
def format_user_tips(tips, fmt=None):
    tips = """
[tips]
{tips}
--
Use `h2o.display.toggle_user_tips()` to switch on/off this section.
""".format(tips=tips) if _user_tips_on_ else ""
    return '<pre style="font-size: smaller; margin: 1em 0 0 0;">{tips}</pre>'.format(tips=tips) if (tips and fmt == 'html') else tips

def _display(obj, fmt=None):
    with repr_context(fmt):
        if isinstance(obj, str) and fmt == 'plain':
            obj = repr(obj)  # keep the string quoted in plain format
        try:
            print2(obj)
        except UnicodeEncodeError:
            print2(str(obj).encode("ascii", "replace"))

def display(obj, fmt=None):
    """
    Render the given object using the provided format.
    :param obj: 
    :param fmt: one of (None, 'auto', 'plain', 'pretty', 'html')
    """
    if fmt == 'auto':
        fmt = None
    if in_zep():  # prioritize as Py in Zep uses iPy 
        if fmt in [None, 'html']:  # default rendering to 'html' in zep
            with repr_context('html'):
                print2("%html {}".format(obj))
        else:
            with repr_context(fmt):
                try:
                    global z  # variable provided by Zeppelin, use of `global` just to get rid of error in IDE
                    z.show(obj)
                except NameError:
                    _display(obj, fmt)
    elif in_ipy():
        from IPython.display import HTML, display as idisplay
        if fmt is None:  # by default, let the iPy mechanism decide on the format
            idisplay(obj)
        elif fmt == 'html':
            with repr_context(fmt):
                idisplay(HTML(str(obj)))
        else:
            _display(obj, fmt)
    else:
        _display(obj, fmt)
                

def to_str(obj, fmt=None):
    with repr_context(fmt, force=True):
        return str(obj)
    
    
def to_pretty_str(obj):
    return to_str(obj, fmt='pretty')


def to_html(obj):
    return to_str(obj, fmt='html')


def _auto_html_element_wrapper(it, pre=None, nex=None):
    if isinstance(it, str):
        before, after = "", ""
        if not isinstance(pre, str):
            before = "<pre style='margin: 1em 0 1em 0;'>"
        if not isinstance(nex, str):
            after = "</pre>"
    else:
        before, after = "<div>", "</div>"
    return before, after


def format_to_html(objs, element_wrapper='auto'):
    """
    :param objs: 
    :param element_wrapper: a html tag name, or a tuple containing
    :return an HTML representation of objs
    """
    items = [objs] if isinstance(objs, str) else list(objs)

    wrap_tags_gen = None
    if element_wrapper == 'auto':
        wrap_tags_gen = _auto_html_element_wrapper
    elif isinstance(element_wrapper, tuple):
        wrap_tags_gen = lambda it, p, n: element_wrapper
    elif isinstance(element_wrapper, str):
        _before, _after = "<{}>".format(element_wrapper), "</{}>".format(element_wrapper)
        wrap_tags_gen = lambda it, p, n: (_before, _after)
    else:
        assert callable(element_wrapper)
        wrap_tags_gen = element_wrapper

    def _make_elem(e, idx):
        pre, nex = items[idx-1] if idx > 0 else None, items[idx+1] if idx < len(items)-1 else None
        before, after = wrap_tags_gen(e, pre=pre, nex=nex)
        return "".join([before, str(e), after])

    with repr_context('html'):
        return "\n".join(_make_elem(it, i) for i, it in enumerate(items))


def format_to_multiline(objs, separator='\n'):
    return separator.join(str(o) for o in objs)


class DisplayMixin(object):
    """
    The core of the H2O framework for formatting and displaying objects in various environments.
    H2O objects that need to be rendered correctly in all circumstances need to have this mixin in their class hierarchy.
    """

    def __repr__(self):
        """
        Standard Py method returning the formal/technical string representation of the current object.
        
        In most cases, subclasses shouldn't need to extend this method.
        
        :return: the "official" representation of the current object.
        By default, this is a structural/technical representation, 
        not aimed at the end user but rather typically for debugging purposes.
        """
        return self._repr_()

    def __str__(self):
        """
        Standard Py method returning the informal, end-user targeted, string representation of the current object.
        
        In most cases, subclasses shouldn't need to extend this method but the more specific `_str_xxx` methods instead.
        This default implementation ensures that complex objects get printed correctly in all situations, 
        ensuring that nested objects are always represented according to the originally requested format.
        
        :return: the "informal" nicely printable representation of the current object.
        
        """
        repr_type = local_env('repr')
        if repr_type == 'html':
            return self._str_html_()
        elif repr_type in ['pretty', 'repl']:
            return self._str_pretty_()
        return self._str_()

    def _repr_(self):
        """Override this method to change the technical string representation."""
        return repr_def(self)

    def _repr_pretty_(self, p, cycle):  # ipy
        """
        IPython hook called when printing the cell result (iPy repl/fallback in Jupyter).
        Please don't override this, override `_str_pretty_()` instead.
        """
        with repr_context('pretty'):
            if cycle:
                p.text("{}(...)".format(self.__class__.__name__))
            else:
                p.text(str(self))

    def _repr_html_(self):  # ipy in jupyter
        """
        IPython hook called when printing the cell result in Jupyter.
        Please don't override this, override `_str_html_()` instead.
        """
        with repr_context('html'):
            return str(self)

    def _repr_repl_(self):  # py repl
        """
        H2O hook called when printing the result in Python REPL.
        Please don't override this, override `_str_repl_()` instead.
        """
        with repr_context('repl'):
            return str(self)

    def _str_(self):
        """Override this method to return the informal string representation."""
        return repr(self)

    def _str_html_(self):
        """Override this method to return a string description in html format."""
        return self._str_()

    def _str_pretty_(self):
        """Override this method to return a pretty string description used by default as repl output."""
        return self._str_()


class H2ODisplay(DisplayMixin):
    """
    A convenient mixin for H2O classes, providing standard methods for formatting and rendering.
    """

    def show(self, fmt=None):
        """
        Describe and renders the current object in the given format,
        by default guessing the best format for the current environment.
        
        :param fmt: one of (None, 'auto', 'plain', 'pretty', 'html').
                    Defaults to None/'auto'.
        """
        display(self, fmt=fmt)
        
    def to_html(self):
        """
        :return: a html representation of the current object.
        """
        return to_html(self)
    
    def to_pretty_str(self):
        """
        :return: a pretty string representation of the current object.
        """
        return to_pretty_str(self)
    
    def to_str(self, fmt=None):
        """
        :return: a string representation of the current object.
        :param fmt: one of (None, 'plain', 'pretty', 'html').
                    Defaults to None/'plain'.
        """
        return to_str(self, fmt=fmt)


class H2OStringDisplay(H2ODisplay):
    """
    Wrapper ensuring that the given string is rendered consistently in unique format for all environments.
    """
    
    def __init__(self, s):
        self._s = s
        
    def _str_(self):
        return self._s
    

class H2ODisplayWrapper(H2ODisplay):
    """
    Wraps a function returning a string into a displayable object 
    that will call the function with the requested format, depending on the environment.
    """

    def __init__(self, repr_fn):
        """
        :param repr_fn: the wrapped representation function with signature: (fmt: Optional[str]) -> str
        See ``display`` function for the list supported formats. 
        """
        self._repr_fn = repr_fn

    def _str_(self):
        return self._repr_fn()

    def _str_pretty_(self):
        return self._repr_fn(fmt='pretty')

    def _str_html_(self):
        return self._repr_fn(fmt='html')


class H2OItemsDisplay(H2ODisplay):

    def __init__(self, items):
        """
        :param items: a list of items to be rendered 
        """
        self._items = items

    def _str_(self):
        return format_to_multiline(self._items)

    def _str_html_(self):
        return format_to_html(self._items)


class H2OTableDisplay(H2ODisplay):
    
    THOUSANDS = "{:,}"
    
    _prefer_pandas = True

    @staticmethod
    def prefer_pandas():
        return H2OTableDisplay._prefer_pandas and can_use_pandas()

    @staticmethod
    def is_pandas(table):
        if can_use_pandas():
            import pandas as pd
            return isinstance(table, pd.DataFrame)
        return False
    
    @staticmethod
    def _shape(table):
        return (table.shape if H2OTableDisplay.is_pandas(table)
                else (len(table), len(table[0])))

    def __init__(self, table=None, caption=None,
                 columns_labels=None, max_rows=-1,
                 prefer_pandas=True,
                 **kwargs):
        self._table = table
        self._caption = caption
        self._columns_labels = columns_labels
        self._max_rows = max_rows
        self._kwargs = kwargs
        self._display_table = None
        self._truncated = False
        self._prepare(prefer_pandas=prefer_pandas)
        
    @property
    def shape(self):
        return H2OTableDisplay._shape(self._table)
    
    @property
    def shape_displayed(self):
        return H2OTableDisplay._shape(self._display_table)
    
    @property
    def truncated(self):
        return self._truncated or self.shape_displayed < self.shape
    
    def show(self, fmt=None):
        super().show()
        if self.truncated:
            print("(Use max_rows=-1 to render the whole table)")
        
    def _prepare(self, prefer_pandas=True):
        if prefer_pandas and H2OTableDisplay.prefer_pandas():
            import pandas as pd
            df = self._table if H2OTableDisplay.is_pandas(self._table) else pd.DataFrame(self._table, columns=self._columns_labels)
            self._display_table = df.head(self._max_rows) if self._max_rows > 0 else df
        else:
            # create a truncated view of the table, first/last rows
            nr, nc = self.shape
            if 0 < self._max_rows < nr:
                first = last = self._max_rows//2
                trunc_table = []
                trunc_table += [v for v in self._table[:first]]
                trunc_table.append(["---"] * nc)
                trunc_table += [v for v in self._table[(nr - last):]]
                self._display_table = trunc_table
                self._truncated = True  # due to the --- row, we can't trust the display_table shape 
            else:
                self._display_table = self._table

    def _str_(self):
        table = self._display_table
        return (table.to_string() if H2OTableDisplay.is_pandas(table)
                else tabulate.tabulate(table,
                                       headers=self._columns_labels or (),
                                       **self._kwargs))

    def _str_html_(self):
        table = self._display_table
        if H2OTableDisplay.is_pandas(table):
            html = (table.style.set_caption(self._caption)
                               .set_table_styles([dict(selector="caption", 
                                                       props=[("font-size", "larger"),
                                                              ("text-align", "left"),
                                                              ("white-space", "nowrap")]),
                                                  dict(selector="table",
                                                       props=[("margin-top", "1em"),
                                                              ("margin-bottom", "1em")])], 
                                                 overwrite=False)
                               .to_html())
        
        else:
            html = H2OTableDisplay._html_table(table, caption=self._caption, column_labels=self._columns_labels)
        return html+H2OTableDisplay.table_footer(self, fmt='html') if self.truncated else html
        
    # some html table builder helper things
    @staticmethod
    def _html_table(rows, caption=None, column_labels=None):
        html = """
<style>
{css}
</style>      
<div class="h2o-container">
  <table class="h2o-table">
    <caption>{caption}</caption>
    <thead>{head}</thead>
    <tbody>{body}</tbody>
  </table>
</div>
"""
        css = """
.h2o-container {
  overflow-x: auto;
}
.h2o-table {
  /* width: 100%; */
  margin-top: 1em;
  margin-bottom: 1em;
}
.h2o-table caption {
  white-space: nowrap;
  caption-side: top;
  text-align: left;
  /* margin-left: 1em; */
  margin: 0;
  font-size: larger;
}
.h2o-table thead {
  white-space: nowrap; 
  background-color: #A0A0A0;
  position: sticky;
  top: 0;
}
.h2o-table tbody {
  overflow: auto;
}
.h2o-table th,
.h2o-table td {
  text-align: right;
  /* border: 1px solid; */
}
"""
        head_trs = []
        if column_labels is not None:
            head_trs.append(H2OTableDisplay._html_row(column_labels, header=True))
        body_trs = []
        for row in rows:
            body_trs.append(H2OTableDisplay._html_row(row))
        return html.format(caption=caption or "",
                           head="\n".join(head_trs), 
                           body="\n".join(body_trs),
                           css=css)

    @staticmethod
    def _html_row(row, header=False):
        entry = "<tr>{}</tr>"
        cell = "<th>{}</th>" if header else "<td>{}</td>"
        # format full floating point numbers to 7 decimal places
        cells = "\n".join([cell.format(str(c))
                           if len(str(c)) < 10 or not _is_number(str(c))
                           else cell.format("{0:.7f}".format(float(str(c)))) for c in row])
        return entry.format(cells)

    @staticmethod
    def table_footer(table, fmt=None):
        nr, nc = table.shape
        nrows = "{nrow} row{s}".format(nrow=nr, s="" if nr == 1 else "s")
        ncols = "{ncol} column{s}".format(ncol=nc, s="" if nc == 1 else "s")
        template = dict(
            html="<pre style='font-size: smaller; margin-bottom: 1em;'>[{nrows} x {ncols}]</pre>"
        ).get(fmt, "\n[{nrows} x {ncols}]\n")
        return template.format(nrows=nrows, ncols=ncols)


def print2(*msgs, **kwargs):
    """
    This function exists here ONLY because Sphinx.ext.autodoc gets into a bad state when seeing the print()
    function. When in that state, autodoc doesn't display any errors or warnings, but instead completely
    ignores the "bysource" member-order option.
    """
    # if sys.gettrace() is not None:
    #     return
    file = kwargs.get('file', None)
    if file is None:
        file = local_env('stdout', sys.stdout)
        kwargs['file'] = file
    bi_print = get_builtin('print')
    if PY2:
        flush = kwargs.pop('flush', False)
        bi_print(*msgs, **kwargs)
        if flush:
            file.flush()
    else:
        bi_print(*msgs, **kwargs)
        
        
def _is_number(s):
    try:
        float(s)
        return True
    except ValueError:
        return False
    
    
__all__ = [s for s in dir() if not s.startswith('_') and s not in __no_export]
