# -*- encoding: utf-8 -*-
"""
Group-by operations on an H2OFrame.

:copyright: (c) 2016 H2O.ai
:license:   Apache License Version 2.0 (see LICENSE for details)
"""
from __future__ import absolute_import, division, print_function, unicode_literals
from h2o.utils.compatibility import *  # NOQA

import warnings

import h2o
from h2o.display import format_user_tips, repr_def
from h2o.expr import ExprNode
from h2o.utils.typechecks import is_type


class GroupBy(object):
    """
    A class that represents the group by operation on an H2OFrame.

    The returned groups are sorted by the natural group-by column sort.

    :param H2OFrame fr: H2OFrame that you want the group by operation to be performed on.
    :param by: by can be a column name (str) or an index (int) of a single column,  or a list for multiple columns
        denoting the set of columns to group by.

    Sample usage:

    >>> my_frame = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv")
    >>> grouped = my_frame.group_by(by=["sepal_len",
    ...                                 "sepal_wid"])
    >>> grouped.sum(col="sepal_len",
    ...             na="all").mean(col="class", na="all").max()
    >>> grouped.get_frame()

    Any number of aggregations may be chained together in this manner.  Note that once the aggregation operations
    are complete, calling the GroupBy object with a new set of aggregations will yield no effect.  You must generate
    a new GroupBy object in order to apply a new aggregation on it.  In addition, certain aggregations are only
    defined for numerical or categorical columns.  An error will be thrown for calling aggregation on the wrong
    data types.

    If no arguments are given to the aggregation (e.g. "max" in the above example), then it is assumed that the
    aggregation should apply to all columns but the group by columns.  However, operations will not be performed
    on String columns.  They will be skipped.

    All GroupBy aggregations take parameter na, which controls treatment of NA values during the calculation.
    It can be one of:

        - "all" (default) -- any NAs are used in the calculation as-is; which usually results in the final result
          being NA too.
        - "ignore" -- NA entries are not included in calculations, but the total number of entries is taken as the
          total number of rows. For example, ``mean([1, 2, 3, nan], na="ignore")`` will produce ``1.5``.  In addition,
          ``median([1, 2, 3, nan], na="ignore")`` will first sort the row as ``[nan, 1, 2, 3]``.  Next, the median is the
          mean of the two middle values in this case producing a median of ``1.5``.
        - "rm" entries are skipped during the calculations, reducing the total effective count of entries. For
          example, ``mean([1, 2, 3, nan], na="rm")`` will produce ``2``.  The median in this case will be ``2`` as the middle
          value.

    Variance (var) and standard deviation (sd) are the sample (not population) statistics.
    """

    def __init__(self, fr, by):
        """
        Return a new ``GroupBy`` object using the H2OFrame specified in fr and the desired grouping columns
        specified in by.  The original H2O frame will be stored as member _fr.  Information on the new grouping
        of the original frame is described in a new H2OFrame in member frame.

        The returned groups are sorted by the natural group-by column sort.

        :param H2OFrame fr: H2OFrame that you want the group by operation to be performed on.
        :param by: can be a column name (str) or an index (int) of a single column,  or a list for multiple columns
            denoting the set of columns to group by.
        """
        self._fr = fr  # IN
        self._by = by  # IN
        self._aggs = {}  # IN
        self._res = None  # OUT

        if is_type(by, str):
            self._by = [self._fr.names.index(by)]
        elif is_type(by, list, tuple):
            self._by = [self._fr.names.index(b) if is_type(b, str) else b for b in by]
        else:
            self._by = [self._by]

    def min(self, col=None, na="all"):
        """
        Calculate the minimum of each column specified in col for each group of a GroupBy object.  If no col is
        given, compute the minimum among all numeric columns other than those being grouped on.

        :param col: col can be None (default), a column name (str) or an index (int) of a single column,  or a
            list for multiple columns denoting the set of columns to group by.
        :param str na:  one of 'rm', 'ignore' or 'all' (default).
        :return: the original GroupBy object (self), for ease of constructing chained operations.

        :examples:

        >>> my_frame = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv")
        >>> grouped = my_frame.group_by(by=["sepal_len", "sepal_wid"])
        >>> grouped.sum(col="sepal_len",
        ...             na="all").mean(col="class", na="all").min()
        >>> grouped.get_frame()
        """
        return self._add_agg("min", col, na)

    def max(self, col=None, na="all"):
        """
        Calculate the maximum of each column specified in col for each group of a GroupBy object.  If no col is
        given, compute the maximum among all numeric columns other than those being grouped on.

        :param col: col can be None (default), a column name (str) or an index (int) of a single column,  or a
            list for multiple columns
        :param str na:  one of 'rm', 'ignore' or 'all' (default).
        :return: the original GroupBy object (self), for ease of constructing chained operations.

        :examples:

        >>> my_frame = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv")
        >>> grouped = my_frame.group_by(by=["sepal_len", "sepal_wid"])
        >>> grouped.sum(col="sepal_len",
        ...             na="all").mean(col="class", na="all").max()
        >>> grouped.get_frame()
        """
        return self._add_agg("max", col, na)

    def mean(self, col=None, na="all"):
        """
        Calculate the mean of each column specified in col for each group of a GroupBy object.  If no col is
        given, compute the mean among all numeric columns other than those being grouped on.

        :param col: col can be None (default), a column name (str) or an index (int) of a single column,  or a
            list for multiple columns
        :param str na:  one of 'rm', 'ignore' or 'all' (default).
        :return: the original GroupBy object (self), for ease of constructing chained operations.

        :examples:

        >>> my_frame = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv")
        >>> grouped = my_frame.group_by(by=["sepal_len", "sepal_wid"])
        >>> grouped.sum(col="sepal_len",
        ...             na="all").mean(col="class", na="all").max()
        >>> grouped.get_frame()
        """
        return self._add_agg("mean", col, na)

    def count(self, na="all"):
        """
        Count the number of rows in each group of a GroupBy object.

        :param str na:  one of 'rm', 'ignore' or 'all' (default).
        :return: the original GroupBy object (self), for ease of constructing chained operations.

        :examples:

        >>> my_frame = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv")
        >>> grouped = my_frame.group_by(by=["sepal_len",
        ...                                 "sepal_wid"])
        >>> grouped.count
        >>> grouped.get_frame()
        """
        return self._add_agg("nrow", None, na)

    def sum(self, col=None, na="all"):
        """
        Calculate the sum of each column specified in col for each group of a GroupBy object.  If no col is given,
        compute the sum among all numeric columns other than those being grouped on.

        :param col: col can be None (default), a column name (str) or an index (int) of a single column,  or a
            list for multiple columns
        :param str na:  one of 'rm', 'ignore' or 'all' (default).
        :return: the original GroupBy object (self), for ease of constructing chained operations.

        :examples:

        >>> my_frame = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv")
        >>> grouped = my_frame.group_by(by=["sepal_len", "sepal_wid"])
        >>> grouped.sum(col="sepal_len",
        ...             na="all").mean(col="class", na="all").max()
        >>> grouped.get_frame()
        """
        return self._add_agg("sum", col, na)

    def sd(self, col=None, na="all"):
        """
        Calculate the standard deviation of each column specified in col for each group of a GroupBy object. If no
        col is given, compute the standard deviation among all numeric columns other than those being grouped on.

        :param col: col can be None (default), a column name (str) or an index (int) of a single column,  or a
            list for multiple columns
        :param str na:  one of 'rm', 'ignore' or 'all' (default).
        :return: the original GroupBy object (self), for ease of constructing chained operations.

        :examples:

        >>> my_frame = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv")
        >>> grouped = my_frame.group_by(by=["sepal_len", "sepal_wid"])
        >>> grouped.sd(col="sepal_len",
        ...             na="all").mean(col="class", na="all").max()
        >>> grouped.get_frame()
        """
        return self._add_agg("sdev", col, na)

    def var(self, col=None, na="all"):
        """
        Calculate the variance of each column specified in col for each group of a GroupBy object.  If no col is
        given, compute the variance among all numeric columns other than those being grouped on.

        :param col: col can be None (default), a column name (str) or an index (int) of a single column,  or a
            list for multiple columns
        :param str na:  one of 'rm', 'ignore' or 'all' (default).
        :return: the original GroupBy object (self), for ease of constructing chained operations.

        :examples:

        >>> my_frame = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv")
        >>> grouped = my_frame.group_by(by=["sepal_len", "sepal_wid"])
        >>> grouped.var(col="sepal_len",
        ...             na="all").mean(col="class", na="all").max()
        >>> grouped.get_frame()
        """
        return self._add_agg("var", col, na)

    def ss(self, col=None, na="all"):
        """
        Calculate the sum of squares of each column specified in col for each group of a GroupBy object.  If no col
        is given, compute the sum of squares among all numeric columns other than those being grouped on.

        :param col: col can be None (default), a column name (str) or an index (int) of a single column,  or a
            list for multiple columns
        :param str na:  one of 'rm', 'ignore' or 'all' (default).
        :return: the original GroupBy object (self), for ease of constructing chained operations.

        :examples:

        >>> my_frame = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv")
        >>> grouped = my_frame.group_by(by=["sepal_len", "sepal_wid"])
        >>> grouped.ss(col="sepal_len",
        ...             na="all").mean(col="class", na="all").max()
        >>> grouped.get_frame()
        """
        return self._add_agg("sumSquares", col, na)

    def mode(self, col=None, na="all"):
        """
        Calculate the mode of each column specified in col for each group of a GroupBy object.  If no col is given,
        compute the mode among all categorical columns other than those being grouped on.

        :param col: col can be None (default), a column name (str) or an index (int) of a single column,  or a
            list for multiple columns
        :param str na:  one of 'rm', 'ignore' or 'all' (default).
        :return: the original GroupBy object (self), for ease of constructing chained operations.

        :examples:

        >>> my_frame = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv")
        >>> grouped = my_frame.group_by(by=["sepal_len", "sepal_wid"])
        >>> grouped.sum(col="sepal_len",
        ...             na="all").mode(col="class", na="all").max()
        >>> grouped.get_frame()
        """
        return self._add_agg("mode", col, na)

    def median(self, col=None, na="all"):
        """
        Calculate the median of each column specified in col for each group of a GroupBy object.  If no col is given,
        compute the median among all numeric columns other than those being grouped on.

        :param col: col can be None (default), a column name (str) or an index (int) of a single column,  or a
            list for multiple columns
        :param str na:  one of 'rm', 'ignore' or 'all' (default).
        :return: the original GroupBy object (self), for ease of constructing chained operations.

        :examples:

        >>> my_frame = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv")
        >>> grouped = my_frame.group_by(by=["sepal_len", "sepal_wid"])
        >>> grouped.sum(col="sepal_len",
        ...             na="all").median(col="class", na="all").max()
        >>> grouped.get_frame()
        """
        return self._add_agg("median", col, na)

    @property
    def frame(self):
        """
        same as ``get_frame()``.

        :examples:

        >>> my_frame = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv")
        >>> grouped = my_frame.group_by(by=["sepal_len", "sepal_wid"])
        >>> grouped.frame
        """
        return self.get_frame()

    def get_frame(self):
        """
        Return the resulting H2OFrame containing the result(s) of aggregation(s) of the group by.

        The number of rows denote the number of groups generated by the group by operation.

        The number of columns depend on the number of aggregations performed, the number of columns specified in
        the col parameter.  Generally, expect the number of columns to be::

            (len(col) of aggregation 0 + len(col) of aggregation 1 +...+ len(col) of aggregation n) x
            (number of groups of the GroupBy object) +1 (for group-by group names).

        Note:
            - the count aggregation only generates one column;
            - if col is a str or int, ``len(col) = 1``.

        :returns: GroupBy H2OFrame

        :examples:

        >>> my_frame = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv")
        >>> grouped = my_frame.group_by(by=["sepal_len", "sepal_wid"])
        >>> grouped.get_frame()
        """
        if self._res is None:
            aggs = []
            cols_operated = []
            for k in self._aggs:
                aggs += (self._aggs[k])
                col_used = self._aggs[k][1]
                if col_used not in cols_operated:
                    cols_operated.append(col_used)

            for cind in cols_operated:
                if cind not in self._by:
                    self._check_string_columns(cind)
            self._res = h2o.H2OFrame._expr(expr=ExprNode("GB", self._fr, self._by, *aggs))

        return self._res

    def _add_agg(self, op, col, na):
        if op == "nrow": col = 0
        if col is None:
            for i in range(self._fr.ncol):
                if i not in self._by: self._add_agg(op, i, na)
            return self
        elif is_type(col, str):
            cidx = self._fr.names.index(col)
        elif is_type(col, int):
            cidx = col
        elif is_type(col, list, tuple):
            for i in col:
                self._add_agg(op, i, na)
            return self
        else:
            raise ValueError("col must be a column name or index.")
        name = "{}_{}".format(op, self._fr.names[cidx])
        self._aggs[name] = [op, cidx, na]
        return self

    def _check_string_columns(self, colIndex):
        if self._fr[colIndex].isstring()[0]:
            warnings.warn("Column {0} is a string column.  No groupby operation will be performed on it.".format(self._fr.names[colIndex]))

    def __repr__(self):
        return repr_def(self, attributes=['_fr', '_by', '_aggs'])

    def __str__(self):
        return """GroupBy:
          Frame: {frame}; by={by}
          Aggregates: {aggr}
        """.format(
            frame=self._fr.frame_id,
            by=self._by,
            aggr=self._aggs.keys(),
        )+format_user_tips("Use get_frame() to get groupby frame")
