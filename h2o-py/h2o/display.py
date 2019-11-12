# -*- encoding: utf-8 -*-
"""
h2o -- module for using H2O services.

:copyright: (c) 2016 H2O.ai
:license:   Apache License Version 2.0 (see LICENSE for details)
"""
from __future__ import absolute_import, division, print_function, unicode_literals

import tabulate

from .utils.compatibility import *  # NOQA


class H2ODisplay(object):
    """
    Pretty printing for H2O Objects.

    Handles both IPython and vanilla console display
    """
    THOUSANDS = "{:,}"

    # True/False flag whether we are connected to a Jupyter notebook. Computed on the first use.
    _jupyter = None
    # True/False flag whether we are connected to a Zeppelin note. Computed on the first use.
    _zeppelin = None

    def __init__(self, table=None, header=None, table_header=None, is_pandas=False, **kwargs):
        self.table_header = table_header
        self.header = header
        self.table = table
        self.kwargs = kwargs
        self.do_print = True

        # one-shot display... never return an H2ODisplay object (or try not to)
        # if holding onto a display object, then may have odd printing behavior
        # the __repr__ and _repr_html_ methods will try to save you from many prints,
        # but just be WARNED that your mileage may vary!
        #
        # In other words, it's better to just new one of these when you're ready to print out.

        if self.table_header is not None:
            print()
            print(self.table_header + ":")
            print()
        if H2ODisplay._in_zep():
            if is_pandas:
                print(table)
            else:
                print("%html " + H2ODisplay._html_table(self.table, self.header))
            self.do_print = False
        elif H2ODisplay._in_ipy():
            from IPython.display import display
            display(table if is_pandas else self)
            self.do_print = False
        else:
            self.pprint()
            self.do_print = False

    # for Ipython
    def _repr_html_(self):
        if self.do_print:
            return H2ODisplay._html_table(self.table, self.header)

    def pprint(self):
        r = self.__repr__()
        print(r)

    # for python REPL console
    def __repr__(self):
        if self.do_print or not H2ODisplay._in_ipy():
            if self.header is None:
                return tabulate.tabulate(self.table, **self.kwargs)
            else:
                return tabulate.tabulate(self.table, headers=self.header, **self.kwargs)
        self.do_print = True
        return ""

    @staticmethod
    def prefer_pandas():
        return H2ODisplay._in_ipy() 

    @staticmethod
    def _in_ipy():  # are we in ipy? then pretty print tables with _repr_html
        if H2ODisplay._jupyter is None:
            try:
                import IPython
                from ipykernel.zmqshell import ZMQInteractiveShell
                ipy = IPython.get_ipython()
                H2ODisplay._jupyter = isinstance(ipy, ZMQInteractiveShell)
            except ImportError:
                H2ODisplay._jupyter = False
        return H2ODisplay._jupyter
        
    @staticmethod
    def _in_zep():  # are we in zeppelin? then use zeppelin pretty print support
        if H2ODisplay._zeppelin is None:
            import os
            H2ODisplay._zeppelin = H2ODisplay._in_ipy() and "ZEPPELIN_RUNNER" in os.environ
        return H2ODisplay._zeppelin

    # some html table builder helper things
    @staticmethod
    def _html_table(rows, header=None):
        # keep table in a div for scrollability
        table = "<div style=\"overflow:auto\"><table style=\"width:50%\">{}</table></div>"
        table_rows = []
        if header is not None:
            table_rows.append(H2ODisplay._html_row(header, bold=True))
        for row in rows:
            table_rows.append(H2ODisplay._html_row(row))
        return table.format("\n".join(table_rows))

    @staticmethod
    def _html_row(row, bold=False):
        res = "<tr>{}</tr>"
        entry = "<td><b>{}</b></td>" if bold else "<td>{}</td>"
        # format full floating point numbers to 7 decimal places
        entries = "\n".join([entry.format(str(r))
                             if len(str(r)) < 10 or not _is_number(str(r))
                             else entry.format("{0:.7f}".format(float(str(r)))) for r in row])
        return res.format(entries)


def _is_number(s):
    try:
        float(s)
        return True
    except ValueError:
        return False
