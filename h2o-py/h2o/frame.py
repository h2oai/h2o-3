# -*- coding: utf-8 -*-
"""
This module contains the abstraction for H2OFrame and H2OVec objects.
"""
import itertools
# import numpy    no numpy cuz windoz
import csv
import tabulate
import uuid
import collections
import tempfile
import os
import re
import h2o
from connection import H2OConnection
from expr import Expr


class H2OFrame(object):
  """A H2OFrame represents a 2D array of data where each column is uniformly typed.

  The data may be local, or it may be in an H2O cluster. The data are loaded from a CSV
  file or the data are loaded from a native python data structure, and is either a
  python-process-local file or a cluster-local file, or a list of H2OVec objects.

  Loading Data From A CSV File
  ============================

      H2O's parser supports data of various formats coming from various sources.
      Briefly, these formats are:

          SVMLight
          CSV (data may delimited by any of the 128 ASCII characters)
          XLS

      Data sources may be:
          NFS / Local File / List of Files
          HDFS
          URL
          A Directory (with many data files inside at the *same* level -- no support for
                       recursive import of data)
          S3/S3N
          Native Language Data Structure (c.f. the subsequent section)

  Loading Data From A Python Object
  =================================

      It is possible to transfer the data that are stored in python data structures to
      H2O by using the H2OFrame constructor and the `python_obj` argument. (Note that if
      the `python_obj` argument is not `None`, then additional arguments are ignored).

      The following types are permissible for `python_obj`:

          tuple ()
          list  []
          dict  {}
          collections.OrderedDict

      The type of `python_obj` is inspected by performing an `isinstance` call. A
      ValueError will be raised if the type of `python_obj` is not one of the above
      types. Notably, sets, byte arrays, and un-contained types are not permissible.

      In the subsequent sections, each data type will be discussed in detail. Each
      discussion will be couched in terms of the "source" representation (the python
      object) and the "target" representation (the H2O object). Concretely, the topics
      of discussion will be on the following: Headers, Data Types, Number of Rows,
      Number of Columns, and Missing Values.

      Aside: Why is Pandas' DataFrame not a permissible type?

          There are two reason that Pandas' DataFrame objects are not included.
          First, it is desirable to keep the number of dependencies to a minimum, and it
          is difficult to justify the inclusion of the Pandas module as a dependency if
          its raison d'être is tied to this small detail of transferring data from
          python to H2O.

          Second, Pandas objects are simple wrappers of numpy arrays together with some
          meta data; therefore if one was adequately motivated, then the transfer of
          data from a Pandas DataFrame to an H2O Frame could readily be achieved.


      In what follows, H2OFrame and Frame will be used synonymously. Technically, an
      H2OFrame is the object-pointer that resides in the python VM and points to a Frame
      object inside of the H2O JVM. Similarly, H2OFrame, Frame, and H2O Frame will all
      refer to the same kind of object. In general, though, the context is from the
      python VM, unless otherwise specified.

      Loading: tuple ()
      =================

          Essentially, the tuple is an immutable list. This immutability does not map to
          the H2OFrame. So pythonistas be ware!

          The restrictions on what goes inside the tuple are fairly relaxed, but if they
          are too unusual, a ValueError will be raised.

          A tuple looks as follows:

              (i1, i2, i3, ..., iN)

          Restrictions are really on the types of the individual `iJ` (1 <= J <= N).

          If `iJ` is {} for some J, then a ValueError will be raised.

          If `iJ` is a () (tuple) or [] (list), then `iJ` must be a () or [] for all J;
          otherwise a ValueError will be raised.

          If `iJ` is a () or [], and if it is in fact a nested () or nested [], then a
          ValueError will be raised. In other words, only a single level of nesting is
          valid, all internal arrays must be flat -- H2O will not flatten them for you.

          If `iJ` is not a () or [], then it must be of type string or a non-complex
          numeric type (float or int). In other words, if `iJ` is not a tuple, list,
          string, float, or int, for some J, then a ValueError will be raised.

          Some acceptable inputs are:
              Example A: (1,2,3)
              Example B: ((1,2,3), (4,5,6), ("cat", "dog"))
              Example C: ((1,2,3), [4,5,6], ["blue", "yellow"], (321.239, "green","hi"))
              Example D: (3284.123891, "dog", 89)

          Note that it is perfectly fine to mix () and [] within a tuple.

          Onward.

          Headers, Columns, Rows, Data Types, and Missing Values:

          The form of the H2OFrame is as follows:

              column1, column2, column3, ..., columnN
              a11,     a12,     a13,     ..., a1N
              .        .        .        ..., .
              .        .        .        ..., .
              .        .        .        ..., .
              aM1,     aM2,     aM3,     ..., aMN

          It looks exactly like an MxN matrix with an additional header "row". This
          header cannot be specified when loading data from a () (or from a []
          but it is possible to specify a header with a python dictionary, see below
          for details).

          Headers:

              Since no header row can be specified for this case, H2O will generate a
              column header on your behalf and the column header will look like this:

                  C1, C2, C3, ..., CN

              Notably, these columns have a 1-based indexing (i.e. the 0th column is
              "C1").

          Rows and Columns and Missing Data:

              The shape of the H2OFrame is determined by the two factors:
                  the number of arrays nested in the ()
                  the number of items in each array

              If there are no nested arrays (as in Example A and Example D above), then
              the resulting H2OFrame will have shape (rows x cols):

                  1 x len(tuple)

              (i.e. a Frame with a single row).

              If there are nested arrays (as in Example B and Example C above), then
              (given the rules stated above) the resulting H2OFrame will have ROWS equal
              to the number of arrays nested within and COLUMNS equal to the maximum sub
              array:

                  max( [len(l) for l in tuple] ) x len(tuple)

              Note that this handles the issue with ragged sub arrays by assuming that
              shorter sub arrays will pad themselves with NA (missing values) at the end
              so that they become the correct length.

              Because the Frame is uniformly typed, mixing and matching data types
              within a column may produce unexpected results. Please read up on the H2O
              parser for details on how a column type is determined for a column of
              initially mixed type.

      Loading: list []
      ================

          The same discussion applies for lists as it does for tuples. Lists are mutable
          objects so there is no semantic difference regarding mutability between an
          H2OFrame and a list (as there is for a tuple).

          Additionally, a list [] is ordered (as is a tuple ()) and the data appearing
          within

      Loading: dict {} or collections.OrderedDict
      ===========================================

          Each entry in the {} is expected to represent a single column. Keys in the {}
          must be character strings following the pattern: ^[a-zA-Z_][a-zA-Z0-9_.]*$
          without restriction on length. That is a valid column name may begin with any
          letter (capital or not) or an "_", it can then be followed by any number of
          letters, digits, "_"s, or "."s.

          Values in the {} may be a flat [], a flat (), or a single int, float, or
          string value. Nested [] and () will raise a ValueError. This is the only
          additional restriction on [] and () that applies in this context.

          Note that the built-in dict does not provide any guarantees on ordering. This
          has implications on the order of columns in the eventual H2OFrame, since they
          may be written out of order from which they were initially put into the dict.

          collections.OrderedDict will preserve the order of the key-value pairs in
          which they were entered.

  """

  def __init__(self, python_obj=None, local_fname=None, remote_fname=None, vecs=None, raw_fname=None):
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
    :param raw_fname: A raw key resulting from an upload_file.
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
      cols = parse['columnNames']
      rows = parse['rows']
      veckeys = parse['vecKeys']
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

    elif raw_fname:
      self._handle_raw_fname(raw_fname, None)

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
      raise ValueError("`python_obj` must be a tuple, list, dict, collections.OrderedDict. Got: " + type(python_obj))

    if header is None or data_to_write is None:
      raise ValueError("No data to write")

    self._write_python_data_to_file_and_upload(header, data_to_write)

  def _write_python_data_to_file_and_upload(self, header, data_to_write):
    # create a temporary file that will be written to
    tmp_file_path = tempfile.mkstemp(suffix=".csv")[1]
    tmp_file = open(tmp_file_path, 'wb')
    # create a new csv writer object thingy
    csv_writer = csv.DictWriter(tmp_file, fieldnames=header, restval=None, dialect="excel", extrasaction="ignore", delimiter=",")
    # write the header
    csv_writer.writeheader()
    # write the data
    csv_writer.writerows(data_to_write)
    # close the streams
    tmp_file.close()
    # actually upload the data to H2O
    self._upload_raw_data(tmp_file_path, header)
    # delete the tmp file
    #os.remove(tmp_file_path)  # not at all secure!

  def _handle_raw_fname(self, raw_fname, column_names=None):
    """
    Handle result of upload_file
    :param raw_fname: A raw key
    :return: Part of the H2OFrame constructor.
    """
    # perform the parse setup
    setup = h2o.parse_setup(raw_fname)
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
    print "Uploaded", raw_fname, "into cluster with", rows, "rows and", len(cols), "cols"
    print

  def _upload_raw_data(self, tmp_file_path, column_names):
    # file upload info is the normalized path to a local file
    fui = {"file": os.path.abspath(tmp_file_path)}
    # create a random name for the data
    dest_key = H2OFrame.py_tmp_key()
    # do the POST -- blocking, and "fast" (does not real data upload)
    H2OConnection.post_json("PostFile", fui, destination_key=dest_key)
    # actually parse the data and setup self._vecs
    self._handle_raw_fname(dest_key, column_names)

  def __iter__(self):
    return (vec for vec in self.vecs().__iter__() if vec is not None)

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
    return [i.name() for i in self._vecs]

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

  def describe(self):
    """
    Generate an in-depth description of this H2OFrame.

    The description is a tabular print of the type, min, max, sigma, number of zeros,
    and number of missing elements for each H2OVec in this H2OFrame.

    :return: None (print to stdout)
    """
    print "Rows:", len(self._vecs[0]), "Cols:", len(self)
    headers = [vec.name() for vec in self._vecs]
    table = [
      self._row('type', None),
      self._row('mins', 0),
      self._row('mean', None),
      self._row('maxs', 0),
      self._row('sigma', None),
      self._row('zeros', None),
      self._row('missing', None)
    ]
    print tabulate.tabulate(table, headers)
    print

  #def __repr__(self):
  #  self.show()
  #  return ""

  # Find a named H2OVec and return it.  Error is name is missing
  def _find(self,name):
    for v in self._vecs:
      if name == v._name:
        return v
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
    if isinstance(i, list):
      vecs = []
      for it in i:
        if isinstance(it, int):
          vecs += self._vecs[it]
          continue
        if isinstance(it, str):
          vecs += self._find(it)
      return H2OFrame(vecs=vecs)

    raise NotImplementedError

  def __setitem__(self, b, c):
    """
    Replace a column in an H2OFrame.
    :param b: A 0-based index or a column name.
    :param c: The vector that 'b' is replaced with.
    :return: Returns this H2OFrame.
    """
    i = v = None
    #  b is a named column, fish out the H2OVec and its index
    if isinstance(b, str):
      for i, v in enumerate(self._vecs):
        if b == v.name():
          break

    # b is a 0-based column index
    elif isinstance(b, int):
      if b < 0 or b > self.__len__():
        raise ValueError("Index out of range: 0 <= " + b + " < " + self.__len__())
      i = b
      v = self.vecs()[i]
    else:
      raise NotImplementedError

    # some error checking
    if not v:
      raise ValueError("Name " + b + " not in Frame")

    if len(c) != len(v):
      raise ValueError("len(c)=" + len(c) + " not compatible with Frame len()=" + len(v))

    c._name = b
    self._vecs[i] = c

  def __delitem__(self, i):
    if isinstance(i, str):
      for v in self._vecs:
        if i == v.name():
          self._vecs.remove(v)
          return
        raise KeyError("Name " + i + " not in Frames")
      raise NotImplementedError

  def drop(self, i):
    """
    Column selection via integer, string(name) returns a Vec
    Column selection via slice returns a subset Frame
    :param i: Column to select
    :return: Returns an H2OVec or H2OFrame.
    """
    if isinstance(i, str):
      for v in self._vecs:
        if i == v.name():
          return H2OFrame(vecs=[v for v in self._vecs if i != v.name()])
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
      return H2OFrame(vecs=[x + y for x, y in zip(self._vecs, i.vecs())])
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

  # Division
  def __div__(self, i):
    if len(self) == 0: return self
    self._len_check(i)
    if isinstance(i, H2OFrame):
      return H2OFrame(vecs=[x / y for x, y in zip(self._vecs, i.vecs())])
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
    cbind += " %".join([vec._expr.eager() for vec in self.vecs()]) + "))"
    h2o.rapids(cbind)
    # And frame columns
    colnames = "(colnames= %" + fr + " {(: #0 #" + str(len(self) - 1) + ")} {"
    cnames = ';'.join([vec.name() for vec in self.vecs()])
    colnames += cnames + "})"
    h2o.rapids(colnames)
    return fr

  def _row(self, field, idx):
    l = [field]
    for vec in self._vecs:
      tmp = vec.summary()[field]
      l.append(tmp[idx] if idx is not None else tmp)
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
    return _vecs[0]._len_check(x)

class H2OVec(object):
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

  # Boolean column select lookup
  def row_select(self, vec):
    """
    Boolean column select lookup
    :param vec: An H2OVec.
    :return: A new H2OVec.
    """
    return H2OVec(self._name, Expr("[", self, vec))

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
      self._expr = Expr("=", Expr("[", self._expr, b), c)
    else:
      raise NotImplementedError("Only vector replacement is currently supported.")

  def __add__(self, i):
    """
    Basic binary addition.
    Supports H2OVec + H2OVec and H2OVec + int
    :param i: A Vec or a float
    :return: A new H2OVec.
    """
    # H2OVec + H2OVec
    if isinstance(i, H2OVec):
      # can only add two vectors of the same length
      self._len_check(i)
      # lazy new H2OVec
      return H2OVec(self._name, Expr("+", self, i))

    # H2OVec + number
    if isinstance(i, (int, float)):
      if i == 0:  return self
      # lazy new H2OVec
      return H2OVec(self._name, Expr("+", self, Expr(i)))
    raise NotImplementedError

  def __radd__(self, i):
    """
    Add is commutative: call __add__(i)
    :param i: A Vec or a float.
    :return: A new H2OVec.
    """
    return self.__add__(i)

  def __div__(self, i):
    """
    :param i: A Vec or a float
    :return: A new H2OVec.
    """
    # H2OVec / H2OVec
    if isinstance(i, H2OVec):
      self._len_check(i)
      return H2OVec(self._name, Expr("/", self, i))

    # H2OVec / number
    if isinstance(i, (int, float)):
      return H2OVec(self._name, Expr("/", self, Expr(i)))
    raise NotImplementedError

  def __eq__(self, i):
    """
    Perform the '==' operation.
    :param i: An H2OVec or a number.
    :return: A new H2OVec.
    """
    # == compare on two H2OVecs
    if isinstance(i, H2OVec):
      # can only compare two vectors of the same length
      self._len_check(i)
      # lazy new H2OVec
      return H2OVec(self._name, Expr("==", self, i))
    # == compare on a Vec and a constant Vec
    if isinstance(i, (int, float)):
      # lazy new H2OVec
      return H2OVec(self._name, Expr("==", self, Expr(i)))
    raise NotImplementedError

  def __lt__(self, i):
    # Vec < Vec
    if isinstance(i, H2OVec):
      self._len_check(i)
      return H2OVec(self._name, Expr("<", self, i))

    # Vec < number
    elif isinstance(i, (int, float)):
      return H2OVec(self._name, Expr("<", self, Expr(i)))

    else:
      raise NotImplementedError

  def __ge__(self, i):
    # Vec >= Vec
    if isinstance(i, H2OVec):
      self._len_check(i)
      return H2OVec(self._name, Expr(">=", self, i))
    # Vec >= number
    elif isinstance(i, (int, float)):
      return H2OVec(self._name, Expr(">=", self, Expr(i)))
    else:
      raise NotImplementedError

  def __len__(self):
    """
    :return: The length of this H2OVec
    """
    return len(self._expr)

  def floor(self):
    """
    :return: A lazy Expr representing the Math.floor() of this H2OVec.
    """
    return Expr("floor", self._expr, None)

  def mean(self):
    """
    :return: A lazy Expr representing the mean of this H2OVec.
    """
    return Expr("mean", self._expr, None, length=1)

  def asfactor(self):
    """
    :return: A transformed H2OVec from numeric to categorical.
    """
    return H2OVec(self._name, Expr("as.factor", self._expr, None))

  def runif(self, seed=None):
    """
    :param seed: A random seed. If None, then one will be generated.
    :return: A new H2OVec filled with doubles sampled uniformly from [0,1).
    """
    if not seed:
      import random
      seed = random.randint(123456789, 999999999)  # generate a seed
    return H2OVec("", Expr("h2o.runif", self._expr, Expr(seed)))

  def _len_check(self,x):
    if not x: return
    if isinstance(x,H2OFrame): x = x._vecs[0]
    if not isinstance(x,H2OVec): return
    if len(self) != len(x):
      raise ValueError("H2OVec length mismatch: "+len(self)+" vs "+len(x))
