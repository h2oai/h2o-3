"""
A two dimensional table having row and column headers.
"""

import copy
import h2o
from frame import _is_list_of_lists


class H2OTwoDimTable(object):
  """
  A class representing an 2D table (for pretty printing output).
  """
  def __init__(self, row_header=None, col_header=None, col_types=None,
             table_header=None, raw_cell_values=None,
             col_formats=None, cell_values=None, table_description=None):
    self.row_header = row_header
    self.col_header = col_header
    self.col_types = col_types
    self.table_header = table_header
    self.cell_values = cell_values if cell_values else self._parse_values(raw_cell_values, col_types)
    self.col_formats = col_formats
    self.table_description = table_description

  def show(self, header=True):
    #if h2o.can_use_pandas():
    #  import pandas
    #  pandas.options.display.max_rows = 20
    #  print pandas.DataFrame(self.cell_values,columns=self.col_header)
    #  return
    print
    if header:
      print self.table_header + ":",
      if self.table_description: print self.table_description
    print
    table = copy.deepcopy(self.cell_values)
    nr=0
    if _is_list_of_lists(table): nr = len(table)  # only set if we truly have multiple rows... not just one long row :)
    if nr > 20:    # create a truncated view of the table, first/last 5 rows
      trunc_table =[]
      trunc_table += [ v for v in table[:5]]
      trunc_table.append(["---"]*len(table[0]))
      trunc_table += [v for v in table[(nr-5):]]
      table = trunc_table

    h2o.H2ODisplay(table, self.col_header, numalign="left", stralign="left")

  def __repr__(self):
    self.show()
    return ""

  def _parse_values(self, values, types):
    if self.col_header[0] is None:
      self.col_header = self.col_header[1:]
      types = types[1:]
      values = values[1:]
    for col_index, column in enumerate(values):
      for row_index, row_value in enumerate(column):
        if types[col_index] == 'integer':
          values[col_index][row_index]  = "" if row_value is None else int(float(row_value))

        elif types[col_index] in ['double', 'float', 'long']:
          values[col_index][row_index]  = "" if row_value is None else float(row_value)

        else:  # string?
          continue
    return zip(*values)  # transpose the values! <3 splat ops

  def __getitem__(self, item):
    if item in self.col_header: #single col selection returns list
      return list(zip(*self.cell_values)[self.col_header.index(item)])
    elif isinstance(item, slice): #row selection if item is slice returns H2OTwoDimTable
      self.cell_values = [self.cell_values[ii] for ii in xrange(*item.indices(len(self.cell_values)))]
      return self
    elif isinstance(item, list) and set(item).issubset(self.col_header): #multiple col selection returns list of cols
      return [list(zip(*self.cell_values)[self.col_header.index(i)]) for i in item]
    else:
      raise TypeError('can not support getting item for ' + str(item))

  def __setitem__(self, key, value):
    cols = zip(*self.cell_values)
    if len(cols[0]) != len(value): raise ValueError('value must be same length as columns')
    if key not in self.col_header:
      self.col_header.append(key)
      cols.append(tuple(value))
    else:
      cols[self.col_header.index(key)] = value
    self.cell_values = [list(x) for x in zip(*cols)]