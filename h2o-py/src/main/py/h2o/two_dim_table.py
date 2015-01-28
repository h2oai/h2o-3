"""
A two dimensional table having row and column headers.
"""

import tabulate
import copy


class H2OTwoDimTable(object):
    """
    A class representing an 2D table (for pretty printing output).
    """
    def __init__(self, row_header=None, col_header=None, col_types=None,
                 table_header=None, cell_values=None, col_formats=None):
        self.row_header = row_header
        self.col_header = col_header
        self.col_types = col_types
        self.table_header = table_header
        self.cell_values = _parse_values(cell_values, col_types)
        self.col_formats = col_formats

    def show(self):
        print self.table_header + ":"
        print
        table = copy.deepcopy(self.cell_values)
        for i in range(len(table)):
            table[i].insert(0, str(self.row_header[i]))
        header = ["Row"]
        header += self.col_header
        print tabulate.tabulate(table, headers=header, numalign="left", stralign="left")


def _parse_values(values, types):
    for k, v in enumerate(values):
        for j, val in enumerate(v):
            if types[j] == 'integer':
                if isinstance(val, unicode) and val == "":
                    values[k][j] = float("nan")
                else:
                    values[k][j] = int(float.fromhex(val))

            elif types[j] == 'double' or types[j] == 'float' or types[j] == 'long':
                if isinstance(val, unicode) and val == "":
                    values[k][j] = float("nan")
                else:
                    values[k][j] = float.fromhex(val)

            else:
                continue
    return values
