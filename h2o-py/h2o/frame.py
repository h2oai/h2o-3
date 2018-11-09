# -*- encoding: utf-8 -*-
"""
H2O data frame.

:copyright: (c) 2016 H2O.ai
:license:   Apache License Version 2.0 (see LICENSE for details)
"""
from __future__ import absolute_import, division, print_function, unicode_literals

import csv
import datetime
import functools
import os
import sys
import tempfile
import traceback
import warnings
from io import StringIO
from types import FunctionType

import requests
import math

import h2o
from h2o.display import H2ODisplay
from h2o.exceptions import H2OTypeError, H2OValueError
from h2o.expr import ExprNode
from h2o.group_by import GroupBy
from h2o.job import H2OJob
from h2o.utils.compatibility import *  # NOQA
from h2o.utils.compatibility import viewitems, viewvalues
from h2o.utils.config import get_config_value
from h2o.utils.shared_utils import (_handle_numpy_array, _handle_pandas_data_frame, _handle_python_dicts,
                                    _handle_python_lists, _is_list, _is_str_list, _py_tmp_key, _quoted,
                                    can_use_pandas, quote, normalize_slice, slice_is_normalized, check_frame_id)
from h2o.utils.typechecks import (assert_is_type, assert_satisfies, Enum, I, is_type, numeric, numpy_ndarray,
                                  numpy_datetime, pandas_dataframe, pandas_timestamp, scipy_sparse, U)

__all__ = ("H2OFrame", )


class H2OFrame(object):
    """
    Primary data store for H2O.

    H2OFrame is similar to pandas' ``DataFrame``, or R's ``data.frame``. One of the critical distinction is that the
    data is generally not held in memory, instead it is located on a (possibly remote) H2O cluster, and thus
    ``H2OFrame`` represents a mere handle to that data.
    """

    # Temp flag: set this to false for now if encountering path conversion/expansion issues when import files to remote server
    __LOCAL_EXPANSION_ON_SINGLE_IMPORT__ = True

    #-------------------------------------------------------------------------------------------------------------------
    # Construction
    #-------------------------------------------------------------------------------------------------------------------

    def __init__(self, python_obj=None, destination_frame=None, header=0, separator=",",
                 column_names=None, column_types=None, na_strings=None, skipped_columns=None):
        """
        Create a new H2OFrame object, possibly from some other object.

        :param python_obj: object that will be converted to an ``H2OFrame``. This could have multiple types:

            - None: create an empty H2OFrame
            - A list/tuple of strings or numbers: create a single-column H2OFrame containing the contents of this list.
            - A dictionary of ``{name: list}`` pairs: create an H2OFrame with multiple columns, each column having the
                provided ``name`` and contents from ``list``. If the source dictionary is not an OrderedDict, then the
                columns in the H2OFrame may appear shuffled.
            - A list of lists of strings/numbers: construct an H2OFrame from a rectangular table of values, with inner
                lists treated as rows of the table. I.e. ``H2OFrame([[1, 'a'], [2, 'b'], [3, 'c']])`` will create a
                frame with 3 rows and 2 columns, one numeric and one string.
            - A Pandas dataframe, or a Numpy ndarray: create a matching H2OFrame.
            - A Scipy sparse matrix: create a matching sparse H2OFrame.

        :param int header: if ``python_obj`` is a list of lists, this parameter can be used to indicate whether the
            first row of the data represents headers. The value of -1 means the first row is data, +1 means the first
            row is the headers, 0 (default) allows H2O to guess whether the first row contains data or headers.
        :param List[str] column_names: explicit list of column names for the new H2OFrame. This will override any
            column names derived from the data. If the python_obj does not contain explicit column names, and this
            parameter is not given, then the columns will be named "C1", "C2", "C3", etc.
        :param column_types: explicit column types for the new H2OFrame. This could be either a list of types for
            each column, or a dictionary of {column name: column type} pairs. In the latter case you may override
            types for only few columns, and let H2O choose the types of the rest.
        :param na_strings: List of strings in the input data that should be interpreted as missing values. This could
            be given on a per-column basis, either as a list-of-lists, or as a dictionary {column name: list of nas}.
        :param str destination_frame: (internal) name of the target DKV key in the H2O backend.
        :param str separator: (deprecated)
        """
        coltype = U(None, "unknown", "uuid", "string", "float", "real", "double", "int", "numeric",
                    "categorical", "factor", "enum", "time")
        assert_is_type(python_obj, None, list, tuple, dict, numpy_ndarray, pandas_dataframe, scipy_sparse)
        assert_is_type(destination_frame, None, str)
        assert_is_type(header, -1, 0, 1)
        assert_is_type(separator, I(str, lambda s: len(s) == 1))
        assert_is_type(column_names, None, [str])
        assert_is_type(column_types, None, [coltype], {str: coltype})
        assert_is_type(na_strings, None, [str], [[str]], {str: [str]})
        check_frame_id(destination_frame)

        self._ex = ExprNode()
        self._ex._children = None
        self._is_frame = True  # Indicate that this is an actual frame, allowing typechecks to be made
        if python_obj is not None:
            self._upload_python_object(python_obj, destination_frame, header, separator,
                                       column_names, column_types, na_strings, skipped_columns)

    @staticmethod
    def _expr(expr, cache=None):
        # TODO: merge this method with `__init__`
        fr = H2OFrame()
        fr._ex = expr
        if cache is not None:
            fr._ex._cache.fill_from(cache)
        return fr


    def _upload_python_object(self, python_obj, destination_frame=None, header=0, separator=",",
                              column_names=None, column_types=None, na_strings=None, skipped_columns=None):
        assert_is_type(python_obj, list, tuple, dict, numpy_ndarray, pandas_dataframe, scipy_sparse)
        if is_type(python_obj, scipy_sparse):
            self._upload_sparse_matrix(python_obj, destination_frame=destination_frame)
            return
        # TODO: all these _handlers should really belong to this class, not to shared_utils.
        processor = (_handle_pandas_data_frame if is_type(python_obj, pandas_dataframe) else
                     _handle_numpy_array if is_type(python_obj, numpy_ndarray) else
                     _handle_python_dicts if is_type(python_obj, dict) else
                     _handle_python_lists)
        col_header, data_to_write = processor(python_obj, header)
        if col_header is None or data_to_write is None:
            raise H2OValueError("No data to write")
        if not column_names:
            column_names = col_header

        # create a temporary file that will be written to
        tmp_handle, tmp_path = tempfile.mkstemp(suffix=".csv")
        tmp_file = os.fdopen(tmp_handle, 'w')
        # create a new csv writer object thingy
        csv_writer = csv.writer(tmp_file, dialect="excel", quoting=csv.QUOTE_NONNUMERIC)
        csv_writer.writerow(column_names)
        if data_to_write and isinstance(data_to_write[0], dict):
            for row in data_to_write:
                csv_writer.writerow([row.get(k, None) for k in col_header])
        else:
            csv_writer.writerows(data_to_write)
        tmp_file.close()  # close the streams
        self._upload_parse(tmp_path, destination_frame, 1, separator, column_names, column_types, na_strings, skipped_columns)
        os.remove(tmp_path)  # delete the tmp file


    def _upload_sparse_matrix(self, matrix, destination_frame=None):
        import scipy.sparse as sp
        if not sp.issparse(matrix):
            raise H2OValueError("A sparse matrix expected, got %s" % type(matrix))

        tmp_handle, tmp_path = tempfile.mkstemp(suffix=".svmlight")
        out = os.fdopen(tmp_handle, "wt")
        if destination_frame is None:
            destination_frame = _py_tmp_key(h2o.connection().session_id)

        # sp.find(matrix) returns (row indices, column indices, values) of the non-zero elements of A. Unfortunately
        # there is no guarantee that those elements are returned in the correct order, so need to sort
        data = zip(*sp.find(matrix))
        if not isinstance(data, list): data = list(data)  # possibly convert from iterator to a list
        data.sort()
        idata = 0  # index of the next element to be consumed from `data`
        for irow in range(matrix.shape[0]):
            if idata < len(data) and data[idata][0] == irow and data[idata][1] == 0:
                y = data[idata][2]
                idata += 1
            else:
                y = 0
            out.write(str(y))
            while idata < len(data) and data[idata][0] == irow:
                out.write(" ")
                out.write(str(data[idata][1]))
                out.write(":")
                out.write(str(data[idata][2]))
                idata += 1
            out.write("\n")
        out.close()

        ret = h2o.api("POST /3/PostFile", filename=tmp_path)
        os.remove(tmp_path)
        rawkey = ret["destination_frame"]

        p = {"source_frames": [rawkey], "destination_frame": destination_frame}
        H2OJob(h2o.api("POST /3/ParseSVMLight", data=p), "Parse").poll()
        self._ex._cache._id = destination_frame
        self._ex._cache.fill()


    @staticmethod
    def get_frame(frame_id, rows=10, rows_offset=0, cols=-1, full_cols=-1, cols_offset=0, light=False):
        """
        Retrieve an existing H2OFrame from the H2O cluster using the frame's id.

        :param str frame_id: id of the frame to retrieve
        :param int rows: number of rows to fetch for preview (10 by default)
        :param int rows_offset: offset to fetch rows from (0 by default)
        :param int cols: number of columns to fetch (all by default)
        :param full_cols: number of columns to fetch together with backed data
        :param int cols_offset: offset to fetch rows from (0 by default)
        :param bool light: wether to use light frame endpoint or not
        :returns: an existing H2OFrame with the id provided; or None if such frame doesn't exist.
        """
        fr = H2OFrame()
        fr._ex._cache._id = frame_id
        try:
            fr._ex._cache.fill(rows=rows, rows_offset=rows_offset, cols=cols, full_cols=full_cols, cols_offset=cols_offset, light=light)
        except EnvironmentError:
            return None
        return fr


    def refresh(self):
        """Reload frame information from the backend H2O server."""
        self._ex._cache.flush()
        self._frame(fill_cache=True)



    #-------------------------------------------------------------------------------------------------------------------
    # Frame properties
    #-------------------------------------------------------------------------------------------------------------------

    @property
    def names(self):
        """The list of column names (List[str])."""
        if not self._ex._cache.names_valid():
            self._ex._cache.flush()
            self._frame(fill_cache=True)
        return list(self._ex._cache.names)

    @names.setter
    def names(self, value):
        self.set_names(value)


    @property
    def nrows(self):
        """Number of rows in the dataframe (int)."""
        if not self._ex._cache.nrows_valid():
            self._ex._cache.flush()
            self._frame(fill_cache=True)
        return self._ex._cache.nrows


    @property
    def ncols(self):
        """Number of columns in the dataframe (int)."""
        if not self._ex._cache.ncols_valid():
            self._ex._cache.flush()
            self._frame(fill_cache=True)
        return self._ex._cache.ncols


    @property
    def shape(self):
        """Number of rows and columns in the dataframe as a tuple ``(nrows, ncols)``."""
        return self.nrows, self.ncols


    @property
    def types(self):
        """The dictionary of column name/type pairs."""
        if not self._ex._cache.types_valid():
            self._ex._cache.flush()
            self._frame(fill_cache=True)
        return dict(self._ex._cache.types)


    @property
    def frame_id(self):
        """Internal id of the frame (str)."""
        return self._frame()._ex._cache._id

    @frame_id.setter
    def frame_id(self, newid):
        check_frame_id(newid)
        if self._ex._cache._id is None:
            h2o.assign(self, newid)
        else:
            oldname = self.frame_id
            self._ex._cache._id = newid
            h2o.rapids("(rename \"{}\" \"{}\")".format(oldname, newid))


    def type(self, col):
        """
        The type for the given column.

        :param col: either a name, or an index of the column to look up
        :returns: type of the column, one of: ``str``, ``int``, ``real``, ``enum``, ``time``, ``bool``.
        :raises H2OValueError: if such column does not exist in the frame.
        """
        assert_is_type(col, int, str)
        if not self._ex._cache.types_valid() or not self._ex._cache.names_valid():
            self._ex._cache.flush()
            self._frame(fill_cache=True)
        types = self._ex._cache.types
        if is_type(col, str):
            if col in types:
                return types[col]
        else:
            names = self._ex._cache.names
            if -len(names) <= col < len(names):
                return types[names[col]]
        raise H2OValueError("Column '%r' does not exist in the frame" % col)


    def _import_parse(self, path, pattern, destination_frame, header, separator, column_names, column_types, na_strings, skipped_columns=None):
        if H2OFrame.__LOCAL_EXPANSION_ON_SINGLE_IMPORT__ and is_type(path, str) and "://" not in path:  # fixme: delete those 2 lines, cf. PUBDEV-5717
            path = os.path.abspath(path)
        rawkey = h2o.lazy_import(path, pattern)
        self._parse(rawkey, destination_frame, header, separator, column_names, column_types, na_strings, skipped_columns)
        return self


    def _upload_parse(self, path, destination_frame, header, sep, column_names, column_types, na_strings, skipped_columns=None):
        ret = h2o.api("POST /3/PostFile", filename=path)
        rawkey = ret["destination_frame"]
        self._parse(rawkey, destination_frame, header, sep, column_names, column_types, na_strings, skipped_columns)
        return self


    def _parse(self, rawkey, destination_frame="", header=None, separator=None, column_names=None, column_types=None,
               na_strings=None, skipped_columns=None):
        setup = h2o.parse_setup(rawkey, destination_frame, header, separator, column_names, column_types, na_strings, skipped_columns)
        return self._parse_raw(setup)


    def _parse_raw(self, setup):
        # Parse parameters (None values provided by setup)
        p = {"destination_frame": None,
             "parse_type": None,
             "separator": None,
             "single_quotes": None,
             "check_header": None,
             "number_columns": None,
             "chunk_size": None,
             "delete_on_done": True,
             "blocking": False,
             "column_types": None,
             "skipped_columns":None
             }

        if setup["column_names"]: p["column_names"] = None
        if setup["na_strings"]: p["na_strings"] = None

        p.update({k: v for k, v in viewitems(setup) if k in p})

        # Extract only 'name' from each src in the array of srcs
        p['source_frames'] = [_quoted(src['name']) for src in setup['source_frames']]

        H2OJob(h2o.api("POST /3/Parse", data=p), "Parse").poll()
        # Need to return a Frame here for nearly all callers
        # ... but job stats returns only a dest_key, requiring another REST call to get nrow/ncol
        self._ex._cache._id = p["destination_frame"]
        self._ex._cache.fill()


    def filter_na_cols(self, frac=0.2):
        """
        Filter columns with proportion of NAs greater or equals than ``frac``.

        :param float frac: Maximum fraction of NAs in the column to keep.

        :returns: A list of indices of columns that have fewer NAs than ``frac``. If all columns are filtered,
            None is returned.
        """
        return ExprNode("filterNACols", self, frac)._eager_scalar()


    def columns_by_type(self, coltype="numeric"):
        """
        Extract columns of the specified type from the frame.

        :param str coltype: A character string indicating which column type to filter by. This must be
            one of the following:

            - ``"numeric"``      - Numeric, but not categorical or time
            - ``"categorical"``  - Integer, with a categorical/factor String mapping
            - ``"string"``       - String column
            - ``"time"``         - Long msec since the Unix Epoch - with a variety of display/parse options
            - ``"uuid"``         - UUID
            - ``"bad"``          - No none-NA rows (triple negative! all NAs or zero rows)

        :returns: list of indices of columns that have the requested type
        """
        assert_is_type(coltype, "numeric", "categorical", "string", "time", "uuid", "bad")
        assert_is_type(self, H2OFrame)
        return ExprNode("columnsByType", self, coltype)._eager_scalar()


    def __iter__(self):
        return (self[i] for i in range(self.ncol))

    def __unicode__(self):
        if sys.gettrace() is None:
            if self._ex is None: return "This H2OFrame has been removed."
            table = self._frame(fill_cache=True)._ex._cache._tabulate("simple", False)
            nrows = "%d %s" % (self.nrow, "row" if self.nrow == 1 else "rows")
            ncols = "%d %s" % (self.ncol, "column" if self.ncol == 1 else "columns")
            return "%s\n\n[%s x %s]" % (table, nrows, ncols)
        return ""

    def __repr__(self):
        if sys.gettrace() is None:
            # PUBDEV-2278: using <method>? from IPython caused everything to dump
            stk = traceback.extract_stack()
            if not ("IPython" in stk[-2][0] and "info" == stk[-2][2]):
                self.show()
        return ""

    def _has_content(self):
        return self._ex and (self._ex._children or self._ex._cache._id)

    def show(self, use_pandas=False, rows=10, cols=200):
        """
        Used by the H2OFrame.__repr__ method to print or display a snippet of the data frame.

        If called from IPython, displays an html'ized result. Else prints a tabulate'd result.
        """
        if self._ex is None:
            print("This H2OFrame has been removed.")
            return
        if not self._has_content():
            print("This H2OFrame is empty and not initialized.")
            return
        if self.nrows == 0:
            print("This H2OFrame is empty.")
            return
        if not self._ex._cache.is_valid(): self._frame()._ex._cache.fill()
        if H2ODisplay._in_ipy():
            import IPython.display
            if use_pandas and can_use_pandas():
                IPython.display.display(self.head(rows=rows, cols=cols).as_data_frame(fill_cache=True))
            else:
                IPython.display.display_html(self._ex._cache._tabulate("html", False), raw=True)
        else:
            if use_pandas and can_use_pandas():
                print(self.head(rows=rows, cols=cols).as_data_frame(True))  # no keyword fill_cache
            else:
                s = self.__unicode__()
                stk = traceback.extract_stack()
                if "IPython" in stk[-3][0]:
                    s = "\n%s" % s
                try:
                    print(s)
                except UnicodeEncodeError:
                    print(s.encode("ascii", "replace"))


    def summary(self, return_data=False):
        """
        Display summary information about the frame.

        Summary includes min/mean/max/sigma and other rollup data.

        :param bool return_data: Return a dictionary of the summary output
        """
        if not self._has_content():
            print("This H2OFrame is empty and not initialized.")
            return self._ex._cache._data;
        if not self._ex._cache.is_valid(): self._frame()._ex._cache.fill()
        if not return_data:
            if self.nrows == 0:
                print("This H2OFrame is empty.")
            elif H2ODisplay._in_ipy():
                import IPython.display
                IPython.display.display_html(self._ex._cache._tabulate("html", True), raw=True)
            else:
                print(self._ex._cache._tabulate("simple", True))
        else:
            return self._ex._cache._data


    def describe(self, chunk_summary=False):
        """
        Generate an in-depth description of this H2OFrame.

        This will print to the console the dimensions of the frame; names/types/summary statistics for each column;
        and finally first ten rows of the frame.

        :param bool chunk_summary: Retrieve the chunk summary along with the distribution summary
        """
        if self._has_content():
            res = h2o.api("GET /3/Frames/%s" % self.frame_id, data={"row_count": 10})["frames"][0]
            self._ex._cache._fill_data(res)

            print("Rows:{}".format(self.nrow))
            print("Cols:{}".format(self.ncol))

            #The chunk & distribution summaries are not cached, so must be pulled if chunk_summary=True.
            if chunk_summary:
                res["chunk_summary"].show()
                res["distribution_summary"].show()
            print("\n")
        self.summary()


    def _frame(self, rows=10, rows_offset=0, cols=-1, cols_offset=0, fill_cache=False):
        self._ex._eager_frame()
        if fill_cache:
            self._ex._cache.fill(rows=rows, rows_offset=rows_offset, cols=cols, cols_offset=cols_offset)
        return self


    def head(self, rows=10, cols=200):
        """
        Return the first ``rows`` and ``cols`` of the frame as a new H2OFrame.

        :param int rows: maximum number of rows to return
        :param int cols: maximum number of columns to return
        :returns: a new H2OFrame cut from the top left corner of the current frame, and having dimensions at
            most ``rows`` x ``cols``.
        """
        assert_is_type(rows, int)
        assert_is_type(cols, int)
        nrows = min(self.nrows, rows)
        ncols = min(self.ncols, cols)
        newdt = self[:nrows, :ncols]
        return newdt._frame(rows=nrows, cols=cols, fill_cache=True)


    def tail(self, rows=10, cols=200):
        """
        Return the last ``rows`` and ``cols`` of the frame as a new H2OFrame.

        :param int rows: maximum number of rows to return
        :param int cols: maximum number of columns to return
        :returns: a new H2OFrame cut from the bottom left corner of the current frame, and having dimensions at
            most ``rows`` x ``cols``.
        """
        assert_is_type(rows, int)
        assert_is_type(cols, int)
        nrows = min(self.nrows, rows)
        ncols = min(self.ncols, cols)
        start_idx = self.nrows - nrows
        newdt = self[start_idx:start_idx + nrows, :ncols]
        return newdt._frame(rows=nrows, cols=cols, fill_cache=True)


    def logical_negation(self):
        """
        Returns new H2OFrame equal to elementwise Logical NOT applied to the current frame.
        """
        return H2OFrame._expr(expr=ExprNode("not", self), cache=self._ex._cache)


    def _unop(self, op, rtype="real"):
        if self._is_frame:
            for cname, ctype in self.types.items():
                if ctype not in {"int", "real", "bool"}:
                    raise H2OValueError("Function %s cannot be applied to %s column '%s'" % (op, ctype, cname))
        ret = H2OFrame._expr(expr=ExprNode(op, self), cache=self._ex._cache)
        ret._ex._cache._names = ["%s(%s)" % (op, name) for name in self._ex._cache._names]
        ret._ex._cache._types = {name: rtype for name in ret._ex._cache._names}
        return ret


    # Binary operations
    def __add__(self, rhs):
        return _binop(self, "+", rhs)

    def __sub__(self, rhs):
        return _binop(self, "-", rhs)

    def __mul__(self, rhs):
        return _binop(self, "*", rhs)

    def __div__(self, rhs):
        return _binop(self, "/", rhs)

    def __truediv__(self, rhs):
        return _binop(self, "/", rhs)

    def __floordiv__(self, rhs):
        return _binop(self, "intDiv", rhs)

    def __mod__(self, rhs):
        return _binop(self, "%", rhs)

    def __or__(self, rhs):
        return _binop(self, "|", rhs, rtype="bool")

    def __and__(self, rhs):
        return _binop(self, "&", rhs, rtype="bool")

    def __ge__(self, rhs):
        return _binop(self, ">=", rhs, rtype="bool")

    def __gt__(self, rhs):
        return _binop(self, ">", rhs, rtype="bool")

    def __le__(self, rhs):
        return _binop(self, "<=", rhs, rtype="bool")

    def __lt__(self, rhs):
        return _binop(self, "<", rhs, rtype="bool")

    def __eq__(self, rhs):
        if rhs is None: rhs = float("nan")
        return _binop(self, "==", rhs, rtype="bool")

    def __ne__(self, rhs):
        if rhs is None: rhs = float("nan")
        return _binop(self, "!=", rhs, rtype="bool")

    def __pow__(self, rhs):
        return _binop(self, "^", rhs)

    def __contains__(self, lhs):
        return all((t == self).any() for t in lhs) if _is_list(lhs) else (lhs == self).any()

    # rops
    def __rmod__(self, lhs):
        return _binop(lhs, "%", self)

    def __radd__(self, lhs):
        return _binop(lhs, "+", self)

    def __rsub__(self, lhs):
        return _binop(lhs, "-", self)

    def __rand__(self, lhs):
        return _binop(lhs, "&", self, rtype="bool")

    def __ror__(self, lhs):
        return _binop(lhs, "|", self, rtype="bool")

    def __rtruediv__(self, lhs):
        return _binop(lhs, "/", self)

    def __rdiv__(self, lhs):
        return _binop(lhs, "/", self)

    def __rfloordiv__(self, lhs):
        return _binop(lhs, "intDiv", self, rtype="int")

    def __rmul__(self, lhs):
        return _binop(lhs, "*", self)

    def __rpow__(self, lhs):
        return _binop(lhs, "^", self)

    # unops
    def __abs__(self):
        return self._unop("abs")

    def __invert__(self):
        return self._unop("!!", rtype="bool")

    def __nonzero__(self):
        if self.nrows > 1 or self.ncols > 1:
            raise H2OValueError(
                'This operation is not supported on an H2OFrame. Try using parentheses. '
                'Did you mean & (logical and), | (logical or), or ~ (logical not)?')
        else:
            return self.__len__()

    def __int__(self):
        return int(self.flatten())

    def __float__(self):
        return float(self.flatten())


    def flatten(self):
        """
        Convert a 1x1 frame into a scalar.

        :returns: content of this 1x1 frame as a scalar (``int``, ``float``, or ``str``).
        :raises H2OValueError: if current frame has shape other than 1x1
        """
        if self.shape != (1, 1): raise H2OValueError("Not a 1x1 Frame")
        return ExprNode("flatten", self)._eager_scalar()


    def getrow(self):
        """
        Convert a 1xn frame into an n-element list.

        :returns: content of this 1xn frame as a Python list.
        :raises H2OValueError: if current frame has more than one row.
        """
        if self.nrows != 1:
            raise H2OValueError("This method can only be applied to single-row frames")
        return ExprNode("getrow", self)._eager_scalar()


    def mult(self, matrix):
        """
        Multiply this frame, viewed as a matrix, by another matrix.

        :param matrix: another frame that you want to multiply the current frame by; must be compatible with the
            current frame (i.e. its number of rows must be the same as number of columns in the current frame).
        :returns: new H2OFrame, which is the result of multiplying the current frame by ``matrix``.
        """
        if self.ncols != matrix.nrows:
            raise H2OValueError("Matrix is not compatible for multiplication with the current frame")
        return H2OFrame._expr(expr=ExprNode("x", self, matrix))


    def cos(self):
        """Return new H2OFrame equal to elementwise cosine of the current frame."""
        return self._unop("cos")


    def sin(self):
        """Return new H2OFrame equal to elementwise sine of the current frame."""
        return self._unop("sin")


    def tan(self):
        """Return new H2OFrame equal to elementwise tangent of the current frame."""
        return self._unop("tan")


    def acos(self):
        """Return new H2OFrame equal to elementwise arc cosine of the current frame."""
        return self._unop("acos")


    def asin(self):
        """Return new H2OFrame equal to elementwise arc sine of the current frame."""
        return self._unop("asin")


    def atan(self):
        """Return new H2OFrame equal to elementwise arc tangent of the current frame."""
        return self._unop("atan")


    def cosh(self):
        """Make new H2OFrame with values equal to the hyperbolic cosines of the values in the current frame."""
        return self._unop("cosh")


    def sinh(self):
        """Return new H2OFrame equal to elementwise hyperbolic sine of the current frame."""
        return self._unop("sinh")


    def tanh(self):
        """Return new H2OFrame equal to elementwise hyperbolic tangent of the current frame."""
        return self._unop("tanh")


    def acosh(self):
        """Return new H2OFrame equal to elementwise inverse hyperbolic cosine of the current frame."""
        return self._unop("acosh")


    def asinh(self):
        """Return new H2OFrame equal to elementwise inverse hyperbolic sine of the current frame."""
        return self._unop("asinh")


    def atanh(self):
        """Return new H2OFrame equal to elementwise inverse hyperbolic tangent of the current frame."""
        return self._unop("atanh")


    def cospi(self):
        """Return new H2OFrame equal to elementwise cosine of the current frame multiplied by Pi."""
        return self._unop("cospi")


    def sinpi(self):
        """Return new H2OFrame equal to elementwise sine of the current frame multiplied by Pi."""
        return self._unop("sinpi")


    def tanpi(self):
        """Return new H2OFrame equal to elementwise tangent of the current frame multiplied by Pi."""
        return self._unop("tanpi")


    def abs(self):
        """Return new H2OFrame equal to elementwise absolute value of the current frame."""
        return self._unop("abs")


    def sign(self):
        """Return new H2OFrame equal to signs of the values in the frame: -1 , +1, or 0."""
        return self._unop("sign", rtype="int")


    def sqrt(self):
        """Return new H2OFrame equal to elementwise square root of the current frame."""
        return self._unop("sqrt")


    def trunc(self):
        """
        Apply the numeric truncation function.

        ``trunc(x)`` is the integer obtained from ``x`` by dropping its decimal tail. This is equal to ``floor(x)``
        if ``x`` is positive, and ``ceil(x)`` if ``x`` is negative. Truncation is also called "rounding towards zero".

        :returns: new H2OFrame of truncated values of the original frame.
        """
        return self._unop("trunc", rtype="int")


    def ceil(self):
        """
        Apply the ceiling function to the current frame.

        ``ceil(x)`` is the smallest integer greater or equal to ``x``.

        :returns: new H2OFrame of ceiling values of the original frame.
        """
        return self._unop("ceiling", rtype="int")


    def floor(self):
        """
        Apply the floor function to the current frame.

        ``floor(x)`` is the largest integer smaller or equal to ``x``.

        :returns: new H2OFrame of floor values of the original frame.
        """
        return self._unop("floor", rtype="int")


    def log(self):
        """Return new H2OFrame equals to elementwise natural logarithm of the current frame."""
        return self._unop("log")


    def log10(self):
        """Return new H2OFrame equals to elementwise decimal logarithm of the current frame."""
        return self._unop("log10")


    def log1p(self):
        """Return new H2OFrame equals to elementwise ``ln(1 + x)`` for each ``x`` in the current frame."""
        return self._unop("log1p")


    def log2(self):
        """Return new H2OFrame equals to elementwise binary logarithm of the current frame."""
        return self._unop("log2")


    def exp(self):
        """Return new H2OFrame equals to elementwise exponent (i.e. ``e^x``) of the current frame."""
        return self._unop("exp")


    def expm1(self):
        """Return new H2OFrame equals to elementwise exponent minus 1 (i.e. ``e^x - 1``) of the current frame."""
        return self._unop("expm1")


    def gamma(self):
        """Return new H2OFrame equals to elementwise gamma function of the current frame."""
        return self._unop("gamma")


    def lgamma(self):
        """Return new H2OFrame equals to elementwise logarithm of the gamma function of the current frame."""
        return self._unop("lgamma")


    def digamma(self):
        """Return new H2OFrame equals to elementwise digamma function of the current frame."""
        return self._unop("digamma")


    def trigamma(self):
        """Return new H2OFrame equals to elementwise trigamma function of the current frame."""
        return self._unop("trigamma")


    @staticmethod
    def moment(year=None, month=None, day=None, hour=None, minute=None, second=None, msec=None, date=None, time=None):
        """
        Create a time column from individual components.

        Each parameter should be either an integer, or a single-column H2OFrame
        containing the corresponding time parts for each row.

        The "date" part of the timestamp can be specified using either the tuple ``(year, month, day)``, or an
        explicit ``date`` parameter. The "time" part of the timestamp is optional, but can be specified either via
        the ``time`` parameter, or via the ``(hour, minute, second, msec)`` tuple.

        :param year: the year part of the constructed date
        :param month: the month part of the constructed date
        :param day: the day-of-the-month part of the constructed date
        :param hour: the hours part of the constructed date
        :param minute: the minutes part of the constructed date
        :param second: the seconds part of the constructed date
        :param msec: the milliseconds part of the constructed date
        :param date date: construct the timestamp from the Python's native ``datetime.date`` (or ``datetime.datetime``)
            object. If the object passed is of type ``date``, then you can specify the time part using either the
            ``time`` argument, or ``hour`` ... ``msec`` arguments (but not both). If the object passed is of type
            ``datetime``, then no other arguments can be provided.
        :param time time: construct the timestamp from this Python's native ``datetime.time`` object. This argument
            cannot be used alone, it should be supplemented with either ``date`` argument, or ``year`` ... ``day``
            tuple.

        :returns: H2OFrame with one column containing the date constructed from the provided arguments.
        """
        assert_is_type(date, None, datetime.date, numpy_datetime, pandas_timestamp)
        assert_is_type(time, None, datetime.time)
        assert_is_type(year, None, int, H2OFrame)
        assert_is_type(month, None, int, H2OFrame)
        assert_is_type(day, None, int, H2OFrame)
        assert_is_type(hour, None, int, H2OFrame)
        assert_is_type(minute, None, int, H2OFrame)
        assert_is_type(second, None, int, H2OFrame)
        assert_is_type(msec, None, int, H2OFrame)
        if time is not None:
            if hour is not None or minute is not None or second is not None or msec is not None:
                raise H2OValueError("Arguments hour, minute, second, msec cannot be used together with `time`.")
            hour = time.hour
            minute = time.minute
            second = time.second
            msec = time.microsecond // 1000
        if date is not None:
            if is_type(date, pandas_timestamp):
                date = date.to_pydatetime()
            if is_type(date, numpy_datetime):
                date = date.astype("M8[ms]").astype("O")
            if year is not None or month is not None or day is not None:
                raise H2OValueError("Arguments year, month and day cannot be used together with `date`.")
            year = date.year
            month = date.month
            day = date.day
            if isinstance(date, datetime.datetime):
                if time is not None:
                    raise H2OValueError("Argument `time` cannot be used together with `date` of datetime type.")
                if hour is not None or minute is not None or second is not None or msec is not None:
                    raise H2OValueError("Arguments hour, minute, second, msec cannot be used together with `date` "
                                        "of datetime type.")
                hour = date.hour
                minute = date.minute
                second = date.second
                msec = date.microsecond // 1000
        if year is None or month is None or day is None:
            raise H2OValueError("Either arguments (`year`, `month` and `day`) or the `date` are required.")
        if hour is None: hour = 0
        if minute is None: minute = 0
        if second is None: second = 0
        if msec is None: msec = 0

        local_vars = locals()
        res_nrows = None
        for n in ["year", "month", "day", "hour", "minute", "second", "msec"]:
            x = local_vars[n]
            if isinstance(x, H2OFrame):
                if x.ncols != 1:
                    raise H2OValueError("Argument `%s` is a frame with more than 1 column" % n)
                if x.type(0) not in {"int", "real"}:
                    raise H2OValueError("Column `%s` is not numeric (type = %s)" % (n, x.type(0)))
                if res_nrows is None:
                    res_nrows = x.nrows
                if x.nrows == 0 or x.nrows != res_nrows:
                    raise H2OValueError("Incompatible column `%s` having %d rows" % (n, x.nrows))
        if res_nrows is None:
            res_nrows = 1
        res = H2OFrame._expr(ExprNode("moment", year, month, day, hour, minute, second, msec))
        res._ex._cache._names = ["name"]
        res._ex._cache._types = {"name": "time"}
        res._ex._cache._nrows = res_nrows
        res._ex._cache._ncols = 1
        return res


    def unique(self):
        """
        Extract the unique values in the column.

        :returns: H2OFrame of just the unique values in the column.
        """
        return H2OFrame._expr(expr=ExprNode("unique", self))


    def levels(self):
        """
        Get the factor levels.

        :returns: A list of lists, one list per column, of levels.
        """
        lol = H2OFrame._expr(expr=ExprNode("levels", self)).as_data_frame(False)
        lol.pop(0)  # Remove column headers
        lol = list(zip(*lol))
        return [[ll for ll in l if ll != ''] for l in lol]


    def nlevels(self):
        """
        Get the number of factor levels for each categorical column.

        :returns: A list of the number of levels per column.
        """
        levels = self.levels()
        return [len(l) for l in levels] if levels else 0


    def set_level(self, level):
        """
        A method to set all column values to one of the levels.

        :param str level: The level at which the column will be set (a string)

        :returns: H2OFrame with entries set to the desired level.
        """
        return H2OFrame._expr(expr=ExprNode("setLevel", self, level), cache=self._ex._cache)


    def set_levels(self, levels):
        """
        Replace the levels of a categorical column.

        New levels must be aligned with the old domain. This call has copy-on-write semantics.

        :param List[str] levels: A list of strings specifying the new levels. The number of new
            levels must match the number of old levels.
        :returns: A single-column H2OFrame with the desired levels.
        """
        assert_is_type(levels, [str])
        return H2OFrame._expr(expr=ExprNode("setDomain", self, False, levels), cache=self._ex._cache)


    def rename(self, columns=None):
        """
        Change names of columns in the frame.

        Dict key is an index or name of the column whose name is to be set.
        Dict value is the new name of the column.

        :param columns: dict-like transformations to apply to the column names
        """
        assert_is_type(columns, None, dict)
        new_names = self.names
        ncols = self.ncols

        for col, name in columns.items():
            col_index = None
            if is_type(col, int) and (-ncols <= col < ncols):
                col_index = (col + ncols) % ncols  # handle negative indices
            elif is_type(col, str) and col in self.names:
                col_index = self.names.index(col)  # lookup the name

            if col_index is not None:
                new_names[col_index] = name

        return self.set_names(new_names)


    def set_names(self, names):
        """
        Change names of all columns in the frame.

        :param List[str] names: The list of new names for every column in the frame.
        """
        assert_is_type(names, [str])
        assert_satisfies(names, len(names) == self.ncol)
        self._ex = ExprNode("colnames=", self, range(self.ncol), names)  # Update-in-place, but still lazy
        return self


    def set_name(self, col=None, name=None):
        """
        Set a new name for a column.

        :param col: index or name of the column whose name is to be set; may be skipped for 1-column frames
        :param name: the new name of the column
        """
        assert_is_type(col, None, int, str)
        assert_is_type(name, str)
        ncols = self.ncols

        col_index = None
        if is_type(col, int):
            if not(-ncols <= col < ncols):
                raise H2OValueError("Index %d is out of bounds for a frame with %d columns" % (col, ncols))
            col_index = (col + ncols) % ncols  # handle negative indices
        elif is_type(col, str):
            if col not in self.names:
                raise H2OValueError("Column %s doesn't exist in the frame." % col)
            col_index = self.names.index(col)  # lookup the name
        else:
            assert col is None
            if ncols != 1:
                raise H2OValueError("The frame has %d columns; please specify which one to rename" % ncols)
            col_index = 0
        if name != self.names[col_index] and name in self.types:
            raise H2OValueError("Column '%s' already exists in the frame" % name)

        oldname = self.names[col_index]
        old_cache = self._ex._cache
        self._ex = ExprNode("colnames=", self, col_index, name)  # Update-in-place, but still lazy
        self._ex._cache.fill_from(old_cache)
        if self.names is None:
            self._frame()._ex._cache.fill()
        else:
            self._ex._cache._names = self.names[:col_index] + [name] + self.names[col_index + 1:]
            self._ex._cache._types[name] = self._ex._cache._types.pop(oldname)
        return


    def as_date(self, format):
        """
        Convert the frame (containing strings / categoricals) into the ``date`` format.

        :param str format: the format string (e.g. "%Y-%m-%d")
        :returns: new H2OFrame with "int" column types
        """
        fr = H2OFrame._expr(expr=ExprNode("as.Date", self, format), cache=self._ex._cache)
        if fr._ex._cache.types_valid():
            fr._ex._cache.types = {k: "int" for k in self._ex._cache.types.keys()}
        return fr


    def cumsum(self,  axis=0):
        """
        Compute cumulative sum over rows / columns of the frame.

        :param int axis: 0 for column-wise, 1 for row-wise
        :returns: new H2OFrame with cumulative sums of the original frame.
        """
        return H2OFrame._expr(expr=ExprNode("cumsum", self, axis), cache=self._ex._cache)


    def cumprod(self, axis=0):
        """
        Compute cumulative product over rows / columns of the frame.

        :param int axis: 0 for column-wise, 1 for row-wise
        :returns: new H2OFrame with cumulative products of the original frame.
        """
        return H2OFrame._expr(expr=ExprNode("cumprod", self, axis), cache=self._ex._cache)


    def cummin(self, axis=0):
        """
        Compute cumulative minimum over rows / columns of the frame.

        :param int axis: 0 for column-wise, 1 for row-wise
        :returns: new H2OFrame with running minimums of the original frame.
        """
        return H2OFrame._expr(expr=ExprNode("cummin", self, axis), cache=self._ex._cache)


    def cummax(self, axis=0):
        """
        Compute cumulative maximum over rows / columns of the frame.

        :param int axis: 0 for column-wise, 1 for row-wise
        :returns: new H2OFrame with running maximums of the original frame.
        """
        return H2OFrame._expr(expr=ExprNode("cummax", self, axis), cache=self._ex._cache)


    def prod(self, na_rm=False):
        """
        Compute the product of all values across all rows in a single column H2O frame.  If you apply
        this command on a multi-column H2O frame, the answer may not be correct.

        :param bool na_rm: If True then NAs will be ignored during the computation.
        :returns: product of all values in the frame (a float)
        """
        return ExprNode("prod.na" if na_rm else "prod", self)._eager_scalar()


    def any(self):
        """Return True if any element in the frame is either True, non-zero or NA."""
        return bool(ExprNode("any", self)._eager_scalar())


    def any_na_rm(self):
        """Return True if any value in the frame is non-zero (disregarding all NAs)."""
        return bool(ExprNode("any.na", self)._eager_scalar())


    def all(self):
        """Return True if every element in the frame is either True, non-zero or NA."""
        return bool(ExprNode("all", self)._eager_scalar())


    def isnumeric(self):
        """
        Test which columns in the frame are numeric.

        :returns: a list of True/False indicating for each column in the frame whether it is numeric.
        """
        return [bool(o) for o in ExprNode("is.numeric", self)._eager_scalar()]


    def isstring(self):
        """
        Test which columns in the frame are string.

        :returns: a list of True/False indicating for each column in the frame whether it is numeric.
        """
        return [bool(o) for o in ExprNode("is.character", self)._eager_scalar()]


    def isin(self, item):
        """
        Test whether elements of an H2OFrame are contained in the ``item``.

        :param items: An item or a list of items to compare the H2OFrame against.

        :returns: An H2OFrame of 0s and 1s showing whether each element in the original H2OFrame is contained in item.
        """
        if is_type(item, list, tuple, set):
            if self.ncols == 1 and (self.type(0) == 'str' or self.type(0) == 'enum'):
                return self.match(item)
            else:
                return functools.reduce(H2OFrame.__or__, (self == i for i in item))
        else:
            return self == item


    def kfold_column(self, n_folds=3, seed=-1):
        """
        Build a fold assignments column for cross-validation.

        This method will produce a column having the same data layout as the source frame.

        :param int n_folds: An integer specifying the number of validation sets to split the training data into.
        :param int seed: Seed for random numbers as fold IDs are randomly assigned.

        :returns: A single column H2OFrame with the fold assignments.
        """
        return H2OFrame._expr(expr=ExprNode("kfold_column", self, n_folds, seed))._frame()  # want this to be eager!


    def modulo_kfold_column(self, n_folds=3):
        """
        Build a fold assignments column for cross-validation.

        Rows are assigned a fold according to the current row number modulo ``n_folds``.

        :param int n_folds: An integer specifying the number of validation sets to split the training data into.
        :returns: A single-column H2OFrame with the fold assignments.
        """
        return H2OFrame._expr(expr=ExprNode("modulo_kfold_column", self, n_folds))._frame()  # want this to be eager!


    def stratified_kfold_column(self, n_folds=3, seed=-1):
        """
        Build a fold assignment column with the constraint that each fold has the same class
        distribution as the fold column.

        :param int n_folds: The number of folds to build.
        :param int seed: A seed for the random number generator.

        :returns: A single column H2OFrame with the fold assignments.
        """
        return H2OFrame._expr(
            expr=ExprNode("stratified_kfold_column", self, n_folds, seed))._frame()  # want this to be eager!


    def structure(self):
        """Compactly display the internal structure of an H2OFrame."""
        df = self.as_data_frame(use_pandas=False)
        cn = df.pop(0)
        nr = self.nrow
        nc = self.ncol
        width = max([len(c) for c in cn])
        isfactor = self.isfactor()
        numlevels = self.nlevels()
        lvls = self.levels()
        print("H2OFrame: '{}' \nDimensions: {} obs. of {} variables".format(self.frame_id, nr, nc))
        for i in range(nc):
            print("$ {} {}: ".format(cn[i], ' ' * (width - max(0, len(cn[i])))), end=' ')
            if isfactor[i]:
                nl = numlevels[i]
                print("Factor w/ {} level(s) {} ".format(nl, '"' + '","'.join(lvls[i]) + '"'), end='\n')
            else:
                print("num {}".format(" ".join(it[0] if it else "nan" for it in h2o.as_list(self[:10, i], False)[1:])))


    def as_data_frame(self, use_pandas=True, header=True):
        """
        Obtain the dataset as a python-local object.

        :param bool use_pandas: If True (default) then return the H2OFrame as a pandas DataFrame (requires that the
            ``pandas`` library was installed). If False, then return the contents of the H2OFrame as plain nested
            list, in a row-wise order.
        :param bool header: If True (default), then column names will be appended as the first row in list

        :returns: A python object (a list of lists of strings, each list is a row, if use_pandas=False, otherwise
            a pandas DataFrame) containing this H2OFrame instance's data.
        """
        if can_use_pandas() and use_pandas:
            import pandas
            return pandas.read_csv(StringIO(self.get_frame_data()), low_memory=False, skip_blank_lines=False)
        frame = [row for row in csv.reader(StringIO(self.get_frame_data()))]
        if not header:
            frame.pop(0)
        return frame


    def get_frame_data(self):
        """
        Get frame data as a string in csv format.

        This will create a multiline string, where each line will contain a separate row of frame's data, with
        individual values separated by commas.
        """
        return h2o.api("GET /3/DownloadDataset", data={"frame_id": self.frame_id, "hex_string": False})


    def __getitem__(self, item):
        """
        Frame slicing, supports row and column slicing.

        :param item: selector of a subframe. This can be one of the following:

            - an int, indicating selection of a single column at the specified index (0-based)
            - a string, selecting a column with the given name
            - a list of ints or strings, selecting several columns with the given indices / names
            - a slice, selecting columns with the indices within this slice
            - a single-column boolean frame, selecting rows for which the selector is true
            - a 2-element tuple, where the first element is a row selector, and the second element is the
              column selector. Here the row selector may be one of: an int, a list of ints, a slice, or
              a boolean frame. The column selector is similarly one of: an int, a list of ints, a string,
              a list of strings, or a slice. It is also possible to use the empty slice (``:``) to select
              all elements within one of the dimensions.

        :returns: A new frame comprised of some rows / columns of the source frame.

        :examples:
        >>> fr[2]              # All rows, 3rd column
        >>> fr[-2]             # All rows, 2nd column from end
        >>> fr[:, -1]          # All rows, last column
        >>> fr[0:5, :]         # First 5 rows, all columns
        >>> fr[fr[0] > 1, :]   # Only rows where first cell is greater than 1, all columns
        >>> fr[[1, 5, 6]]      # Columns 2, 6, and 7
        >>> fr[0:50, [1,2,3]]  # First 50 rows, columns 2, 3, and 4
        """
        # Select columns based on a string, a list of strings, an int or a slice.
        # Note that the python column selector handles the case of negative
        # selections, or out-of-range selections - without having to compute
        # self._ncols in the front-end - which would force eager evaluation just to
        # range check in the front-end.
        new_ncols = -1
        new_nrows = -1
        new_names = None
        new_types = None
        fr = None
        flatten = False
        if isinstance(item, slice):
            item = normalize_slice(item, self.ncols)
        if is_type(item, str, int, list, slice):
            new_ncols, new_names, new_types, item = self._compute_ncol_update(item)
            new_nrows = self.nrow
            fr = H2OFrame._expr(expr=ExprNode("cols_py", self, item))
        elif isinstance(item, (ExprNode, H2OFrame)):
            new_ncols = self.ncol
            new_names = self.names
            new_types = self.types
            new_nrows = -1  # have a "big" predicate column -- update cache later on...
            fr = H2OFrame._expr(expr=ExprNode("rows", self, item))
        elif isinstance(item, tuple):
            rows, cols = item
            allrows = allcols = False
            if isinstance(cols, slice):
                cols = normalize_slice(cols, self.ncols)
                allcols = cols == slice(0, self.ncols, 1)
            if isinstance(rows, slice):
                rows = normalize_slice(rows, self.nrows)
                allrows = rows == slice(0, self.nrows, 1)

            if allrows and allcols: return self  # fr[:,:]    -> all rows and columns.. return self
            if allrows:
                new_ncols, new_names, new_types, cols = self._compute_ncol_update(cols)
                new_nrows = self.nrow
                fr = H2OFrame._expr(expr=ExprNode("cols_py", self, cols))  # fr[:,cols] -> really just a column slice
            if allcols:
                new_ncols = self.ncols
                new_names = self.names
                new_types = self.types
                new_nrows, rows = self._compute_nrow_update(rows)
                fr = H2OFrame._expr(expr=ExprNode("rows", self, rows))  # fr[rows,:] -> really just a row slices

            if not allrows and not allcols:
                new_ncols, new_names, new_types, cols = self._compute_ncol_update(cols)
                new_nrows, rows = self._compute_nrow_update(rows)
                fr = H2OFrame._expr(expr=ExprNode("rows", ExprNode("cols_py", self, cols), rows))

            flatten = is_type(rows, int) and is_type(cols, str, int)
        else:
            raise ValueError("Unexpected __getitem__ selector: " + str(type(item)) + " " + str(item.__class__))

        assert fr is not None
        # Pythonic: if the row & col selector turn into ints (or a single col
        # name), then extract the single element out of the Frame.  Otherwise
        # return a Frame, EVEN IF the selectors are e.g. slices-of-1-value.
        if flatten:
            return fr.flatten()

        fr._ex._cache.ncols = new_ncols
        fr._ex._cache.nrows = new_nrows
        fr._ex._cache.names = new_names
        fr._ex._cache.types = new_types
        fr._is_frame = self._is_frame
        return fr

    def _compute_ncol_update(self, item):  # computes new ncol, names, and types
        try:
            new_ncols = -1
            if isinstance(item, list):
                new_ncols = len(item)
                if _is_str_list(item):
                    new_types = {k: self.types[k] for k in item}
                    new_names = item
                else:
                    new_names = [self.names[i] for i in item]
                    new_types = {name: self.types[name] for name in new_names}
            elif isinstance(item, slice):
                assert slice_is_normalized(item)
                new_names = self.names[item]
                new_types = {name: self.types[name] for name in new_names}
            elif is_type(item, str, int):
                new_ncols = 1
                if is_type(item, str):
                    new_names = [item]
                    new_types = None if item not in self.types else {item: self.types[item]}
                else:
                    new_names = [self.names[item]]
                    new_types = {new_names[0]: self.types[new_names[0]]}
            else:
                raise ValueError("Unexpected type: " + str(type(item)))
            return (new_ncols, new_names, new_types, item)
        except:
            return (-1, None, None, item)

    def _compute_nrow_update(self, item):
        try:
            new_nrows = -1
            if isinstance(item, list):
                new_nrows = len(item)
            elif isinstance(item, slice):
                assert slice_is_normalized(item)
                new_nrows = (item.stop - item.start + item.step - 1) // item.step
            elif isinstance(item, H2OFrame):
                new_nrows = -1
            else:
                new_nrows = 1
            return [new_nrows, item]
        except:
            return [-1, item]


    def __setitem__(self, item, value):
        """
        Replace, update or add column(s) in an H2OFrame.

        :param item: A 0-based index of a column, or a column name, or a list of column names, or a slice.
            Alternatively, this may also be a two-element tuple where the first element in the tuple is a row selector,
            and the second element is a row selector. Finally, this can also be a boolean frame indicating which
            rows/columns to modify. If ``item`` is a column name that does not exist in the frame, then a new column
            will be appended to the current frame.
        :param value: The value replacing elements at positions given by ``item``. This can be either a constant, or
            another frame.
        """
        # TODO: add far stronger type checks, so that we never run in a situation where the server has to
        #       tell us that we requested an illegal operation.
        assert_is_type(item, str, int, tuple, list, H2OFrame)
        assert_is_type(value, None, numeric, str, H2OFrame)
        col_expr = None
        row_expr = None
        colname = None  # When set, we are doing an append

        if is_type(item, str):  # String column name, could be new or old
            if item in self.names:
                col_expr = self.names.index(item)  # Update an existing column
            else:
                col_expr = self.ncols
                colname = item  # New, append
        elif is_type(item, int):
            if not(-self.ncols <= item < self.ncols):
                raise H2OValueError("Incorrect column index: %d" % item)
            col_expr = item  # Column by number
            if col_expr < 0:
                col_expr += self.ncols
        elif isinstance(item, tuple):  # Both row and col specifiers
            # Need more type checks
            row_expr = item[0]
            col_expr = item[1]
            if is_type(col_expr, str):  # Col by name
                if col_expr not in self.names:  # Append
                    colname = col_expr
                    col_expr = self.ncol
            elif is_type(col_expr, int):
                if not(-self.ncols <= col_expr < self.ncols):
                    raise H2OValueError("Incorrect column index: %d" % item)
                if col_expr < 0:
                    col_expr += self.ncols
            elif isinstance(col_expr, slice):  # Col by slice
                if col_expr.start is None and col_expr.stop is None:
                    col_expr = slice(0, self.ncol)  # Slice of all
            if isinstance(row_expr, slice):
                start = row_expr.start
                step = row_expr.step
                stop = row_expr.stop
                if start is None: start = 0
                if stop is None: stop = self.nrows
                row_expr = slice(start, stop, step)
        elif isinstance(item, H2OFrame):
            row_expr = item  # Row slicing
        elif isinstance(item, list):
            col_expr = item

        if value is None: value = float("nan")
        value_is_own_subframe = isinstance(value, H2OFrame) and self._is_frame_in_self(value)
        old_cache = self._ex._cache
        if colname is None:
            self._ex = ExprNode(":=", self, value, col_expr, row_expr)
            self._ex._cache.fill_from(old_cache)
            if isinstance(value, H2OFrame) and \
                    value._ex._cache.types_valid() and \
                    self._ex._cache.types_valid():
                self._ex._cache._types.update(value._ex._cache.types)
            else:
                self._ex._cache.types = None
        else:
            self._ex = ExprNode("append", self, value, colname)
            self._ex._cache.fill_from(old_cache)
            self._ex._cache.names = self.names + [colname]
            self._ex._cache._ncols += 1
            if self._ex._cache.types_valid() and isinstance(value, H2OFrame) and value._ex._cache.types_valid():
                self._ex._cache._types[colname] = list(viewvalues(value._ex._cache.types))[0]
            else:
                self._ex._cache.types = None
        if value_is_own_subframe:
            value._ex = None  # wipe out to keep ref counts correct


    def _is_frame_in_self(self, frame):
        if self._ex is frame._ex: return True
        if frame._ex._children is None: return False
        return any(self._is_expr_in_self(ch) for ch in frame._ex._children)

    def _is_expr_in_self(self, expr):
        if not isinstance(expr, ExprNode): return False
        if self._ex is expr: return True
        if expr._children is None: return False
        return any(self._is_expr_in_self(ch) for ch in expr._children)

    def drop(self, index, axis=1):
        """
        Drop a single column or row or a set of columns or rows from a H2OFrame.

        Dropping a column or row is not in-place.
        Indices of rows and columns are zero-based.

        :param index: A list of column indices, column names, or row indices to drop; or
            a string to drop a single column by name; or an int to drop a single column by index.

        :param int axis: If 1 (default), then drop columns; if 0 then drop rows.

        :returns: a new H2OFrame with the respective dropped columns or rows. The original H2OFrame remains
            unchanged.
        """
        if axis == 1:
            if not isinstance(index, list):
                #If input is a string, i.e., "C1":
                if is_type(index, str):
                    #Check if index is an actual column(s) in the frame
                    if index not in self.names:
                        raise H2OValueError("Column(s) selected to drop are not in original frame: %r" % index)
                    index = self.names.index(index)
                #If input is an int indicating a column index, i.e., 3:
                elif is_type(index, int):
                    #Check if index is an actual column index in the frame
                    if index > self.ncol:
                        raise H2OValueError("Column index selected to drop is not part of the frame: %r" % index)
                    if index < 0:
                        raise H2OValueError("Column index selected to drop is not positive: %r" % index)

                fr = H2OFrame._expr(expr=ExprNode("cols", self, -(index + 1)), cache=self._ex._cache)
                fr._ex._cache.ncols -= 1
                fr._ex._cache.names = self.names[:index] + self.names[index + 1:]
                fr._ex._cache.types = {name: self.types[name] for name in fr._ex._cache.names}
                return fr

            elif isinstance(index, list):
                #If input is an int array indicating a column index, i.e., [3] or [1,2,3]:
                if is_type(index, [int]):
                    if max(index) > self.ncol:
                        raise H2OValueError("Column index selected to drop is not part of the frame: %r" % index)
                    if min(index) < 0:
                        raise H2OValueError("Column index selected to drop is not positive: %r" % index)
                    for i in range(len(index)):
                        index[i] = -(index[i] + 1)
                #If index is a string array, i.e., ["C1", "C2"]
                elif is_type(index, [str]):
                    #Check if index is an actual column(s) in the frame
                    if not set(index).issubset(self.names):
                        raise H2OValueError("Column(s) selected to drop are not in original frame: %r" % index)
                    for i in range(len(index)):
                        index[i] = -(self.names.index(index[i]) + 1)
                fr = H2OFrame._expr(expr=ExprNode("cols", self, index), cache=self._ex._cache)
                fr._ex._cache.ncols -= len(index)
                fr._ex._cache.names = [i for i in self.names
                                       if self.names.index(i) not in list(map(lambda x: abs(x) - 1, index))]
                fr._ex._cache.types = {name: fr.types[name] for name in fr._ex._cache.names}

            else:
                raise ValueError("Invalid column index types. Must either be a list of all int indexes, "
                                 "a string list of all column names, a single int index, or"
                                 "a single string for dropping columns.")
            return fr
        elif axis == 0:
            if is_type(index, [int]):
                #Check if index is an actual column index in the frame
                if max(index) > self.nrow:
                    raise H2OValueError("Row index selected to drop is not part of the frame: %r" % index)
                if min(index) < 0:
                    raise H2OValueError("Row index selected to drop is not positive: %r" % index)
                index = [-(x + 1) for x in index]
                fr = H2OFrame._expr(expr=ExprNode("rows", self, index), cache=self._ex._cache)
                fr._ex._cache.nrows -= len(index)
            else:
                raise ValueError("Invalid row indexes. Must be a list of int row indexes to drop from the H2OFrame.")
        return fr


    def pop(self, i):
        """
        Pop a column from the H2OFrame at index i.

        :param i: The index (int) or name (str) of the column to pop.
        :returns: an H2OFrame containing the column dropped from the current frame; the current frame is modified
            in-place and loses the column.
        """
        if is_type(i, str): i = self.names.index(i)
        col = H2OFrame._expr(expr=ExprNode("cols", self, i))
        old_cache = self._ex._cache
        self._ex = ExprNode("cols", self, -(i + 1))
        self._ex._cache.ncols -= 1
        self._ex._cache.names = old_cache.names[:i] + old_cache.names[i + 1:]
        self._ex._cache.types = {name: old_cache.types[name] for name in self._ex._cache.names}
        self._ex._cache._data = None
        col._ex._cache.ncols = 1
        col._ex._cache.names = [old_cache.names[i]]
        return col


    def quantile(self, prob=None, combine_method="interpolate", weights_column=None):
        """
        Compute quantiles.

        :param List[float] prob: list of probabilities for which quantiles should be computed.
        :param str combine_method: for even samples this setting determines how to combine quantiles. This can be
            one of ``"interpolate"``, ``"average"``, ``"low"``, ``"high"``.
        :param weights_column: optional weights for each row. If not given, all rows are assumed to have equal
            importance. This parameter can be either the name of column containing the observation weights in
            this frame, or a single-column separate H2OFrame of observation weights.

        :returns: a new H2OFrame containing the quantiles and probabilities.
        """
        if len(self) == 0: return self
        if prob is None: prob = [0.01, 0.1, 0.25, 0.333, 0.5, 0.667, 0.75, 0.9, 0.99]
        if weights_column is None:
            weights_column = "_"
        else:
            assert_is_type(weights_column, str, I(H2OFrame, lambda wc: wc.ncol == 1 and wc.nrow == self.nrow))
            if isinstance(weights_column, H2OFrame):
                merged = self.cbind(weights_column)
                weights_column = merged.names[-1]
                return H2OFrame._expr(expr=ExprNode("quantile", merged, prob, combine_method, weights_column))
        return H2OFrame._expr(expr=ExprNode("quantile", self, prob, combine_method, weights_column))


    def concat(self, frames, axis=1):
        """
        Append multiple H2OFrames to this frame, column-wise or row-wise.

        :param List[H2OFrame] frames: list of frames that should be appended to the current frame.
        :param int axis: if 1 then append column-wise (default), if 0 then append row-wise.

        :returns: an H2OFrame of the combined datasets.
        """
        if len(frames) == 0:
            raise ValueError("Input list of frames is empty! Nothing to concat.")

        if axis == 1:
            df = self.cbind(frames)
        else:
            df = self.rbind(frames)
        return df


    def cbind(self, data):
        """
        Append data to this frame column-wise.

        :param H2OFrame data: append columns of frame ``data`` to the current frame. You can also cbind a number,
            in which case it will get converted into a constant column.

        :returns: new H2OFrame with all frames in ``data`` appended column-wise.
        """
        assert_is_type(data, H2OFrame, numeric, [H2OFrame, numeric])
        frames = [data] if not isinstance(data, list) else data
        new_cols = list(self.columns)
        new_types = dict(self.types)
        for frame in frames:
            if isinstance(frame, H2OFrame):
                if frame.nrow != self.nrow:
                    raise H2OValueError("Cannot bind a dataframe with %d rows to a data frame with %d rows: "
                                        "the number of rows should match" % (frame.nrow, self.nrow))
                new_cols += frame.columns
                new_types.update(frame.types)
            else:
                new_cols += [None]
        unique_cols = set(new_cols)
        fr = H2OFrame._expr(expr=ExprNode("cbind", self, *frames), cache=self._ex._cache)
        fr._ex._cache.ncols = len(new_cols)
        if len(new_cols) == len(unique_cols) and None not in unique_cols:
            fr._ex._cache.names = new_cols
            fr._ex._cache.types = new_types
        else:
            # Invalidate names and types since they contain duplicate / unknown names, and the server will choose those.
            fr._ex._cache.names = None
            fr._ex._cache.types = None
        return fr


    def rbind(self, data):
        """
        Append data to this frame row-wise.

        :param data: an H2OFrame or a list of H2OFrame's to be combined with current frame row-wise.
        :returns: this H2OFrame with all frames in data appended row-wise.
        """
        assert_is_type(data, H2OFrame, [H2OFrame])
        frames = [data] if not isinstance(data, list) else data
        for frame in frames:
            if frame.ncol != self.ncol:
                raise H2OValueError("Cannot row-bind a dataframe with %d columns to a data frame with %d columns: "
                                    "the columns must match" % (frame.ncol, self.ncol))
            if frame.columns != self.columns or frame.types != self.types:
                raise H2OValueError("Column names and types must match for rbind() to work")
        fr = H2OFrame._expr(expr=ExprNode("rbind", self, *frames), cache=self._ex._cache)
        fr._ex._cache.nrows = self.nrow + sum(frame.nrow for frame in frames)
        return fr


    def split_frame(self, ratios=None, destination_frames=None, seed=None):
        """
        Split a frame into distinct subsets of size determined by the given ratios.

        The number of subsets is always 1 more than the number of ratios given. Note that
        this does not give an exact split. H2O is designed to be efficient on big data
        using a probabilistic splitting method rather than an exact split. For example
        when specifying a split of 0.75/0.25, H2O will produce a test/train split with
        an expected value of 0.75/0.25 rather than exactly 0.75/0.25. On small datasets,
        the sizes of the resulting splits will deviate from the expected value more than
        on big data, where they will be very close to exact.

        :param List[float] ratios: The fractions of rows for each split.
        :param List[str] destination_frames: The names of the split frames.
        :param int seed: seed for the random number generator

        :returns: A list of H2OFrames
        """
        assert_is_type(ratios, [numeric], None)
        assert_is_type(destination_frames, [str], None)
        assert_is_type(seed, int, None)

        if ratios is None:
            ratios = [0.75]
        if not ratios:
            raise ValueError("Ratios array may not be empty")

        if destination_frames is not None:
            if len(ratios) + 1 != len(destination_frames):
                raise ValueError("The number of provided destination_frames must be one more "
                                 "than the number of provided ratios")

        num_slices = len(ratios) + 1
        boundaries = []

        last_boundary = 0
        i = 0
        while i < num_slices - 1:
            ratio = ratios[i]
            if ratio < 0:
                raise ValueError("Ratio must be greater than 0")
            boundary = last_boundary + ratio
            if boundary >= 1.0:
                raise ValueError("Ratios must add up to less than 1.0")
            boundaries.append(boundary)
            last_boundary = boundary
            i += 1

        splits = []
        tmp_runif = self.runif(seed)
        tmp_runif.frame_id = "%s_splitter" % _py_tmp_key(h2o.connection().session_id)

        i = 0
        while i < num_slices:
            if i == 0:
                # lower_boundary is 0.0
                upper_boundary = boundaries[i]
                tmp_slice = self[(tmp_runif <= upper_boundary), :]
            elif i == num_slices - 1:
                lower_boundary = boundaries[i - 1]
                # upper_boundary is 1.0
                tmp_slice = self[(tmp_runif > lower_boundary), :]
            else:
                lower_boundary = boundaries[i - 1]
                upper_boundary = boundaries[i]
                tmp_slice = self[((tmp_runif > lower_boundary) & (tmp_runif <= upper_boundary)), :]

            if destination_frames is None:
                splits.append(tmp_slice)
            else:
                destination_frame_id = destination_frames[i]
                tmp_slice.frame_id = destination_frame_id
                splits.append(tmp_slice)

            i += 1

        del tmp_runif
        return splits


    def group_by(self, by):
        """
        Return a new ``GroupBy`` object using this frame and the desired grouping columns.

        The returned groups are sorted by the natural group-by column sort.

        :param by: The columns to group on (either a single column name, or a list of column names, or
            a list of column indices).
        """
        assert_is_type(by, str, int, [str, int])
        return GroupBy(self, by)

    def sort(self, by, ascending=[]):
        """
        Return a new Frame that is sorted by column(s) in ascending order. A fully distributed and parallel sort.
        However, the original frame can contain String columns but sorting cannot be done on String columns.
        Default sorting direction is ascending.

        :param by: The column to sort by (either a single column name, or a list of column names, or
            a list of column indices)
        :param ascending: Boolean array to denote sorting direction for each sorting column.  True for ascending
            sort and False for descending sort.

        :return:  a new sorted Frame
        """
        assert_is_type(by, str, int, [str, int])
        if type(by) != list: by = [by]
        if type(ascending) != list: ascending = [ascending]   # convert to list
        ascendingI=[1]*len(by)  # intitalize sorting direction to ascending by default
        for c in by:
            if self.type(c) not in ["enum","time","int","real","string"]:
                raise H2OValueError("Sort by column: " + str(c) + " not of enum, time, int, real, or string type")
        if len(ascending)>0:  # user did not specify sort direction, assume all columns ascending
            assert len(ascending)==len(by), "Sorting direction must be specified for each sorted column."
            for index in range(len(by)):
                ascendingI[index]=1 if ascending[index] else -1
        return H2OFrame._expr(expr=ExprNode("sort",self,by,ascendingI))

    def fillna(self,method="forward",axis=0,maxlen=1):
        """
        Return a new Frame that fills NA along a given axis and along a given direction with a maximum fill length

        :param method: ``"forward"`` or ``"backward"``
        :param axis:  0 for columnar-wise or 1 for row-wise fill
        :param maxlen: Max number of consecutive NA's to fill
        
        :return: 
        """
        assert_is_type(axis, 0, 1)
        assert_is_type(method,str)
        assert_is_type(maxlen, int)
        return H2OFrame._expr(expr=ExprNode("h2o.fillna",self,method,axis,maxlen))

    def impute(self, column=-1, method="mean", combine_method="interpolate", by=None, group_by_frame=None, values=None):
        """
        Impute missing values into the frame, modifying it in-place.

        :param int column: Index of the column to impute, or -1 to impute the entire frame.
        :param str method: The method of imputation: ``"mean"``, ``"median"``, or ``"mode"``.
        :param str combine_method: When the method is ``"median"``, this setting dictates how to combine quantiles
            for even samples. One of ``"interpolate"``, ``"average"``, ``"low"``, ``"high"``.
        :param by: The list of columns to group on.
        :param H2OFrame group_by_frame: Impute the values with this pre-computed grouped frame.
        :param List values: The list of impute values, one per column. None indicates to skip the column.

        :returns: A list of values used in the imputation or the group-by result used in imputation.
        """
        if is_type(column, str): column = self.names.index(column)
        if is_type(by, str):     by = self.names.index(by)

        if values is None:
            values = "_"
        else:
            assert len(values) == len(self.columns), "Length of values does not match length of columns"
            # convert string values to categorical num values
            values2 = []
            for i in range(0,len(values)):
                if self.type(i) == "enum":
                    try:
                        values2.append(self.levels()[i].index(values[i]))
                    except:
                        raise H2OValueError("Impute value of: " + values[i] + " not found in existing levels of"
                                            " column: " + self.col_names[i])
                else:
                    values2.append(values[i])
            values = values2
        if group_by_frame is None: group_by_frame = "_"


        # This code below is needed to ensure the frame (self) exists on the server. Without it, self._ex._cache.fill()
        # fails with an assertion that ._id is None.
        # This code should be removed / reworked once we have a more consistent strategy of dealing with frames.
        self._ex._eager_frame()

        if by is not None or group_by_frame is not "_":
            res = H2OFrame._expr(
                expr=ExprNode("h2o.impute", self, column, method, combine_method, by, group_by_frame, values))._frame()
        else:
            res = ExprNode("h2o.impute", self, column, method, combine_method, by, group_by_frame,
                           values)._eager_scalar()

        self._ex._cache.flush()
        self._ex._cache.fill(10)
        return res


    def merge(self, other, all_x=False, all_y=False, by_x=None, by_y=None, method="auto"):
        """
        Merge two datasets based on common column names.  We do not support all_x=True and all_y=True.
        Only one can be True or none is True.  The default merge method is auto and it will default to the
        radix method.  The radix method will return the correct merge result regardless of duplicated rows
         in the right frame.  In addition, the radix method can perform merge even if you have string columns
         in your frames.  If there are duplicated rows in your rite frame, they will not be included if you use
        the hash method.  The hash method cannot perform merge if you have string columns in your left frame.
        Hence, we consider the radix method superior to the hash method and is the default method to use.

        :param H2OFrame other: The frame to merge to the current one. By default, must have at least one column in common with
            this frame, and all columns in common are used as the merge key.  If you want to use only a subset of the
            columns in common, rename the other columns so the columns are unique in the merged result.
        :param bool all_x: If True, include all rows from the left/self frame
        :param bool all_y: If True, include all rows from the right/other frame
        :param by_x: list of columns in the current frame to use as a merge key.
        :param by_y: list of columns in the ``other`` frame to use as a merge key. Should have the same number of
            columns as in the ``by_x`` list.
        :param method: string representing the merge method, one of auto(default), radix or hash.

        :returns: New H2OFrame with the result of merging the current frame with the ``other`` frame.
        """

        if by_x is None and by_y is None:
            common_names = list(set(self.names) & set(other.names))
            if not common_names:
                raise H2OValueError("No columns in common to merge on!")

        if by_x is None:
            by_x = [self.names.index(c) for c in common_names]
        else:
            by_x = _getValidCols(by_x,self)

        if by_y is None:
            by_y = [other.names.index(c) for c in common_names]
        else:
            by_y = _getValidCols(by_y,other)


        return H2OFrame._expr(expr=ExprNode("merge", self, other, all_x, all_y, by_x, by_y, method))


    def relevel(self, y):
        """
        Reorder levels of an H2O factor for one single column of a H2O frame

        The levels of a factor are reordered such that the reference level is at level 0, all remaining levels are
        moved down as needed.

        :param str y: The reference level
        :returns: New reordered factor column
        """
        return H2OFrame._expr(expr=ExprNode("relevel", self, quote(y)))


    def insert_missing_values(self, fraction=0.1, seed=None):
        """
        Insert missing values into the current frame, modifying it in-place.

        Randomly replaces a user-specified fraction of entries in a H2O dataset with missing
        values.

        :param float fraction: A number between 0 and 1 indicating the fraction of entries to replace with missing.
        :param int seed: The seed for the random number generator used to determine which values to make missing.

        :returns: the original H2OFrame with missing values inserted.
        """
        kwargs = {}
        kwargs['dataset'] = self.frame_id  # Eager; forces eval now for following REST call
        kwargs['fraction'] = fraction
        if seed is not None: kwargs['seed'] = seed
        job = {}
        job['job'] = h2o.api("POST /3/MissingInserter", data=kwargs)
        H2OJob(job, job_type=("Insert Missing Values")).poll()
        self._ex._cache.flush()
        return self


    def min(self):
        """The minimum value of all frame entries."""
        return ExprNode("min", self)._eager_scalar()


    def max(self):
        """The maximum value of all frame entries."""
        return ExprNode("max", self)._eager_scalar()


    def sum(self, skipna=True, axis=0, **kwargs):
        """
        Compute the frame's sum by-column (or by-row).

        :param bool skipna: If True (default), then NAs are ignored during the computation. Otherwise presence
            of NAs renders the entire result NA.
        :param int axis: Direction of sum computation. If 0 (default), then sum is computed columnwise, and the result
            is a frame with 1 row and number of columns as in the original frame. If 1, then sum is computed rowwise
            and the result is a frame with 1 column (called "sum"), and number of rows equal to the number of rows
            in the original frame.
        :returns: either an aggregated value with sum of values per-column (old semantic); or an H2OFrame containing sum of values
            per-column/per-row in the original frame (new semantic). The new semantic is triggered by either
            providing the ``return_frame=True`` parameter, or having the ``general.allow_breaking_changed`` config
            option turned on.
        """
        assert_is_type(skipna, bool)
        assert_is_type(axis, 0, 1)
        # Deprecated since 2016-10-14,
        if "na_rm" in kwargs:
            warnings.warn("Parameter na_rm is deprecated; use skipna instead", category=DeprecationWarning)
            na_rm = kwargs.pop("na_rm")
            assert_is_type(na_rm, bool)
            skipna = na_rm  # don't assign to skipna directly, to help with error reporting
        # Determine whether to return a frame or a list
        return_frame = get_config_value("general.allow_breaking_changes", False)
        if "return_frame" in kwargs:
            return_frame = kwargs.pop("return_frame")
            assert_is_type(return_frame, bool)
        if kwargs:
            raise H2OValueError("Unknown parameters %r" % list(kwargs))

        if return_frame:
            return H2OFrame._expr(ExprNode("sumaxis", self, skipna, axis))
        else:
            return ExprNode("sumNA" if skipna else "sum", self)._eager_scalar()


    def mean(self, skipna=True, axis=0, **kwargs):
        """
        Compute the frame's means by-column (or by-row).

        :param bool skipna: If True (default), then NAs are ignored during the computation. Otherwise presence
            of NAs renders the entire result NA.
        :param int axis: Direction of mean computation. If 0 (default), then mean is computed columnwise, and the
            result is a frame with 1 row and number of columns as in the original frame. If 1, then mean is computed
            rowwise and the result is a frame with 1 column (called "mean"), and number of rows equal to the number
            of rows in the original frame.
        :returns: either a list of mean values per-column (old semantic); or an H2OFrame containing mean values
            per-column/per-row from the original frame (new semantic). The new semantic is triggered by either
            providing the ``return_frame=True`` parameter, or having the ``general.allow_breaking_changed`` config
            option turned on.
        """
        assert_is_type(skipna, bool)
        assert_is_type(axis, 0, 1)
        # Deprecated since 2016-10-14,
        if "na_rm" in kwargs:
            warnings.warn("Parameter na_rm is deprecated; use skipna instead", category=DeprecationWarning)
            na_rm = kwargs.pop("na_rm")
            assert_is_type(na_rm, bool)
            skipna = na_rm  # don't assign to skipna directly, to help with error reporting
        # Determine whether to return a frame or a list
        return_frame = get_config_value("general.allow_breaking_changes", False)
        if "return_frame" in kwargs:
            return_frame = kwargs.pop("return_frame")
            assert_is_type(return_frame, bool)
        if kwargs:
            raise H2OValueError("Unknown parameters %r" % list(kwargs))

        new_frame = H2OFrame._expr(ExprNode("mean", self, skipna, axis))
        if return_frame:
            return new_frame
        else:
            return new_frame.getrow()


    def skewness(self, na_rm=False):
        """
        Compute the skewness of each column in the frame.

        :param bool na_rm: If True, then ignore NAs during the computation.
        :returns: A list containing the skewness for each column (NaN for non-numeric columns).
        """
        return ExprNode("skewness", self, na_rm)._eager_scalar()


    def kurtosis(self, na_rm=False):
        """
        Compute the kurtosis of each column in the frame.

        We calculate the common kurtosis, such that kurtosis(normal distribution) is 3.

        :param bool na_rm: If True, then ignore NAs during the computation.
        :returns: A list containing the kurtosis for each column (NaN for non-numeric columns).
        """
        return ExprNode("kurtosis", self, na_rm)._eager_scalar()


    def nacnt(self):
        """
        Count of NAs for each column in this H2OFrame.

        :returns: A list of the na counts (one entry per column).
        """
        return ExprNode("naCnt", self)._eager_scalar()


    def median(self, na_rm=False):
        """
        Compute the median of each column in the frame.

        :param bool na_rm: If True, then ignore NAs during the computation.
        :returns: A list containing the median for each column (NaN for non-numeric columns).
        """
        return ExprNode("median", self, na_rm)._eager_scalar()


    def var(self, y=None, na_rm=False, use=None):
        """
        Compute the variance-covariance matrix of one or two H2OFrames.

        :param H2OFrame y: If this parameter is given, then a covariance  matrix between the columns of the target
            frame and the columns of ``y`` is computed. If this parameter is not provided then the covariance matrix
            of the target frame is returned. If target frame has just a single column, then return the scalar variance
            instead of the matrix. Single rows are treated as single columns.
        :param str use: A string indicating how to handle missing values. This could be one of the following:

            - ``"everything"``: outputs NaNs whenever one of its contributing observations is missing
            - ``"all.obs"``: presence of missing observations will throw an error
            - ``"complete.obs"``: discards missing values along with all observations in their rows so that only
              complete observations are used
        :param bool na_rm: an alternative to ``use``: when this is True then default value for ``use`` is
            ``"everything"``; and if False then default ``use`` is ``"complete.obs"``. This parameter has no effect
            if ``use`` is given explicitly.

        :returns: An H2OFrame of the covariance matrix of the columns of this frame (if ``y`` is not given),
            or with the columns of ``y`` (if ``y`` is given). However when this frame and ``y`` are both single rows
            or single columns, then the variance is returned as a scalar.
        """
        symmetric = False
        if y is None:
            y = self
            symmetric = True
        if use is None: use = "complete.obs" if na_rm else "everything"
        if self.nrow == 1 or (self.ncol == 1 and y.ncol == 1):
            return ExprNode("var", self, y, use, symmetric)._eager_scalar()
        return H2OFrame._expr(expr=ExprNode("var", self, y, use, symmetric))._frame()


    def sd(self, na_rm=False):
        """
        Compute the standard deviation for each column in the frame.

        :param bool na_rm: if True, then NAs will be removed from the computation.
        :returns: A list containing the standard deviation for each column (NaN for non-numeric columns).
        """
        return ExprNode("sd", self, na_rm)._eager_scalar()


    def cor(self, y=None, na_rm=False, use=None):
        """
        Compute the correlation matrix of one or two H2OFrames.

        :param H2OFrame y: If this parameter is provided, then compute correlation between the columns of ``y``
            and the columns of the current frame. If this parameter is not given, then just compute the correlation
            matrix for the columns of the current frame.
        :param str use: A string indicating how to handle missing values. This could be one of the following:

            - ``"everything"``: outputs NaNs whenever one of its contributing observations is missing
            - ``"all.obs"``: presence of missing observations will throw an error
            - ``"complete.obs"``: discards missing values along with all observations in their rows so that only
              complete observations are used
        :param bool na_rm: an alternative to ``use``: when this is True then default value for ``use`` is
            ``"everything"``; and if False then default ``use`` is ``"complete.obs"``. This parameter has no effect
            if ``use`` is given explicitly.

        :returns: An H2OFrame of the correlation matrix of the columns of this frame (if ``y`` is not given),
            or with the columns of ``y`` (if ``y`` is given). However when this frame and ``y`` are both single rows
            or single columns, then the correlation is returned as a scalar.
        """
        assert_is_type(y, H2OFrame, None)
        assert_is_type(na_rm, bool)
        assert_is_type(use, None, "everything", "all.obs", "complete.obs")
        if y is None:
            y = self
        if use is None: use = "complete.obs" if na_rm else "everything"
        if self.nrow == 1 or (self.ncol == 1 and y.ncol == 1): return ExprNode("cor", self, y, use)._eager_scalar()
        return H2OFrame._expr(expr=ExprNode("cor", self, y, use))._frame()


    def distance(self, y, measure=None):
        """
        Compute a pairwise distance measure between all rows of two numeric H2OFrames.

        :param H2OFrame y: Frame containing queries (small)
        :param str use: A string indicating what distance measure to use. Must be one of:

            - ``"l1"``:        Absolute distance (L1-norm, >=0)
            - ``"l2"``:        Euclidean distance (L2-norm, >=0)
            - ``"cosine"``:    Cosine similarity (-1...1)
            - ``"cosine_sq"``: Squared Cosine similarity (0...1)

        :examples:
          >>>
          >>> iris_h2o = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris.csv"))
          >>> references = iris_h2o[10:150,0:4
          >>> queries    = iris_h2o[0:10,0:4]
          >>> A = references.distance(queries, "l1")
          >>> B = references.distance(queries, "l2")
          >>> C = references.distance(queries, "cosine")
          >>> D = references.distance(queries, "cosine_sq")
          >>> E = queries.distance(references, "l1")
          >>> (E.transpose() == A).all()

        :returns: An H2OFrame of the matrix containing pairwise distance / similarity between the 
            rows of this frame (N x p) and ``y`` (M x p), with dimensions (N x M).
        """
        assert_is_type(y, H2OFrame)
        if measure is None: measure = "l2"
        return H2OFrame._expr(expr=ExprNode("distance", self, y, measure))._frame()


    def strdistance(self, y, measure=None, compare_empty=True):
        """
        Compute element-wise string distances between two H2OFrames. Both frames need to have the same
        shape and only contain string/factor columns.

        :param H2OFrame y: A comparison frame.
        :param str measure: A string identifier indicating what string distance measure to use. Must be one of:

            - ``"lv"``:        Levenshtein distance
            - ``"lcs"``:       Longest common substring distance
            - ``"qgram"``:     q-gram distance
            - ``"jaccard"``:   Jaccard distance between q-gram profiles
            - ``"jw"``:        Jaro, or Jaro-Winker distance
            - ``"soundex"``:   Distance based on soundex encoding

        :param compare_empty if set to FALSE, empty strings will be handled as NaNs

        :examples:
          >>>
          >>> x = h2o.H2OFrame.from_python(['Martha', 'Dwayne', 'Dixon'], column_types=['factor'])
          >>> y = h2o.H2OFrame.from_python(['Marhta', 'Duane', 'Dicksonx'], column_types=['string'])
          >>> x.strdistance(y, measure="jw")

        :returns: An H2OFrame of the matrix containing element-wise distance between the
            strings of this frame and ``y``. The returned frame has the same shape as the input frames.
        """
        assert_is_type(y, H2OFrame)
        assert_is_type(measure, Enum('lv', 'lcs', 'qgram', 'jaccard', 'jw', 'soundex'))
        assert_is_type(compare_empty, bool)
        return H2OFrame._expr(expr=ExprNode("strDistance", self, y, measure, compare_empty))._frame()


    def asfactor(self):
        """
        Convert columns in the current frame to categoricals.

        :returns: new H2OFrame with columns of the "enum" type.
        """
        for colname in self.names:
            t = self.types[colname]
            if t not in {"bool", "int", "string", "enum"}:
                raise H2OValueError("Only 'int' or 'string' are allowed for "
                                    "asfactor(), got %s:%s " % (colname, t))
        fr = H2OFrame._expr(expr=ExprNode("as.factor", self), cache=self._ex._cache)
        if fr._ex._cache.types_valid():
            fr._ex._cache.types = {name: "enum" for name in self.types}
        else:
            raise H2OTypeError("Types are not available in result")
        
        return fr


    def isfactor(self):
        """
        Test which columns in the current frame are categorical.

        :returns: a list of True/False indicating for each column in the frame whether it is categorical.
        """
        return [bool(o) for o in ExprNode("is.factor", self)._eager_scalar()]


    def anyfactor(self):
        """Return True if there are any categorical columns in the frame."""
        return bool(ExprNode("any.factor", self)._eager_scalar())


    def categories(self):
        """
        Return the list of levels for an enum (categorical) column.

        This function can only be applied to single-column categorical frame.
        """
        if self.ncols != 1:
            raise H2OValueError("This operation only applies to a single factor column")
        if self.types[self.names[0]] != "enum":
            raise H2OValueError("Input is not a factor. This operation only applies to a single factor column")
        return self.levels()[0]


    def transpose(self):
        """
        Transpose rows and columns of this frame.

        :returns: new H2OFrame where with rows/columns from the original frame transposed.
        """
        return H2OFrame._expr(expr=ExprNode("t", self))


    def strsplit(self, pattern):
        """
        Split the strings in the target column on the given regular expression pattern.

        :param str pattern: The split pattern.
        :returns: H2OFrame containing columns of the split strings.
        """
        fr = H2OFrame._expr(expr=ExprNode("strsplit", self, pattern))
        fr._ex._cache.nrows = self.nrow
        return fr

    def tokenize(self, split):
        """
        Tokenize String

        tokenize() is similar to strsplit(), the difference between them is that tokenize() will store the tokenized
        text into a single column making it easier for additional processing (filtering stop words, word2vec algo, ...).

        :param str split The regular expression to split on.
        @return An H2OFrame with a single column representing the tokenized Strings. Original rows of the input DF are separated by NA.
        """
        fr = H2OFrame._expr(expr=ExprNode("tokenize", self, split))
        return fr

    def countmatches(self, pattern):
        """
        For each string in the frame, count the occurrences of the provided pattern.  If countmathces is applied to
        a frame, all columns of the frame must be type string, otherwise, the returned frame will contain errors.

        The pattern here is a plain string, not a regular expression. We will search for the occurrences of the
        pattern as a substring in element of the frame. This function is applicable to frames containing only
        string or categorical columns.

        :param str pattern: The pattern to count matches on in each string. This can also be a list of strings,
            in which case all of them will be searched for.
        :returns: numeric H2OFrame with the same shape as the original, containing counts of matches of the
            pattern for each cell in the original frame.
        """
        assert_is_type(pattern, str, [str])
        fr = H2OFrame._expr(expr=ExprNode("countmatches", self, pattern))
        fr._ex._cache.nrows = self.nrow
        fr._ex._cache.ncols = self.ncol
        return fr


    def trim(self):
        """
        Trim white space on the left and right of strings in a single-column H2OFrame.

        :returns: H2OFrame with trimmed strings.
        """
        fr = H2OFrame._expr(expr=ExprNode("trim", self))
        fr._ex._cache.nrows = self.nrow
        fr._ex._cache.ncol = self.ncol
        return fr


    def substring(self, start_index, end_index=None):
        """
        For each string, return a new string that is a substring of the original string.

        If end_index is not specified, then the substring extends to the end of the original string. If the start_index
        is longer than the length of the string, or is greater than or equal to the end_index, an empty string is
        returned. Negative start_index is coerced to 0.

        :param int start_index: The index of the original string at which to start the substring, inclusive.
        :param int end_index: The index of the original string at which to end the substring, exclusive.
        :returns: An H2OFrame containing the specified substrings.
        """
        fr = H2OFrame._expr(expr=ExprNode("substring", self, start_index, end_index))
        fr._ex._cache.nrows = self.nrow
        fr._ex._cache.ncol = self.ncol
        return fr


    def lstrip(self, set=" "):
        """
        Return a copy of the column with leading characters removed.

        The set argument is a string specifying the set of characters to be removed.
        If omitted, the set argument defaults to removing whitespace.

        :param character set: The set of characters to lstrip from strings in column.
        :returns: a new H2OFrame with the same shape as the original frame and having all its values
            trimmed from the left (equivalent of Python's ``str.lstrip()``).
        """
        # work w/ None; parity with python lstrip
        if set is None: set = " "

        fr = H2OFrame._expr(expr=ExprNode("lstrip", self, set))
        fr._ex._cache.nrows = self.nrow
        fr._ex._cache.ncol = self.ncol
        return fr


    def rstrip(self, set=" "):
        """
        Return a copy of the column with trailing characters removed.

        The set argument is a string specifying the set of characters to be removed.
        If omitted, the set argument defaults to removing whitespace.

        :param character set: The set of characters to rstrip from strings in column
        :returns: a new H2OFrame with the same shape as the original frame and having all its values
            trimmed from the right (equivalent of Python's ``str.rstrip()``).
        """
        # work w/ None; parity with python rstrip
        if set is None: set = " "

        fr = H2OFrame._expr(expr=ExprNode("rstrip", self, set))
        fr._ex._cache.nrows = self.nrow
        fr._ex._cache.ncol = self.ncol
        return fr


    def entropy(self):
        """
        For each string compute its Shannon entropy, if the string is empty the entropy is 0.

        :returns: an H2OFrame of Shannon entropies.
        """
        fr = H2OFrame._expr(expr=ExprNode("entropy", self))
        fr._ex._cache.nrows = self.nrow
        fr._ex._cache.ncol = self.ncol
        return fr


    def num_valid_substrings(self, path_to_words):
        """
        For each string, find the count of all possible substrings with 2 characters or more that are contained in
        the line-separated text file whose path is given.

        :param str path_to_words: Path to file that contains a line-separated list of strings considered valid.
        :returns: An H2OFrame with the number of substrings that are contained in the given word list.
        """
        assert_is_type(path_to_words, str)
        fr = H2OFrame._expr(expr=ExprNode("num_valid_substrings", self, path_to_words))
        fr._ex._cache.nrows = self.nrow
        fr._ex._cache.ncol = self.ncol
        return fr


    def nchar(self):
        """
        Count the length of each string in a single-column H2OFrame of string type.

        :returns: A single-column H2OFrame containing the per-row character count.
        """
        return H2OFrame._expr(expr=ExprNode("strlen", self))


    def table(self, data2=None, dense=True):
        """
        Compute the counts of values appearing in a column, or co-occurence counts between two columns.

        :param H2OFrame data2: An optional single column to aggregate counts by.
        :param bool dense: If True (default) then use dense representation, which lists only non-zero counts,
            1 combination per row. Set to False to expand counts across all combinations.

        :returns: H2OFrame of the counts at each combination of factor levels
        """
        return H2OFrame._expr(expr=ExprNode("table", self, data2, dense)) if data2 is not None else H2OFrame._expr(
            expr=ExprNode("table", self, dense))


    def hist(self, breaks="sturges", plot=True, **kwargs):
        """
        Compute a histogram over a numeric column.

        :param breaks: Can be one of ``"sturges"``, ``"rice"``, ``"sqrt"``, ``"doane"``, ``"fd"``, ``"scott"``;
            or a single number for the number of breaks; or a list containing the split points, e.g:
            ``[-50, 213.2123, 9324834]``. If breaks is "fd", the MAD is used over the IQR in computing bin width.
        :param bool plot: If True (default), then a plot will be generated using ``matplotlib``.

        :returns: If ``plot`` is False, return H2OFrame with these columns: breaks, counts, mids_true,
            mids, and density; otherwise this method draws a plot and returns nothing.
        """
        server = kwargs.pop("server") if "server" in kwargs else False
        assert_is_type(breaks, int, [numeric], Enum("sturges", "rice", "sqrt", "doane", "fd", "scott"))
        assert_is_type(plot, bool)
        assert_is_type(server, bool)
        if kwargs:
            raise H2OValueError("Unknown parameters to hist(): %r" % kwargs)
        hist = H2OFrame._expr(expr=ExprNode("hist", self, breaks))._frame()

        if plot:
            try:
                import matplotlib
                if server:
                    matplotlib.use("Agg", warn=False)
                import matplotlib.pyplot as plt
            except ImportError:
                print("ERROR: matplotlib is required to make the histogram plot. "
                      "Set `plot` to False, if a plot is not desired.")
                return

            hist["widths"] = hist["breaks"].difflag1()
            # [2:] because we're removing the title and the first row (which consists of NaNs)
            lefts = [float(c[0]) for c in h2o.as_list(hist["breaks"], use_pandas=False)[2:]]
            widths = [float(c[0]) for c in h2o.as_list(hist["widths"], use_pandas=False)[2:]]
            counts = [float(c[0]) for c in h2o.as_list(hist["counts"], use_pandas=False)[2:]]

            plt.xlabel(self.names[0])
            plt.ylabel("Frequency")
            plt.title("Histogram of %s" % self.names[0])
            plt.bar(left=lefts, width=widths, height=counts, bottom=0)
            if not server:
                plt.show()
        else:
            hist["density"] = hist["counts"] / (hist["breaks"].difflag1() * hist["counts"].sum())
            return hist


    def isax(self, num_words, max_cardinality, optimize_card=False, **kwargs):
        """
        Compute the iSAX index for DataFrame which is assumed to be numeric time series data.

        References:

            - http://www.cs.ucr.edu/~eamonn/SAX.pdf
            - http://www.cs.ucr.edu/~eamonn/iSAX_2.0.pdf

        :param int num_words: Number of iSAX words for the timeseries, i.e. granularity along the time series
        :param int max_cardinality: Maximum cardinality of the iSAX word. Each word can have less than the max
        :param bool optimized_card: An optimization flag that will find the max cardinality regardless of what is
            passed in for ``max_cardinality``.

        :returns: An H2OFrame with the name of time series, string representation of iSAX word, followed by
            binary representation.
        """
        if num_words <= 0: raise H2OValueError("num_words must be greater than 0")
        if max_cardinality <= 0: raise H2OValueError("max_cardinality must be greater than 0")
        return H2OFrame._expr(expr=ExprNode("isax", self, num_words, max_cardinality, optimize_card))

    def convert_H2OFrame_2_DMatrix(self, predictors, yresp, h2oXGBoostModel):
        '''
        This method requires that you import the following toolboxes: xgboost, pandas, numpy and scipy.sparse.

        This method will convert an H2OFrame to a DMatrix that can be used by native XGBoost.  The H2OFrame contains
        numerical and enum columns alone.  Note that H2O one-hot-encoding introduces a missing(NA)
        column. There can be NAs in any columns.

        Follow the steps below to compare H2OXGBoost and native XGBoost:

        1. Train the H2OXGBoost model with H2OFrame trainFile and generate a prediction:
        h2oModelD = H2OXGBoostEstimator(**h2oParamsD) # parameters specified as a dict()
        h2oModelD.train(x=myX, y=y, training_frame=trainFile) # train with H2OFrame trainFile
        h2oPredict = h2oPredictD = h2oModelD.predict(trainFile)

        2. Derive the DMatrix from H2OFrame:
        nativeDMatrix = trainFile.convert_H2OFrame_2_DMatrix(myX, y, h2oModelD)

        3. Derive the parameters for native XGBoost:
        nativeParams = h2oModelD.convert_H2OXGBoostParams_2_XGBoostParams()

        4. Train your native XGBoost model and generate a prediction:
        nativeModel = xgb.train(params=nativeParams[0], dtrain=nativeDMatrix, num_boost_round=nativeParams[1])
        nativePredict = nativeModel.predict(data=nativeDMatrix, ntree_limit=nativeParams[1].

        5. Compare the predictions h2oPredict from H2OXGBoost, nativePredict from native XGBoost.

        :param h2oFrame: H2OFrame to be converted to DMatrix for native XGBoost
        :param predictors: List of predictor columns, can be column names or indices
        :param yresp: response column, can be column index or name
        :param h2oXGBoostModel: H2OXGboost model that are built with the same H2OFrame as input earlier
        :return: DMatrix that can be an input to a native XGBoost model
        '''
        import xgboost as xgb
        import pandas as pd
        import numpy as np
        from scipy.sparse import csr_matrix

        assert isinstance(predictors, list) or isinstance(predictors, tuple)
        assert h2oXGBoostModel._model_json['algo'] == 'xgboost', \
            "convert_H2OFrame_2_DMatrix is used for H2OXGBoost model only."

        colnames = self.names
        if type(predictors[0])=='int': # convert integer indices to column names
            temp = []
            for colInd in predictors:
                temp.append(colnames[colInd])
            predictors = temp

        if (type(yresp) == 'int'):
            tempy = colnames[yresp]
            yresp = tempy

        enumCols = [] # extract enum columns out to process them
        typeDict = self.types
        for predName in predictors:
            if str(typeDict[predName])=='enum':
                enumCols.append(predName)

        pandaFtrain = self.as_data_frame(use_pandas=True, header=True)
        nrows = self.nrow

        # convert H2OFrame to DMatrix starts here
        if len(enumCols) > 0:   # start with first enum column
            pandaTrainPart = generatePandaEnumCols(pandaFtrain, enumCols[0], nrows)
            pandaFtrain.drop([enumCols[0]], axis=1, inplace=True)

            for colInd in range(1, len(enumCols)):
                cname=enumCols[colInd]
                ctemp = generatePandaEnumCols(pandaFtrain, cname,  nrows)
                pandaTrainPart=pd.concat([pandaTrainPart, ctemp], axis=1)
                pandaFtrain.drop([cname], axis=1, inplace=True)

            pandaFtrain = pd.concat([pandaTrainPart, pandaFtrain], axis=1)

        c0= self[yresp].asnumeric().as_data_frame(use_pandas=True, header=True)
        pandaFtrain.drop([yresp], axis=1, inplace=True)
        pandaF = pd.concat([c0, pandaFtrain], axis=1)
        pandaF.rename(columns={c0.columns[0]:yresp}, inplace=True)
        newX = list(pandaFtrain.columns.values)
        data = pandaF.as_matrix(newX)
        label = pandaF.as_matrix([yresp])

        return xgb.DMatrix(data=csr_matrix(data), label=label) \
            if h2oXGBoostModel._model_json['output']['sparse'] else xgb.DMatrix(data=data, label=label)

    def pivot(self, index, column, value):
        """
        Pivot the frame designated by the three columns: index, column, and value. Index and column should be
        of type enum, int, or time.
        For cases of multiple indexes for a column label, the aggregation method is to pick the first occurrence in the data frame

        :param index: Index is a column that will be the row label
        :param column: The labels for the columns in the pivoted Frame
        :param value: The column of values for the given index and column label
        :returns:
        """
        assert_is_type(index, str)
        assert_is_type(column, str)
        assert_is_type(value, str)
        col_names = self.names
        if index not in col_names:
            raise H2OValueError("Index not in H2OFrame")
        if column not in col_names:
            raise H2OValueError("Column not in H2OFrame")
        if value not in col_names:
            raise H2OValueError("Value column not in H2OFrame")
        if self.type(column) not in ["enum","time","int"]:
            raise H2OValueError("'column' argument is not type enum, time or int")
        if self.type(index) not in ["enum","time","int"]:
            raise H2OValueError("'index' argument is not type enum, time or int")
        return H2OFrame._expr(expr=ExprNode("pivot",self,index,column,value))

    def rank_within_group_by(self, group_by_cols, sort_cols, ascending=[], new_col_name="New_Rank_column", sort_cols_sorted=False):
        """
        This function will add a new column rank where the ranking is produced as follows:
         1. sorts the H2OFrame by columns sorted in by columns specified in group_by_cols and sort_cols in the directions
           specified by the ascending for the sort_cols.  The sort directions for the group_by_cols are ascending only.
         2. A new rank column is added to the frame which will contain a rank assignment performed next.  The user can
           choose to assign a name to this new column.  The default name is New_Rank_column.
         3. For each groupby groups, a rank is assigned to the row starting from 1, 2, ... to the end of that group.
         4. If sort_cols_sorted is TRUE, a final sort on the frame will be performed frame according to the sort_cols and
            the sort directions in ascending.  If sort_cols_sorted is FALSE (by default), the frame from step 3 will be
            returned as is with no extra sort.  This may provide a small speedup if desired.

        :param group_by_cols: The columns to group on (either a single column name/index, or a list of column names
          or column indices
        :param sort_cols: The columns to sort on (either a single column name/index, or a list of column names or
          column indices
        :param ascending: Optional Boolean array to denote sorting direction for each sorting column.  True for
          ascending, False for descending.  Default is ascending sort.  Sort direction for enums will be ignored.
        :param new_col_name: Optional String to denote the new column names.  Default to New_Rank_column.
        :param sort_cols_sorted: Optional Boolean to denote if the returned frame should be sorted according to sort_cols
          and sort directions specified in ascending.  Default is False.

        :return: a new Frame with new rank (sorted by columns in sort_cols) column within the grouping specified
          by the group_by_cols.

         The following example is generated by Nidhi Mehta.
         If the input frame is train:

         ID Group_by_column        num data Column_to_arrange_by       num_1 fdata
         12               1   2941.552    1                    3  -3177.9077     1
         12               1   2941.552    1                    5 -13311.8247     1
         12               2 -22722.174    1                    3  -3177.9077     1
         12               2 -22722.174    1                    5 -13311.8247     1
         13               3 -12776.884    1                    5 -18421.6171     0
         13               3 -12776.884    1                    4  28080.1607     0
         13               1  -6049.830    1                    5 -18421.6171     0
         13               1  -6049.830    1                    4  28080.1607     0
         15               3 -16995.346    1                    1  -9781.6373     0
         16               1 -10003.593    0                    3 -61284.6900     0
         16               3  26052.495    1                    3 -61284.6900     0
         16               3 -22905.288    0                    3 -61284.6900     0
         17               2 -13465.496    1                    2  12094.4851     1
         17               2 -13465.496    1                    3 -11772.1338     1
         17               2 -13465.496    1                    3   -415.1114     0
         17               2  -3329.619    1                    2  12094.4851     1
         17               2  -3329.619    1                    3 -11772.1338     1
         17               2  -3329.619    1                    3   -415.1114     0

         If the following commands are issued:
         rankedF1 = h2o.rank_within_group_by(train, ["Group_by_column"], ["Column_to_arrange_by"], [TRUE])
         rankedF1.summary()

         The returned frame rankedF1 will look like this:
         ID Group_by_column        num fdata Column_to_arrange_by       num_1 fdata.1 New_Rank_column
         12               1   2941.552     1                    3  -3177.9077       1               1
         16               1 -10003.593     0                    3 -61284.6900       0               2
         13               1  -6049.830     0                    4  28080.1607       0               3
         12               1   2941.552     1                    5 -13311.8247       1               4
         13               1  -6049.830     0                    5 -18421.6171       0               5
         17               2 -13465.496     0                    2  12094.4851       1               1
         17               2  -3329.619     0                    2  12094.4851       1               2
         12               2 -22722.174     1                    3  -3177.9077       1               3
         17               2 -13465.496     0                    3 -11772.1338       1               4
         17               2 -13465.496     0                    3   -415.1114       0               5
         17               2  -3329.619     0                    3 -11772.1338       1               6
         17               2  -3329.619     0                    3   -415.1114       0               7
         12               2 -22722.174     1                    5 -13311.8247       1               8
         15               3 -16995.346     1                    1  -9781.6373       0               1
         16               3  26052.495     0                    3 -61284.6900       0               2
         16               3 -22905.288     1                    3 -61284.6900       0               3
         13               3 -12776.884     1                    4  28080.1607       0               4
         13               3 -12776.884     1                    5 -18421.6171       0               5

         If the following commands are issued:
         rankedF1 = h2o.rank_within_group_by(train, ["Group_by_column"], ["Column_to_arrange_by"], [TRUE], sort_cols_sorted=True)
         h2o.summary(rankedF1)

         The returned frame will be sorted according to sort_cols and hence look like this instead:
         ID Group_by_column        num fdata Column_to_arrange_by       num_1 fdata.1 New_Rank_column
         15               3 -16995.346     1                    1  -9781.6373       0               1
         17               2 -13465.496     0                    2  12094.4851       1               1
         17               2  -3329.619     0                    2  12094.4851       1               2
         12               1   2941.552     1                    3  -3177.9077       1               1
         12               2 -22722.174     1                    3  -3177.9077       1               3
         16               1 -10003.593     0                    3 -61284.6900       0               2
         16               3  26052.495     0                    3 -61284.6900       0               2
         16               3 -22905.288     1                    3 -61284.6900       0               3
         17               2 -13465.496     0                    3 -11772.1338       1               4
         17               2 -13465.496     0                    3   -415.1114       0               5
         17               2  -3329.619     0                    3 -11772.1338       1               6
         17               2  -3329.619     0                    3   -415.1114       0               7
         13               3 -12776.884     1                    4  28080.1607       0               4
         13               1  -6049.830     0                    4  28080.1607       0               3
         12               1   2941.552     1                    5 -13311.8247       1               4
         12               2 -22722.174     1                    5 -13311.8247       1               8
         13               3 -12776.884     1                    5 -18421.6171       0               5
         13               1  -6049.830     0                    5 -18421.6171       0               5

        """
        assert_is_type(group_by_cols, str, int, [str, int])
        if type(group_by_cols) != list: group_by_cols = [group_by_cols]
        if type(sort_cols) != list: sort_cols = [sort_cols]

        if type(ascending) != list: ascending = [ascending]   # convert to list
        ascendingI=[1]*len(sort_cols)  # intitalize sorting direction to ascending by default
        for c in sort_cols:
            if self.type(c) not in ["enum","time","int","real"]:
                raise H2OValueError("Sort by column: " + str(c) + " not of enum, time, int or real type")
        for c in group_by_cols:
            if self.type(c) not in ["enum","time","int","real"]:
                raise H2OValueError("Group by column: " + str(c) + " not of enum, time, int or real type")

        if len(ascending)>0:  # user specify sort direction, assume all columns ascending
            assert len(ascending)==len(sort_cols), "Sorting direction must be specified for each sorted column."
            for index in range(len(sort_cols)):
                ascendingI[index]=1 if ascending[index] else -1

        finalSortedOrder=0
        if (sort_cols_sorted):
            finalSortedOrder=1
        return H2OFrame._expr(expr=ExprNode("rank_within_groupby",self,group_by_cols,sort_cols,ascendingI,new_col_name, finalSortedOrder))

    def topNBottomN(self, column=0, nPercent=10, grabTopN=-1):
        """
        Given a column name or one column index, a percent N, this function will return the top or bottom N% of the
         values of the column of a frame.  The column must be a numerical column.
    
        :param column: a string for column name or an integer index
        :param nPercent: a top or bottom percentage of the column values to return
        :param grabTopN: -1 to grab bottom N percent and 1 to grab top N percent
        :returns: a H2OFrame containing two columns.  The first column contains the original row indices where
            the top/bottom values are extracted from.  The second column contains the values.
        """
        assert (nPercent >= 0) and (nPercent<=100.0), "nPercent must be between 0.0 and 100.0"
        assert round(nPercent*0.01*self.nrows)>0, "Increase nPercent.  Current value will result in top 0 row."

        if isinstance(column, int):
            if (column < 0) or (column>=self.ncols):
                raise H2OValueError("Invalid column index H2OFrame")
            else:
                colIndex = column
        else:       # column is a column name
            col_names = self.names
            if column not in col_names:
                raise H2OValueError("Column name not found H2OFrame")
            else:
                colIndex = col_names.index(column)

        if not(self[colIndex].isnumeric()):
            raise H2OValueError("Wrong column type!  Selected column must be numeric.")

        return H2OFrame._expr(expr=ExprNode("topn", self, colIndex, nPercent, grabTopN))

    def topN(self, column=0, nPercent=10):
        """
        Given a column name or one column index, a percent N, this function will return the top N% of the values
        of the column of a frame.  The column must be a numerical column.
    
        :param column: a string for column name or an integer index
        :param nPercent: a top percentage of the column values to return
        :returns: a H2OFrame containing two columns.  The first column contains the original row indices where
            the top values are extracted from.  The second column contains the top nPercent values.
        """
        return self.topNBottomN(column, nPercent, 1)

    def bottomN(self, column=0, nPercent=10):
        """
        Given a column name or one column index, a percent N, this function will return the bottom N% of the values
        of the column of a frame.  The column must be a numerical column.
    
        :param column: a string for column name or an integer index
        :param nPercent: a bottom percentage of the column values to return
        :returns: a H2OFrame containing two columns.  The first column contains the original row indices where
            the bottom values are extracted from.  The second column contains the bottom nPercent values.
        """
        return self.topNBottomN(column, nPercent, -1)

    def sub(self, pattern, replacement, ignore_case=False):
        """
        Substitute the first occurrence of pattern in a string with replacement.

        :param str pattern: A regular expression.
        :param str replacement: A replacement string.
        :param bool ignore_case: If True then pattern will match case-insensitively.
        :returns: an H2OFrame with all values matching ``pattern`` replaced with ``replacement``.
        """
        return H2OFrame._expr(expr=ExprNode("replacefirst", self, pattern, replacement, ignore_case))


    def gsub(self, pattern, replacement, ignore_case=False):
        """
        Globally substitute occurrences of pattern in a string with replacement.

        :param str pattern: A regular expression.
        :param str replacement: A replacement string.
        :param bool ignore_case: If True then pattern will match case-insensitively.
        :returns: an H2OFrame with all occurrences of ``pattern`` in all values replaced with ``replacement``.
        """
        return H2OFrame._expr(expr=ExprNode("replaceall", self, pattern, replacement, ignore_case))


    def interaction(self, factors, pairwise, max_factors, min_occurrence, destination_frame=None):
        """
        Categorical Interaction Feature Creation in H2O.

        Creates a frame in H2O with n-th order interaction features between categorical columns, as specified by
        the user.

        :param factors: list of factor columns (either indices or column names).
        :param bool pairwise: Whether to create pairwise interactions between factors (otherwise create one
            higher-order interaction). Only applicable if there are 3 or more factors.
        :param int max_factors: Max. number of factor levels in pair-wise interaction terms (if enforced, one extra
            catch-all factor will be made).
        :param int min_occurrence: Min. occurrence threshold for factor levels in pair-wise interaction terms.
        :param str destination_frame: (internal) string indicating the key for the frame created.

        :returns: an H2OFrame
        """
        return h2o.interaction(data=self, factors=factors, pairwise=pairwise, max_factors=max_factors,
                               min_occurrence=min_occurrence, destination_frame=destination_frame)


    def toupper(self):
        """
        Translate characters from lower to upper case for a particular column.

        :returns: new H2OFrame with all strings in the current frame converted to the uppercase.
        """
        return H2OFrame._expr(expr=ExprNode("toupper", self), cache=self._ex._cache)

    def grep(self,pattern, ignore_case = False, invert = False, output_logical = False):
        """
        Searches for matches to argument `pattern` within each element
        of a string column.

        Default behavior is to return indices of the elements matching the pattern. Parameter
        `output_logical` can be used to return a logical vector indicating if the element matches
        the pattern (1) or not (0).

        :param str pattern: A character string containing a regular expression.
        :param bool ignore_case: If True, then case is ignored during matching.
        :param bool invert:  If True, then identify elements that do not match the pattern.
        :param bool output_logical: If True, then return logical vector of indicators instead of list of matching positions
        :return: H2OFrame holding the matching positions or a logical list if `output_logical` is enabled.
        """
        return H2OFrame._expr(expr=ExprNode("grep", self, pattern, ignore_case, invert, output_logical))

    def tolower(self):
        """
        Translate characters from upper to lower case for a particular column.

        :returns: new H2OFrame with all strings in the current frame converted to the lowercase.
        """
        return H2OFrame._expr(expr=ExprNode("tolower", self), cache=self._ex._cache)


    def rep_len(self, length_out):
        """
        Create a new frame replicating the current frame.

        If the source frame has a single column, then the new frame will be replicating rows and its dimensions
        will be ``length_out x 1``. However if the source frame has more than 1 column, then then new frame
        will be replicating data in columnwise direction, and its dimensions will be ``nrows x length_out``,
        where ``nrows`` is the number of rows in the source frame. Also note that if ``length_out`` is smaller
        than the corresponding dimension of the source frame, then the new frame will actually be a truncated
        version of the original.

        :param int length_out: Number of columns (rows) of the resulting H2OFrame
        :returns: new H2OFrame with repeated data from the current frame.
        """
        return H2OFrame._expr(expr=ExprNode("rep_len", self, length_out))


    def scale(self, center=True, scale=True):
        """
        Center and/or scale the columns of the current frame.

        :param center: If True, then demean the data. If False, no shifting is done. If ``center`` is a list of
            numbers then shift each column by the corresponding amount.
        :param scale: If True, then scale the data by each column's standard deviation. If False, no scaling
            is done. If ``scale`` is a list of numbers, then scale each column by the requested amount.
        :returns: an H2OFrame with scaled values from the current frame.
        """
        return H2OFrame._expr(expr=ExprNode("scale", self, center, scale), cache=self._ex._cache)


    def signif(self, digits=6):
        """
        Round doubles/floats to the given number of significant digits.

        :param int digits: Number of significant digits to retain.
        :returns: new H2OFrame with rounded values from the original frame.
        """
        return H2OFrame._expr(expr=ExprNode("signif", self, digits), cache=self._ex._cache)


    def round(self, digits=0):
        """
        Round doubles/floats to the given number of decimal places.

        :param int digits: The number of decimal places to retain. Rounding to a negative number of decimal places is
            not supported. For rounding we use the "round half to even" mode (IEC 60559 standard), so that
            ``round(2.5) = 2`` and ``round(3.5) = 4``.
        :returns: new H2OFrame with rounded values from the original frame.
        """
        return H2OFrame._expr(expr=ExprNode("round", self, digits), cache=self._ex._cache)


    def asnumeric(self):
        """Return new frame with all columns converted to numeric."""
        fr = H2OFrame._expr(expr=ExprNode("as.numeric", self), cache=self._ex._cache)
        if fr._ex._cache.types_valid():
            fr._ex._cache.types = {k: "real" for k in fr._ex._cache.types.keys()}
        return fr


    def ascharacter(self):
        """
        Convert all columns in the frame into strings.

        :returns: new H2OFrame with columns of "string" type.
        """
        fr = H2OFrame._expr(expr=ExprNode("as.character", self), cache=self._ex._cache)
        if fr._ex._cache.types_valid():
            fr._ex._cache.types = {k: "string" for k in fr._ex._cache.types.keys()}
        return fr


    def na_omit(self):
        """
        Remove rows with NAs from the H2OFrame.

        :returns: new H2OFrame with all rows from the original frame containing any NAs removed.
        """
        fr = H2OFrame._expr(expr=ExprNode("na.omit", self), cache=self._ex._cache)
        fr._ex._cache.nrows = -1
        return fr


    def difflag1(self):
        """
        Conduct a diff-1 transform on a numeric frame column.

        :returns: an H2OFrame where each element is equal to the corresponding element in the source
            frame minus the previous-row element in the same frame.
        """
        if self.ncols > 1:
            raise H2OValueError("Only single-column frames supported")
        if self.types[self.columns[0]] not in {"real", "int", "bool"}:
            raise H2OValueError("Numeric column expected")
        fr = H2OFrame._expr(expr=ExprNode("difflag1", self), cache=self._ex._cache)
        return fr


    def isna(self):
        """
        For each element in an H2OFrame, determine if it is NA or not.

        :returns: an H2OFrame of 1s and 0s, where 1s mean the values were NAs.
        """
        fr = H2OFrame._expr(expr=ExprNode("is.na", self))
        fr._ex._cache.nrows = self._ex._cache.nrows
        fr._ex._cache.ncols = self._ex._cache.ncols
        if self._ex._cache.names:
            fr._ex._cache.names = ["isNA(%s)" % n for n in self._ex._cache.names]
            fr._ex._cache.types = {"isNA(%s)" % n: "int" for n in self._ex._cache.names}
        return fr


    def year(self):
        """
        Extract the "year" part from a date column.

        :returns: a single-column H2OFrame containing the "year" part from the source frame.
        """
        fr = H2OFrame._expr(expr=ExprNode("year", self), cache=self._ex._cache)
        if fr._ex._cache.types_valid():
            fr._ex._cache.types = {k: "int" for k in self._ex._cache.types.keys()}
        return fr


    def month(self):
        """
        Extract the "month" part from a date column.

        :returns: a single-column H2OFrame containing the "month" part from the source frame.
        """
        fr = H2OFrame._expr(expr=ExprNode("month", self), cache=self._ex._cache)
        if fr._ex._cache.types_valid():
            fr._ex._cache.types = {k: "int" for k in self._ex._cache.types.keys()}
        return fr


    def week(self):
        """
        Extract the "week" part from a date column.

        :returns: a single-column H2OFrame containing the "week" part from the source frame.
        """
        fr = H2OFrame._expr(expr=ExprNode("week", self), cache=self._ex._cache)
        if fr._ex._cache.types_valid():
            fr._ex._cache.types = {k: "int" for k in self._ex._cache.types.keys()}
        return fr


    def day(self):
        """
        Extract the "day" part from a date column.

        :returns: a single-column H2OFrame containing the "day" part from the source frame.
        """
        fr = H2OFrame._expr(expr=ExprNode("day", self), cache=self._ex._cache)
        if fr._ex._cache.types_valid():
            fr._ex._cache.types = {k: "int" for k in self._ex._cache.types.keys()}
        return fr


    def dayOfWeek(self):
        """
        Extract the "day-of-week" part from a date column.

        :returns: a single-column H2OFrame containing the "day-of-week" part from the source frame.
        """
        fr = H2OFrame._expr(expr=ExprNode("dayOfWeek", self), cache=self._ex._cache)
        if fr._ex._cache.types_valid():
            fr._ex._cache.types = {k: "int" for k in self._ex._cache.types.keys()}
        return fr


    def hour(self):
        """
        Extract the "hour-of-day" part from a date column.

        :returns: a single-column H2OFrame containing the "hour-of-day" part from the source frame.
        """
        fr = H2OFrame._expr(expr=ExprNode("hour", self), cache=self._ex._cache)
        if fr._ex._cache.types_valid():
            fr._ex._cache.types = {k: "int" for k in self._ex._cache.types.keys()}
        return fr


    def minute(self):
        """
        Extract the "minute" part from a date column.

        :returns: a single-column H2OFrame containing the "minute" part from the source frame.
        """
        fr = H2OFrame._expr(expr=ExprNode("minute", self), cache=self._ex._cache)
        if fr._ex._cache.types_valid():
            fr._ex._cache.types = {k: "int" for k in self._ex._cache.types.keys()}
        return fr


    def second(self):
        """
        Extract the "second" part from a date column.

        :returns: a single-column H2OFrame containing the "second" part from the source frame.
        """
        fr = H2OFrame._expr(expr=ExprNode("second", self), cache=self._ex._cache)
        if fr._ex._cache.types_valid():
            fr._ex._cache.types = {k: "int" for k in self._ex._cache.types.keys()}
        return fr


    def runif(self, seed=None):
        """
        Generate a column of random numbers drawn from a uniform distribution [0,1) and
        having the same data layout as the source frame.

        :param int seed: seed for the random number generator.

        :returns: Single-column H2OFrame filled with doubles sampled uniformly from [0,1).
        """
        fr = H2OFrame._expr(expr=ExprNode("h2o.runif", self, -1 if seed is None else seed))
        fr._ex._cache.ncols = 1
        fr._ex._cache.nrows = self.nrow
        return fr


    def stratified_split(self, test_frac=0.2, seed=-1):
        """
        Construct a column that can be used to perform a random stratified split.

        :param float test_frac: The fraction of rows that will belong to the "test".
        :param int seed: The seed for the random number generator.

        :returns: an H2OFrame having single categorical column with two levels: ``"train"`` and ``"test"``.

        :examples:
          >>> stratsplit = df["y"].stratified_split(test_frac=0.3, seed=12349453)
          >>> train = df[stratsplit=="train"]
          >>> test = df[stratsplit=="test"]
          >>>
          >>> # check that the distributions among the initial frame, and the
          >>> # train/test frames match
          >>> df["y"].table()["Count"] / df["y"].table()["Count"].sum()
          >>> train["y"].table()["Count"] / train["y"].table()["Count"].sum()
          >>> test["y"].table()["Count"] / test["y"].table()["Count"].sum()
        """
        return H2OFrame._expr(expr=ExprNode('h2o.random_stratified_split', self, test_frac, seed))


    def match(self, table, nomatch=0):
        """
        Make a vector of the positions of (first) matches of its first argument in its second.

        Only applicable to single-column categorical/string frames.

        :param List table: the list of items to match against
        :param int nomatch: value that should be returned when there is no match.
        :returns: a new H2OFrame containing for each cell from the source frame the index where
            the pattern ``table`` first occurs within that cell.
        """
        return H2OFrame._expr(expr=ExprNode("match", self, table, nomatch, None))


    def cut(self, breaks, labels=None, include_lowest=False, right=True, dig_lab=3):
        """
        Cut a numeric vector into categorical "buckets".

        This method is only applicable to a single-column numeric frame.

        :param List[float] breaks: The cut points in the numeric vector.
        :param List[str] labels: Labels for categorical levels produced. Defaults to set notation of
            intervals defined by the breaks.
        :param bool include_lowest: By default, cuts are defined as intervals ``(lo, hi]``. If this parameter
            is True, then the interval becomes ``[lo, hi]``.
        :param bool right: Include the high value: ``(lo, hi]``. If False, get ``(lo, hi)``.
        :param int dig_lab: Number of digits following the decimal point to consider.

        :returns: Single-column H2OFrame of categorical data.
        """
        assert_is_type(breaks, [numeric])
        if self.ncols != 1: raise H2OValueError("Single-column frame is expected")
        if self.types[self.names[0]] not in {"int", "real"}: raise H2OValueError("A numeric column is expected")
        fr = H2OFrame._expr(expr=ExprNode("cut", self, breaks, labels, include_lowest, right, dig_lab),
                            cache=self._ex._cache)
        fr._ex._cache.types = {k: "enum" for k in self.names}
        return fr


    def which(self):
        """
        Compose the list of row indices for which the frame contains non-zero values.

        Only applicable to integer single-column frames.
        Equivalent to comprehension ``[index for index, value in enumerate(self) if value]``.

        :returns: a new single-column H2OFrame containing indices of those rows in the original frame
            that contained non-zero values.
        """
        return H2OFrame._expr(expr=ExprNode("which", self))

    def idxmax(self,skipna=True, axis=0):
        """
        Get the index of the max value in a column or row

        :param bool skipna: If True (default), then NAs are ignored during the search. Otherwise presence
            of NAs renders the entire result NA.
        :param int axis: Direction of finding the max index. If 0 (default), then the max index is searched columnwise, and the
            result is a frame with 1 row and number of columns as in the original frame. If 1, then the max index is searched
            rowwise and the result is a frame with 1 column, and number of rows equal to the number of rows in the original frame.
        :returns: either a list of max index values per-column or an H2OFrame containing max index values
                  per-row from the original frame.
        """
        return H2OFrame._expr(expr=ExprNode("which.max", self, skipna, axis))

    def idxmin(self,skipna=True, axis=0):
        """
        Get the index of the min value in a column or row

        :param bool skipna: If True (default), then NAs are ignored during the search. Otherwise presence
            of NAs renders the entire result NA.
        :param int axis: Direction of finding the min index. If 0 (default), then the min index is searched columnwise, and the
            result is a frame with 1 row and number of columns as in the original frame. If 1, then the min index is searched
            rowwise and the result is a frame with 1 column, and number of rows equal to the number of rows in the original frame.
        :returns: either a list of min index values per-column or an H2OFrame containing min index values
                  per-row from the original frame.
        """
        return H2OFrame._expr(expr=ExprNode("which.min", self, skipna, axis))


    def ifelse(self, yes, no):
        """
        Equivalent to ``[y if t else n for t,y,n in zip(self,yes,no)]``.

        Based on the booleans in the test vector, the output has the values of the
        yes and no vectors interleaved (or merged together).  All Frames must have
        the same row count.  Single column frames are broadened to match wider
        Frames.  Scalars are allowed, and are also broadened to match wider frames.

        :param yes: Frame to use if ``test`` is true; may be a scalar or single column
        :param no: Frame to use if ``test`` is false; may be a scalar or single column

        :returns: an H2OFrame of the merged yes/no frames/scalars according to the test input frame.
        """
        return H2OFrame._expr(expr=ExprNode("ifelse", self, yes, no))


    def apply(self, fun=None, axis=0):
        """
        Apply a lambda expression to an H2OFrame.

        :param fun: a lambda expression to be applied per row or per column.
        :param axis: 0 = apply to each column; 1 = apply to each row
        :returns: a new H2OFrame with the results of applying ``fun`` to the current frame.
        """
        from .astfun import lambda_to_expr
        assert_is_type(axis, 0, 1)
        assert_is_type(fun, FunctionType)
        assert_satisfies(fun, fun.__name__ == "<lambda>")
        res = lambda_to_expr(fun)
        return H2OFrame._expr(expr=ExprNode("apply", self, 1 + (axis == 0), *res))


    #-------------------------------------------------------------------------------------------------------------------
    # Synonyms + Deprecated
    #-------------------------------------------------------------------------------------------------------------------
    # Here we have all methods that are provided as alternative names to some other names defined above. This also
    # includes methods that we rename as part of the deprecation process (but keeping the old name for the sake of
    # backward compatibility). We gather them all down here to have a slightly cleaner code.

    @staticmethod
    def mktime(year=1970, month=0, day=0, hour=0, minute=0, second=0, msec=0):
        """
        Deprecated, use :func:`moment` instead.

        This function was left for backward-compatibility purposes only. It is
        not very stable, and counterintuitively uses 0-based months and days,
        so "January 4th, 2001" should be entered as ``mktime(2001, 0, 3)``.
        """
        return H2OFrame._expr(ExprNode("mktime", year, month, day, hour, minute, second, msec))

    @property
    def columns(self):
        """Same as ``self.names``."""
        return self.names

    @columns.setter
    def columns(self, value):
        self.set_names(value)

    @property
    def col_names(self):
        """Same as ``self.names``."""
        return self.names

    @col_names.setter
    def col_names(self, value):
        self.set_names(value)

    def __len__(self):
        """Number of rows in the dataframe, same as ``self.nrows``."""
        return self.nrows

    @property
    def nrow(self):
        """Same as ``self.nrows``."""
        return self.nrows

    @property
    def ncol(self):
        """Same as ``self.ncols``."""
        return self.ncols

    @property
    def dim(self):
        """Same as ``list(self.shape)``."""
        return [self.nrow, self.ncol]

    #@property
    #def frame_id(self):
    #    """Same as ``frame.id``."""
    #    return self.id

    #@frame_id.setter
    #def frame_id(self, value):
    #    self.id = value

    @staticmethod
    def from_python(python_obj, destination_frame=None, header=0, separator=",", column_names=None,
                    column_types=None, na_strings=None):
        """[DEPRECATED] Use constructor ``H2OFrame()`` instead."""
        return H2OFrame(python_obj, destination_frame, header, separator, column_names, column_types,
                        na_strings)


    def ischaracter(self):
        """[DEPRECATED] Use ``frame.isstring()``."""
        return self.isstring()



#-----------------------------------------------------------------------------------------------------------------------
# Helpers
#-----------------------------------------------------------------------------------------------------------------------

def _getValidCols(by_idx, fr):  # so user can input names of the columns as well is idx num
    tmp = []
    for i in by_idx:
        if type(i) == str:
            if i not in fr.names:
                raise H2OValueError("Column: " + i + " not in frame.")
            tmp.append(fr.names.index(i))
        elif type(i) != int:
            raise H2OValueError("Join on column: " + i + " not of type int")
        else:
            tmp.append(i)
    return list(set(tmp))

def _binop(lhs, op, rhs, rtype=None):
    assert_is_type(lhs, str, numeric, datetime.date, pandas_timestamp, numpy_datetime, H2OFrame)
    assert_is_type(rhs, str, numeric, datetime.date, pandas_timestamp, numpy_datetime, H2OFrame)
    if isinstance(lhs, H2OFrame) and isinstance(rhs, H2OFrame) and lhs._is_frame and rhs._is_frame:
        lrows, lcols = lhs.shape
        rrows, rcols = rhs.shape
        compatible = ((lcols == rcols and lrows == rrows) or
                      (lcols == 1 and lrows == rrows) or
                      (lcols == 1 and lrows == 1) or
                      (rcols == 1 and lrows == rrows) or
                      (rcols == 1 and rrows == 1) or
                      (lrows == 1 and lcols == rcols) or
                      (rrows == 1 and lcols == rcols)
                      )
        if not compatible:
            raise H2OValueError("Attempting to operate on incompatible frames: (%d x %d) and (%d x %d)"
                                % (lrows, lcols, rrows, rcols))

    if is_type(lhs, pandas_timestamp, numpy_datetime, datetime.date):
        lhs = H2OFrame.moment(date=lhs)
    if is_type(rhs, pandas_timestamp, numpy_datetime, datetime.date):
        rhs = H2OFrame.moment(date=rhs)

    cache = lhs._ex._cache if isinstance(lhs, H2OFrame) else rhs._ex._cache
    res = H2OFrame._expr(expr=ExprNode(op, lhs, rhs), cache=cache)
    if rtype is not None and res._ex._cache._names is not None:
        res._ex._cache._types = {name: rtype for name in res._ex._cache._names}
    return res




def generatePandaEnumCols(pandaFtrain, cname, nrows):
    """
    For an H2O Enum column, we perform one-hot-encoding here and add one more column, "missing(NA)" to it.

    :param pandaFtrain: panda frame derived from H2OFrame
    :param cname: column name of enum col
    :param nrows: number of rows of enum col
    :return: panda frame with enum col encoded correctly for native XGBoost
    """
    import numpy as np
    import pandas as pd
    
    cmissingNames=[cname+".missing(NA)"]
    tempnp = np.zeros((nrows,1), dtype=np.int)
    # check for nan and assign it correct value
    colVals = pandaFtrain[cname]
    for ind in range(nrows):
        try:
            float(colVals[ind])
            if math.isnan(colVals[ind]):
                tempnp[ind]=1
        except ValueError:
            pass
    zeroFrame = pd.DataFrame(tempnp)
    zeroFrame.columns=cmissingNames
    temp = pd.get_dummies(pandaFtrain[cname], prefix=cname, drop_first=False)
    tempNames = list(temp)  # get column names
    colLength = len(tempNames)
    newNames = ['a']*colLength
    newIndics = [0]*colLength
    header = tempNames[0].split('.')[0]

    for ind in range(colLength):
        newIndics[ind] = int(tempNames[ind].split('.')[1][1:])
    newIndics.sort()

    for ind in range(colLength):
        newNames[ind] = header+'.l'+str(newIndics[ind])  # generate correct order of names
    ftemp = temp[newNames]
    ctemp = pd.concat([ftemp, zeroFrame], axis=1)
    return ctemp
