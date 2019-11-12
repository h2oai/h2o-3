# -*- encoding: utf-8 -*-
"""
A two dimensional table having row and column headers.
"""
from __future__ import absolute_import, division, print_function, unicode_literals

import copy

from h2o.display import H2ODisplay
from h2o.exceptions import H2OValueError
from h2o.utils.compatibility import *  # NOQA
from h2o.utils.shared_utils import _is_list_of_lists, can_use_pandas
from h2o.utils.typechecks import I, assert_is_type, is_type


class H2OTwoDimTable(object):
    """A class representing an 2D table (for pretty printing output)."""

    def __init__(self, table_header=None, table_description=None, col_header=None,
                 cell_values=None, raw_cell_values=None, col_types=None, row_header=None, col_formats=None):
        """
        Create new H2OTwoDimTable object.

        :param table_header: Header for the entire table.
        :param table_description: Longer description of the table.
        :param col_header: list of column names (used in conjunction with)
        :param cell_values: table values, as an array of individual rows
        :param raw_cell_values:
        :param col_types:
        :param row_header: ignored.
        :param col_formats: ignored.
        """
        assert_is_type(table_header, None, str)
        assert_is_type(table_description, None, str)
        assert_is_type(col_header, None, [str])
        assert_is_type(col_types, None, [str])
        assert_is_type(cell_values, None, I([[object]], lambda m: all(len(row) == len(m[0]) for row in m)))
        self._table_header = table_header
        self._table_description = table_description
        self._col_header = col_header
        self._col_types = col_types
        self._cell_values = cell_values or self._parse_values(raw_cell_values, col_types)


    @staticmethod
    def make(keyvals):
        """
        Create new H2OTwoDimTable object from list of (key,value) tuples which are a pre-cursor to JSON dict.

        :param keyvals: list of (key, value) tuples
        :return: new H2OTwoDimTable object
        """
        kwargs = {}
        for key, value in keyvals:
            if key == "columns":
                kwargs["col_formats"] = [c["format"] for c in value]
                kwargs["col_types"] = [c["type"] for c in value]
                kwargs["col_header"] = [c["name"] for c in value]
                kwargs["row_header"] = len(value)
            if key == "name": kwargs["table_header"] = value
            if key == "description": kwargs["table_description"] = value
            if key == "data": kwargs["raw_cell_values"] = value
        return H2OTwoDimTable(**kwargs)


    @property
    def cell_values(self):
        """The contents of the table, as a list of rows."""
        return self._cell_values


    @property
    def col_header(self):
        """Array of column names."""
        return self._col_header


    @property
    def col_types(self):
        """Array of column types."""
        return self._col_types


    def as_data_frame(self):
        """Convert to a python 'data frame'."""
        if can_use_pandas():
            import pandas
            pandas.options.display.max_colwidth = 70
            return pandas.DataFrame(self._cell_values, columns=self._col_header)
        return self


    def show(self, header=True):
        """Print the contents of this table."""
        print()
        if header and self._table_header:
            table_header = self._table_header + ": "
            if self._table_description:
                table_header += self._table_description
            print(table_header)

        (table, nr, is_pandas) = self._as_show_table()

        H2ODisplay(table, is_pandas=is_pandas, header=self._col_header, numalign="left", stralign="left")
        if nr > 20 and can_use_pandas(): print('\nSee the whole table with table.as_data_frame()')

    def _as_show_table(self):
        if H2ODisplay.prefer_pandas() and can_use_pandas():
            pd = self.as_data_frame()
            return self.as_data_frame().head(20), pd.shape[0], True
        else:
            table = copy.deepcopy(self._cell_values)
            nr = 0
            if _is_list_of_lists(table): nr = len(
                table)  # only set if we truly have multiple rows... not just one long row :)
            if nr > 20:  # create a truncated view of the table, first/last 5 rows
                trunc_table = []
                trunc_table += [v for v in table[:5]]
                trunc_table.append(["---"] * len(table[0]))
                trunc_table += [v for v in table[(nr - 5):]]
                table = trunc_table
            return table, nr, False

    def __repr__(self):
        # FIXME: should return a string rather than printing it
        self.show()
        return ""


    def _parse_values(self, values, types):
        if self._col_header[0] is None:
            self._col_header = self._col_header[1:]
            types = types[1:]
            values = values[1:]
        for col_index, column in enumerate(values):
            for row_index, row_value in enumerate(column):
                if types[col_index] == 'integer':
                    values[col_index][row_index] = "" if row_value is None else int(float(row_value))

                elif types[col_index] in ['double', 'float', 'long']:
                    values[col_index][row_index] = "" if row_value is None else float(row_value)

                else:  # string?
                    continue
        return list(zip(*values))  # transpose the values! <3 splat ops


    def __getitem__(self, item):
        if is_type(item, int, str):
            # single col selection returns list
            if is_type(item, int):
                index = item
                if index < 0: index += len(self._col_header)
                if index < 0 or index >= len(self._col_header):
                    raise H2OValueError("Index %d is out of range" % item)
            else:
                if item in self._col_header:
                    index = self._col_header.index(item)
                else:
                    raise H2OValueError("Column `%s` does not exist in the table" % item)
            return [row[index] for row in self._cell_values]
        elif isinstance(item, slice):
            # row selection if item is slice returns H2OTwoDimTable
            # FIXME! slice behavior should be consistent with other selectors - return columns instead of rows...
            self._cell_values = [self._cell_values[ii] for ii in range(*item.indices(len(self._cell_values)))]
            return self
        elif is_type(item, [int, str]):
            # multiple col selection returns list of cols
            return [self[i] for i in item]
        else:
            raise TypeError('can not support getting item for ' + str(item))


    def __setitem__(self, key, value):
        # This is not tested, and probably not used anywhere... That's why it's so horrible.
        cols = list(zip(*self._cell_values))
        if len(cols[0]) != len(value): raise ValueError('value must be same length as columns')
        if key not in self._col_header:
            self._col_header.append(key)
            cols.append(tuple(value))
        else:
            cols[self._col_header.index(key)] = value
        self._cell_values = [list(x) for x in zip(*cols)]
