# -*- coding: utf-8 -*-
"""
This module contains the abstraction for H2OFrame and H2OVec objects.
"""
import numpy
import csv
import tabulate
import uuid
import h2o
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

        The following types are permissible for the python_obj argument:

            tuple ()
            list  []
            dict  {}
            numpy.array

        The type of `python_obj` is inspected by performing an `isinstance` call. A
        ValueError exception will be raised if the type of `python_obj` is not one of the
        above types.

        In the subsequent sections, each data type will be discussed in detail. Each
        discussion will be couched in terms of the "source" representation (the python
        object) and the "target" representation (the H2O object). Concretely, the topics
        of discussion will be on the following: Headers, Data Types, Number of Rows,
        Number of Columns.

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

        Essentially, the tuple is an immutable list. This immutability does not map to the
        H2OFrame. <em>So pythonistas be ware!</em>


        Loading: list []
        ================



        Loading: dict {}
        ================


        Loading: numpy.array
        ====================

    """

    def __init__(self, python_obj=None, local_fname=None, remote_fname=None, vecs=None):
        """
        Create a new H2OFrame object by passing a file path or a list of H2OVecs.

        If `remote_fname` is not None, then a REST call will be made to import the
        data specified at the location `remote_fname`.

        If `local_fname` is not None, then the data is not imported into the H2O cluster
        at the time of object creation.

        If `python_obj` is not None, then an attempt to upload the python object to H2O
        will be made. A valid python object has type `list`, `dict`, or `numpy.array`.

        For more information on the structure of the input for the various native python
        data types ("native" meaning non-H2O), please see the general documentation for
        this object.

        :param python_obj: A "native" python object - numpy array, Pandas DataFrame, list.
        :param local_fname: A local path to a data source. Data is python-process-local.
        :param remote_fname: A remote path to a data source. Data is cluster-local.
        :param vecs: A list of H2OVec objects.
        :return: An instance of an H2OFrame object.
        """

        self.local_fname  = local_fname
        self.remote_fname = remote_fname
        self._vecs = None

        if python_obj:
            # handle python [], python {}, and numpy.array

            # handle []
            if isinstance(python_obj, list):

                # do we have a list of lists?
                lol = any(isinstance(l, list) for l in python_obj)
                lol_all = False

                # if we have a list of lists, then all items in python_obj must be a list.
                # otherwise, raise a ValueError exception.
                if lol:
                    lol_all = all(isinstance(l, list) for l in python_obj)
                if not lol_all:
                    raise ValueError(
                        "`python_obj` is a mixture of nested lists and other types.")

                if lol:
                    # have list of lists, each list is a row
                    # length of the longest list is the number of columns
                    cols = max([len(l) for l in python_obj])
                    header = H2OFrame._gen_header(cols)

                    # write header
                    # write each list
                    # call upload_file
                    # return

                else:
                    cols = len(python_obj)
                    header = H2OFrame._gen_header(cols)

                    # write header
                    # write list
                    # call upload_file
                    # return

                return
            elif isinstance(python_obj, numpy.array):
                return

            elif isinstance(python_obj, dict):
                return

            raise ValueError("Object must be a python list of numbers or a numpy array")

        # Import the data into H2O cluster
        if remote_fname:
            rawkey  = h2o.import_file(remote_fname)
            setup   = h2o.parse_setup(rawkey)
            parse   = h2o.parse(setup, H2OFrame.py_tmp_key())  # create a new key
            cols    = parse['columnNames']
            rows    = parse['rows']
            veckeys = parse['vecKeys']
            self._vecs = H2OVec.new_vecs(zip(cols, veckeys), rows)
            print "Imported", remote_fname, "into cluster with", \
                rows, "rows and", len(cols), "cols"

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
        elif vecs is not None:
            vlen = len(vecs[0])
            for v in vecs:
                if not isinstance(v, H2OVec):
                    raise ValueError("Not a list of Vecs")
                if len(v) != vlen:
                    raise ValueError("Vecs not the same size: "
                                     + str(vlen) + " != " + str(len(v)))
            self._vecs = vecs
        else:
            raise ValueError("Frame made from CSV file or an array of Vecs only")

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
        s = ""
        for vec in self._vecs:
            s += vec.show()
        return s
    # Comment out to help in debugging
    #def __str__(self): return self.show()

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
            self._row('type'   , None),
            self._row('mins'   , 0),
            self._row('mean'   , None),
            self._row('maxs'   , 0),
            self._row('sigma'  , None),
            self._row('zeros'  , None),
            self._row('missing', None)
        ]
        print tabulate.tabulate(table, headers)

    # Column selection via integer, string (name) returns a Vec
    # Column selection via slice returns a subset Frame
    def __getitem__(self, i):
        if isinstance(i, int):
            return self._vecs[i]
        if isinstance(i, str):
            for v in self._vecs:
                if i == v.name():
                    return v
            raise ValueError("Name " + i + " not in Frame")
        # Slice; return a Frame not a Vec
        if isinstance(i, slice):
            return H2OFrame(vecs=self._vecs[i])
        # Row selection from a boolean Vec
        if isinstance(i, H2OVec):
            if len(i) != len(self._vecs[0]):
                raise ValueError("len(vec)=" + len(self._vecs[0]) +
                                 " cannot be broadcast across len(i)=" + len(i))
            return H2OFrame(vecs=[x.row_select(i) for x in self._vecs])

        # have a list of numbers or strings
        if isinstance(i, list):
            vecs = []
            for it in i:
                if isinstance(it, int):
                    vecs += [self._vecs[it]]
                    continue
                if isinstance(it, str):
                    has_vec = False
                    for v in self._vecs:
                        if it == v.name():
                            has_vec = True
                            vecs += [v]
                    if not has_vec:
                        raise ValueError("Name " + it + " not in Frame")
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
            v = self.get_vecs()[i]
        else:
            raise NotImplementedError

        # some error checking
        if not v:
            raise ValueError("Name " + b + " not in Frame")

        if len(c) != len(v):
            raise ValueError("len(c)=" + len(c) +
                             " not compatible with Frame len()=" + len(v))

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
        if len(self) == 0:
            return self
        if isinstance(i, H2OFrame):
            if len(i) != len(self):
                raise ValueError("ncol(self)" + len(self) +
                                 " cannot be broadcast across len(i)=" + len(i))
            return H2OFrame(vecs=[x + y for x, y in zip(self._vecs, i.get_vecs())])
        if isinstance(i, H2OVec):
            if len(i) != len(self._vecs[0]):
                raise ValueError("nrow(self)" + len(self._vecs[0]) +
                                 " cannot be broadcast across len(i)=" + len(i))
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

    @staticmethod
    def py_tmp_key():
        """
        :return: a unique h2o key obvious from python
        """
        return unicode("py" + str(uuid.uuid4()))

    # Send over a frame description to H2O
    @staticmethod
    def send_frame(dataset):
        """
        Send a frame description to H2O, returns a key.
        :param dataset: An H2OFrame object
        :return: A key
        """
        # Send over the frame
        fr = H2OFrame.py_tmp_key()
        cbind = "(= !" + fr + " (cbind %"
        cbind += " %".join([vec.get_expr().eager() for vec in dataset.get_vecs()]) + "))"
        h2o.rapids(cbind)
        # And frame columns
        colnames = "(colnames= %" + fr + " {(: #0 #" + str(len(dataset) - 1) + ")} {"
        cnames = ';'.join([vec.name() for vec in dataset.get_vecs()])
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
        return ["C" + c for c in range(1, cols + 1, 1)]


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
        if not vecs:
            return vecs
        return [H2OVec(str(col), Expr(op=veckey['name'], length=rows))
                for idx, (col, veckey) in enumerate(vecs)]

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

    def show(self):
        """
        Pretty print this H2OVec.
        :return: void
        """
        return self._name + " " + self._expr.show()

    # Comment out to help in debugging
    #def __str__(self): return self.show()

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
        return H2OVec(self._name + "[" + vec.name() + "]", Expr("[", self, vec))

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
        if c and len(c) != 1 and len(c) != len(self):
            raise ValueError("len(self)=" + len(self) +
                             " cannot be broadcast across len(c)=" + len(c))
        # row-wise assignment
        if isinstance(b, H2OVec):

            # whole vec replacement
            if len(b) != len(self):
                raise ValueError("len(self)=" + len(self) +
                                 " cannot be broadcast across len(b)=" + len(b))

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
            if len(i) != len(self):
                raise ValueError("len(self)=" + len(self) +
                                 " cannot be broadcast across len(i)=" + len(i))
            # lazy new H2OVec
            return H2OVec(self._name + "+" + i._name, Expr("+", self, i))

        # H2OVec + number
        if isinstance(i, (int, float)):
            if i == 0:
                return self

            # lazy new H2OVec
            return H2OVec(self._name + "+" + str(i), Expr("+", self, Expr(i)))
        raise NotImplementedError

    def __radd__(self, i):
        """
        Add is commutative: call __add__(i)
        :param i: A Vec or a float.
        :return: A new H2OVec.
        """
        return self.__add__(i)

    def __eq__(self, i):
        """
        Perform the '==' operation.
        :param i: An H2OVec or a number.
        :return: A new H2OVec.
        """

        # == compare on two H2OVecs
        if isinstance(i, H2OVec):

            # can only compare two vectors of the same length
            if len(i) != len(self):
                raise ValueError("len(self)=" + len(self) +
                                 " cannot be broadcast across len(i)=" + len(i))
            # lazy new H2OVec
            return H2OVec(self._name + "==" + i._name, Expr("==", self, i))

        # == compare on a Vec and a constant Vec
        if isinstance(i, (int, float)):

            # lazy new H2OVec
            return H2OVec(self._name + "==" + str(i), Expr("==", self, Expr(i)))

        raise NotImplementedError

    def __lt__(self, i):
        # Vec < Vec
        if isinstance(i, H2OVec):
            if len(i) != len(self):
                raise ValueError("len(self)=" + len(self) +
                                 " cannot be broadcast across len(i)=" + len(i))
            return H2OVec(self.name() + "<" + i.name(), Expr("<", self, i))

        # Vec < number
        elif isinstance(i, (int, float)):
            return H2OVec(self.name() + "<" + str(i), Expr("<", self, Expr(i)))

        else:
            raise NotImplementedError

    def __ge__(self, i):
        # Vec >= Vec
        if isinstance(i, H2OVec):
            if len(i) != len(self):
                raise ValueError("len(self)=" + len(self) +
                                 " cannot be broadcast across len(i)=" + len(i))
            return H2OVec(self.name() + ">=" + i.name(), Expr(">=", self, i))

        # Vec >= number
        elif isinstance(i, (int, float)):
            return H2OVec(self.name() + ">=" + str(i), Expr(">=", self, Expr(i)))

        else:
            raise NotImplementedError

    def __len__(self):
        """
        :return: The length of this H2OVec
        """
        return len(self._expr)

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
        return H2OVec("runif", Expr("h2o.runif", self.get_expr(), Expr(seed)))