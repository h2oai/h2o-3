# -*- encoding: utf-8 -*-
"""
Rapids expressions. These are helper classes for H2OFrame.

:copyright: (c) 2016 H2O.ai
:license:   Apache License Version 2.0 (see LICENSE for details)
"""
from __future__ import division, print_function, absolute_import, unicode_literals

import collections
import copy
import gc
import math
import sys
import time
import numbers

import tabulate

import h2o
from h2o.backend.connection import H2OConnectionError
from h2o.utils.compatibility import *  # NOQA
from h2o.utils.compatibility import repr2, viewitems, viewvalues
from h2o.utils.shared_utils import _is_fr, _py_tmp_key
from h2o.model.model_base import ModelBase
from h2o.expr_optimizer import optimize

class ExprNode(object):
    """
    Composable Expressions: This module contains code for the lazy expression DAG.

    Execution Overview
    ------------------
      The job of ExprNode is to provide a layer of indirection to H2OFrame instances that
      are built of arbitrarily large, lazy expression DAGs. In order to do this job well,
      ExprNode must also track top-level entry points to the such DAGs, maintain a sane
      amount of state to know which H2OFrame instances are temporary (or not), and maintain
      a cache of H2OFrame properties (nrows, ncols, types, names, few rows of data).

      Top-Level Entry Points
      ----------------------
        An expression is declared top-level if it
          A) Computes and returns an H2OFrame to some on-demand call from somewhere
          B) An H2OFrame instance has more referrers than the 4 needed for the usual flow
             of python execution (see MAGIC_REF_COUNT below for more details).

      Sane Amount of State
      --------------------
        Instances of H2OFrame live and die by the state contained in the _ex field. The three
        pieces of state -- _op, _children, _cache -- are the fewest pieces of state (and no
        fewer) needed to unambiguously track temporary H2OFrame instances and prune
        them according to the usual scoping laws of python.

        If _cache._id is None, then this DAG has never been sent over to H2O, and there's
        nothing more to do when the object goes out of scope.

        If _cache._id is not None, then there has been some work done by H2O to compute the
        big data object sitting in H2O to which _id points. At the time that __del__ is
        called on this object, a determination to throw out the corresponding data in H2O or
        to keep that data is made by the None'ness of _children.

        tl;dr:
          If _cache._id is not None and _children is None, then do not delete in H2O cluster
          If _cache._id is not None and _children is not None, then do delete in H2O cluster

      H2OCache
      --------
        To prevent several unnecessary REST calls and unnecessary execution, a few of the
        oft-needed attributes of the H2OFrame are cached for convenience. The primary
        consumers of these cached values are __getitem__, __setitem__, and a few other
        H2OFrame ops that do argument validation or exchange (e.g. colnames for indices).
        There are more details available under the H2OCache class declaration.
    """

    # Magical count-of-5:   (get 2 more when looking at it in debug mode)
    #  2 for _get_ast_str frame, 2 for _get_ast_str local dictionary list, 1 for parent
    MAGIC_REF_COUNT = 5 if sys.gettrace() is None else 7  # M = debug ? 7 : 5

    # Flag to control application of local expression tree optimizations
    __ENABLE_EXPR_OPTIMIZATIONS__ = True

    def __init__(self, op="", *args):      
        # assert isinstance(op, str), op
        self._op = op  # Base opcode string
        self._children = tuple(
            a._ex if _is_fr(a) else a for a in args)  # ast children; if not None and _cache._id is not None then tmp
        self._cache = H2OCache()  # ncols, nrows, names, types
        # try to fuse/simplify expression
        if self.__ENABLE_EXPR_OPTIMIZATIONS__:
            self._optimize()

    def _eager_frame(self):
        if not self._cache.is_empty(): return
        if self._cache._id is not None: return  # Data already computed under ID, but not cached locally
        self._eval_driver(True)

    def _eager_scalar(self):  # returns a scalar (or a list of scalars)
        if not self._cache.is_empty():
            assert self._cache.is_scalar()
            return self
        assert self._cache._id is None
        self._eval_driver(False)
        assert self._cache._id is None
        assert self._cache.is_scalar()
        return self._cache._data

    def _eval_driver(self, top):
        exec_str = self._get_ast_str(top)
        res = ExprNode.rapids(exec_str)
        if 'scalar' in res:
            if isinstance(res['scalar'], list):
                self._cache._data = [float(x) for x in res['scalar']]
            else:
                self._cache._data = None if res['scalar'] is None else float(res['scalar'])
        if 'string' in res: self._cache._data = res['string']
        if 'funstr' in res: raise NotImplementedError
        if 'key' in res:
            self._cache.nrows = res['num_rows']
            self._cache.ncols = res['num_cols']
        return self

    def _optimize(self):
        while True:
            opt = optimize(self)
            if opt is not None:
                opt(ctx=None)
            else:
                break

    # Recursively build a rapids execution string.  Any object with more than
    # MAGIC_REF_COUNT referrers will be cached as a temp until the next client GC
    # cycle - consuming memory.  Do Not Call This except when you need to do some
    # other cluster operation on the evaluated object.  Examples might be: lazy
    # dataset time parse vs changing the global timezone.  Global timezone change
    # is eager, so the time parse as to occur in the correct order relative to
    # the timezone change, so cannot be lazy.
    #
    def _get_ast_str(self, top):
        if not self._cache.is_empty():  # Data already computed and cached; could a "false-like" cached value
            return str(self._cache._data) if self._cache.is_scalar() else self._cache._id
        if self._cache._id is not None:
            return self._cache._id  # Data already computed under ID, but not cached
        assert isinstance(self._children,tuple)
        exec_str = "({} {})".format(self._op, " ".join([ExprNode._arg_to_expr(ast) for ast in self._children]))
        gc_ref_cnt = len(gc.get_referrers(self))
        if top or gc_ref_cnt >= ExprNode.MAGIC_REF_COUNT:
            self._cache._id = _py_tmp_key(append=h2o.connection().session_id)
            exec_str = "(tmp= {} {})".format(self._cache._id, exec_str)
        return exec_str

    @staticmethod
    def _arg_to_expr(arg):
        if arg is None:
            return "[]"  # empty list
        if isinstance(arg, ExprNode):
            return arg._get_ast_str(False)
        if isinstance(arg, ASTId):
            return str(arg)
        if isinstance(arg, (list, tuple, range)):
            return "[%s]" % " ".join(repr2(x) for x in arg)
        if isinstance(arg, slice):
            start = 0 if arg.start is None else arg.start
            stop = float("nan") if arg.stop is None else arg.stop
            step = 1 if arg.step is None else arg.step
            assert start >= 0 and step >= 1 and (math.isnan(stop) or stop >= start + step)
            if step == 1:
                return "[%d:%s]" % (start, str(stop - start))
            else:
                return "[%d:%s:%d]" % (start, str((stop - start + step - 1) // step), step)
        if isinstance(arg, ModelBase):
            return arg.model_id
        # Number representation without Py2 L suffix enforced
        if isinstance(arg, numbers.Integral):
            return repr2(arg).strip('L')
        return repr2(arg)

    def __del__(self):
        try:
            if self._cache._id is not None and self._children is not None:
                ExprNode.rapids("(rm {})".format(self._cache._id))
        except (AttributeError, H2OConnectionError):
            pass

    def arg(self, idx):
        return self._children[idx]

    def args(self):
        return self._children

    def narg(self):
        return len(self._children)

    @staticmethod
    def _collapse_sb(sb):
        return ' '.join("".join(sb).replace("\n", "").split()).replace(" )", ")")

    def _debug_print(self, pprint=True):
        return "".join(self._2_string(sb=[])) if pprint else ExprNode._collapse_sb(self._2_string(sb=[]))

    def _to_string(self):
        return ' '.join(["(" + self._op] + [ExprNode._arg_to_expr(a) for a in self._children] + [")"])

    def _2_string(self, depth=0, sb=None):
        sb += ['\n', " " * depth, "(" + self._op, " "]
        if self._children is not None:
            for child in self._children:
                if _is_fr(child) and child._ex._cache._id is None:
                    child._ex._2_string(depth + 2, sb)
                elif _is_fr(child):
                    sb += ['\n', ' ' * (depth + 2), child._ex._cache._id]
                elif isinstance(child, ExprNode):
                    child._2_string(depth + 2, sb)
                else:
                    sb += ['\n', ' ' * (depth + 2), str(child)]
        sb += ['\n', ' ' * depth + ") "] + ['\n'] * (depth == 0)  # add a \n if depth == 0
        return sb

    def __repr__(self):
        return "<Expr(%s)%s%s>" % (
            " ".join([self._op] + [repr(x) for x in (self._children or [])]),
            "#%s" % self._cache._id if self._cache._id else "",
            "; scalar" if self._cache.is_scalar() else "",
        )

    @staticmethod
    def rapids(expr):
        """
        Execute a Rapids expression.

        :param expr: The rapids expression (ascii string).

        :returns: The JSON response (as a python dictionary) of the Rapids execution
        """
        return h2o.api("POST /99/Rapids", data={"ast": expr, "session_id": h2o.connection().session_id})




class ASTId:
    def __init__(self, name=None):
        if name is None:
            raise ValueError("Attempted to make ASTId with no name.")
        self.name = name

    def __repr__(self):
        return self.name




class H2OCache(object):
    def __init__(self):
        self._id = None
        self._nrows = -1
        self._ncols = -1
        self._types = None  # col types
        self._names = None  # col names
        self._data = None  # ordered dict of cached rows, or a scalar
        self._l = 0  # nrows cached

    @property
    def nrows(self):
        return self._nrows

    @nrows.setter
    def nrows(self, value):
        self._nrows = value

    def nrows_valid(self):
        return self._nrows >= 0

    @property
    def ncols(self):
        return self._ncols

    @ncols.setter
    def ncols(self, value):
        self._ncols = value

    def ncols_valid(self):
        return self._ncols >= 0

    @property
    def names(self):
        return self._names

    @names.setter
    def names(self, value):
        self._names = value

    def names_valid(self):
        return self._names is not None

    @property
    def types(self):
        return self._types

    @types.setter
    def types(self, value):
        self._types = value

    def types_valid(self):
        return self._types is not None

    @property
    def scalar(self):
        return self._data if self.is_scalar() else None

    @scalar.setter
    def scalar(self, value):
        self._data = value

    def __len__(self):
        return self._l

    def is_empty(self):
        return self._data is None

    def is_scalar(self):
        return not isinstance(self._data, dict)

    def is_valid(self):
        return (  # self._id is not None and
                not self.is_empty() and
                self.nrows_valid() and
                self.ncols_valid() and
                self.names_valid() and
                self.types_valid())

    def fill(self, rows=10, rows_offset=0, cols=-1, full_cols=-1, cols_offset=0, light=False):
        assert self._id is not None
        if self._data is not None:
            if rows <= len(self):
                return
        req_params = {
            "row_count": rows,
            "row_offset": rows_offset,
            "column_count" : cols,
            "full_column_count" : full_cols,
            "column_offset" : cols_offset
        }
        if light:
            endpoint = "/3/Frames/%s/light"
        else:
            endpoint = "/3/Frames/%s"
        res = h2o.api("GET " + endpoint % self._id, data=req_params)["frames"][0]
        self._l = rows
        self._nrows = res["rows"]
        self._ncols = res["total_column_count"]
        self._names = [c["label"] for c in res["columns"]]
        self._types = dict(zip(self._names, [c["type"] for c in res["columns"]]))
        self._fill_data(res)

    def _fill_data(self, json):
        self._data = collections.OrderedDict()
        for c in json["columns"]:
            c.pop('__meta')  # Redundant description ColV3
            c.pop('domain_cardinality')  # Same as len(c['domain'])
            sdata = c.pop('string_data')
            if sdata:
                c['data'] = sdata  # Only use data field; may contain either [str] or [real]
            # Data (not string) columns should not have a string in them.  However,
            # our NaNs are encoded as string literals "NaN" as opposed to the bare
            # token NaN, so the default python json decoder does not convert them
            # to math.nan.  Do that now.
            else:
                if c['data'] and (len(c['data']) > 0):  # orc file parse can return frame with zero rows
                    c['data'] = [float('nan') if x == "NaN" else x for x in c['data']]
            if c['data']:
                self._data[c.pop('label')] = c  # Label used as the Key
        return self

    #---- pretty printing ----

    def _tabulate(self, tablefmt="simple", rollups=False, rows=10):
        """Pretty tabulated string of all the cached data, and column names"""
        if not self.is_valid(): self.fill(rows=rows)
        # Pretty print cached data
        d = collections.OrderedDict()
        # If also printing the rollup stats, build a full row-header
        if rollups:
            col = next(iter(viewvalues(self._data)))  # Get a sample column
            lrows = len(col['data'])  # Cached rows being displayed
            d[""] = ["type", "mins", "mean", "maxs", "sigma", "zeros", "missing"] + list(map(str, range(lrows)))
        # For all columns...
        for k, v in viewitems(self._data):
            x = v['data']  # Data to display
            t = v["type"]  # Column type
            if t == "enum":
                domain = v['domain']  # Map to cat strings as needed
                x = ["" if math.isnan(idx) else domain[int(idx)] for idx in x]
            elif t == "time":
                x = ["" if math.isnan(z) else time.strftime("%Y-%m-%d %H:%M:%S", time.gmtime(z / 1000)) for z in x]
            if rollups:  # Rollups, if requested
                mins = v['mins'][0] if v['mins'] and v["type"] != "enum" else None
                maxs = v['maxs'][0] if v['maxs'] and v["type"] != "enum" else None
                #Cross check type with mean and sigma. Set to None if of type enum.
                if v['type'] == "enum":
                    v['mean'] = v['sigma'] = v['zero_count'] = None
                x = [v['type'], mins, v['mean'], maxs, v['sigma'], v['zero_count'], v['missing_count']] + x
            d[k] = x  # Insert into ordered-dict
        return tabulate.tabulate(d, headers="keys", tablefmt=tablefmt)

    def flush(self):  # flush everything but the frame_id
        fr_id = self._id
        self.__dict__ = H2OCache().__dict__.copy()
        self._id = fr_id
        return self

    def fill_from(self, cache):
        assert isinstance(cache, H2OCache)
        cur_id = self._id
        self.__dict__ = copy.copy(cache.__dict__)  # copy.deepcopy is buggy :( https://bugs.python.org/issue16251
        self._data = None
        self._id = cur_id

    def dummy_fill(self):
        self._id = "dummy"
        self._nrows = 0
        self._ncols = 0
        self._names = []
        self._types = {}
        self._data = {}
