"""
This module contains the abstraction for H2OFrame and H2OVec objects.
"""

import csv
import tabulate
import uuid
from h2o import H2OCONN
from h2o import Expr


class H2OFrame(object):
    """
    Frame represents a 2D array of data where each column is uniformly typed.
    The data may be local, or it may be in an H2O cluster. The data is loaded
    from a CSV file, and is either a python-process-local file or a cluster-local
    file, or a list of H2OVec objects.
    """

    def __init__(self, local_fname=None, remote_fname=None, vecs=None):
        """
        Create a new H2OFrame object by passing a file path or a list of H2OVecs.

        If `remote_fname` is not None, then a REST call will be made to import the
        data specified at the location `remote_fname`.

        If `local_fname` is not None, then the data is not imported into the H2O cluster
        at the time of object creation.

        :param local_fname: A local path to a data source. Data is python-process-local.
        :param remote_fname: A remote path to a data source. Data is cluster-local.
        :param vecs: A list of H2OVec objects.
        :return: An instance of an H2OFrame object.
        """

        self.local_fname  = local_fname
        self.remote_fname = remote_fname
        self._vecs = None

        # Import the data into H2O cluster
        if remote_fname:
            if not H2OCONN:
                raise ValueError("No open h2o connection")
            rawkey  = H2OCONN.ImportFile(remote_fname)
            setup   = H2OCONN.ParseSetup(rawkey)
            parse   = H2OCONN.Parse(setup, H2OFrame._py_tmp_key())  # create a new key
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

    def get_vecs(self):
        return self._vecs

    # Print [col, cols...]
    def show(self):
        s = ""
        for vec in self._vecs:
            s += vec.show()
        return s
    # Comment out to help in debugging
    #def __str__(self): return self.show()

    # In-depth description of data frame
    def describe(self):
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
        raise NotImplementedError

    def __setitem__(self, b, c):
        """
        Replace a column in an H2OFrame.
        :param b: A 0-based index or a column name.
        :param c: The vector that 'b' is replaced with.
        :return: Returns this H2OFrame.
        """
        i, v = None
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
        if not i or not v:
            raise ValueError("Name" + b + " not in Frame")

        if len(c) != len(v):
            raise ValueError("len(c)=" + len(c) +
                             " not compatible with Frame len()=" + len(v))

        c._name = b
        self._vecs[i] = c

    # Column selection via integer, string (name) returns a Vec
    # Column selection via slice returns a subset Frame
    def drop(self, i):
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
    def _py_tmp_key():
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
        fr = H2OFrame._py_tmp_key()
        cbind = "(= !" + fr + " (cbind %"
        cbind += " %".join([vec.get_expr().eager() for vec in dataset.get_vecs()]) + "))"
        H2OCONN.Rapids(cbind)
        # And frame columns
        colnames = "(colnames= %" + fr + " {(: #0 #" + str(len(dataset) - 1) + ")} {"
        cnames = ';'.join([vec.name() for vec in dataset.get_vecs()])
        colnames += cnames + "})"
        H2OCONN.Rapids(colnames)
        return fr

    def _row(self, field, idx):
        l = [field]
        for vec in self._vecs:
            tmp = vec.summary()[field]
            l.append(tmp[idx] if idx is not None else tmp)
        return l


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
