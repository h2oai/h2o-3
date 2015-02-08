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
             table_header=None, raw_cell_values=None,
             col_formats=None, cell_values=None):
    self.row_header = row_header
    self.col_header = col_header
    self.col_types = col_types
    self.table_header = table_header
    self.cell_values = cell_values if cell_values else H2OTwoDimTable._parse_values(raw_cell_values, col_types)
    self.col_formats = col_formats

  def show(self):
    print
    print self.table_header + ":"
    print
    table = copy.deepcopy(self.cell_values)
    print tabulate.tabulate(table, headers=self.col_header, numalign="left", stralign="left")
    print

  def __repr__(self):
    self.show()
    return ""

  @staticmethod
  def _parse_values(values, types):
    for col_index, column in enumerate(values):
      for row_index, row_value in enumerate(column):
        if types[col_index] == 'integer':
          values[col_index][row_index]  = "" if not row_value else int(float.fromhex(row_value))

        elif types[col_index] == 'double' or types[col_index] == 'float' or types[col_index] == 'long':
          values[col_index][row_index]  = "" if not row_value else float.fromhex(row_value)

        else: # string?
          continue
    return zip(*values)  # transpose the values! <3 splat ops
