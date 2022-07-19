# -*- encoding: utf-8 -*-
"""
h2o -- module for using H2O services.

:copyright: (c) 2016 H2O.ai
:license:   Apache License Version 2.0 (see LICENSE for details)
"""
from __future__ import absolute_import, division, print_function, unicode_literals

from contextlib import contextmanager
import os
import sys

try:
    from StringIO import StringIO  # py2 (first as py2 also has io.StringIO, but only with unicode support)
except:
    from io import StringIO  # py3

import tabulate

# noinspection PyUnresolvedReferences
from .utils.compatibility import *  # NOQA
from .utils.compatibility import str2 as str, bytes2 as bytes
from .utils.shared_utils import can_use_pandas
from .utils.threading import local_context, local_context_safe, local_env

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


_repr_formats = [None, 'plain', 'pretty', 'html']


@contextmanager
def _repr_format(fmt=None, force=False):
    assert fmt in _repr_formats, "Unsupported format '%s', `fmt` should be one of %s" % (fmt, _repr_formats)
    with local_context_safe('repr_format', fmt, force) as lc:
        yield lc


_repr_verbosity_levels = [None, 'short', 'medium', 'full']


@contextmanager
def _repr_verbosity(verbosity=None, force=False):
    assert verbosity in _repr_verbosity_levels, "Unsupported verbosity '%s', `verbosity` should be one of %s" % (verbosity, _repr_verbosity_levels)
    with local_context_safe('repr_verbosity', verbosity, force) as lc:
        yield lc
   
           
def _get_repr_format(default=None):
    return local_env('repr_format', default)


def _get_repr_verbosity(default=None):
    return local_env('repr_verbosity', default)


def in_py_repl():
    """test if we are in Python REPL: 
    not perfect, doesn't handle the case where a script is executed from the REPL 
    (in which case the script is interpreted as running instructions in the REPL 
    which shouldn't harm regarding display behaviour)
    so, use wisely.
    """
    import inspect
    interpreter_frame = inspect.stack()[-1]
    is_py = interpreter_frame[1] == '<stdin>'  # use index `[1]` instead of `.filename` attribute to also work in Py2.7
    return is_py


def in_ipy():
    """test if we are in iPython runner."""
    return get_builtin('__IPYTHON__')


def in_zep():
    """test if we are in Zeppelin runner."""
    return in_ipy() and "ZEPPELIN_RUNNER" in os.environ


class ReplHook:

    def __init__(self):
        self.ori_displayhook = None

    def __enter__(self):
        self.ori_displayhook = sys.displayhook
        sys.displayhook = self.displayhook

    def __exit__(self, *args):
        if self.ori_displayhook is not None:
            sys.displayhook = self.ori_displayhook
            
    def displayhook(self, value):
        if local_env('repl_hook', True):
            if value is None:
                return
            set_builtin('_', None)
            if hasattr(value, '_repr_repl_'):
                s = value._repr_repl_()
            else:
                s = repr(value)
            print2(s)
            set_builtin('_', value)
        else:
            assert self.ori_displayhook is not None
            self.ori_displayhook(value)


_user_tips_on_ = True


@contextmanager
def user_tips_enabled(on=True):
    global _user_tips_on_
    ut = _user_tips_on_
    try:
        _user_tips_on_=on
        yield
    finally:
        _user_tips_on_=ut
        

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
    tips_section = """

[tips]
{tips}
--
Use `h2o.display.toggle_user_tips()` to switch on/off this section.""".format(tips=tips) if _user_tips_on_ else ""
    html_wrap = '<pre style="font-size: smaller; margin: 1em 0 0 0;">{tips}</pre>'
    return html_wrap.format(tips=tips_section) if (tips and fmt == 'html') else tips_section


def _display(obj, fmt=None, verbosity=None):
    with _repr_format(fmt), _repr_verbosity(verbosity):
        if isinstance(obj, str_type) and fmt == 'plain':
            obj = repr(obj)  # keep the string quoted in plain format
        try:
            print2(obj)
        except UnicodeEncodeError:
            print2(str(obj))  # compatibility.str2 will handle the encoding issue


def display(obj, fmt=None, verbosity=None):
    """
    Render the given object using the provided format and verbosity level (if supported).
    :param obj: 
    :param fmt: one of (None, 'plain', 'pretty', 'html').
                Defaults to None (picks appropriate format depending on platform/context).
    :param verbosity: one of (None, 'short', 'medium', 'full').
                Defaults to None (object's default verbosity).
    """
    if in_zep():  # prioritize as Py in Zep uses iPy 
        if fmt in [None, 'html']:  # default rendering to 'html' in zep
            with _repr_format('html'), _repr_verbosity(verbosity):
                print2("%html {}".format(obj))
        else:
            with _repr_format(fmt), _repr_verbosity(verbosity):
                try:
                    global z  # variable provided by Zeppelin, use of `global` just to get rid of error in IDE
                    z.show(obj)
                except NameError:
                    _display(obj, fmt, verbosity=verbosity)
    elif in_ipy():
        from IPython.display import HTML, display as idisplay
        if fmt is None:  # by default, let the iPy mechanism decide on the format
            with _repr_verbosity(verbosity):
                idisplay(obj)
        elif fmt == 'html':
            with _repr_format(fmt), _repr_verbosity(verbosity):
                idisplay(HTML(str(obj)))
        else:
            _display(obj, fmt, verbosity=verbosity)
    else:
        _display(obj, fmt, verbosity=verbosity)
                

def to_str(obj, verbosity=None, fmt=None):
    """
    :param obj: 
    :param verbosity: one of (None, 'short', 'medium', 'full').
                Defaults to None (object's default verbosity).
    :param fmt: one of (None, 'plain', 'pretty', 'html').
                Defaults to None (picks appropriate format depending on platform/context).
    :return: a string representation of the given object using the provided format and verbosity level (if supported).
    """
    with _repr_format(fmt, force=True), _repr_verbosity(verbosity, force=True):
        return str(obj)
   
    
def to_pretty_str(obj, verbosity=None):
    """
    :param obj: 
    :param verbosity: one of (None, 'short', 'medium', 'full').
                Defaults to None (object's default verbosity).
    :return: a pretty string representation of the given object using the provided verbosity level (if supported).
    """
    return to_str(obj, verbosity=verbosity, fmt='pretty')


def to_html(obj, verbosity=None):
    """
    :param obj: 
    :param verbosity: one of (None, 'short', 'medium', 'full').
                Defaults to None (object's default verbosity).
    :return: a html string representation of the given object using the provided verbosity level (if supported).
    """
    return to_str(obj, verbosity=verbosity, fmt='html')


def _auto_html_element_wrapper(it, pre=None, nex=None):
    """
    To avoid consuming code of `format_to_html` having to deal with proper formatting,
    this default html wrapper tries to be clever by:
    - wrapping consecutive string items into a single <pre> element.
    - wrapping complex objects into a <div> bloc.
    - ensuring there is a collapsable fixed margin between all elements.
    """
    if isinstance(it, str_type):
        before, after = "", ""
        if not isinstance(pre, str_type):
            before = "<pre style='margin: 1em 0 1em 0;'>"
        if not isinstance(nex, str_type):
            after = "</pre>"
    else:
        before, after = "<div style='margin: 1em 0 1em 0;'>", "</div>"
    return before, after


def _auto_end_of_line(it, nex=None):
    """
    To avoid consuming code of `format_to_multiline` having to deal with proper formatting,
    this default end-of-line generator tries to be clever by:
     - adding a new line character between items by default,
     - adding an additional new line between "blocs/objects" for clarity: every item that is not a simple string is considered as a bloc.
    """
    sep = "\n"
    if nex is None:
        sep = ""
    elif not isinstance(it, str_type) or not isinstance(nex, str_type):
        sep += "\n"  # 2 lines sep between "blocks" 
    return sep


def format_to_html(objs, element_wrapper='auto'):
    """
    :param objs: 
    :param element_wrapper: a html tag name, or a tuple containing
    :return an HTML representation of objs
    """
    items = [objs] if isinstance(objs, str_type) else list(objs)

    wrap_tags_gen = None
    if element_wrapper in [None, 'auto']:
        wrap_tags_gen = _auto_html_element_wrapper
    elif isinstance(element_wrapper, tuple):
        wrap_tags_gen = lambda it, p, n: element_wrapper
    elif isinstance(element_wrapper, str_type):
        _before, _after = "<{}>".format(element_wrapper), "</{}>".format(element_wrapper)
        wrap_tags_gen = lambda it, p, n: (_before, _after)
    else:
        assert callable(element_wrapper)
        wrap_tags_gen = element_wrapper

    def _make_elem(it, idx):
        pre, nex = items[idx-1] if idx > 0 else None, items[idx+1] if idx < len(items)-1 else None
        before, after = wrap_tags_gen(it, pre, nex)
        return "".join([before, str(it), after])

    with _repr_format('html'):
        return "\n".join(_make_elem(it, i) for i, it in enumerate(items))


def format_to_multiline(objs, end_of_line='auto'):
    items = [objs] if isinstance(objs, str_type) else list(objs)
    eol_gen = None
    if end_of_line in [None, 'auto']:
        eol_gen = _auto_end_of_line
    elif isinstance(end_of_line, str_type):
        eol_gen = lambda it, n: end_of_line
    else:
        assert callable(end_of_line)
        eol_gen = end_of_line

    def _make_line(it, idx):
        nex = items[idx+1] if idx < len(items)-1 else None
        eol = eol_gen(it, nex)
        return str(it)+eol
    
    return "".join(_make_line(it, i) for i, it in enumerate(items))


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
        repr_fmt = _get_repr_format()
        repr_verb = _get_repr_verbosity()
        s = None
        if repr_fmt == 'html':
            s = self._str_html_(verbosity=repr_verb)
        elif repr_fmt == 'pretty':
            s = self._str_pretty_(verbosity=repr_verb)
        else:
            s = self._str_(verbosity=repr_verb)
        if PY2:  # in Py2, this must return a byte array, otherwise print() fails
            return bytes(s)
        return s
    
    def __unicode__(self):
        """for Py2 compatibility"""
        return str(self.__str__())  # calling str2 ensures proper encoding

    def _repr_(self):
        """Override this method to change the technical string representation."""
        return repr_def(self)

    def _repr_pretty_(self, p, cycle):  # ipy
        """
        IPython hook called when printing the cell result (iPy repl/fallback in Jupyter).
        Please don't override this, override `_str_pretty_()` instead.
        """
        with _repr_format('pretty'):
            if cycle:
                p.text("{}(...)".format(self.__class__.__name__))
            else:
                p.text(str(self))

    def _repr_html_(self):  # ipy in jupyter
        """
        IPython hook called when printing the cell result in Jupyter.
        Please don't override this, override `_str_html_()` instead.
        """
        with _repr_format('html'):
            return str(self)

    def _repr_repl_(self):  # py repl
        """
        H2O hook called when printing the result in Python REPL.
        This hook is triggered only if `ReplHook` is activated, and we activate it only when using default Python REPL.
        Please don't override this, override `_str_pretty_()` instead.
        """
        with _repr_format('pretty'):
            return str(self)

    def _str_(self, verbosity=None):
        """Override this method to return the informal string representation."""
        return repr(self)

    def _str_html_(self, verbosity=None):
        """Override this method to return a string description in html format."""
        return self._str_(verbosity=verbosity)

    def _str_pretty_(self, verbosity=None):
        """Override this method to return a pretty string description used by default as repl output."""
        return self._str_(verbosity=verbosity)


class H2ODisplay(DisplayMixin):
    """
    A convenient mixin for H2O classes, providing standard methods for formatting and rendering.
    """

    def show(self, verbosity=None, fmt=None):
        """
        Describe and renders the current object in the given format and verbosity level if supported,
        by default guessing the best format for the current environment.
        
        :param verbosity: one of (None, 'short', 'medium', 'full').
                    Defaults to None (object's default verbosity).
        :param fmt: one of (None, 'plain', 'pretty', 'html').
                    Defaults to None (picks appropriate format depending on platform/context).
        """
        display(self, fmt=fmt, verbosity=verbosity)
        
    def to_html(self, verbosity=None):
        """
        :param verbosity: one of (None, 'short', 'medium', 'full').
                    Defaults to None (object's default verbosity).
        :return: a html representation of the current object.
        """
        return to_html(self, verbosity)
    
    def to_pretty_str(self, verbosity=None):
        """
        :param verbosity: one of (None, 'short', 'medium', 'full').
                    Defaults to None (object's default verbosity).
        :return: a pretty string representation of the current object.
        """
        return to_pretty_str(self, verbosity)
    
    def to_str(self, verbosity=None, fmt=None):
        """
        :param verbosity: one of (None, 'short', 'medium', 'full').
                    Defaults to None (object's default verbosity).
        :param fmt: one of (None, 'plain', 'pretty', 'html').
                    Defaults to None/'plain'.
        :return: a string representation of the current object.
        """
        return to_str(self, verbosity=verbosity, fmt=fmt)


class H2OStringDisplay(H2ODisplay):
    """
    Wrapper ensuring that the given string is rendered consistently in unique format for all environments.
    
    """
    
    def __init__(self, s):
        self._s = s
        
    def _repr_(self):
        return self._s
    

class H2ODisplayWrapper(H2ODisplay):
    """
    Wraps a function returning a string into a displayable object 
    that will call the function with the requested format, depending on the environment.
    """

    def __init__(self, repr_fn):
        """
        :param repr_fn: the wrapped representation function with signature: (verbosity: Optional[str], fmt: Optional[str]) -> str
        See ``display`` function for the list supported formats. 
        """
        self._repr_fn = repr_fn

    def _str_(self, verbosity=None):
        return self._repr_fn(verbosity)

    def _str_pretty_(self, verbosity=None):
        return self._repr_fn(verbosity, 'pretty')

    def _str_html_(self, verbosity=None):
        return self._repr_fn(verbosity, 'html')


class H2OItemsDisplay(H2ODisplay):

    def __init__(self, items):
        """
        :param items: a list of items to be rendered 
        """
        self._items = items

    def _str_(self, verbosity=None):
        return format_to_multiline(self._items)

    def _str_html_(self, verbosity=None):
        return format_to_html(self._items)


class H2OTableDisplay(H2ODisplay):
    
    THOUSANDS = "{:,}"
    
    _prefer_pandas = False
    __html_table_counter = 0
    
    @staticmethod
    def gen_html_table_id():
        H2OTableDisplay.__html_table_counter +=1
        return "h2o-table-%s" % H2OTableDisplay.__html_table_counter
    
    @staticmethod
    def fixup_table_repr(table_repr, fmt=None):
        """
        A fix-up function to improve table rendering in some environments.
        
        :param str table_repr: the string representation of the table, to which the fix-up can be applied.
        :param fmt: the format of this representation.
        :return: the table representation (as string) with the potential fix-up applied if needed.
        """
        if fmt == 'html' and in_ipy():
            # Applying `class='dataframe'` greatly improves rendering in JetBrain's tools,
            # but unfortunately there doesn't seem to be any way to detect DataSpell or PyCharm, 
            # this would allow us to apply this more specifically.
            return table_repr.replace("<table>", "<table class='dataframe'>")
        return table_repr
    
    @staticmethod
    def toggle_pandas_rendering(on=None):
        """
        Globally toggles usage of pandas for rendering of frames and tables in H2O.
        :param on: True or False to force or prevent usage of pandas for all display.
        """
        if on is None:
            H2OTableDisplay._prefer_pandas = not H2OTableDisplay._prefer_pandas
        else:
            H2OTableDisplay._prefer_pandas = on

    @staticmethod
    @contextmanager
    def pandas_rendering_enabled(value=True):
        """
        Context manager used to temporarily prefer (ie. use pandas if available) 
        or disable usage of pandas for H2OTableDisplay rendering.
        
        Usage recommended mainly for tests or other single threaded usages: this is not thread-safe!
        :param bool value: True iff pandas should be preferred.
        """
        pp = H2OTableDisplay._prefer_pandas
        try:
            H2OTableDisplay._prefer_pandas = value
            yield
        finally:
            H2OTableDisplay._prefer_pandas = pp

    @staticmethod
    def use_pandas():
        """
        :return: True iff pandas will be used for rendering.
        """
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
                 columns_labels=None, rows=-1,
                 prefer_pandas=None,
                 **kwargs):
        self._table = table
        self._caption = caption
        self._columns_labels = columns_labels
        self._max_rows = rows
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
    
    def show(self, verbosity=None, fmt=None):
        super().show(verbosity=verbosity, fmt=fmt)
        if self.truncated:
            print("(Use rows=-1 to render the whole table)")
        
    def _prepare(self, prefer_pandas=None):
        if prefer_pandas or (prefer_pandas is None and H2OTableDisplay.use_pandas()):
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

    def _str_(self, verbosity=None):
        table = self._display_table
        table_str = (table.to_string() if H2OTableDisplay.is_pandas(table)
                     else tabulate.tabulate(table,
                                            headers=self._columns_labels or (),
                                            **self._kwargs))
        table_str = format_to_multiline([self._caption, table_str]) if self._caption else table_str
        return table_str+H2OTableDisplay.table_footer(self) if self.truncated else table_str

    def _str_html_(self, verbosity=None):
        table = self._display_table
        if H2OTableDisplay.is_pandas(table):
            
            html = (table.style.set_caption(self._caption)
                               .set_table_styles([dict(selector="",
                                                       props=[("margin-top", "1em"),
                                                              ("margin-bottom", "1em")]),
                                                  dict(selector="caption",
                                                       props=[("font-size", "larger"),
                                                              ("text-align", "left"),
                                                              ("white-space", "nowrap")])])
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
<div id="{id}" class="h2o-container">
  <table class="h2o-table">
    <caption>{caption}</caption>
    <thead>{head}</thead>
    <tbody>{body}</tbody>
  </table>
</div>
"""
        css = """
#id.h2o-container {
  overflow-x: auto;
}
#id .h2o-table {
  /* width: 100%; */
  margin-top: 1em;
  margin-bottom: 1em;
}
#id .h2o-table caption {
  white-space: nowrap;
  caption-side: top;
  text-align: left;
  /* margin-left: 1em; */
  margin: 0;
  font-size: larger;
}
#id .h2o-table thead {
  white-space: nowrap; 
  position: sticky;
  top: 0;
  box-shadow: 0 -1px inset;
}
#id .h2o-table tbody {
  overflow: auto;
}
#id .h2o-table th,
#id .h2o-table td {
  text-align: right;
  /* border: 1px solid; */
}
#id .h2o-table tr:nth-child(even) {
  /* background: #F5F5F5 */
}
"""
        head_trs = []
        if column_labels is not None:
            head_trs.append(H2OTableDisplay._html_row(column_labels, header=True))
        body_trs = []
        for row in rows:
            body_trs.append(H2OTableDisplay._html_row(row))
        table_id = H2OTableDisplay.gen_html_table_id()
        return html.format(id=table_id,
                           caption=caption or "",
                           head="\n".join(head_trs), 
                           body="\n".join(body_trs),
                           css=css.replace("#id", "#"+table_id))

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
            plain="\n[{nrows} x {ncols}]\n",
            pretty="\n[{nrows} x {ncols}]\n",
            html="<pre style='font-size: smaller; margin-bottom: 1em;'>[{nrows} x {ncols}]</pre>"
        ).get(fmt or 'plain')
        return template.format(nrows=nrows, ncols=ncols)


@contextmanager
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


def print2(*msgs, **kwargs):
    """
    This function exists here ONLY because Sphinx.ext.autodoc gets into a bad state when seeing the print()
    function. When in that state, autodoc doesn't display any errors or warnings, but instead completely
    ignores the "bysource" member-order option.
    """
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
