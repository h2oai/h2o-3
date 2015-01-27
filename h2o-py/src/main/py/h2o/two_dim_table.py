"""
A two dimensional table having row and column headers.
"""


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
        self.cell_values = H2OTwoDimTable._parse_values(cell_values, col_types)
        self.col_formats = col_formats

    def show(self):
        pass

    @staticmethod
    def _parse_values(values, types):
        i = 0
        for k, v in enumerate(values):
            if types[i] == 'integer':
                for j, val in enumerate(v):
                    values[k][j] = int(float.fromhex(val))

            if types[i] == 'double' or types[i] == 'float' or types[i] == 'long':
                for j, val in enumerate(v):
                    values[k][j] = float.fromhex(val)

            else:
                for j, val in enumerate(v):
                    values[k][j] = val
        return values
