# -*- coding: utf-8 -*-
# import numpy    no numpy cuz windoz
import collections, csv, itertools, os, re, tabulate, tempfile, uuid
import h2o
from connection import H2OConnection
from expr import Expr


class H2OFrame:

  def __init__(self, python_obj=None, local_fname=None, remote_fname=None, vecs=None, text_key=None):
    """
    Create a new H2OFrame object by passing a file path or a list of H2OVecs.

    If `remote_fname` is not None, then a REST call will be made to import the
    data specified at the location `remote_fname`.  This path is relative to the
    H2O cluster, NOT the local Python process

    If `local_fname` is not None, then the data is not imported into the H2O cluster
    at the time of object creation.

    If `python_obj` is not None, then an attempt to upload the python object to H2O
    will be made. A valid python object has type `list`, or `dict`.

    For more information on the structure of the input for the various native python
    data types ("native" meaning non-H2O), please see the general documentation for
    this object.

    :param python_obj: A "native" python object - list, dict, tuple.
    :param local_fname: A local path to a data source. Data is python-process-local.
    :param remote_fname: A remote path to a data source. Data is cluster-local.
    :param vecs: A list of H2OVec objects.
    :param text_key: A raw key resulting from an upload_file.
    :return: An instance of an H2OFrame object.
    """
    self.local_fname = local_fname
    self.remote_fname = remote_fname
    self._vecs = None

    if python_obj is not None:  # avoids the truth value of an array is ambiguous err
      self._upload_python_object(python_obj)
      return

    # Import the data into H2O cluster
    if remote_fname:
      rawkey = h2o.import_file(remote_fname)
      setup = h2o.parse_setup(rawkey)
      parse = h2o.parse(setup, H2OFrame.py_tmp_key())  # create a new key
      veckeys = parse['vecKeys']
      rows = parse['rows']
      cols = parse['columnNames'] if parse["columnNames"] else ["C" + str(x) for x in range(1,len(veckeys)+1)] 
      self._vecs = H2OVec.new_vecs(zip(cols, veckeys), rows)
      print "Imported", remote_fname, "into cluster with", rows, "rows and", len(cols), "cols"

    # Read data locally into python process
    elif local_fname:
      with open(local_fname, 'rb') as csvfile:
        self._vecs = []
        for name in csvfile.readline().split(','):
          self._vecs.append(H2OVec(name.rstrip(), Expr([])))
        for row in csv.reader(csvfile):
          for i, data in enumerate(row):
            self._vecs[i].append(data)
      print "Imported", local_fname, "into local python process"

    # Construct from an array of Vecs already passed in
    elif vecs:
      vlen = len(vecs[0])
      for v in vecs:
        if not isinstance(v, H2OVec):
          raise ValueError("Not a list of Vecs")
        if len(v) != vlen:
          raise ValueError("Vecs not the same size: " + str(vlen) + " != " + str(len(v)))
      self._vecs = vecs

    elif text_key:
      self._handle_text_key(text_key, None)

    else:
      raise ValueError("Frame made from CSV file or an array of Vecs only")

  def _upload_python_object(self, python_obj):
    """
    Properly handle native python data types. For a discussion of the rules and
    permissible data types please refer to the main documentation for H2OFrame.

    :param python_obj: A tuple, list, dict, collections.OrderedDict
    :return: None
    """
    # [] and () cases -- folded together since H2OFrame is mutable
    if isinstance(python_obj, (list, tuple)):
      header, data_to_write = H2OFrame._handle_python_lists(python_obj)

    # {} and collections.OrderedDict cases
    elif isinstance(python_obj, (dict, collections.OrderedDict)):
      header, data_to_write = H2OFrame._handle_python_dicts(python_obj)

    # handle a numpy.ndarray
    # elif isinstance(python_obj, numpy.ndarray):
    #
    #     header, data_to_write = H2OFrame._handle_numpy_array(python_obj)
    else:
      raise ValueError("`python_obj` must be a tuple, list, dict, collections.OrderedDict. Got: " + str(type(python_obj)))

    if header is None or data_to_write is None:
      raise ValueError("No data to write")

    #
    ## write python data to file and upload
    #

    # create a temporary file that will be written to
    tmp_handle,tmp_path = tempfile.mkstemp(suffix=".csv")
    tmp_file = os.fdopen(tmp_handle,'wb')
    # create a new csv writer object thingy
    csv_writer = csv.DictWriter(tmp_file, fieldnames=header, restval=None, dialect="excel", extrasaction="ignore", delimiter=",")
    csv_writer.writeheader()            # write the header
    csv_writer.writerows(data_to_write) # write the data
    tmp_file.close()                    # close the streams
    self._upload_raw_data(tmp_path, header) # actually upload the data to H2O
    os.remove(tmp_path)                     # delete the tmp file

  def _handle_text_key(self, text_key, column_names):
    """
    Handle result of upload_file
    :param test_key: A key pointing to raw text to be parsed
    :return: Part of the H2OFrame constructor.
    """
    # perform the parse setup
    setup = h2o.parse_setup(text_key)
    # blocking parse, first line is always a header (since "we" wrote the data out)
    parse = h2o.parse(setup, H2OFrame.py_tmp_key(), first_line_is_header=1)
    # a hack to get the column names correct since "parse" does not provide them
    cols = column_names if column_names and not parse["columnNames"] else parse['columnNames']
    # set the rows
    rows = parse['rows']
    # set the vector keys
    veckeys = parse['vecKeys']
    # create a new vec[] array
    self._vecs = H2OVec.new_vecs(zip(cols, veckeys), rows)
    # print some information on the *uploaded* data
    print "Uploaded", text_key, "into cluster with", rows, "rows and", len(cols), "cols"

  def _upload_raw_data(self, tmp_file_path, column_names):
    # file upload info is the normalized path to a local file
    fui = {"file": os.path.abspath(tmp_file_path)}
    # create a random name for the data
    dest_key = H2OFrame.py_tmp_key()
    # do the POST -- blocking, and "fast" (does not real data upload)
    H2OConnection.post_json("PostFile", fui, destination_key=dest_key)
    # actually parse the data and setup self._vecs
    self._handle_text_key(dest_key, column_names)

  def __iter__(self):
    return (vec for vec in self._vecs.__iter__() if vec is not None)

  def vecs(self):
    """
    Retrieve the array of H2OVec objects comprising this H2OFrame.
    :return: The array of H2OVec objects.
    """
    return self._vecs

  def col_names(self):
    """
    Retrieve the column names (one name per H2OVec) for this H2OFrame.
    :return: A character list[] of column names.
    """
    return [i._name for i in self._vecs]

  def names(self):
    """
    Retrieve the column names (one name per H2OVec) for this H2OFrame.
    :return: A character list[] of column names.
    """
    return self.col_names()

  def nrow(self):
    """
    Get the number of rows in this H2OFrame.
    :return: The number of rows in this dataset.
    """
    return len(self._vecs[0])

  def ncol(self):
    """
    Get the number of columns in this H2OFrame.
    :return: The number of columns in this H2OFrame.
    """
    return len(self)

  # Print [col, cols...]
  def show(self):
    if len(self) == 1:
      to_show = [[v] for v in self._vecs[0].show(noprint=True)]
      print tabulate.tabulate(to_show, headers=self.names())
    else:
      vecs = [vec.show(noprint=True) for vec in self]
      vecs.insert(0, range(1, len(vecs[0]) + 1, 1))
      print "Displaying " + str(len(vecs[0])) + " row(s):"
      print tabulate.tabulate(zip(*vecs), headers=["Row ID"] + self.names())
      print

  def head(self, rows=10, cols=200, **kwargs):
    nrows = min(self.nrow(), rows)
    ncols = min(self.ncol(), cols)
    colnames = self.names()[0:ncols]

    fr = H2OFrame.py_tmp_key()
    cbind = "(= !" + fr + " (cbind %"
    cbind += " %".join([vec._expr.eager() for vec in self]) + "))"
    res = h2o.rapids(cbind)
    h2o.remove(fr)
    head_rows = [range(1, nrows + 1, 1)]
    head_rows += [rows[0:nrows] for rows in res["head"][0:ncols]]
    head = zip(*head_rows)
    print "First", str(nrows), "rows and first", str(ncols), "columns: "
    print tabulate.tabulate(head, headers=["Row ID"] + colnames)
    print

  def tail(self, rows=10, cols=200, **kwargs):
    nrows = min(self.nrow(), rows)
    ncols = min(self.ncol(), cols)
    colnames = self.names()[0:ncols]

    exprs = [self[c][(self.nrow()-nrows):(self.nrow())] for c in range(ncols)]
    print "Last", str(nrows), "rows and first", str(ncols), "columns: "
    if nrows != 1:
      fr = H2OFrame.py_tmp_key()
      cbind = "(= !" + fr + " (cbind %"
      cbind += " %".join([expr.eager() for expr in exprs]) + "))"
      res = h2o.rapids(cbind)
      h2o.remove(fr)
      tail_rows = [range(self.nrow()-nrows+1, self.nrow() + 1, 1)]
      tail_rows += [rows[0:nrows] for rows in res["head"][0:ncols]]
      tail = zip(*tail_rows)
      print tabulate.tabulate(tail, headers=["Row ID"] + colnames)
    else:
      print tabulate.tabulate([[self.nrow()] + [expr.eager() for expr in exprs]], headers=["Row ID"] + colnames)
    print

  def describe(self):
    """
    Generate an in-depth description of this H2OFrame.

    The description is a tabular print of the type, min, max, sigma, number of zeros,
    and number of missing elements for each H2OVec in this H2OFrame.

    :return: None (print to stdout)
    """
    print "Rows:", len(self._vecs[0]), "Cols:", len(self)
    headers = [vec._name for vec in self._vecs]
    table = [
      self._row('type', None),
      self._row('mins', 0),
      self._row('mean', None),
      self._row('maxs', 0),
      self._row('sigma', None),
      self._row('zeros', None),
      self._row('missing', None)
    ]

    chunk_summary_tmp_key = H2OFrame.send_frame(self)

    chunk_summary = h2o.frame(chunk_summary_tmp_key)["frames"][0]["chunkSummary"]

    h2o.remove(chunk_summary_tmp_key)

    print tabulate.tabulate(table, headers)
    print
    print chunk_summary
    print

  #def __repr__(self):
  #  self.show()
  #  return ""

  # Find a named H2OVec and return it.  Error is name is missing
  def _find(self,name):
    return self._vecs[self._find_idx(name)];

  # Find a named H2OVec and return the zero-based index for it.  Error is name is missing
  def _find_idx(self,name):
    for i,v in enumerate(self._vecs):
      if name == v._name:
        return i
    raise ValueError("Name " + name + " not in Frame")

  # Column selection via integer, string (name) returns a Vec
  # Column selection via slice returns a subset Frame
  def __getitem__(self, i):
    if isinstance(i, int):   return self._vecs[i]
    if isinstance(i, str):   return self._find(i)
    # Slice; return a Frame not a Vec
    if isinstance(i, slice): return H2OFrame(vecs=self._vecs[i])
    # Row selection from a boolean Vec
    if isinstance(i, H2OVec):
      self._len_check(i)
      return H2OFrame(vecs=[x.row_select(i) for x in self._vecs])

    # have a list of numbers or strings
    if isinstance(i, (list,tuple)):
      vecs = []
      for it in i:
        if isinstance(it, int):    vecs.append(self._vecs[it])
        elif isinstance(it, str):  vecs.append(self._find(it))
        else:                      raise NotImplementedError
      return H2OFrame(vecs=vecs)

    raise NotImplementedError("Slicing by unknown type: "+str(type(i)))

  def __setitem__(self, b, c):
    """
    Replace a column in an H2OFrame.
    :param b: A 0-based index or a column name.
    :param c: The vector that 'b' is replaced with.
    :return: Returns this H2OFrame.
    """
    #  b is a named column, fish out the H2OVec and its index
    ncols = len(self._vecs)
    if isinstance(b, str):  
      for i in xrange(ncols):
        if b == self._vecs[i]._name:
          break
      else:
        i = ncols               # Not found, so append at end
    # b is a 0-based column index
    elif isinstance(b, int):
      if b < 0 or b > self.__len__():
        raise ValueError("Index out of range: 0 <= " + b + " < " + self.__len__())
      i = b
      b = self._vecs[i]._name
    else:  raise NotImplementedError
    self._len_check(c)
    # R-like behavior: the column name remains the same, even if replacing via index
    c._name = b
    if i >= ncols: self._vecs.append(c)
    else:          self._vecs[i] = c

  # Modifies the collection in-place to remove a named item
  def __delitem__(self, i):
    if isinstance(i, str):
      return self._vecs.pop(self._find_idx(i))
    raise NotImplementedError

  # Makes a new collection
  def drop(self, i):
    """
    Column selection via integer, string(name) returns a Vec
    Column selection via slice returns a subset Frame
    :param i: Column to select
    :return: Returns an H2OVec or H2OFrame.
    """
    if isinstance(i, str):
      for v in self._vecs:
        if i == v._name:
          return H2OFrame(vecs=[v for v in self._vecs if i != v._name])
      raise ValueError("Name " + i + " not in Frame")
    raise NotImplementedError

  def __len__(self):
    """
    :return: Number of columns in this H2OFrame
    """
    return len(self._vecs)

  # Addition
  def __add__(self, i):
    if len(self) == 0: return self
    self._len_check(i)
    if isinstance(i, H2OFrame):
      return H2OFrame(vecs=[x + y for x, y in zip(self._vecs, i._vecs)])
    if isinstance(i, H2OVec):
      return H2OFrame(vecs=[x + i for x in self._vecs])
    if isinstance(i, int):
      return H2OFrame(vecs=[x + i for x in self._vecs])
    raise NotImplementedError

  def __radd__(self, i):
    """
    Add is commutative, so call __add__
    :param i: The value to add
    :return: Return a new H2OFrame
    """
    return self.__add__(i)

  def __and__(self, i):
    print "FRAME AND"
    if len(self) == 0: return self
    self._len_check(i)
    if isinstance(i, H2OFrame):
      return H2OFrame(vecs=[x and y for x, y in zip(self._vecs, i._vecs)])
    if isinstance(i, H2OVec):
      return H2OFrame(vecs=[x and i for x in self._vecs])
    if isinstance(i, int,bool):
      return H2OFrame(vecs=[x and i for x in self._vecs])
    raise NotImplementedError

  # Division
  def __div__(self, i):
    if len(self) == 0: return self
    self._len_check(i)
    if isinstance(i, H2OFrame):
      return H2OFrame(vecs=[x / y for x, y in zip(self._vecs, i._vecs)])
    if isinstance(i, H2OVec):
      return H2OFrame(vecs=[x / i for x in self._vecs])
    if isinstance(i, int):
      return H2OFrame(vecs=[x / i for x in self._vecs])
    raise NotImplementedError

  @staticmethod
  def py_tmp_key():
    """
    :return: a unique h2o key obvious from python
    """
    return unicode("py" + str(uuid.uuid4()))

  # Send over a frame description to H2O
  def send_frame(self):
    """
    Send a frame description to H2O, returns a key.
    :return: A key
    """
    # Send over the frame
    fr = H2OFrame.py_tmp_key()
    cbind = "(= !" + fr + " (cbind %"
    cbind += " %".join([vec._expr.eager() for vec in self._vecs]) + "))"
    h2o.rapids(cbind)
    # And frame columns
    colnames = "(colnames= %" + fr + " {(: #0 #" + str(len(self) - 1) + ")} {"
    cnames = ';'.join([vec._name for vec in self._vecs])
    colnames += cnames + "})"
    h2o.rapids(colnames)
    return fr

  def _row(self, field, idx):
    l = [field]
    for vec in self._vecs:
      tmp = vec.summary()[field]
      l.append(tmp[idx] if idx is not None and tmp is not None else tmp)
    return l

  # private static methods
  @staticmethod
  def _gen_header(cols):
    return ["C" + str(c) for c in range(1, cols + 1, 1)]

  @staticmethod
  def _check_lists_of_lists(python_obj):
    # all items in the list must be a list too
    lol_all = all(isinstance(l, (tuple, list)) for l in python_obj)
    # All items in the list must be a list!
    if not lol_all:
      raise ValueError("`python_obj` is a mixture of nested lists and other types.")

    # in fact, we must have a list of flat lists!
    for l in python_obj:
      if any(isinstance(ll, (tuple, list)) for ll in l):
        raise ValueError("`python_obj` is not a list of flat lists!")

  @staticmethod
  def _handle_python_lists(python_obj):
    cols = len(python_obj)  # cols will be len(python_obj) if not a list of lists
    # do we have a list of lists: [[...], ..., [...]] ?
    lol = H2OFrame._is_list_of_lists(python_obj)
    if lol:
      # must be a list of flat lists, raise ValueError if not
      H2OFrame._check_lists_of_lists(python_obj)
      # have list of lists, each list is a row
      # length of the longest list is the number of columns
      cols = max([len(l) for l in python_obj])

    # create the header
    header = H2OFrame._gen_header(cols)
    # shape up the data for csv.DictWriter
    data_to_write = [dict(zip(header, row)) for row in python_obj] if lol else [dict(zip(header, python_obj))]
    return header, data_to_write

  @staticmethod
  def _is_list_of_lists(o): return any(isinstance(l, (list, tuple)) for l in o)

  @staticmethod
  def _handle_python_dicts(python_obj):
    header = python_obj.keys()
    # is this a valid header?
    is_valid = all([re.match(r'^[a-zA-Z_][a-zA-Z0-9_.]*$', col) for col in header])
    if not is_valid:
      raise ValueError("Did not get a valid set of column names! Must match the regular expression: ^[a-zA-Z_][a-zA-Z0-9_.]*$ ")
    # check that each value entry is a flat list/tuple
    for k in python_obj:
      v = python_obj[k]
      # if value is a tuple/list, then it must be flat
      if isinstance(v, (tuple, list)):
        if H2OFrame._is_list_of_lists(v):
          raise ValueError("Values in the dictionary must be flattened!")

    rows = map(list, itertools.izip_longest(*python_obj.values()))
    data_to_write = [dict(zip(header, row)) for row in rows]
    return header, data_to_write

# @staticmethod
  # def _handle_numpy_array(python_obj):
  #     header = H2OFrame._gen_header(python_obj.shape[1])
  #
  #     as_list = python_obj.tolist()
  #     lol = H2OFrame._is_list_of_lists(as_list)
  #     data_to_write = [dict(zip(header, row)) for row in as_list] \
  #         if lol else [dict(zip(header, as_list))]
  #
  #     return header, data_to_write
  def _len_check(self,x):
    if len(self) == 0: return
    return self._vecs[0]._len_check(x)

  # Quantiles
  def quantile(self, prob=None):
    if len(self) == 0: return self
    return H2OFrame(vecs=[vec.quantile(prob) for vec in self._vecs ])

  # ddply in h2o
  def ddply(self,cols,fun):
    """
    :param cols: Column names used to control grouping
    :param fun: Function to execute on each group.  Right now limited to textual Rapids expression
    :return: New frame with 1 row per-group, of results from 'fun'
    """
    # Confirm all names present in dataset; collect column indices
    colnums = [str(self._find_idx(name)) for name in cols]
    rapids_series = "{"+";".join(colnums)+"}"
  
    # Eagerly eval and send the cbind'd frame over
    key = self.send_frame()
    tmp_key = H2OFrame.py_tmp_key()
    expr = "(= !{} (h2o.ddply %{} {} {}))".format(tmp_key,key,rapids_series,fun)
    h2o.rapids(expr) # ddply in h2o
    # Remove h2o temp frame after ddply
    h2o.remove(key)
    # Make backing H2OVecs for the remote h2o vecs
    j = h2o.frame(tmp_key) # Fetch the frame as JSON
    fr = j['frames'][0]    # Just the first (only) frame
    rows = fr['rows']      # Row count
    veckeys = fr['veckeys']# List of h2o vec keys
    cols = fr['columns']   # List of columns
    colnames = [col['label'] for col in cols]
    return H2OFrame(vecs=H2OVec.new_vecs(zip(colnames, veckeys), rows))

  def merge(self, other, allLeft=False, allRite=False):
    """
    Merge two datasets based on common column names
    :param other: Other dataset to merge.  Must have at least one column in
    common with self, and all columns in common are used as the merge key.  If
    you want to use only a subset of the columns in common, rename the other
    columns so the columns are unique in the merged result.
    :param allLeft: If true, include all rows from the left/self frame
    :param allRite: If true, include all rows from the right/other frame
    :return: Original self frame enhanced with merged columns and rows
    """
    for v0 in self._vecs:
      for v1 in other._vecs:
        if v0._name==v1._name: break
      if v0._name==v1._name: break
    else:
      raise ValueError("frames must have some columns in common to merge on")
    # Eagerly eval and send the cbind'd frame over
    lkey = self .send_frame()
    rkey = other.send_frame()
    tmp_key = H2OFrame.py_tmp_key()
    expr = "(= !{} (merge %{} %{} %{} %{}))".format(tmp_key,lkey,rkey,
                                                    "TRUE" if allLeft else "FALSE",
                                                    "TRUE" if allRite else "FALSE")
    # Remove h2o temp frame after merge
    expr2 = "(, "+expr+" (del %"+lkey+" #0) (del %"+rkey+" #0) )"

    h2o.rapids(expr2)      # merge in h2o
    # Make backing H2OVecs for the remote h2o vecs
    j = h2o.frame(tmp_key) # Fetch the frame as JSON
    fr = j['frames'][0]    # Just the first (only) frame
    rows = fr['rows']      # Row count
    veckeys = fr['veckeys']# List of h2o vec keys
    cols = fr['columns']   # List of columns
    colnames = [col['label'] for col in cols]
    return H2OFrame(vecs=H2OVec.new_vecs(zip(colnames, veckeys), rows))

class H2OVec:
  """
  A single column of data that is uniformly typed and possibly lazily computed.
  """

  def __init__(self, name, expr):
    """
    Create a new instance of an H2OVec object
    :param name: The name of the column corresponding to this H2OVec.
    :param expr: The lazy expression representing this H2OVec
    :return: A new H2OVec
    """
    assert isinstance(name, str)
    assert isinstance(expr, Expr)
    self._name = name  # String
    self._expr = expr  # Always an expr
    expr._name = name  # Pass name along to expr

  @staticmethod
  def new_vecs(vecs=None, rows=-1):
    if not vecs:  return vecs
    return [H2OVec(str(col), Expr(op=veckey['name'], length=rows))  for idx, (col, veckey) in enumerate(vecs)]

  def name(self):
    return self._name

  def get_expr(self):
    return self._expr

  def append(self, data):
    """
    Append a value during CSV read, convert to float.

    :param data: An element being appended to the end of this H2OVec
    :return: void
    """
    __x__ = data
    try:
      __x__ = float(data)
    except ValueError:
      pass
    self._expr.data().append(__x__)
    self._expr.set_len(self._expr.get_len() + 1)

  def show(self, noprint=False):
    """
    Pretty print this H2OVec, or return values up to an iterator on an enclosing Frame
    :param noprint: A boolean stating whether to print or to return data.
    :return: If noprint is False, then self._expr is returned.
    """
    if noprint:
      return self._expr.show(noprint=True)
    else:
      to_show = [[v] for v in self._expr.show(noprint=True)]
      nrows = min(11, len(to_show) + 1) - 1
      for i in range(1, min(11, len(to_show) + 1), 1):
        to_show[i - 1].insert(0, i)
      header = self._name + " (first " + str(nrows) + " row(s))"
      print tabulate.tabulate(to_show, headers=["Row ID", header])
      print

  #def __repr__(self):
  #  self.show()
  #  return ""

  def summary(self):
    """
    Compute the rollup data summary (min, max, mean, etc.)
    :return: the summary from this Expr object
    """
    return self._expr.summary()

  def __getitem__(self, i):
    """
    Basic index/sliced lookup
    :param i: An Expr or an H2OVec
    :return: A new Expr object corresponding to the input query
    """
    if isinstance(i, H2OVec):
      return self.row_select(i)
    e = Expr(i)
    return Expr("[", self, e, length=len(e))

  # Boolean column select lookup.  Eager, to compute the result length
  def row_select(self, vec):
    """
    Boolean column select lookup
    :param vec: An H2OVec.
    :return: A new H2OVec.
    """
    e = Expr("[", self, vec)
    j = h2o.frame(e.eager())
    e.set_len(j['frames'][0]['rows'])
    return H2OVec(self._name, e)

  def __setitem__(self, b, c):
    """
    Update-in-place of a Vec.
    This interface currently only supports whole vector replacement.

    If `c` has length 1, then it's assumed that `c` represents a constant vector
    of its current value.

    :param b: An H2OVec for selecting rows to update in-place.
    :param c: The "new" values that will write over the values stipulated by `b`.
    :return: void
    """
    self._len_check(c)
    # row-wise assignment
    if isinstance(b, H2OVec):
      # whole vec replacement
      self._len_check(b)
      # lazy update in-place of the whole vec
      self._expr = Expr("=", Expr("[", self._expr, b), None if c is None else Expr(c))
    else:
      raise NotImplementedError("Only vector replacement is currently supported.")

  # Simple boolean operators, which auto-expand a right scalar argument
  def _simple_bin_op( self, i, op):
    if isinstance(i,  H2OVec     ):  return H2OVec(self._name, Expr(op, self._len_check(i), i))
    if isinstance(i, (int, float)):  return H2OVec(self._name, Expr(op, self, Expr(i)))
    if isinstance(i, Expr)        :  return H2OVec(self._name, Expr(op, self, i))
    if op == "==" and i is None   :  return H2OVec(self._name, Expr("is.na", self._expr, None))
    raise NotImplementedError

  def _simple_bin_rop(self, i, op):
    if isinstance(i,  H2OVec     ):  return H2OVec(self._name, Expr(op, i, self._len_check(i)))
    if isinstance(i, (int, float)):  return H2OVec(self._name, Expr(op, Expr(i), self))
    if isinstance(i, Expr)        :  return H2OVec(self._name, Expr(op, i, self))
    raise NotImplementedError


  def __add__(self, i):  return self._simple_bin_op(i,"+" )
  def __sub__(self, i):  return self._simple_bin_op(i,"-" )
  def __and__(self, i):  return self._simple_bin_op(i,"&" )
  def __or__ (self, i):  return self._simple_bin_op(i,"|" )
  def __div__(self, i):  return self._simple_bin_op(i,"/" )
  def __mul__(self, i):  return self._simple_bin_op(i,"*" )
  def __eq__ (self, i):  return self._simple_bin_op(i,"==")
  def __neg__(self, i):  return self._simple_bin_op(i,"!=")
  def __pow__(self, i):  return self._simple_bin_op(i,"^" )
  def __ge__ (self, i):  return self._simple_bin_op(i,">=")
  def __gt__ (self, i):  return self._simple_bin_op(i,">" )
  def __le__ (self, i):  return self._simple_bin_op(i,"<=")
  def __lt__ (self, i):  return self._simple_bin_op(i,"<" )

  def __radd__(self, i): return self.__add__(i)  # commutativity
  def __rsub__(self, i): return self._simple_bin_rop(i,"-")  # not commutative
  def __rand__(self, i): return self.__and__(i)  # commutativity (no short circuiting)
  def __ror__ (self, i): return self.__or__ (i)
  def __rdiv__(self, i): return self._simple_bin_rop(i,"/")  # not commutative
  def __rmul__(self, i): return self.__mul__(i)


  def __len__(self):
    """
    :return: The length of this H2OVec
    """
    return len(self._expr)

  def floor(self):
    """
    :return: A lazy Expr representing the Math.floor() of this H2OVec.
    """
    return H2OVec(self._name,Expr("floor", self._expr, None))

  def mean(self):
    """
    :return: A lazy Expr representing the mean of this H2OVec.
    """
    return Expr("mean", self._expr, None, length=1)

  def quantile(self,prob=None):
    """
    :return: A lazy Expr representing the quantiles of this H2OVec.
    """
    if not prob: prob=[0.01,0.1,0.25,0.333,0.5,0.667,0.75,0.9,0.99]
    return H2OVec(self._name,Expr("quantile", self, Expr(prob), length=len(prob)))

  def asfactor(self):
    """
    :return: A transformed H2OVec from numeric to categorical.
    """
    return H2OVec(self._name, Expr("as.factor", self._expr, None))

  def month(self):
    """
    :return: Returns a new month column from a msec-since-Epoch column
    """
    return H2OVec(self._name, Expr("month", self._expr, None))

  def dayOfWeek(self):
    """
    :return: Returns a new Day-of-Week column from a msec-since-Epoch column
    """
    return H2OVec(self._name, Expr("dayOfWeek", self._expr, None))

  def runif(self, seed=None):
    """
    :param seed: A random seed. If None, then one will be generated.
    :return: A new H2OVec filled with doubles sampled uniformly from [0,1).
    """
    if not seed:
      import random
      seed = random.randint(123456789, 999999999)  # generate a seed
    return H2OVec("", Expr("h2o.runif", self._expr, Expr(seed)))

  # Error if lengths are not compatible.  Return self for flow-coding
  def _len_check(self,x):
    if not x: return self
    if isinstance(x,H2OFrame): x = x._vecs[0]
    if isinstance(x,Expr): raise ValueError("Mixing Vec and Expr")
    if not isinstance(x,H2OVec): return self
    if len(self) != len(x):
      raise ValueError("H2OVec length mismatch: "+str(len(self))+" vs "+str(len(x)))
    return self

  @staticmethod
  def mktime(year=1970,month=0,day=0,hour=0,minute=0,second=0,msec=0):
    """
    All units are zero-based (including months and days).  Missing year is 1970.
    :return: Returns msec since the Epoch.
    """
    # Some error checking on length
    xlen = 1
    e = None
    for x in [msec,second,minute,hour,day,month,year]:
      (l,x) = (1,Expr(x)) if isinstance(x,int) else (len(x),x)
      if xlen != l:
        if xlen == 1: xlen = l
        else:  raise ValueError("length of "+str(x)+" not compatible with "+xlen)
      e = Expr(",", x, e)
    e2 = Expr("mktime",e,None,xlen)
    return e2 if xlen==1 else H2OVec("mktime",e2)
