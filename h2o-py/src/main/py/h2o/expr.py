"""
This module contains code for the lazy expression DAG.
"""

import sys
from math import sqrt, isnan
from frame import H2OVec
from h2o import H2OCONN

class Expr(object):
    """
    Expr objects have a few different flavors:
        1. A pending to-be-computed BigData expression. Does _NOT_ have a Key
        2. An already compted BigData expression. Does have a key
        3. A small-data computation, pending or not.

    Pending computations point to other Expr objects in a DAG of pending computations.
    Pointed at by at most one H2OVec (during construction) and no others. If that H2OVec
    goes dead, this computation is known to be an internal temp, used only in building
    other Expr objects.
    """

    def __init__(self, op, left=None, rite=None, length=None):
        """
        Create a new Expr object.

        Constructor choices:
            ("op"   left rite): pending calc, awaits left & rite being computed
            ("op"   None None): precomputed local small data
            (hexkey #num name): precomputed remote Big Data

        :param op: An operation to perform
        :param left: An Expr to the "left"
        :param rite: An Expr to the "right"
        :param length: The length of the H2OVec/H2OFrame object.
        :return: A new Expr object.
        """

        # instance variables
        self._op      = None
        self._data    = None
        self._left    = None
        self._rite    = None
        self._name    = None
        self._summary = None  # computed lazily
        self._len     = None

        self._op, self._data = (op, None) if isinstance(op, str) else ("rawdata", op)
        self._name = self._op  # Set an initial name, generally overwritten

        assert self._is_valid(), str(self._name) + str(self._data)

        self._left = left.get_expr() if isinstance(left, H2OVec) else left
        self._rite = rite.get_expr() if isinstance(rite, H2OVec) else rite

        assert self._left is None or isinstance(self._left, Expr) \
            or isinstance(self._data, unicode), self.debug()
        assert self._rite is None or isinstance(self._rite, Expr) \
            or isinstance(self._data, unicode), self.debug()

        # Compute length eagerly
        if self.is_remote():
            assert length is not None
            self._len = length
        elif self.is_local():
            self._len = len(self._data) if isinstance(self._data, list) else 1
        else:
            self._len = length if length else len(self._left)
        assert self._len is not None

    def name(self):
        return self._name

    def set_len(self, i):
        self._len = i

    def get_len(self):
        return self._len

    def data(self):
        return self._data

    def is_local(self):
        return isinstance(self._data, (list, int, float))

    def is_remote(self):
        return isinstance(self._data, unicode)

    def is_pending(self):
        return self._data is None

    def is_computed(self):
        return not self.is_pending()

    def _is_valid(self, child=False):
        check = False
        if child:

        else:

        return self.is_local() or self.is_remote() or self.is_pending()

    # Length, generally withOUT triggering an eager evaluation
    def __len__(self):
        return self._len

    # Print structure without eval'ing
    def debug(self):
        return ("([" + self.name() + "] = " +
                str(self._left.name() if isinstance(self._left, Expr) else self._left) +
                " " + self._op + " " +
                str(self._rite.name() if isinstance(self._rite, Expr) else self._rite) +
                " = " + str(type(self._data)) + ")")

    # Eval and print
    def show(self):
        self.eager()
        if isinstance(self._data, unicode):
            j = H2OCONN.Frame(self._data)
            data = j['frames'][0]['columns'][0]['data']
            return str(data)
        return self._data.__str__()

    # Comment out to help in debugging
    # def __str__(self): return self.show()

    # Compute summary data
    def summary(self):
        self.eager()
        if self.is_local():
            x = self._data[0]
            t = 'int' if isinstance(x, int) else (
                'enum' if isinstance(x, str) else 'real')
            mins = [min(self._data)]
            maxs = [max(self._data)]
            n = len(self._data)
            mean = sum(self._data) / n if t != 'enum' else None
            ssq = 0
            zeros = 0
            missing = 0
            for x in self._data:
                if t != 'enum': ssq += (x - mean) * (x - mean)
                if x == 0:  zeros += 1
                if x is None or (t != 'enum' and isnan(x)): missing += 1
            stddev = sqrt(ssq / (n - 1)) if t != 'enum' else None
            return {'type': t, 'mins': mins, 'maxs': maxs, 'mean': mean, 'sigma': stddev,
                    'zeros': zeros, 'missing': missing}
        if self._summary: return self._summary
        j = H2OCONN.Frame(self._data)
        self._summary = j['frames'][0]['columns'][0]
        return self._summary

    # Basic indexed or sliced lookup
    def __getitem__(self, i):
        x = self.eager()
        if self.is_local(): return x[i]
        if not isinstance(i, int): raise NotImplementedError  # need a bigdata slice here
        # ([ %vec #row #0)
        #j = H2OCONN.Rapids("([ %"+str(self._data)+" #"+str(i)+" #0)")
        #return j['scalar']
        raise NotImplementedError

    # Small-data add; result of a (lazy but small) Expr vs a plain int/float
    def __add__(self, i):
        return self.eager() + i

    def __radd__(self, i):
        return self + i  # Add is associative

    def __del__(self):
        if self.is_pending() or self.is_local(): return  # Dead pending op or local data; nothing to delete
        assert self.is_remote()
        global _CMD;
        if _CMD is None:
            H2OCONN.Remove(self._data)
        else:
            s = " (del %" + self._data + " #0)"
            global _TMPS
            if _TMPS is None:
                print "Lost deletes: ", s
            else:
                _TMPS += s

    # This forces a top-level execution, as needed, and produces a top-level
    # result LOCALLY.  Frames are returned and truncated to the standard head()
    # response - 200cols by 100rows.
    def eager(self):
        if self.is_computed(): return self._data
        # Gather the computation path for remote work, or doit locally for local work
        global _CMD, _TMPS
        assert not _CMD and not _TMPS
        _CMD = "";
        _TMPS = ""  # Begin gathering rapids commands
        self._doit()  # Symbolically execute the command
        cmd = _CMD;
        tmps = _TMPS  # Stop  gathering rapids commands
        _CMD = None;
        _TMPS = None
        if self.is_local():  return self._data  # Local computation, all done
        # Remote computation - ship Rapids over wire, assigning key to result
        if tmps:
            cmd = "(, " + cmd + tmps + ")"
        j = H2OCONN.Rapids(cmd)
        if isinstance(self._data, unicode):
            pass  # Big Data Key is the result
        # Small data result pulled locally
        else:
            self._data = j['head'] if j['num_rows'] else j['scalar']
        return self._data

    # External API for eager; called by all top-level demanders (e.g. print)
    # May trigger (recursive) big-data eval.
    def _doit(self):
        if self.is_computed(): return

        # Slice assignments are a 2-nested deep structure, returning the left-left
        # vector.  Must fetch it now, before it goes dead after eval'ing the slice.
        # Shape: (= ([ %vec bool_slice_expr) vals_to_assign)
        # Need to fetch %vec out
        assign_vec = self._left._left if self._op == "=" and self._left._op == "[" else None

        global _CMD
        # See if this is not a temp and not a scalar; if so it needs a name
        cnt = sys.getrefcount(
            self) - 1  # Remove one count for the call to getrefcount itself
        # Magical count-of-4 is the depth of 4 interpreter stack
        py_tmp = cnt != 4 and self._len > 1 and not assign_vec

        if py_tmp:
            self._data = _py_tmp_key()  # Top-level key/name assignment
            _CMD += "(= !" + self._data + " "
        _CMD += "(" + self._op + " "

        left = self._left
        if left:
            if left.isPending():
                left._doit()
            elif isinstance(left._data, (int, float)):
                _CMD += "#" + str(left._data)
            elif isinstance(left._data, unicode):
                _CMD += "%" + str(left._data)
            else:
                pass  # Locally computed small data
        _CMD += " "

        rite = self._rite
        if rite:
            if rite.is_pending():
                rite._doit()
            elif isinstance(rite._data, (int, float)):
                _CMD += "#" + str(rite._data)
            elif isinstance(rite._data, unicode):
                _CMD += "%" + str(rite._data)
            else:
                pass  # Locally computed small data

        if self._op == "+":
            if isinstance(left._data, (int, float)):
                if isinstance(rite._data, (int, float)):
                    self._data = left + rite
                elif rite.is_local():
                    self._data = [left + x for x in rite._data]
                else:
                    pass
            elif isinstance(rite._data, (int, float)):
                if left.isLocal():
                    self._data = [x + rite for x in left._data]
                else:
                    pass
            else:
                if left.isLocal() and rite.is_local():
                    self._data = [x + y for x, y in zip(left._data, rite._data)]
                elif (left.isRemote() or left._data is None) and \
                        (rite.is_remote() or rite._data is None):
                    pass
                else:
                    raise NotImplementedError
        elif self._op == "==":
            if isinstance(left._data, (int, float)):
                raise NotImplementedError
            elif isinstance(rite._data, (int, float)):
                if left.isLocal():
                    self._data = [x == rite._data for x in left._data]
                else:
                    pass
            else:
                raise NotImplementedError
        elif self._op == "[":
            if left.isLocal():
                self._data = left._data[rite._data]
            else:
                _CMD += ' "null"'  # Rapids column zero lookup
        elif self._op == "=":
            if left.isLocal():
                raise NotImplementedError
            else:
                if rite is None: _CMD += "#NaN"
        elif self._op == "mean":
            if left.isLocal():
                self._data = sum(left._data) / len(left._data)
            else:
                _CMD += " #0 %TRUE"  # Rapids mean extra args (trim=0, rmNA=TRUE)
        elif self._op == "as.factor":
            if left.isLocal():
                self._data = map(str, left._data)
            else:
                pass
        else:
            raise NotImplementedError
        # End of expression... wrap up parens
        _CMD += ")"
        if py_tmp:
            _CMD += ")"
        # Free children expressions; might flag some subexpresions as dead
        self._left = None  # Trigger GC/ref-cnt of temps
        self._rite = None
        # Keep LHS alive
        if assign_vec:
            if assign_vec._op != "rawdata":  # Need to roll-up nested exprs
                raise NotImplementedError
            self._left = assign_vec
            self._data = assign_vec._data

        return

# Global list of pending expressions and deletes to ship to the cluster
_CMD = None
_TMPS = None