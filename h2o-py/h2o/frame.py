# -*- coding: utf-8 -*-
# import numpy    no numpy cuz windoz
import collections, csv, itertools, os, re, tempfile, uuid, copy, urllib, sys, imp
import h2o
from connection import H2OConnection
from expr import Expr
from job  import H2OJob


class H2OFrame:

  def __del__(self):
    """
    :return: None
    """

    # this is essentially a hack to short-circuit the Expr.__del__ method when doing /DKV/<frame> remove
    vecs=[]
    for v in self._vecs:
      cnt = sys.getrefcount(v)
      # magical count of 4 for each vec if it will NOT be deleted
      # 1 for __del__ call, 1 for __del__ local dict
      # 1 for _vecs
      # 1 for parent
      if v._expr.is_pending() or cnt>=4: continue  # leave vec alone!
      else:                                        # collect the vecs to be deleted in bulk.
        v._expr._removed_by_frame_del=True
        vecs+=[v]

    if len(vecs)==0: return
    h2o.remove(H2OFrame(vecs=vecs))

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
      veckeys = parse['vec_ids']
      rows = parse['rows']
      cols = parse['column_names'] if parse["column_names"] else ["C" + str(x) for x in range(1,len(veckeys)+1)]
      self._vecs = H2OVec.new_vecs(zip(cols, veckeys), rows)
      thousands_sep = h2o.H2ODisplay.THOUSANDS
      if isinstance(remote_fname, str):
        print "Imported ", remote_fname, ". Parsed {} rows and {} cols".format(thousands_sep.format(rows), thousands_sep.format(len(cols)))
      else:
        h2o.H2ODisplay([["File"+str(i+1),f] for i,f in enumerate(remote_fname)],None, "Parsed {} rows and {} cols".format(thousands_sep.format(rows), thousands_sep.format(len(cols))))

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
    # attempt to import numpy
    have_numpy = False
    try:
      imp.find_module('numpy')
      import numpy
      have_numpy = True
    except ImportError:
      pass

    # attempt to import pandas
    have_pandas = False
    try:
      imp.find_module('pandas')
      import pandas
      have_pandas = True
    except ImportError:
      pass

    # [] and () cases -- folded together since H2OFrame is mutable
    if isinstance(python_obj, (list, tuple)):
      header, data_to_write = H2OFrame._handle_python_lists(python_obj)

    # {} and collections.OrderedDict cases
    elif isinstance(python_obj, (dict, collections.OrderedDict)):
      header, data_to_write = H2OFrame._handle_python_dicts(python_obj)

    # try to handle a numpy.ndarray
    elif have_numpy and isinstance(python_obj, numpy.ndarray):
        header, data_to_write = H2OFrame._handle_numpy_array(python_obj)

    # try to handle a pandas.DataFrame
    elif have_pandas and isinstance(python_obj, pandas.DataFrame):
      header, data_to_write = H2OFrame._handle_pandas_data_frame(python_obj)

    else:
      raise ValueError("`python_obj` must be a tuple, list, dict, collections.OrderedDict, numpy.ndarray, or "
                       "pandas.DataFrame. Got: " + str(type(python_obj)))

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
    csv_writer.writeheader()                 # write the header
    csv_writer.writerows(data_to_write)      # write the data
    tmp_file.close()                         # close the streams
    self._upload_raw_data(tmp_path, header)  # actually upload the data to H2O
    os.remove(tmp_path)                      # delete the tmp file

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
    cols = parse['column_names'] if parse["column_names"] else ["C" + str(x) for x in range(1,len(parse['vec_ids'])+1)]
    # set the rows
    rows = parse['rows']
    # set the vector keys
    veckeys = parse['vec_ids']
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
    H2OConnection.post_json("PostFile", fui, destination_frame=dest_key)
    # actually parse the data and setup self._vecs
    self._handle_text_key(dest_key, column_names)

  def __iter__(self):
    """
    Allows for list comprehensions over an H2OFrame

    :return: An iterator over the H2OFrame
    """
    import traceback
    if self._vecs is None or self._vecs == []:
      for line in traceback.format_stack():
        print line.strip()
      print "PADASDAS"
      raise ValueError("Frame Removed")
    return (vec for vec in self._vecs.__iter__() if vec is not None)

  def vecs(self):
    """
    Retrieve the array of H2OVec objects comprising this H2OFrame.

    :return: The array of H2OVec objects.
    """
    if self._vecs is None or self._vecs == []:
      raise ValueError("Frame Removed")
    return self._vecs

  def keys(self):
    """
    Retrieve the keys for each of the H2OVec objects comrpising this H2OFrame.

    :return: the array of keys.
    """
    return [i.key() for i in self._vecs]

  def col_names(self):
    """
    Retrieve the column names (one name per H2OVec) for this H2OFrame.

    :return: A character list[] of column names.
    """
    if self._vecs is None or self._vecs == []:
      raise ValueError("Frame Removed")
    return [i._name for i in self._vecs]

  def names(self):
    """
    Retrieve the column names (one name per H2OVec) for this H2OFrame.

    :return: A character list[] of column names.
    """
    if self._vecs is None or self._vecs == []:
      raise ValueError("Frame Removed")
    return self.col_names()

  def nrow(self):
    """
    Get the number of rows in this H2OFrame.

    :return: The number of rows in this dataset.
    """
    if self._vecs is None or self._vecs == []:
      raise ValueError("Frame Removed")
    return len(self._vecs[0])

  def ncol(self):
    """
    Get the number of columns in this H2OFrame.

    :return: The number of columns in this H2OFrame.
    """
    if self._vecs is None or self._vecs == []:
      raise ValueError("Frame Removed")
    return len(self)

  def filterNACols(self, frac=0.2):
    """
    Filter columns with prportion of NAs >= frac.
    :param frac: Fraction of NAs in the column.
    :return: A  list of column indices.
    """
    fr = self.send_frame()
    res = h2o.rapids("(filterNACols %{} #{})".format(fr,str(frac)))
    l = res["head"][0]
    h2o.removeFrameShallow(fr)
    return [int(float(i)) for i in l]

  def dim(self):
    """
    Get the number of rows and columns in the H2OFrame.

    :return: The number of rows and columns in the H2OFrame as a list [rows, cols].
    """
    if self._vecs is None or self._vecs == []:
      raise ValueError("Frame Removed")
    return [self.nrow(), self.ncol()]

  # Print [col, cols...]
  def show(self):
    if self._vecs is None or self._vecs == []:
      raise ValueError("Frame Removed")

    else:
      if len(self) == 1:
        to_show = [[v] for v in self._vecs[0].show(noprint=True)]
        h2o.H2ODisplay(to_show,self.names())
      else:
        vecs = [vec.show(noprint=True) for vec in self]
        # vecs = self._vecs
        l=1
        if isinstance(vecs[0], float):
          vecs.insert(0,1)
          print "Displaying " + str(l) + " row(s):"
          vecs = [[v] for v in vecs]
          h2o.H2ODisplay(zip(*vecs),["Row ID"]+self.names())
        else:
          l = len(vecs[0])
          vecs.insert(0, range(1, len(vecs[0])+1, 1))
          print "Displaying " + str(l) + " row(s):"
          h2o.H2ODisplay(zip(*vecs),["Row ID"]+self.names())

  def head(self, rows=10, cols=200, **kwargs):
    """
    Analgous to R's `head` call on a data.frame. Display a digestible chunk of the H2OFrame starting from the beginning.

    :param rows: Number of rows to display.
    :param cols: Number of columns to display.
    :param kwargs: Extra arguments passed from other methods.
    :return: None
    """
    if self._vecs is None or self._vecs == []:
      raise ValueError("Frame Removed")
    nrows = min(self.nrow(), rows)
    ncols = min(self.ncol(), cols)
    colnames = self.names()[0:ncols]
    fr = H2OFrame.py_tmp_key()
    rapids_call = "(, "  # fold into a single rapids call
    cbind = "(gput " + fr + " (cbind %FALSE '"  # false flag means no deep copy!
    cbind += "' '".join([vec._expr.eager() for vec in self._vecs]) + "')) "
    rapids_call += cbind
    res = h2o.rapids(rapids_call)
    h2o.removeFrameShallow(fr)
    head_rows = [range(1, nrows + 1, 1)]
    head_rows += [rows[0:nrows] for rows in res["head"][0:ncols]]
    head = zip(*head_rows)
    print "First", str(nrows), "rows and first", str(ncols), "columns: "
    h2o.H2ODisplay(head,["Row ID"]+self.names())


  def tail(self, rows=10, cols=200, **kwargs):
    """
    Analgous to R's `tail` call on a data.frame. Display a digestible chunk of the H2OFrame starting from the end.

    :param rows: Number of rows to display.
    :param cols: Number of columns to display.
    :param kwargs: Extra arguments passed from other methods.
    :return: None
    """
    if self._vecs is None or self._vecs == []:
      raise ValueError("Frame Removed")
    nrows = min(self.nrow(), rows)
    ncols = min(self.ncol(), cols)
    colnames = self.names()[0:ncols]

    vecs = [self[c][(self.nrow()-nrows):(self.nrow())] for c in range(ncols)]
    print "Last", str(nrows), "rows and first", str(ncols), "columns: "
    if nrows != 1:
      fr = H2OFrame.py_tmp_key()
      rapids_call = "(, "  # fold into a single rapids call
      cbind = "(gput " + fr + " (cbind %FALSE '"  # false flag means no deep copy!
      cbind += "' '".join([vec._expr.eager() for vec in self._vecs]) + "')) "
      rapids_call += cbind
      res = h2o.rapids(rapids_call)
      h2o.removeFrameShallow(fr)
      tail_rows = [range(self.nrow()-nrows+1, self.nrow() + 1, 1)]
      tail_rows += [rows[0:nrows] for rows in res["head"][0:ncols]]
      tail = zip(*tail_rows)
      h2o.H2ODisplay(tail,["Row ID"]+self.names())
    else:
      h2o.H2ODisplay([[self.nrow()] + [expr.eager() for expr in exprs]], ["Row ID"] + colnames)

  def levels(self, col=0):
    """
    Get the factor levels for this frame and the specified column index.

    :param col: A column index in this H2OFrame.
    :return: a list of strings that are the factor levels for the column.
    """
    if self._vecs is None or self._vecs == []:
      raise ValueError("Frame Removed")
    if col < 0: col = 0
    if col >= self.ncol(): col = self.ncol() - 1
    vec = self._vecs[col]
    res = H2OConnection.get_json("Frames/{}/columns/{}/domain".format(urllib.quote(vec._expr.eager()), "C1"))
    return res["domain"][0]

  def nlevels(self, col=0):
    """
    Get the number of factor levels for this frame and the specified column index.

    :param col: A column index in this H2OFrame.
    :return: an integer.
    """
    nlevels = self.levels(col=col)
    return len(nlevels) if nlevels else 0

  def setLevel(self, level):
    """
    A method to set all column values to one of the levels.
    :param level: The level at which the column will be set (a string)
    :return: An H2OFrame with all entries set to the desired level
    """
    if self._vecs is None or self._vecs == []:
      raise ValueError("Frame Removed")
    if len(self) != 1: raise(ValueError, "`setLevel` can only be called on a single H2OVec or an H2OFrame with "
                                         "one column")
    return H2OFrame(vecs=[self[0].setLevel(level=level)])

  def setLevels(self, levels):
    """
    Works on a single categorical vector. New domains must be aligned with the old domains. This call has SIDE
    EFFECTS and mutates the column in place (does not make a copy).
    :param level: The level at which the column will be set (a string)
    :param x: A single categorical column.
    :param levels: A list of strings specifying the new levels. The number of new levels must match the number of
    old levels.
    :return: None
    """
    if self._vecs is None or self._vecs == []:
      raise ValueError("Frame Removed")
    if len(self) != 1: raise(ValueError, "`setLevels` can only be called on a single H2OVec or an H2OFrame with "
                                         "one column")
    self[0].setLevels(levels=levels)

  def as_date(self,format):
    """
    Return the column with all elements converted to millis since the epoch.
    :param format: The date time format string
    :return: H2OFrame
    """
    if self._vecs is None or self._vecs == []:
      raise ValueError("Frame Removed")
    if len(self) != 1: raise(ValueError, "`setLevels` can only be called on a single H2OVec or an H2OFrame with "
                                         "one column")
    return H2OFrame(vecs=[self[0].as_date(format=format)])

  def setNames(self,names):
    """
    Change the column names to `names`.

    :param names: A list of strings equal to the number of columns in the H2OFrame.
    :return: None. Rename the column names in this H2OFrame.
    """
    if self._vecs is None or self._vecs == []:
      raise ValueError("Frame Removed")
    if not names or not isinstance(names,list):
      raise ValueError("names parameter must be a list of strings")
    if len(names) != self.ncol():
      raise ValueError("names parameter must be a list of ncol names")
    for s in names:
      if not isinstance(s,str):
        raise ValueError("all names in names parameter must be strings")
    for name, vec in zip(names,self._vecs):
      vec._name = name

  def summary(self):
    """
    Generate summary of the frame on a per-Vec basis.
    :return: None
    """
    frtmp=self.send_frame()
    fr_sum = h2o.frame_summary(frtmp)["frames"][0]  # only ONE frame summary at a time, the first one...
    h2o.removeFrameShallow(frtmp)  # wipe the frame binding the vecs immediately
    type = ["type"]
    mins = ["mins"]
    mean = ["mean"]
    maxs = ["maxs"]
    sigma= ["sigma"]
    zeros= ["zero_count"]
    miss = ["missing_count"]
    for v in fr_sum["columns"]:
      type.append(v["type"])
      mins.append(v["mins"][0] if v is not None else v["mins"])
      mean.append(v["mean"])
      maxs.append(v["maxs"][0] if v is not None else v["maxs"])
      sigma.append(v["sigma"])
      zeros.append(v["zero_count"])
      miss.append(v["missing_count"])

    table = [type,mins,maxs,sigma,zeros,miss]
    headers = [vec._name for vec in self._vecs]
    h2o.H2ODisplay(table, [""] + headers, "Column-by-Column Summary")

  def describe(self):
    """
    Generate an in-depth description of this H2OFrame.

    The description is a tabular print of the type, min, max, sigma, number of zeros,
    and number of missing elements for each H2OVec in this H2OFrame.

    :return: None (print to stdout)
    """
    if self._vecs is None or self._vecs == []:
      raise ValueError("Frame Removed")
    thousands_sep = h2o.H2ODisplay.THOUSANDS
    print "Rows:", thousands_sep.format(len(self._vecs[0])), "Cols:", thousands_sep.format(len(self))
    chunk_summary_tmp_key = H2OFrame.send_frame(self)
    chunk_dist_sum = h2o.frame(chunk_summary_tmp_key)["frames"][0]
    dist_summary = chunk_dist_sum["distribution_summary"]
    chunk_summary = chunk_dist_sum["chunk_summary"]
    h2o.removeFrameShallow(chunk_summary_tmp_key)
    chunk_summary.show()
    dist_summary.show()
    self.summary()

  # def __repr__(self):
  #   if self._vecs is None or self._vecs == []:
  #     raise ValueError("Frame Removed")
  #   self.show()
  #   return ""



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
  # Multi-dimensional slicing via 2-tuple
  def __getitem__(self, i):
    """
    Column selection via integer, string(name)
    Column selection via slice returns a subset of the H2OFrame

    :param i: An int, str, slice, H2OVec, or list/tuple
    :return: An H2OVec, an H2OFrame, or scalar depending on the input slice.
    """
    if self._vecs is None or self._vecs == []:
      raise ValueError("Frame Removed")
    if isinstance(i, int):   return self._vecs[i]
    if isinstance(i, str):   return self._find(i)
    # Slice; return a Frame not a Vec
    if isinstance(i, slice): return H2OFrame(vecs=self._vecs[i])
    # Row selection from a boolean Vec
    if isinstance(i, H2OVec):
      self._len_check(i)
      return H2OFrame(vecs=[x.row_select(i) for x in self._vecs])

    # have a list/tuple of numbers or strings
    if isinstance(i, list) or (isinstance(i, tuple) and len(i) != 2):
      vecs = []
      for it in i:
        if isinstance(it, int):    vecs.append(self._vecs[it])
        elif isinstance(it, str):  vecs.append(self._find(it))
        else:                      raise NotImplementedError
      return H2OFrame(vecs=vecs)

    # multi-dimensional slicing via 2-tuple
    if isinstance(i, tuple) and len(i)==2:
      res = self[i[1]] # Slice by columns eagerly
      # Now slice by rows
      if isinstance(res,H2OFrame):
        if   isinstance(i[0], slice): return H2OFrame(vecs      =[vec[i[0]] for vec in res._vecs])
        elif isinstance(i[0], int)  : return H2OFrame(python_obj=[vec[i[0]] for vec in res._vecs])
        else:                         raise NotImplementedError
      if isinstance(res,H2OVec): return res[i[0]]
      raise NotImplementedError

    raise NotImplementedError("Slicing by unknown type: "+str(type(i)))

  def __setitem__(self, b, c):
    """
    Replace a column in an H2OFrame.

    :param b: A 0-based index or a column name.
    :param c: The vector that 'b' is replaced with.
    :return: Returns this H2OFrame.
    """
    if self._vecs is None or self._vecs == []:
      raise ValueError("Frame Removed")
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
    """
    Remove a vec specified at the index i.

    :param i: The index of the vec to delete.
    :return: The Vec to be deleted.
    """
    if self._vecs is None or self._vecs == []:
      raise ValueError("Frame Removed")
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
    if self._vecs is None or self._vecs == []:
      raise ValueError("Frame Removed")
    # i is a named column
    if isinstance(i, str):
      for v in self._vecs:
        if i == v._name:
          return H2OFrame(vecs=[v for v in self._vecs if i != v._name])
      raise ValueError("Name " + i + " not in Frame")
    # i is a 0-based column
    elif isinstance(i, int):
      if i < 0 or i >= self.__len__():
        raise ValueError("Index out of range: 0 <= " + str(i) + " < " + str(self.__len__()))
      return H2OFrame(vecs=[v for v in self._vecs if v._name != self._vecs[i]._name])
    raise NotImplementedError

  def __len__(self):
    """
    :return: Number of columns in this H2OFrame
    """
    if self._vecs is None or self._vecs == []:
      raise ValueError("Frame Removed")
    return len(self._vecs)

  def logical_negation(self):  
    return H2OFrame(vecs=[H2OVec(x._name, Expr("not", x)) for x in self._vecs])

  def _simple_frames_bin_op(self, data, op):
    if len(self) == 0: return self
    if self._vecs is None or self._vecs == []:  raise ValueError("Frame Removed")
    self._len_check(data)
    if isinstance(data,  H2OFrame):
      return H2OFrame(vecs=[H2OVec(x._name, Expr(op, x._len_check( y  ),  y  )) for x, y in zip(self._vecs, data._vecs)])
    if isinstance(data,  H2OVec):
      return H2OFrame(vecs=[H2OVec(x._name, Expr(op, x._len_check(data), data)) for x    in     self._vecs          ])
    if isinstance(data, (int,float,str)):
      return H2OFrame(vecs=[H2OVec(x._name, Expr(op, x,            Expr(data))) for x    in     self._vecs          ])
    raise NotImplementedError

  def _simple_frames_bin_rop(self, data, op):
    if len(self) == 0: return self
    if self._vecs is None or self._vecs == []:  raise ValueError("Frame Removed")
    self._len_check(data)
    if isinstance(data,  H2OFrame):
      return H2OFrame(vecs=[H2OVec(x._name, Expr(op,  y  , x._len_check( y  ))) for x, y in zip(self._vecs, data._vecs)])
    if isinstance(data,  H2OVec):
      return H2OFrame(vecs=[H2OVec(x._name, Expr(op, data, x._len_check(data))) for x    in     self._vecs          ])
    if isinstance(data, (int,float,str)):
      return H2OFrame(vecs=[H2OVec(x._name, Expr(op, Expr(data),x,length=len(x))) for x  in     self._vecs          ])
    raise NotImplementedError

  # ops
  def __add__(self, i): return self._simple_frames_bin_op(i, "+")
  def __mod__(self, i): return self._simple_frames_bin_op(i, "mod")
  def __and__(self, i): return self._simple_frames_bin_op(i, "&")
  def __gt__ (self, i): return self._simple_frames_bin_op(i, "g")
  def __sub__(self, i): return self._simple_frames_bin_op(i,"-" )
  def __or__ (self, i): return self._simple_frames_bin_op(i,"|" )
  def __div__(self, i): return self._simple_frames_bin_op(i,"/" )
  def __mul__(self, i): return self._simple_frames_bin_op(i,"*" )
  def __eq__ (self, i): return self._simple_frames_bin_op(i,"n")
  def __ne__ (self, i): return self._simple_frames_bin_op(i,"N")
  def __pow__(self, i): return self._simple_frames_bin_op(i,"^" )
  def __ge__ (self, i): return self._simple_frames_bin_op(i,"G")
  def __le__ (self, i): return self._simple_frames_bin_op(i,"L")
  def __lt__ (self, i): return self._simple_frames_bin_op(i,"l" )

  # rops
  def __rmod__(self, i): return self._simple_frames_bin_rop(i,"mod")
  def __radd__(self, i): return self.__add__(i)
  def __rsub__(self, i): return self._simple_frames_bin_rop(i,"-")
  def __rand__(self, i): return self.__and__(i)
  def __ror__ (self, i): return self.__or__ (i)
  def __rdiv__(self, i): return self._simple_frames_bin_rop(i,"/")
  def __rmul__(self, i): return self.__mul__(i)
  def __rpow__(self, i): return self._simple_frames_bin_rop(i,"^")

  # unops
  def __abs__ (self): return h2o.abs(self)

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
    if self._vecs is None or self._vecs == []:
      raise ValueError("Frame Removed")
    # Send over the frame
    fr = H2OFrame.py_tmp_key()
    rapids_call = "(, "  # fold into a single rapids call
    cbind = "(gput " + fr + " (cbind %FALSE '"  # false flag means no deep copy!
    cbind += "' '".join([vec._expr.eager() for vec in self._vecs]) + "')) "
    rapids_call += cbind
    # h2o.rapids(cbind)
    # And frame columns
    colnames = "(colnames= %" + fr + " (: #0 #" + str(len(self) - 1) + ") "
    cnames = "(slist \"" + '" "'.join([vec._name for vec in self._vecs]) +"\")"
    colnames += cnames
    rapids_call += colnames
    h2o.rapids(rapids_call)
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
    # check that each value entry is a flat list/tuple
    for k in python_obj:
      v = python_obj[k]
      # if value is a tuple/list, then it must be flat
      if isinstance(v, (tuple, list)):
        if H2OFrame._is_list_of_lists(v):
          raise ValueError("Values in the dictionary must be flattened!")

    python_obj = {k:(v if hasattr(v,'__iter__') else [v]) for (k, v) in python_obj.iteritems()}
    header = python_obj.keys()
    # is this a valid header?
    is_valid = all([re.match(r'^[a-zA-Z_][a-zA-Z0-9_.]*$', col) for col in header])
    if not is_valid:
      raise ValueError("Did not get a valid set of column names! Must match the regular expression: ^[a-zA-Z_][a-zA-Z0-9_.]*$ ")

    rows = map(list, itertools.izip_longest(*python_obj.values()))
    data_to_write = [dict(zip(header, row)) for row in rows]
    return header, data_to_write

  @staticmethod
  def _handle_numpy_array(python_obj):
    return H2OFrame._handle_python_lists(python_obj=python_obj.tolist())

  @staticmethod
  def _handle_pandas_data_frame(python_obj):
    return H2OFrame._handle_numpy_array(python_obj=python_obj.as_matrix())

  def _len_check(self,x):
    if len(self) == 0: return
    return self._vecs[0]._len_check(x)

  # Quantiles
  def quantile(self, prob=None, combine_method="interpolate"):
    """
    Compute quantiles over a given H2OFrame.

    :param prob: A list of probabilties, default is [0.01,0.1,0.25,0.333,0.5,0.667,0.75,0.9,0.99]. You may provide any sequence of any length.
    :param combine_method: For even samples, how to combine quantiles. Should be one of ["interpolate", "average", "low", "hi"]
    :return: an H2OFrame containing the quantiles and probabilities.
    """
    if self._vecs is None or self._vecs == []:
      raise ValueError("Frame Removed")
    if len(self) == 0: return self
    if not prob: prob=[0.01,0.1,0.25,0.333,0.5,0.667,0.75,0.9,0.99]
    if not isinstance(prob, list): raise ValueError("prob must be a list")
    probs = "(dlist #"+" #".join([str(p) for p in prob])+")"
    if combine_method not in ["interpolate","average","low","high"]:
      raise ValueError("combine_method must be one of: [" + ",".join(["interpolate","average","low","high"])+"]")

    key = self.send_frame()
    tmp_key = H2OFrame.py_tmp_key()
    expr = "(= !{} (quantile '{}' {} '{}'".format(tmp_key,key,probs,combine_method)
    h2o.rapids(expr)
    # Remove h2o temp frame after groupby
    h2o.removeFrameShallow(key)
    # Make backing H2OVecs for the remote h2o vecs
    j = h2o.frame(tmp_key)
    fr = j['frames'][0]       # Just the first (only) frame
    rows = fr['rows']         # Row count
    veckeys = fr['vec_ids']  # List of h2o vec keys
    cols = fr['columns']      # List of columns
    colnames = [col['label'] for col in cols]
    vecs=H2OVec.new_vecs(zip(colnames, veckeys), rows) # Peel the Vecs out of the returned Frame
    h2o.removeFrameShallow(tmp_key)
    return H2OFrame(vecs=vecs)

  # H2OFrame Mutating cbind
  def cbind(self,data):
    """
    :param data: H2OFrame or H2OVec to cbind to self
    :return: void
    """
    if self._vecs is None or self._vecs == []:
      raise ValueError("Frame Removed")
    if isinstance(data, H2OFrame):
      num_vecs = len(data._vecs)
      for vidx in range(num_vecs):
        self._vecs.append(data._vecs[vidx])
    elif isinstance(data, H2OVec):
      self._vecs.append(data)
    else:
      raise ValueError("data to cbind must be H2OVec or H2OFrame")

  # ddply in h2o
  def ddply(self,cols,fun):
    """
    :param cols: Column names used to control grouping
    :param fun: Function to execute on each group.  Right now limited to textual Rapids expression
    :return: New frame with 1 row per-group, of results from 'fun'
    """
    if self._vecs is None or self._vecs == []:
      raise ValueError("Frame Removed")
    # Confirm all names present in dataset; collect column indices
    rapids_series = "(llist #"+" #".join([str(self._find_idx(name)) for name in cols])+")"

    # Eagerly eval and send the cbind'd frame over
    key = self.send_frame()
    tmp_key = H2OFrame.py_tmp_key()
    expr = "(= !{} (h2o.ddply %{} {} {}))".format(tmp_key,key,rapids_series,fun)
    h2o.rapids(expr) # ddply in h2o
    # Remove h2o temp frame after ddply
    h2o.removeFrameShallow(key)
    # Make backing H2OVecs for the remote h2o vecs
    j = h2o.frame(tmp_key) # Fetch the frame as JSON
    fr = j['frames'][0]    # Just the first (only) frame
    rows = fr['rows']      # Row count
    veckeys = fr['vec_ids']# List of h2o vec keys
    cols = fr['columns']   # List of columns
    colnames = [col['label'] for col in cols]
    vecs=H2OVec.new_vecs(zip(colnames, veckeys), rows) # Peel the Vecs out of the returned Frame
    h2o.removeFrameShallow(tmp_key)
    return H2OFrame(vecs=vecs)

  def group_by(self,cols,a,order_by=None):
    """
    GroupBy
    :param cols: The columns to group on.
    :param a: A dictionary of aggregates having the following shape: \
    {"colname":[aggregate, column, naMethod]}\
    e.g.: {"bikes":["count", 0, "all"]}\

    The naMethod is one of "all", "ignore", or "rm", which specifies how to handle
    NAs that appear in columns that are being aggregated.

    "all" - include NAs
    "rm"  - exclude NAs
    "ignore" - ignore NAs in aggregates, but count them (e.g. in denominators for mean, var, sd, etc.)
    :param order_by: A list of column names or indices on which to order the results.
    :return: The group by frame.
    """
    if self._vecs is None or self._vecs == []:
      raise ValueError("Frame Removed")
    rapids_series = "(llist #"+" #".join([str(self._find_idx(name)) for name in cols])+")"
    aggregates = copy.deepcopy(a)
    key = self.send_frame()
    tmp_key = H2OFrame.py_tmp_key()

    aggs = []

    # transform cols in aggregates to their indices...
    for k in aggregates:
      if isinstance(aggregates[k][1],str):
        aggregates[k][1] = '#'+str(self._find_idx(aggregates[k][1]))
      else:
        aggregates[k][1] = '#'+str(aggregates[k][1])
      aggs+=["\"{1}\" {2} \"{3}\" \"{0}\"".format(str(k),*aggregates[k])]
    aggs = "(agg {})".format(" ".join(aggs))

    # deal with order by
    if order_by is None: order_by="()"
    else:
      if isinstance(order_by, list):
        oby = [cols.index(i) for i in order_by]
        order_by = "(llist #"+" #".join([str(o) for o in oby])+")"
      elif isinstance(order_by, str):
        order_by = "#" + str(self._find_idx(order_by))
      else:
        order_by = "#" + str(order_by)

    expr = "(= !{} (GB %{} {} {} {}))".format(tmp_key,key,rapids_series,aggs,order_by)
    h2o.rapids(expr)  # group by
    # Remove h2o temp frame after groupby
    h2o.removeFrameShallow(key)
    # Make backing H2OVecs for the remote h2o vecs
    j = h2o.frame(tmp_key)
    fr = j['frames'][0]       # Just the first (only) frame
    rows = fr['rows']         # Row count
    veckeys = fr['vec_ids']  # List of h2o vec keys
    cols = fr['columns']      # List of columns
    colnames = [col['label'] for col in cols]
    vecs=H2OVec.new_vecs(zip(colnames, veckeys), rows) # Peel the Vecs out of the returned Frame
    h2o.removeFrameShallow(tmp_key)
    return H2OFrame(vecs=vecs)

  def impute(self,column,method,combine_method,by,inplace):
    """
    Impute a column in this H2OFrame.

    :param column: The column to impute
    :param method: How to compute the imputation value.
    :param combine_method: For even samples and method="median", how to combine quantiles.
    :param by: Columns to group-by for computing imputation value per groups of columns.
    :param inplace: Impute inplace?
    :return: the imputed frame.
    """
    # sanity check columns, get the column index
    col_id = -1

    if isinstance(column, list): column = column[0]  # only take the first one ever...

    if isinstance(column, (unicode,str)):
      col_id = self._find_idx(column)
    elif isinstance(column, int):
      col_id = column
    elif isinstance(column, H2OVec):
      try:
        col_id = [a._name==v._name for a in self].index(True)
      except:
        raise ValueError("No column found to impute.")

  # setup the defaults, "mean" for numeric, "mode" for enum
    if isinstance(method, list) and len(method) > 1:
      if self[col_id].isfactor(): method="mode"
      else:                       method="mean"
    elif isinstance(method, list):method=method[0]

    # choose "interpolate" by default for combine_method
    if isinstance(combine_method, list) and len(combine_method) > 1: combine_method="interpolate"
    if combine_method == "lo":                                       combine_method = "low"
    if combine_method == "hi":                                       combine_method = "high"

    # sanity check method
    if method=="median":
      # no by and median!
      if by is not None:
        raise ValueError("Unimplemented: No `by` and `median`. Please select a different method (e.g. `mean`).")

    # method cannot be median or mean for factor columns
    if self[col_id].isfactor() and method not in ["ffill", "bfill", "mode"]:
      raise ValueError("Column is categorical, method must not be mean or median.")


    # setup the group by columns
    gb_cols = "()"
    if by is not None:
      if not isinstance(by, list):          by = [by]  # just make it into a list...
      if isinstance(by[0], (unicode,str)):  by = [self._find_idx(name) for name in by]
      elif isinstance(by[0], int):          by = by
      elif isinstance(by[0], H2OVec):       by = [[a._name==v._name for a in self].index(True) for v in by]  # nested list comp. WOWZA
      else:                                 raise ValueError("`by` is not a supported type")

    if by is not None:                      gb_cols = "(llist #"+" #".join([str(b) for b in by])+")"

    key = self.send_frame()
    tmp_key = H2OFrame.py_tmp_key()

    if inplace:
      # frame, column, method, combine_method, gb_cols, inplace
      expr = "(h2o.impute %{} #{} \"{}\" \"{}\" {} %TRUE".format(key, col_id, method, combine_method, gb_cols)
      h2o.rapids(expr)  # exec the thing
      h2o.removeFrameShallow(key)  # "soft" delete of the frame key, keeps vecs live
      return self
    else:
      expr = "(= !{} (h2o.impute %{} #{} \"{}\" \"{}\" {} %FALSE))".format(tmp_key,key,col_id,method,combine_method,gb_cols)
      h2o.rapids(expr)  # exec the thing
      h2o.removeFrameShallow(key)
      # Make backing H2OVecs for the remote h2o vecs
      j = h2o.frame(tmp_key)
      fr = j['frames'][0]       # Just the first (only) frame
      rows = fr['rows']         # Row count
      veckeys = fr['vec_ids']   # List of h2o vec keys
      cols = fr['columns']      # List of columns
      colnames = [col['label'] for col in cols]
      vecs = H2OVec.new_vecs(zip(colnames, veckeys), rows) # Peel the Vecs out of the returned Frame
      h2o.removeFrameShallow(tmp_key) # soft delete the new Frame, keep the imputed Vecs alive
      return H2OFrame(vecs=vecs)

  def merge(self, other, allLeft=False, allRite=False):
    """
    Merge two datasets based on common column names

    :param other: Other dataset to merge.  Must have at least one column in common with self, and all columns in common are used as the merge key.  If you want to use only a subset of the columns in common, rename the other columns so the columns are unique in the merged result.
    :param allLeft: If true, include all rows from the left/self frame
    :param allRite: If true, include all rows from the right/other frame
    :return: Original self frame enhanced with merged columns and rows
    """
    if self._vecs is None or self._vecs == []:
      raise ValueError("Frame Removed")
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

    h2o.rapids(expr2)       # merge in h2o
    # Make backing H2OVecs for the remote h2o vecs
    j = h2o.frame(tmp_key)  # Fetch the frame as JSON
    fr = j['frames'][0]     # Just the first (only) frame
    rows = fr['rows']       # Row count
    veckeys = fr['vec_ids']# List of h2o vec keys
    cols = fr['columns']    # List of columns
    colnames = [col['label'] for col in cols]
    vecs=H2OVec.new_vecs(zip(colnames, veckeys), rows) # Peel the Vecs out of the returned Frame
    h2o.removeFrameShallow(tmp_key)
    return H2OFrame(vecs=vecs)

  def insert_missing_values(self, fraction = 0.1, seed=None):
    """
    Inserting Missing Values to an H2OFrame
    *This is primarily used for testing*. Randomly replaces a user-specified fraction of entries in a H2O dataset with
    missing values.
    WARNING: This will modify the original dataset. Unless this is intended, this function should only be called on a
    subset of the original.

    :param fraction: A number between 0 and 1 indicating the fraction of entries to replace with missing.
    :param seed: A random number used to select which entries to replace with missing values. Default of seed = -1 will
    automatically generate a seed in H2O.
    :return: H2OFrame with missing values inserted
    """
    kwargs = {}
    data_key = self.send_frame()
    kwargs['dataset'] = data_key
    kwargs['fraction'] = fraction
    if seed is not None: kwargs['seed'] = seed

    job = {}
    job['job'] = H2OConnection.post_json("MissingInserter", **kwargs)
    H2OJob(job, job_type=("Insert Missing Values")).poll()
    return self

  # generic reducers (min, max, sum, var)
  def min(self):
    """
    :return: The minimum value of all frame entries
    """
    return min([vec.min() for vec in self._vecs])

  def max(self):
    """
    :return: The maximum value of all frame entries
    """
    return max([vec.max() for vec in self._vecs])

  def sum(self):
    """
    :return: The sum of all frame entries
    """
    return sum([vec.sum() for vec in self._vecs])

  def anyfactor(self):
    """
    :return: Whether or not the frame has any factor columns
    """
    return any([vec.anyfactor() for vec in self._vecs])

  def var(self):
    """
    :return: The covariance matrix of the columns in this H2OFrame.
    """
    if self._vecs is None or self._vecs == []:
      raise ValueError("Frame Removed")
    frame_keys = [self.send_frame()]
    tmp_key = H2OFrame.py_tmp_key()
    expr = "(= !{} (var %{} () %FALSE \"everything\"))".format(tmp_key,frame_keys[0])
    return H2OFrame._get_frame_from_rapids_string(expr, tmp_key, frame_keys)

  def transpose(self):
    """
    :return: The transpose of the H2OFrame.
    """
    if self._vecs is None or self._vecs == []:
      raise ValueError("Frame Removed")
    frame_keys = [self.send_frame()]
    tmp_key = H2OFrame.py_tmp_key()
    expr = "(= !{} (t %{}))".format(tmp_key,frame_keys[0])
    return H2OFrame._get_frame_from_rapids_string(expr, tmp_key, frame_keys)

  def table(self, data2=None):
    """
    :return: a frame of the counts at each combination of factor levels
    """
    if self._vecs is None or self._vecs == []:
      raise ValueError("Frame Removed")
    frame_keys = [self.send_frame()]
    if data2: frame_keys.append(data2.send_frame())
    tmp_key = H2OFrame.py_tmp_key()
    expr = "(= !{} (table %{} {}))".format(tmp_key,frame_keys[0],"%"+frame_keys[1] if data2 else "()")
    return H2OFrame._get_frame_from_rapids_string(expr, tmp_key, frame_keys)

  def scale(self, center=True, scale=True):
    """
    Centers and/or scales the columns of the H2OFrame
    :return: H2OFrame
    :param center: either a ‘logical’ value or numeric list of length equal to the number of columns of the H2OFrame
    :param scale: either a ‘logical’ value or numeric list of length equal to the number of columns of H2OFrame.
    """
    if self._vecs is None or self._vecs == []:
      raise ValueError("Frame Removed")
    if   isinstance(center, bool) and isinstance(scale, bool):
      return H2OFrame(vecs=[vec.scale(center=center, scale=scale) for vec in self._vecs])
    elif isinstance(center, list) and isinstance(scale, bool):
      return H2OFrame(vecs=[vec.scale(center=c, scale=scale) for vec, c in zip(self._vecs,center)])
    elif isinstance(center, list) and isinstance(scale, list):
      return H2OFrame(vecs=[vec.scale(center=c, scale=s) for vec, c, s in zip(self._vecs, center, scale)])
    elif isinstance(center, bool) and isinstance(scale, list):
      return H2OFrame(vecs=[vec.scale(center=center, scale=s) for vec, s in zip(self._vecs,scale)])
    else: raise(ValueError, "`center` and `scale` arguments (for a frame) must be bools or lists of numbers, but got "
                            "center: {0}, scale: {1}".format(center, scale))

  def signif(self, digits=6):
    """
    :return: The rounded values in the H2OFrame to the specified number of significant digits.
    """
    if self._vecs is None or self._vecs == []:
      raise ValueError("Frame Removed")
    return H2OFrame(vecs=[vec.signif(digits=digits) for vec in self._vecs])

  def round(self, digits=0):
    """
    :return: The rounded values in the H2OFrame to the specified number of decimal digits.
    """
    if self._vecs is None or self._vecs == []:
      raise ValueError("Frame Removed")
    return H2OFrame(vecs=[vec.round(digits=digits) for vec in self._vecs])

  def asnumeric(self):
    """
    :return: A frame with factor columns converted to numbers (numeric columns untouched).
    """
    if self._vecs is None or self._vecs == []:
      raise ValueError("Frame Removed")
    return H2OFrame(vecs=[vec.asnumeric() for vec in self._vecs])

  @staticmethod
  def _get_frame_from_rapids_string(expr, tmp_key, frame_keys):
    h2o.rapids(expr)
    # Remove h2o temp frame after var
    for key in frame_keys: h2o.removeFrameShallow(key)
    j = h2o.frame(tmp_key)
    fr = j['frames'][0]
    rows = fr['rows']
    veckeys = fr['vec_ids']
    cols = fr['columns']
    colnames = [col['label'] for col in cols]
    vecs=H2OVec.new_vecs(zip(colnames, veckeys), rows) # Peel the Vecs out of the returned Frame
    h2o.removeFrameShallow(tmp_key)
    return H2OFrame(vecs=vecs)

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
    """
    :return: Return the column name for this H2OVec
    """
    return self._name

  def key(self):
    """
    :return: Return the H2O Key for this Vec.
    """
    return self._expr._data if isinstance(self._expr._data, (unicode, str)) else ""

  def setName(self,name):
    """
    Set the column name for this column.

    :param name: The new name for this column.
    :return: None
    """
    if name and isinstance(name,str):
      self._name = name
    else:
        raise ValueError("name parameter must be a string")

  def get_expr(self):
    """
    Helper method to obtain the expr object in self. Can also get it directly  @ ._expr.

    :return: the _expr member of this H2OVec
    """
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


  def cut(self, breaks, labels=None, include_lowest=False, right=True, dig_lab=3):
    breaks_list = "(dlist #"+" #".join([str(b) for b in breaks])+")"
    labels_list = "()"
    if labels is not None:
      labels_list = "(slist \"" + '" "'.join(labels) +"\")"

    if self.key() == "": self._expr.eager()

    expr = "(cut '{}' {} {} {} {} #{}".format(self.key(), breaks_list, labels_list, "%TRUE" if include_lowest else "%FALSE", "%TRUE" if right else "%FALSE", dig_lab)
    res = h2o.rapids(expr)
    return H2OVec(self._name, Expr(op=res["vec_ids"][0]["name"], length=res["num_rows"]))

  def as_date(self,format):
    """
    Return the column with all elements converted to millis since the epoch.
    :param format: The date time format string
    :return: H2OVec
    """
    if not isinstance(format, str):
      raise ValueError("format must be a string")

    if self.key() == "": self._expr.eager()
    expr = "(as.Date '" + self.key() + '\' "{}"'.format(format) + ")"
    res = h2o.rapids(expr)
    return H2OVec(self._name, Expr(op=res["vec_ids"][0]["name"], length=res["num_rows"]))

  # H2OVec non-mutating cbind
  def cbind(self,data):
    """
    :param data: H2OFrame or H2OVec
    :return: new H2OFrame with data cbinded to the end
    """
    # Check data type
    vecs = []
    if isinstance(data,H2OFrame):
      vecs.append(self)
      [vecs.append(vec) for vec in data._vecs]
    elif isinstance(data,H2OVec):
      vecs = [self, data]
    else:
      raise ValueError("data parameter must be H2OVec or H2OFrame")
    names = [vec.name() for vec in vecs]

    fr = H2OFrame.py_tmp_key()
    cbind = "(= !" + fr + " (cbind %FALSE %"
    cbind += " %".join([vec._expr.eager() for vec in vecs]) + "))"
    h2o.rapids(cbind)

    j = h2o.frame(fr)
    fr = j['frames'][0]
    rows = fr['rows']
    veckeys = fr['vec_ids']
    cols = fr['columns']
    colnames = [col['label'] for col in cols]
    result = H2OFrame(vecs=H2OVec.new_vecs(zip(colnames, veckeys), rows))
    result.setNames(names)
    return result

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
      header=["Row ID", header]
      h2o.H2ODisplay(to_show, header)

  # def __repr__(self):
  #   self.show()
  #   return ""

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
    if isinstance(i, H2OVec): return self.row_select(i)
    if isinstance(i, int):  return Expr("[", self, Expr(i), length=1).eager() # Single row select, makes a scalar
    if isinstance(i, slice):
      e = Expr(i)
      return H2OVec(self._name,Expr("[", self, e, length=len(e)))
    raise ValueError("Row selection from a Vec is limited to 1 row, or a boolean Vec")

  # Boolean column select lookup.  Eager, to compute the result length
  def row_select(self, vec):
    """
    Boolean column select lookup

    :param vec: An H2OVec.
    :return: A new H2OVec.
    """
    e = Expr("[", self, vec)
    r = e.eager()
    if isinstance(r, (float,int)):
      e.set_len(1)
    else:
      j = h2o.frame(r)
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
  def _simple_vec_bin_op( self, i, op):
    if isinstance(i, H2OFrame    ):  return i._simple_frames_bin_op(H2OFrame(vecs=[self]),op)
    if isinstance(i, H2OVec      ):  return H2OVec(self._name, Expr(op, self._len_check(i), i))
    if isinstance(i, (int, float)):  return H2OVec(self._name, Expr(op, self, Expr(i)))
    if isinstance(i, Expr)        :  return H2OVec(self._name, Expr(op, self, i))
    if isinstance(i, str)         :  return H2OVec(self._name, Expr(op, self, Expr(None,i)))
    if op == "n" and i is None    :  return H2OVec(self._name, Expr("is.na", self._expr, None))
    raise NotImplementedError

  def _simple_vec_bin_rop(self, i, op):
    if isinstance(i, (int, float)):  return H2OVec(self._name, Expr(op, Expr(i), self, length=len(self)))
    if isinstance(i, Expr)        :  return H2OVec(self._name, Expr(op, i, self, length=len(self)))
    raise NotImplementedError

  def logical_negation(self):  return H2OVec(self._name, Expr("not", self))

  def __mod__(self, i):  return self._simple_vec_bin_op(i, "mod")
  def __add__(self, i):  return self._simple_vec_bin_op(i,"+" )
  def __sub__(self, i):  return self._simple_vec_bin_op(i,"-" )
  def __and__(self, i):  return self._simple_vec_bin_op(i,"&" )
  def __or__ (self, i):  return self._simple_vec_bin_op(i,"|" )
  def __div__(self, i):  return self._simple_vec_bin_op(i,"/" )
  def __mul__(self, i):  return self._simple_vec_bin_op(i,"*" )
  def __eq__ (self, i):  return self._simple_vec_bin_op(i,"n")
  def __ne__ (self, i):  return self._simple_vec_bin_op(i,"N")
  def __pow__(self, i):  return self._simple_vec_bin_op(i,"^" )
  def __ge__ (self, i):  return self._simple_vec_bin_op(i,"G")
  def __gt__ (self, i):  return self._simple_vec_bin_op(i,"g" )
  def __le__ (self, i):  return self._simple_vec_bin_op(i,"L")
  def __lt__ (self, i):  return self._simple_vec_bin_op(i,"l" )

  def __rmod__(self, i): return self._simple_vec_bin_rop(i,"mod")
  def __radd__(self, i): return self.__add__(i)  # commutativity
  def __rsub__(self, i): return self._simple_vec_bin_rop(i,"-")  # not commutative
  def __rand__(self, i): return self.__and__(i)  # commutativity (no short circuiting)
  def __ror__ (self, i): return self.__or__ (i)
  def __rdiv__(self, i): return self._simple_vec_bin_rop(i,"/")  # not commutative
  def __rmul__(self, i): return self.__mul__(i)
  def __rpow__(self, i): return self._simple_vec_bin_rop(i,"^")  # not commutative

  def __abs__ (self): return h2o.abs(self)

  def __len__(self):
    """
    :return: The length of this H2OVec
    """
    return len(self._expr)

  def dim(self):
    """
    :return: The length of the H2OVec
    """
    return len(self), 1

  def floor(self):
    """
    :return: A lazy Expr representing the Math.floor() of this H2OVec.
    """
    return H2OVec(self._name,Expr("floor", self._expr, None))

  # generic reducers (min, max, sum, sd, var, mean, median)
  def min(self):
    """
    :return: Min value of the H2OVec elements.
    """
    return Expr("min", self._expr).eager()

  def max(self):
    """
    :return: Max value of the H2OVec elements.
    """
    return Expr("max", self._expr).eager()

  def sum(self):
    """
    :return: Sum of the H2OVec elements.
    """
    return Expr("sum", self._expr).eager()

  def sd(self):
    """
    :return: Standard deviation of the H2OVec elements.
    """
    return Expr("sd", self._expr).eager()

  def var(self):
    """
    :return: A lazy Expr representing the variance of this H2OVec.
    """
    return Expr("var", self._expr).eager()

  def mean(self):
    """
    :return: Mean of this H2OVec.
    """
    return Expr("mean", self._expr).eager()

  def median(self):
    """
    :return: Median of this H2OVec.
    """
    return Expr("median", self._expr).eager()

  def quantile(self,prob=None,combine_method="interpolate"):
    """
    :return: A lazy Expr representing the quantiles of this H2OVec.
    """
    if not prob: prob=[0.01,0.1,0.25,0.333,0.5,0.667,0.75,0.9,0.99]
    return H2OFrame(vecs=[self]).quantile(prob,combine_method)

  def asfactor(self):
    """
    :return: A lazy Expr representing this vec converted to a factor
    """
    return H2OVec(self._name, Expr("as.factor", self._expr, None))

  def isfactor(self):
    """
    :return: A lazy Expr representing the truth of whether or not this vec is a factor.
    """
    return Expr("is.factor", self._expr, None, length=1).eager()

  def anyfactor(self):
    """
    :return: Same as isfactor for a vec
    """
    return Expr("is.factor", self._expr, None, length=1).eager()

  def ascharacter(self):
    """
    :return: A lazy Expr representing this vec converted to characters
    """
    return H2OVec(self._name, Expr("as.character", self._expr, None))

  def isna(self):
    """
    :return: Returns a new boolean H2OVec.
    """
    return H2OVec("", Expr("is.na", self._expr, None))

  def year(self):
    """
    :return: Returns a new year column from a msec-since-Epoch column
    """
    return H2OVec(self._name, Expr("year", self._expr, None))

  def month(self):
    """
    :return: Returns a new month column from a msec-since-Epoch column
    """
    return H2OVec(self._name, Expr("month", self._expr, None))

  def week(self):
    """
    :return: Returns a new week column from a msec-since-Epoch column
    """
    return H2OVec(self._name, Expr("week", self._expr, None))

  def day(self):
    """
    :return: Returns a new day column from a msec-since-Epoch column
    """
    return H2OVec(self._name, Expr("day", self._expr, None))

  def dayOfWeek(self):
    """
    :return: Returns a new Day-of-Week column from a msec-since-Epoch column
    """
    return H2OVec(self._name, Expr("dayOfWeek", self._expr, None))

  def hour(self):
    """
    :return: Returns a new Hour-of-Day column from a msec-since-Epoch column
    """
    return H2OVec(self._name, Expr("hour", self._expr, None))

  def runif(self, seed=None):
    """
    :param seed: A random seed. If None, then one will be generated.
    :return: A new H2OVec filled with doubles sampled uniformly from [0,1).
    """
    if not seed:
      import random
      seed = random.randint(123456789, 999999999)  # generate a seed
    return H2OVec("", Expr("h2o.runif", self._expr, Expr(seed)))

  def match(self, table, nomatch=0):
    """
    Makes a vector of the positions of (first) matches of its first argument in its second.
    :return: bit H2OVec
    """
    # make table to pass to rapids
    rtable = ""
    if hasattr(table, '__iter__'): # make slist or list
      if all([isinstance(t, (int,float)) for t in table]): # make list
        rtable += "(list"
        for t in table: rtable += " #"+str(t)
        rtable += ")"
      elif all([isinstance(t, str) for t in table]): # make slist
        rtable += "(slist"
        for t in table: rtable += " \""+str(t)+"\""
        rtable += ")"
    elif isinstance(table, (int, float)): # make #
      rtable += "#"+str(table)
    elif isinstance(table, str): # make str
      rtable += "\""+table+"\""
    else:
      raise ValueError("`table` must be a scaler (str, int, float), or a iterable of scalers of the same type.")

    tmp_key = H2OFrame.py_tmp_key()
    expr = "(= !{} (match %{} {} #{} ()))".format(tmp_key,self.key(),rtable,nomatch)
    return H2OFrame._get_frame_from_rapids_string(expr, tmp_key, [])

  def scale(self, center=True, scale=True):
    """
    Centers and/or scales the column (H2OVec)
    :return: H2OVec
    :param center: either a ‘logical’ value or numeric value.
    :param scale: either a ‘logical’ value or numeric value.
    """
    tmp_key = H2OFrame.py_tmp_key()
    if   isinstance(center, bool) and isinstance(scale, bool):
      c = '%TRUE' if center else '%FALSE'
      s = '%TRUE' if scale else '%FALSE'
    elif isinstance(center, bool) and isinstance(scale, (int, float)):
      c = '%TRUE' if center else '%FALSE'
      s = '#'+str(scale)
    elif isinstance(center, (int, float)) and isinstance(scale, bool):
      c = '#'+str(center)
      s = '%TRUE' if scale else '%FALSE'
    elif isinstance(center, (int, float)) and isinstance(scale, (int, float)):
      c = '#'+str(center)
      s = '#'+str(scale)
    else: raise(ValueError, "`center` and `scale` arguments (for a H2OVec) must be a bool or a number, but "
                            "got center: {0}, scale: {1}".format(center, scale))
    expr = "(= !{} (scale %{} {} {}))".format(tmp_key,self.key(), c, s)
    return H2OVec._get_vec_from_rapids_string(self, expr, tmp_key)

  def round(self, digits=0):
    """
    :return: The rounded values in the H2OFrame to the specified number of decimal digits.
    """
    tmp_key = H2OFrame.py_tmp_key()
    expr = "(= !{} (round %{} #{}))".format(tmp_key,self.key(),digits)
    return H2OVec._get_vec_from_rapids_string(self, expr, tmp_key)

  def signif(self, digits=6):
    """
    :return: The rounded values in the H2OFrame to the specified number of significant digits.
    """
    tmp_key = H2OFrame.py_tmp_key()
    expr = "(= !{} (signif %{} #{}))".format(tmp_key,self.key(),digits)
    return H2OVec._get_vec_from_rapids_string(self, expr, tmp_key)

  def asnumeric(self):
    """
    :return: A vec with factor columns converted to numbers.
    """
    tmp_key = H2OFrame.py_tmp_key()
    expr = "(= !{} (as.numeric %{}))".format(tmp_key,self.key())
    return H2OVec._get_vec_from_rapids_string(self, expr, tmp_key)

  def _get_vec_from_rapids_string(self, expr, tmp_key):
    h2o.rapids(expr)
    j = h2o.frame(tmp_key)
    fr = j['frames'][0]
    rows = fr['rows']
    veckeys = fr['vec_ids']
    cols = fr['columns']
    colnames = [col['label'] for col in cols]
    vec=H2OVec.new_vecs(zip(colnames, veckeys), rows)[0]
    vec.setName(self._name)
    h2o.removeFrameShallow(tmp_key)
    return vec

  def transpose(self):
    """
    :return: The transpose of the H2OVec (as an H20Frame).
    """
    tmp_key = H2OFrame.py_tmp_key()
    expr = "(= !{} (t %{}))".format(tmp_key,self.key())
    return H2OFrame._get_frame_from_rapids_string(expr, tmp_key, [])

  def table(self, data2=None):
    """
    :return: a frame of the counts at each combination of factor levels
    """
    if data2 and not isinstance(data2, H2OVec): raise ValueError("`data2` must be a vec, but got {0}"
                                                                 "".format(type(data2)))
    tmp_key = H2OFrame.py_tmp_key()
    data2_key = "%"+data2.key() if data2 else "()"
    expr = "(= !{} (table %{} {}))".format(tmp_key,self.key(),data2_key)
    return H2OFrame._get_frame_from_rapids_string(expr, tmp_key, [])

  def levels(self):
    """
    Get the factor levels for this vec.

    :return: a list of strings that are the factor levels for the column.
    """
    res = H2OConnection.get_json("Frames/{}/columns/{}/domain".format(urllib.quote(self._expr.eager()), "C1"))
    return res["domain"][0]

  def nlevels(self):
    """
    Get the number of factor levels for this frame and the specified column index.

    :return: an integer.
    """
    nlevels = self.levels()
    return len(nlevels) if nlevels else 0

  def setLevel(self, level):
    """
    A method to set all column values to one of the levels.
    :param level: The level at which the column will be set (a string). Must be a member of self.levels().
    :return: An H2OVec with all entries set to the desired level
    """
    tmp_key = H2OFrame.py_tmp_key()
    expr = "(= !{} (setLevel %{} {}))".format(tmp_key,self.key(), '\"'+level+'\"')
    return H2OVec._get_vec_from_rapids_string(self, expr, tmp_key)

  def setLevels(self, levels):
    """
    Works on a single categorical vector. New domains must be aligned with the old domains. This call has SIDE
    EFFECTS and mutates the column in place (does not make a copy).
    :param level: The level at which the column will be set (a string)
    :param x: A single categorical column.
    :param levels: A list of strings specifying the new levels. The number of new levels must match the number of
    old levels.
    :return: None
    """
    tmp_key = H2OFrame.py_tmp_key()
    expr = "(= !{} (setDomain %{} {}))".format(tmp_key,self.key(), '(slist '+ ' '.join(['\"'+l+'\"' for l in
                                                                                        levels]) + ')')
    h2o.rapids(expr)
    h2o.removeFrameShallow(tmp_key)

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
    xlen = -1
    for x in [msec,second,minute,hour,day,month,year]:
      if not isinstance(x,int):
        l2 = len(x)
        if xlen != l2:
          if xlen == -1: xlen = l2
          else:  raise ValueError("length of "+str(xlen)+" not compatible with "+str(l2))
    e = None
    for x in [msec,second,minute,hour,day,month,year]:
      x = Expr(x) if isinstance(x,int) else x
      e = Expr(",", x, e)
    e2 = Expr("mktime",e,None,xlen)
    return e2 if xlen==1 else H2OVec("mktime",e2)
