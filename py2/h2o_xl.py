import h2o_exec as h2e, h2o_print as h2p, h2o_cmd
import re, math
from copy import copy
# from h2o_xl import Fcn, Seq, Cbind, Colon, Assign, Item, Exec, KeyIndexed, Cut
from h2o_test import dump_json

# can set this in a test to disable the actual exec, just debugprint()
debugPrintEnable = False
debugRefPrintEnable = False
# to work without h2o (just print asts)
debugNoH2O = False
def debugprint(*args, **kwargs):
    if debugPrintEnable:
        # compatible with print definition
        for x in args:
            print x,
        for x in kwargs:
            print x,
        print

def checkAst(expected):
    ast = h2o_xl.Xbase.lastExecResult['ast']
    ast = re.sub('knon_0x[0-9a-fA-F]+', 'knon_0x...', ast)
    expected  = re.sub('knon_0x[0-9a-fA-F]+', 'knon_0x...', expected)
    # remove the id suffix for knon_ created keys, before comparing
    # knon_0x1a34250
    assert ast==expected, 'Actual: "%s"    Expected: "%s"' % (ast, expected)
    print "----------------------------------------------------------------\n"

# we init to 1 row/col. (-1) can't figure how how to init to no rows in a single expression
def astForInit(frame):
    return '(= !%s (c {#-1}))' % frame
    # will this work?
    # return '(= !%s "null")' % frame

class Xbase(object):
    lastExecResult = {}
    lastResult = None
    defaultAst = "Empty from Xbase init"
    keyWriteHistoryList = []

    def refcntInc(self, *args):
        # Expr shouldn't be used? but maybe useful redirect.
        # can't do a = 1 and assume a key is created
        if not isinstance(self, (Key, KeyIndexed, Fcn, Expr, Def, DF, Col)):
            return

        self.refcnt += 1
        # if a lhs Assign does exist due to a indexed key, then the last function won't be the root
        # by making that non-indexed assign look like the indexed assign, things should be easier.
        # Items can be root?
        if debugRefPrintEnable:
            h2p.red_print("refcntInc: %s for" % self.refcnt, id(self), type(self), self)
        # so we refcnt ourselves once? so if refcnt=1, that's a "root" ?
        if self.refcnt > 1:
            if debugRefPrintEnable:
                h2o.red_print("INTERESTING: refcnt is > 1: %s %s %s" % (self.refcnt, type(self), self))

        for a in args:
            if a:
                if isinstance(a, (int, float, list, tuple, basestring)):
                    continue
                if isinstance(a, (list, tuple)):
                    for operand in a:
                        if operand:
                            operand.refcntInc()
                else:
                    a.refcntInc()

    # not used
    def json(self): # returns a json string. debugprint(s it too.)
        import json
        s = vars(self)
        debugprint(json.dumps(s, indent=4, sort_keys=True))
        return s

    def __init__(self):
        # increment this on every rhs of an object. If an objec still has this == 0, then it's the root object for a rhs.
        self.refcnt = 0

        self.assignDisable = True # easier than making type-dependent decisions in .do()?
        self.funs = False
        self.execExpr = None
        self.execDone = False
        self.assignDone = False
        self.ilshiftDone = False
        # maybe getting to be too much stuff here
        self.execResult = None
        # .result could be a property that triggers a csv download, if we didn't cache the scalar/list result because it was small?
        # i.e. check if .result_cached was None, when .result property is used (property to avoid the need for ()
        self.result = None
        self.scalar = None

        # refcnt looks at this?
        self.function = None
        self.rhs = None
        self.lhs = None

    def __str__(self):
        # this should always be overwritten by the other classes"
        return Xbase.defaultAst

    __repr__ = __str__

    def __getitem__(self, items):
        debugprint("\n%s __getitem__ start" % type(self))
        # If self is anything other a Key, means it was from a pending eval with no Assign.
        # Assign's should always have been .do()'ed, so views of them become Keys?
        # Keys with indexed views, become KeyIndexed.
        # Fcn operations can happen on KeyIndexed or Key.

        # a = b[0] is just a view.
        # b[1] = c[3] is a transformation of a dataframe, since b[1:0] will be different view after.

        # Keys: If there's no name (None) , one will be created based on the instance id
        # KeyIndexeds are always already named.
        if isinstance(self, KeyIndexed):
            raise Exception("Shouldn't be getitem indexing KeyIndexed? %s %s" % (type(self), self))
        elif isinstance(self, Key):
            myKeyIndexed = self.add_indexing(items)
        else:
            # h2o can support indexing on any Fcn or anything? Don't use that capability.
            # Assign.do() that anything so there is a temp key
            # Future? Maybe allow direct indexing of Fcn result, to avoid the temp?
            # h2o probably can't index an indexed thing. (KeyIndexed) or Seq?
            if self is None:
                raise Exception("Can't index something that doesn't exist yet, even on lhs. %s %s" % (type(self), self))
            myKeyIndexed = myKey.add_indexing(items)
        debugprint("%s __getitem__  end\n" % type(self))
        return myKeyIndexed

    # this method is used by both ilshift and Assign class (inherits thru Key class)
    def _do_assign(self, lhs, rhs, parent):
        if lhs.assignDone and parent!="ilshift":
            debugprint("WARNING: %s _do_assign %s lhs already done %s" % (parent, type(self), lhs))
        if self.assignDone and parent!="ilshift":
            debugprint("WARNING: %s _do_assign %s self already done %s" % (parent, type(self), self))
        if isinstance(self, Assign):
            raise Exception("%s _do_assign %s is already Assign..shouldn't happen? %s" % (parent, type(self), self))

        debugprint("%s _do_assign Assign.do() %s" % (parent, type(self)))
        # .do() sets assignDone
        new = Assign(lhs=lhs, rhs=rhs) # it does it's own .do()
        # should set assignDone inside, but doesn't matter
        lhs.assignDone = True
        self.assignDone = True
        new.assignDone = True
        debugprint("%s _do_assign done" % parent)

        return Key(key=lhs.frame)

    # this allows a Key to be used to slice another Key?
    def __index__(self):
        return self

    # http://www.siafoo.net/article/57
    # def __len__(self)
    # def __contains__(self, item)
    # def __iter__(self)
    # def __reversed__(self)

    # Only Keys are flexible enough to be index'ed
    def __setitem__(self, items, rhs):
        # Don't try to do it twice, if ilshift was involved
        if 1==0: # should never happen (only if '=' was being used)
            if isinstance(self, (Key, KeyIndexed)) and not (self.assignDone or self.ilshiftDone):
                debugprint("%s __setitem__ completing %s %s" % (type(self), self, rhs))
                lhs = self.add_indexing(items)
                self._do_assign(lhs, rhs, '__setitem__')

        debugprint("%s __setitem__  end" % type(self))
        # no return?
        return None

    # A trick for getting an assign overload (<<=) with .do()
    # Don't want the .do() if you're in a user function though?
    # Need to be able to do KeyIndexed and Key here?
    def __ilshift__(self, rhs):
        if isinstance(self, (Key, KeyIndexed)):
            lhs = self
        else:
            debugprint('WARNING: lhs for <<= needs to be Key/KeyIndexed %s %s' % (type(self), self))
            debugprint("coercing lhs to Key")
            lhs = Key() # anonymous

        a = self._do_assign(lhs, rhs, 'ilshift')
        debugprint("ilshift _do_assign %s %s" % (lhs, rhs))
        # belt and suspenders? may not be all needed
        self.ilshiftDone = True
        self.assignDone = True
        a.ilshiftDone = True
        a.assignDone = True
        return a


    def _unary_common(self, funstr):
        # funstr is the h2o function string..this function is just fot standard binary ops?
        # FIX! add row/col len checks against Key objects
        if not isinstance(self, (Key, KeyIndexed, Fcn, Item)):
            raise TypeError('h2o_xl unsupported operand type(s) for %s: %s' % (funstr, type(self)))
        return Fcn(funstr, self)

    def _binary_common(self, funstr, right):
        # funstr is the h2o function string..this function is just fot standard binary ops?
        # FIX! add row/col len checks against Key objects
        if isinstance(right, (int, list, tuple)):
            return Fcn(funstr, self, Item(right))
        elif isinstance(right, (float)):
            raise TypeError('Rapids unsupported operand type(s) for %s: %s and %s' % \
                (funstr, type(self), type(right)))
        elif isinstance(right, (Key, KeyIndexed, Fcn, Item)):
            return Fcn(funstr, self, right)
        else:
            raise TypeError('h2o_xl unsupported operand type(s) for %s: %s and %s' % \
                (funstr, type(self), type(right)))

    def __add__(self, right):
        return self._binary_common('+', right)
    def __radd_(self, left):
        return self.__add__(left)  # or self + left

    def __sub__(self, right):
        return self._binary_common('-', right)
    def __rsub_(self, left):
        return self.__sub__(left)

    def __mul__(self, right):
        return self._binary_common('*', right)
    def __rmul_(self, left):
        return self.__mul__(left)

    def __div__(self, right):
        return self._binary_common('/', right)
    def __rdiv_(self, left):
        return self.__div__(left)

    def __mod__(self, right):
        return self._binary_common('%', right)
    def __rmod_(self, left):
        return self.__mod__(left)

    def __pow__(self, right):
        return self._binary_common('**', right)
    def __rpow_(self, left):
        return self.__pow__(left)

    def __and__(self, right):
        return self._binary_common('&', right)
    def __rand_(self, left):
        return self.__and__(left)

    def __or__(self, right):
        return self._binary_common('|', right)
    def __ror_(self, left):
        return self.__or__(left)

    def __xor__(self, right):
        return self._binary_common('^', right)
    def __rxor_(self, left):
        return self.__xor__(left)

    # don't use __cmp__ ?

    # http://www.python-course.eu/python3_magic_methods.php
    # none of the extended assigns (since we overload <<=)
    # // is __floordiv (does h2o do that)
    # << and >> are lshift and rshift

    # unary
    # -, +, abs, ~, int, float
    def __invert__(self):
        # FIX! this isn't right if more than one col?
        return self._binary_common('^', 1)
    def __neg__(self):
        return self._unary_common('_') # use special Rapids negation function
    def __pos__(self): # pos is a no-op? just return self?
        return self
    def __abs__(self):
        return self._unary_common('abs')
    def __int__(self):
        return self._unary_common('trunc')

    # does h2o allow conversion to reals? ints to reals?  does it matter *because of compression*
    # (what if enums or strings)
    # FIX! for now, just leave it as is
    def __float__(self):
        print "WARNING: not converting your h2o data to float %s %s" % (type(self), self)
        return self

    # complex/long/oct/hex not supported

    # this all work inside h2o
    # FIX! how does a condition on a h2o operation, get used with a python if?
    # these result in function objects, not booleans
    # maybe h2o_to_boolean(...) expects as boolean h2o function
    # or a generic h2o_to_local(..) just gets whatever the response is?
    # if it's a column, it's a list. If it's a value, it can be used as a boolean
    # maybe LocalAssign()
    # I suppose LocalAssign() could be deduced, by seeing if a Assign() target is Key or not (we do allow string?)
    # what if the Assign target was none? If Assigns always go to a key, then is it as simple as viewing the key
    # can't know always, when to Get() to local
    # a = Get(b)
    # Could do Put, but Assign takes local objects okay? (will it know when to download csv)
    # Put(key, a)
    # or key <<= Put(a)

    # these are used for local h2o compare expressions..i.e when they operate on Keys
    def __lt__(self, right):
        return self._binary_common('<', right)
    def __rlt_(self, left):
        return self.__lt__(left)

    def __le__(self, right):
        return self._binary_common('<=', right)
    def __rle_(self, left):
        return self.__le__(left)

    def __gt__(self, right):
        return self._binary_common('<', right)
    def __rgt_(self, left):
        return self.__gt__(left)

    def __ge__(self, right):
        return self._binary_common('<=', right)
    def __rge_(self, left):
        return self.__ge__(left)

    def __eq__(self, right):
        # raise Exception("__eq__ What is doing this? %s %s %s" % (type(self), self, right))
        print "__eq__", self, right
        # return self._binary_common('==', Item(right))
        # return NotImplemented
        # if result is NotImplemented:

    def __ne__(self, right):
        # raise Exception("__ne__ What is doing this? %s %s %s" % (type(self), self, right))
        return self._binary_common('!=', Item(right))

    # not everything will "do" at h2o correctly? Should just be Assign/Expr/Def. maybe Key/KeyIndexed.
    # get=True will get the actual result. Easy if it's scalar. Will have to inspect the key to get a col result/
    # if it has more than one col, unsupported for now.
    def do(self, timeoutSecs=30):
        print "enter .do()"
        if not isinstance(self, (Assign, Expr, Def, Key, KeyInit, KeyIndexed, Item, Fcn, If, IfElse, Return)):
            raise Exception(".do() Maybe you're trying to send a wrong instance to h2o? %s %s" % \
                (type(self), self))

        # this can only happen if we already Exec'ed it? that's not legal. Just exception for now..means bug?
        if self.execExpr:
            raise Exception(".do() Appears already .do()'ed this?\n" +\
                "You may need 'do=False' param on an Assign to avoid the default do=True\n" +\
                "That causes .do() on all Assign inits.\n" +\
                "Also, if you're using function objects multiple times, you should \n" +\
                "use copy.copy() to copy them to fresh instances, because of internal state in the objects (mutables)..\n" +\
                "Maybe I'll change this sometime \n    type self: %s \n    self.execExpr: %s" % (type(self), self.execExpr))

        # a little belt and suspenders
        if self.execDone:
            debugprint("%s .do() already done:" (type (self, self.execExpr)))
            return

        self.execResult = None
        self.result = None

        if self.funs:
            execExpr1 = "[%s]" % self
        else:
            execExpr1 = "%s" % self
        # self.check_do_against_ast()

        h2p.green_print("%s .do() ast: %s" % (type(self), execExpr1))
        if not debugNoH2O:
            # functions can be multiple statements in Rapids, need []
            execResult1, result1 = h2e.exec_expr(execExpr=execExpr1, doFuns=self.funs, timeoutSecs=timeoutSecs)
            # look at our secret stash in the base class
            if execResult1['key'] is not None:
                Xbase.keyWriteHistoryList.append(execExpr1)

            # remember the num_rows/num_cols (maybe update the saved col names
            # this could update to None for scalar/string
            # (temporary till we assign to key next in those cases)
            self.numRows = execResult1['num_rows']
            self.numCols = execResult1['num_cols']
            self.scalar = execResult1['scalar']

            # Deal with h2o weirdness.
            # If it gave a scalar result and didn't create a key, put that scalar in the key
            # with another assign. Why should I deal with another type that "depends" if the key existed already?
            if self.funs or isinstance(self, (If, IfElse, Return)):
                returnResult = None

            elif execResult1['key'] is None:
                debugprint("Hacking scalar result %s into a key. %s" % (self.scalar, self.frame))
                if self.scalar is None: # this shouldn't happen? h2o should be giving a scalar result?
                    debugprint("WARNING: %.do() is creating a one-row/oneCol result key, for %s" % (type(self)))
                    assert self.numRows==0 and self.numCols==0, "%s %s" % (self.numRows, self.numCols)
                    # make it match what we're doing
                    execExpr2 = astForInit(self.frame)
                else:
                    print "Hack scalar to int for new key for scalar, because rapids doesn't take reals yet"
                    # doesn't like 0.0?
                    # we always want a key for the result, regardless of what h2o does.
                    # what if self.scalar is NaN
                    if math.isnan(float(self.scalar)):
                        print "Rapids returned scalar result that's NaN. Using -1 instead: %s" % self.scalar
                        execExpr2 = astForInit(self.frame)
                    else:
                        execExpr2 = "(= !%s (c {#%s}))" % (self.frame, int(self.scalar))
                

                self.numRows = 1
                self.numCols = 1

                execResult2, result2 = h2e.exec_expr(execExpr=execExpr2)
                # NEW: don't care if the key is null here. the lhs key is always created. We can inspect it if we know the name
                # assert execResult2['key'] is not None, dump_json(execResult2)
                # assert self.numRows==execResult2['num_rows'], "%s %s" % (self.numRows, execResult2['num_rows'])
                # assert self.numCols==execResult2['num_cols'], "%s %s" % (self.numCols, execResult2['num_cols'])

                Xbase.keyWriteHistoryList.append(execExpr2)
                returnResult = self.scalar

            elif self.numCols==1:
                if self.numRows<=1024:
                    co = h2o_cmd.runSummary(key=self.frame, column=0, noPrint=True)
                    # data json
                    returnResult = co.data
                else:
                    raise Exception("Expr-caused Assign.do() wants to return a key with num_rows>1024\n" + \
                        "Did you really mean it?. frame: %s numRows: %s numCols %s" %\
                         (self.frame, self.numRows, self.numCols))
            else:
                if self.numCols==0 and self.numRows==0:
                    return None # both assignDisable or not
                elif self.assignDisable: # Expr modifies Assign with this
                    if self.numCols>1:
                        h2p.red_print("Expr-caused Assign.do()  wants to return a key with num_cols>1\n" + \
                            "not supported. frame: %s numRows: %s numCols %s" % \
                            (self.frame, self.numRows, self.numCols))
                    # return a nice clean Key that points to the frame
                    returnResult = Key(key=self.frame)
                else:
                    returnResult = Key(key=self.frame)

        if debugNoH2O:
            execResult1 = {'debug': True}
            returnResult = None

        self.execResult = execResult1
        self.result = returnResult

        self.execExpr = execExpr1
        Xbase.lastExecResult = copy(execResult1)
        Xbase.lastResult = copy(returnResult)

        self.execDone = True
        if isinstance(self, Assign):
            self.assignDone = True

        return

    # from http://stackoverflow.com/questions/1500718/what-is-the-right-way-to-override-the-copy-deepcopy-operations-on-an-object-in-p
    def __copy__(self):
        # I could use copy()?
        from copy import copy
        # just copy top level? so lists aren't copied? Need to think if I keep track of key history or ?? in lists
        cls = self.__class__
        result = cls.__new__(cls)
        result.__dict__.update(self.__dict__)
        return result

    def __deepcopy__(self, memo):
        # I could use deepcopy()?
        # here __deepcopy__ fills in the memo dict to avoid excess copying
        # in case the object itself is referenced from its member.
        from copy import deepcopy
        cls = self.__class__
        result = cls.__new__(cls)
        memo[id(self)] = result
        for k, v in self.__dict__.items():
            setattr(result, k, deepcopy(v, memo))
        return result

    # __call__ = __init__
    # __call__ = do

#********************************************************************************
def translateValue(item="F"):
    # translate any common text abbreviations to the required long form
    # T and F can be translated? we shouldn't get key names when this is used?
    translate = {
        'T': '%TRUE',
        'F': '%FALSE',
        'TRUE': '%TRUE',
        'FALSE': '%FALSE',
        'True': '%TRUE',
        'False': '%FALSE',
        'true': '%TRUE',
        'false': '%FALSE',
        '"NULL"': '"null"',
        'NULL': '"null"',
        'null': '"null"',
    }
    if item is None:
        return '"null"'
    elif item is True:
        return '%TRUE'
    elif item is False:
        return '%FALSE'
    elif isinstance(item, basestring) and  item in translate:
        return translate[item]
    else:
        return item


#********************************************************************************
class Item(Xbase):
    def __init__(self, item, listOk=False):

        # self.funs is not resolved until a string resolution?
        # tolerate a list for item? Assume it's a list of things Seq can handle
        if isinstance(item, (list, tuple)):
            if not listOk:
                raise Exception("Item doesn't take lists, tuples (or dicts) %s, unless listOk. %s" % (item, listOk))
            else:
                if len(item) > 1024:
                    raise Exception("Key is trying to index a h2o frame with a really long list (>1024)" +
                        "Probably don't want that? %s" % item)
                # Seq and Col don't need to inc refcnt, since they can never be root
                self.item = Col(Seq(item)) # Seq can take a list or tuple
        else:
            self.item = item


    def __str__(self):
        item = self.item
        # debugprint("Item:", item)
        # xItem can't be used for lhs
        if isinstance(item, (list, tuple, dict)):
            raise Exception("Item doesn't take lists, tuples (or dicts) %s" % item)

        item = translateValue(item)

        # if string and has comma, -> exception
        # space can arise from prior expansion
        itemStr = str(item)
        if re.search(r"[,]", itemStr):
            raise Exception("Item has comma. Bad. %s" % item)
        elif len(itemStr)==0:
            # Colon can return length 0 thing?..no longer
            # return itemStr
            raise Exception("Item is len 0 %s" % item)

        # if string & starts with #, strip and check it's a number. Done if so. Else Exception
        start = itemStr[0]
        if start=="!":
            raise Exception("Item starts with !. Only for lhs (Assign*). Bad. %s" % item)
        elif start=="#":
            if itemStr=="#":
                raise Exception("Item is just #. Bad. %s" % item)
            # can be a number, or the start of a string with a number at the beginning
        # elif string & starts with %, Done. Else if next char is a-zA-Z, done. Else Exception
        elif start=="%":
            if itemStr=="%":
                raise Exception("Item is just %. Bad. %s" % item)
            # can be a ref , or the start of a string with a ref at the beginning
        # elif number, add #
        else:
            if isinstance(item, (int, float)):
                # number!
                itemStr = "#%s" % item # good number!
            else: # not number
                # if it's just [a-zA-Z0-9_], tack on the % for probable initial key reference
                itemStr = "%s" % item
                if re.match(r"[a-zA-Z0-9_]+$", itemStr):
                    itemStr = "%{}".format(item)

        return itemStr

    def __getitem__(self, items):
        raise Exception("trying to __getitem__ index an Item? doesn't make sense? %s %s" % (self, items))

    def __setitem__(self, items, rhs):
        raise Exception("trying to __setitem__ index an Item? doesn't make sense? %s %s" % (self, items))

    __repr__ = __str__


#********************************************************************************

xFcnXlate = { '>':'g', '>=':'G', '<':'l', '<=':'L', '==':'n', '!=':'N', '!':'not', '~': 'not', '_': 'not'}

# FIX! should we use weakref Dicts, to avoid inhibiting garbage collection by having it
# in a list here?
xFcnUser = set()

xFcnOpBinSet = set([
'&&', '||', '+', '-', '*', '/', '**', '%', '&', '|',
'not', 'plus', 'sub', 'mul', 'div', 'pow', 'pow2', 'mod',
'and', 'or', 'lt', 'le', 'gt', 'ge', 'eq', 'ne',
'la', 'lo', 'g', 'G', 'l', 'L', 'n', 'N',
])

#'canbecoercedtological',
xFcnOp1Set = set([
'c', '_', 'not',
'is.na', 'is.factor', 'any.factor', 'any.na',
'nrow', 'ncol', 'length',
'abs', 'sign', 'sqrt', 'ceiling', 'floor', 'log', 'exp', 'scale', 'factor',
'cos', 'sin', 'tan', 'acos', 'asin', 'atan', 'cosh', 'sinh', 'tanh',
'min', 'max', 'sum', 'sd', 'mean', 'match', 'unique', 'xorsum',
'ls',
])

# 'rename',
xFcnOp2Set = set()

xFcnOp3Set = set([
'cut', 'round', 'signif', 'trun', 'quantile', 'runif',
'cbind', 'rbind',
'ifelse',
'apply', 'sapply', 'ddply',
'seq', 'seq_len', 'rep_len',
'reduce', 'table',
'var',
])

#********************************************************************************
# operands is a list of items or an item. Each item can be number, string, list or tuple
# there is only one level of unpacking lists or tuples
# returns operandString, operandList
# this doesn't unpack a dict
def unpackOperands(operands, parent=None, toItem=True):
    def addItem(opr):
        if toItem:
            operandList.append(Item(opr))
        else:
            # just keep whatever type it is (params should be string?, maybe list)
            operandList.append(opr)
    if operands is None:
        raise Exception("%s unpackOperands no operands: %s" % (parent, operands))

    operandList = []
    if isinstance(operands, (list, tuple)):
        for opr in operands:
            # can we handle any operand being a list here too? might be compact
            # just one level of extra unpacking
            if not isinstance(opr, (list,tuple)):
                addItem(opr)
            else:
                # collapse it into the one new list
                for opr2 in opr:
                    # if isinstance(operands, (list, tuple)):
                    #    raise Exception("%s unpackOperands, can't have lists within lists, %s", (parent, opr2))
                    # this can be a list?? Seq seems to like a list. maybe just a list of number or string
                    addItem(opr2)
    else:
        addItem(operands)

    if operandList is None or len(operandList)==0:
        raise Exception("%s unpackOperands operandList is None or empty: %s" % (parent, operandList))

    if parent:
        debugprint("%s: %s" % (parent, map(str,operandList)))

    # always returns a list, even if one thing
    return operandList

#********************************************************************************
# check whether a frame string (h2o name) is legal)
def legalKey(frame, parent):
    frameStr = str(frame)
    if 1==0:
        if re.match('\%', frameStr):
            raise Exception("%s: frame shouldn't start with '%' %s" % (parent, frameStr))
        if re.match('c$', frameStr):
            raise Exception("%s: frame can't be 'c' %s" % (parent, frameStr))
        if not re.match('[\a-zA-Z0-9_]', frameStr):
            raise Exception("%s: Don't like the chars in your frame %s" % (parent, frameStr))
    debugprint("legalKey %s frame: %s" % (parent, frameStr))
    return True

#********************************************************************************
# operands is a list of items
class Seq(Xbase):
    def __init__(self, *operands):
        operandList = unpackOperands(operands, parent="Seq operands")
        self.operandList = operandList
        # FIX! should we do more type checking on operands?

    def __str__(self):
        oprString = ";".join(map(str, self.operandList))
        return "{%s}" % oprString

    __repr__ = __str__

    def __getitem__(self, items):
        raise Exception("trying to __getitem__ index a Seq? doesn't make sense? %s %s" % (self, items))
    def __setitem__(self, items, rhs):
        raise Exception("trying to __setitem__ index a Seq? doesn't make sense? %s %s" % (self, items))

#********************************************************************************
# a/b can be number or string
class Colon(Xbase):
    def __init__(self, a='#0', b='#0'):
        # if it's not a string, turn the assumed number into a number string
        self.a = Item(a)
        self.b = Item(b)
        # FIX! should we do more type checking on operands?


    def __str__(self):
        # no colon if both None
        if str(self.a) in  ['"null"', None] and str(self.b) in ['"null"', None]:
            return '"null"'
        else:
            return '(: %s %s)' % (self.a, self.b)

    def __getitem__(self, items):
        raise Exception("trying to __getitem__ index a Colon? doesn't make sense? %s %s" % (self, items))
    def __setitem__(self, items, rhs):
        raise Exception("trying to __setitem__ index a Colon? doesn't make sense? %s %s" % (self, items))

#********************************************************************************
# key is a string
# change to init from Xbase not KeyIndexed
# a Key is the nebulous thing, that can get locked down into a KeyIndexed by indexing..
# a KeyIndexed can't be re-indexed, or turned back into a Key,
# until it's executed by rapids (so the Key can be used again)
# Note we don't actually init a key in h2o with this class. User uses DF() for that if desired.
class Key(Xbase):
    def __init__(self, key=None):
        if key is None:
            # no h2o name? give it one that's unique for the instance
            key = "knon_" + hex(id(self))
            debugprint("Key creating h2o key name for the instance, none provided: %s" % key)

        # to date, have been passing strings
        # all the __getitem__ stuff in Key should modify a Key?
        # same with Assign here, since we inherit from Key?
        if isinstance(key, (Key, KeyIndexed)):
            # FIX! what if the lhs frame has row/col? need to included it?
            frame = key.frame
        elif isinstance(key, basestring):
            frame = key
        else:
            raise Exception("Key: key not string/Key/Assign/KeyIndexed %s %s" % (type(key), key))

        super(Key, self).__init__()

        # can have row/col?
        legalKey(frame, "Key")
        self.frame = frame

        # add to list of created h2o keys (for deletion later?)
        # FIX! should make this a weak dictionary reference? don't want to affect python GC?
        # xKeyIndexedList.append(frame)

    def __str__(self):
        frame = self.frame
        if not re.match('\%', frame):
            frame = "%{}".format(self.frame)
        return '%s' % frame

    __repr__ = __str__

    # slicing
    # this is used on lhs and rhs? we never use a[0] = ... Just a[0] <== ...
    def add_indexing(self, items):
        def indexer(item):
            debugprint('Key item %-15s  %s' % (type(item), item))

            if type(item) is int:
                debugprint("Key item int", item)
                return Item(item)

            elif isinstance(item, Seq):
                debugprint("Key item Seq", Seq)
                return item

            elif isinstance(item, Colon):
                debugprint("Key item Colon", Colon)
                return item

            elif isinstance(item, Fcn):
                debugprint("Key item Fcn", Fcn)
                return item

            # what if the indexer is a list/tuple, string, or Key?
            # well, use Seq to handle a list (hopefully not too big? check if > 1024)
            elif isinstance(item, (list, tuple)):
                if len(item) > 1024:
                    raise Exception("Key is trying to index a h2o frame with a really long list (>1024)" +
                        "Probably don't want that? %s" % item)
                return Seq(item) # Seq can take a list or tuple

            elif isinstance(item, basestring):
                raise Exception("Key is trying to index a h2o frame with a string? %s" % item)

            elif isinstance(item, dict):
                raise Exception("Key is trying to index a h2o frame with a dict? %s" % item)

            elif isinstance( item, slice):
                # debugprint("Key item start", str(item.start))
                # debugprint("Key item stop", str(item.stop))
                # debugprint("Key item step", str(item.step))
                # assume step is always None..
                assert item.step==None, "Key assuming step should be None %s" % item.step
                return Colon(item.start, item.stop)

            else:
                raise Exception("Key.add_indexing item(%s) must be int/Seq/Colon/list/tuple/slice" % item)

        if isinstance(items, (list, tuple)):
            itemsList = list(items)
            # if there's a list, it better be just one or two dimensions
            # if length 0, ignore
            # one is row, two is row/col
            if len(itemsList)==0:
                debugprint("Key ignoring length 0 items list/tuple) %s" % itemsList)

            elif len(itemsList)==1:
                # we return another python object, which inherits the h2o key name
                return(KeyIndexed(
                    frame=self.frame,
                    row=indexer(itemsList[0])
                ))

            elif len(itemsList)==2:
                return(KeyIndexed(
                    frame=self.frame,
                    row=indexer(itemsList[0]),
                    col=indexer(itemsList[1])
                ))

            else:
                raise Exception("Key itemsList is >2 %s" % itemsList)
        else:
            return(KeyIndexed(
                frame=self.frame,
                row=indexer(items),
                dim=1, # one dimensional if using the single style?
            ))

        # FIX! should return an instance of the key with the updated row/col values
        return self

    # __call__ = __str__

#*******************************************************************************
# maybe do some reading here
# http://python-3-patterns-idioms-test.readthedocs.org/en/latest/Factory.html
# row/col can be numbers or strings or not specified
# Key can do indexing/slicing. KeyIndexed is fixed at row/col
class KeyIndexed(Key):
    # row 0/col0 should always exist if we init keys to 0?
    def __init__(self, frame=None, row=0, col=0, dim=2):

        super(KeyIndexed, self).__init__()

        # can have row/col?
        legalKey(frame, "KeyIndexed")
        self.frame = frame
        # if it's not a string, turn the assumed number into a number string
        self.row = Item(row)
        self.col = Item(col)
        self.dim = dim # dimensions

        # how to decide whether to send 2d or 1d references to h2o
        # is there no such thing as a row vector, only a column vector (or data frame)

        # row and col can be Seq, Colon, Item (could return a Cbind?)
        # or should it pass the python construct to h2o?
        # it could put the list into a h2o key, and then do a[b] in hto?
        # row extracts problematic?

    def __str__(self):
        frame = self.frame
        row = self.row
        col = self.col
        # these could be slice objects, strings, ints
        # row and col == None is okay too?
        if row is not None:
            assert isinstance(row, (Seq, Colon, Item)), "KeyIndexed unexpected row type. %s %s" % (type(row), row)
        if col is not None:
            assert isinstance(col, (Seq, Colon, Item)), "KeyIndexed unexpected col type. %s %s" % (type(col), col)
        # 'row in' will use __eq__ method

        if row is None:
            row = '"null"'
        if col is None:
            col = '"null"'

        # detect the case where row/col say "everything"
        # have to use str() because they could be objects and don't want to use __eq__ (which is used for ast resolution?)
        if  str(row)=='"null"' and str(col)=='"null"':
            return "%{}".format(frame)

        # does it already start with '%' ?
        # we always add % to a here?. Suppose could detect whether it's already there
        # does it already start with '%' ?
        if not re.match('\%', frame):
            frame = "%{}".format(self.frame)

        # is a 1 dimensional frame all rows (1 col?)
        if self.dim==1:
            return '([ %s %s %s)' % (frame, row, '#0')
        else:
            return '([ %s %s %s)' % (frame, row, col)

    __repr__ = __str__

# slicing/indexing magic methods
# http://www.siafoo.net/article/57

#********************************************************************************
# like Assign with constant rhs, but doesn't inherit from Key or KeyIndexed
# no indexing is allowed on key..it's just the whole key that get's initted, not some of it
# KeyInit() should only be used by Key() with a .do() ...so it executes

# GENIUS or INSANITY: it's good to have the init to have zero rows, to see what blows up
# create a zero row result with a row slice that is never true.
class KeyInit(Xbase):
    def __init__(self, frame):
        super(KeyInit, self).__init__()
        # guaranteed to be string
        assert isinstance(frame, basestring)
        self.frame = frame

    def __str__(self):
        # This should give zero row key result. Does that result in Scalar?
        # return "(= !%s %s)" % (self.frame, '(is.na (c {#0}))' )
        return astForInit(self.frame)

    # this shouldn't be used with any of the setiem/getitem type stuff..add stuff to make that illegal?
    # or any operators?

#********************************************************************************
# Users uses this? it adds an init
class DF(Key):
    def __init__(self, key=None, existing=False):
        super(DF, self).__init__(key)
        if not existing:
            # actually make the key in h2o with 0 rows
            KeyInit(self.frame).do()
        # if you don't init it, it assumes the name can be use for indexed write, or normal write
        # normal writes always work, even if it really wasn't existing.

    def __str__(self):
        frame = self.frame
        # no % prefix
        return '%s' % frame

#********************************************************************************

def legalFunction(function):
    # return required operands
    if function in xFcnOp1Set: return 1
    if function in xFcnOp2Set: return 2
    if function in xFcnOp3Set: return 3
    if function in xFcnOpBinSet: return 2
    if function in xFcnUser: return 3
    else: return 0

# function is a string. operands is a list of items
class Fcn(Xbase):

    # Attach an Assign to all root Fcn's
    # And put it on the pending Assign list, which is flushed at appropriate times.
    # figure out if this is a root function. Only the root function can create an Assign, which accomplishes a .do()
    def __init__(self, function='sum', *operands):
        super(Fcn, self).__init__()
        operandList = unpackOperands(operands, parent="Fcn operands")

        # no checking for correct number of params
        debugprint("Fcn %s has %s operands" % (function, len(operands)))
        # see if we should translate the function name
        if function in xFcnXlate:
            function = xFcnXlate[function]

        required = legalFunction(function)
        if required==0:
            print "Fcn legalFunction not found...maybe future defined user function?: %s" % function

        # FIX! currently not checking any.
        # only check 1 and 2. not sure of the 3 group. cbind is conditional..need to do that special
        if False and len(operandList)!=required and required<3 and function!='cbind':
            raise Exception("Fcn wrong # of operands: %s %s" % (required, len(operandList)))

        self.operandList = operandList
        self.function = function
        # can I do a str() here before everything has been initted?
        debugprint("Fcn:", str(self))

    def __str__(self):
        return "(%s %s)" % (self.function, " ".join(map(str, self.operandList)))

    __repr__ = __str__

    def __getitem__(self, items):
        raise Exception("trying to __getitem__ index a Seq? doesn't make sense? %s %s" % (self, items))
    def __setitem__(self, items, rhs):
        raise Exception("trying to __setitem__ index a Seq? doesn't make sense? %s %s" % (self, items))


class Return(Xbase):
    # return only has one expression?
    def __init__(self, expr):
        super(Return, self).__init__()
        self.expr = Item(expr)

    def __str__(self):
        return "%s" % self.expr

    __repr__ = __str__

    def __getitem__(self, items):
        raise Exception("trying to __getitem__ index a Return? doesn't make sense? %s %s" % (self, items))
    def __setitem__(self, items, rhs):
        raise Exception("trying to __setitem__ index a Return? doesn't make sense? %s %s" % (self, items))

from weakref import WeakSet
# always does a .do() on init
class Assign(Key):

    # want to use weak references for tracking instances.
    # Otherwise the class could likely end up keeping track of instances
    # that were meant to have been deleted.
    # A weakref.WeakSet will automatically remove any dead instances from its set.

    # http://stackoverflow.com/questions/12101958/keep-track-of-instances-in-python
    # 1) Each subclass of ... will keep track of its own instances separately.
    # 2) The instances set uses weak references to the classs instances,
    # so if you del or reassign all the other references to an instance elsewhere in your code,
    # the bookkeeping code will not prevent it from being garbage collected.

    # can put this in the Xbase base class if I want?
    # pass the instances set to list() before printing.
    def __new__(cls, *args, **kwargs):
        instance = Key.__new__(cls, *args, **kwargs)
        if "instances" not in cls.__dict__:
            cls.instances = WeakSet()
        cls.instances.add(instance)
        return instance
        # can create a dict from the list with:
        # foo_vars = {id(instance): instance.foo for instance in Assign.instances}

    @classmethod
    def get_instances(cls):
        # the list should go empty after del ... of the instance
        return list(Assign.instances) #Returns list of all current instances

    def __init__(self, lhs=None, rhs=None, do=True, assignDisable=False, timeoutSecs=30):
        super(Assign, self).__init__(lhs)

        debugprint("Assign enter. lhs %s %s" % (type(lhs), lhs))
        # base init for execResult etc results. Should only need for Assign, Expr, Def ?
        if lhs is None:
            lhs = Key()
            debugprint("Assign: creating Key for lhs: %s" % lhs)
        elif not isinstance(lhs, (Key, KeyIndexed, basestring)):
            raise Exception("Assign: lhs not Key/KeyIndexed/string or None %s %s" % (type(lhs), lhs))


        # can pass strings
        # all the __getitem__ stuff in Key should modify a Key?
        # same with Assign here, since we inherit from Key?
        if isinstance(lhs, basestring):
            frame = lhs
        else: # Key, KeyIndexed
            # FIX! what if the lhs frame has row/col? need to included it?
            frame = lhs.frame

        legalKey(frame, "Assign")
        self.frame = frame
        self.lhs = lhs
        self.assignDisable = assignDisable

        # mangling of lists into h2o column vectors of scalars, is done in Item now
        self.rhs = Item(rhs, listOk=True)

        debugprint("Assign lhs: %s" % self.lhs)
        debugprint("Assign rhs: %s" % self.rhs)

        if do: # param set to false when building functions and don't want auto .do()?
            self.do()
            # some belt and suspenders.
            self.assignDone = True
            if not isinstance(lhs, basestring):
                # maybe can get rid of this down the road.
                lhs.assignDone = True


    # leading % is illegal on lhs
    # could check that it's a legal key name
    # FIX! what about checking rhs references have % for keys.
    def __str__(self):
        if self.assignDisable:
            return "%s" % self.rhs
        else:
            # if there is row/col for lhs, have to resolve here?
            # hack: change the rhs reference '%' to the lhs '!'
            # to be 'more correct', only replace the first character
            # can't assign to immutable string indices
            # this is all side-effect of having the lhs get indexing like the rhs, so treated equally
            # no...self.lhs is type KeyIndexed
            # the % may be in a ([ ...) ..so screw it, just do a translate
            # only if first?
            lhsAssign = re.sub('^\%','',str(self.lhs))

            # only add the ! if Key..once indexed, you don't use !
            # KeyIndexed is also Key.
            if isinstance(self.lhs, (Key, basestring)) and not isinstance(self.lhs, KeyIndexed):
                lhsprefix = '!'
            else:
                lhsprefix = ''
            return "(= %s%s %s)" % (lhsprefix, lhsAssign, self.rhs)

# same as Assign, just have do=False default
# can never set do=True
class AssignObj(Assign):
    def __init__(self, *args, **kwargs):
        super(AssignObj, self).__init__(*args, do=False, **kwargs)

# can only do one expression/statement per ast.
# Not currently used
# NEW: rhsOnly means, when we evaluate, that even thought we travelled to .do() land with a lhs (not previously
# existing anon_ Key)...it's not used, and never created,
# and the result that's returned from the .do() will either be None (if a key was created by Rapids because result wasn't
# scalar, or the scalar result. I suppose Rapids could do the expression with the temp key and return that, and then delete
# the key? (since python doesn't have a name pointing to it, it can't be used. But then some big temps might be created that
# we don't want? We'll just pass along a assignDisable here that the .do() can decide how to use, when doing assign
class Expr(Assign):
    def __init__(self, expr, timeoutSecs=30):
        # just be like Assign with no lhs?
        # create an anonymous key name. Only eval it to a key if we have to?
        # i.e. if we can't build up a new expression due to limitations of h2o support
        # suppose we can wait to create that name until we have to create a key
        super(Expr, self).__init__(lhs=None, rhs=expr, assignDisable=True, timeoutSecs=timeoutSecs)

    def __getitem__(self, items):
        raise Exception("trying to __getitem__ index a Expr? doesn't make sense? %s %s" % (self, items))
    def __setitem__(self, items, rhs):
        raise Exception("trying to __setitem__ index a Expr? doesn't make sense? %s %s" % (self, items))


# args should be individual strings, not lists
# FIX! take any number of params
class Def(Xbase):
    def __init__(self, function, params, *exprs):
        super(Def, self).__init__()
        # params and exprs can be lists or string
        # expand lists/tupcles

        # add to the list of legal user functions
        xFcnUser.add(function)

        paramList = unpackOperands(params, parent="Def params", toItem=False)

        # check that all the parms are legal strings (variable names
        for p in paramList:
            if not re.match(r"[a-zA-Z0-9_]+$", str(p)):
                raise Exception("Def, bad name for parameter: %s" % p)

        exprList = unpackOperands(exprs, parent="Def exprs")

        # legal function name (I overconstrain compared to what Rapids allows)
        if not re.match(r"[a-zA-Z0-9_]+$", function):
            raise Exception("Def, bad name for function: %s" % function)

        self.funs = True
        self.function = function
        self.paramList = paramList
        self.exprList = exprList

        debugprint("xFcnUser", xFcnUser)

    def __str__(self):
        # could check that it's a legal key name
        # FIX! what about checking rhs references have % for keys.
        paramStr = " ".join(map(str, self.paramList))
        exprStr = ";;".join(map(str, self.exprList))
        return "(def %s {%s} %s;;;)" % (self.function, paramStr, exprStr)

    __repr__ = __str__

class If(Xbase):
    def __init__(self, clause, *exprs): 
        super(If, self).__init__()
        # clause can't be a list
        # exprs can be lists or string
        # expand lists/tuples
        if isinstance(clause, (list, tuple)):
            raise Exception("If, clause shouldn't be list/tuple: %s" % exprs)
        if clause is None:
            raise Exception("If, clause shouldn't be None: %s" % clause)

        # else pairing to if is checked inside here
        # but what about pairing to this else? done at higher level
        exprList = unpackOperands(exprs, parent="If exprs")
        self.clause = Item(clause)
        self.exprList = exprList

    def __str__(self):
        exprStr = ";;".join(map(str, self.exprList))
        if len(self.exprList)>1:
            exprStr += ";;;"
        return "(if (%s) %s)" % (self.clause, exprStr)

    __repr__ = __str__


# can only text Expr or a Expr List for ifExpr/ElseExpr
class IfElse(Xbase):
    def __init__(self, clause, ifExpr, elseExpr): 
        super(IfElse, self).__init__()

        ifExprList = unpackOperands(ifExpr, parent="IfElse ifExprs")
        elseExprList = unpackOperands(elseExpr, parent="IfElse elseExprs")
        self.clause = Item(clause)
        self.ifExprList = ifExprList
        self.elseExprList = elseExprList

    def __str__(self):
        ifExprStr = ";;".join(map(str, self.ifExprList))
        if len(self.ifExprList)>1:
            ifExprStr += ";;;"

        elseExprStr = ";;".join(map(str, self.elseExprList))
        if len(self.elseExprList)>1:
            elseExprStr += ";;;"

        return "(if (%s) %s) (else %s)" % (self.clause, ifExprStr, elseExprStr)

    __repr__ = __str__


# 'c' can only have one operand? And it has to be a string or number
# have this because it's so common as a function
class Col(Fcn):
    def __init__(self, operand):
        super(Col, self).__init__("c", operand)

class Cbind(Fcn):
    def __init__(self, *operands):
        super(Cbind, self).__init__("cbind", *operands)

class Cut(Fcn):
    def __init__(self,
        vector=None, breaks='#2', labels='%FALSE', include_lowest='%FALSE', right='%FALSE', dig_lab='#0'):

        vector = Item(vector)
        breaks = Item(breaks) # can be a Seq or a number?
        labels = translateValue(labels) # string? "(a,b]" or FALSE
        include_lowest = translateValue(include_lowest) # boolean
        right = translateValue(right) # boolean
        lab = Item(dig_lab) # integer
        super(Cut, self).__init__("cut", vector, breaks, labels, include_lowest, right, dig_lab)

    # cut(a, breaks = c(min(a), mean(a), max(a)), labels = c("a", "b"))
    # (cut $a {4.3;5.84333333333333;7.9} {"a";"b"} %FALSE %TRUE #3))

    # arg1: single column vector
    # arg2: the cuts to make in the vector
    # arg3: labels for the cuts (always 1 fewer than length of cuts)
    # arg4: include lowest? (default FALSE)
    # arg5: right? (default TRUE)
    # arg6: dig.lab (default 3)

    #        x: a numeric vector which is to be converted to a factor by cutting.
    #   breaks: either a numeric vector of two or more unique cut points or a single number
    #           (greater than or equal to 2) giving the number of intervals into which 'x' is to be cut.
    #   labels: labels for the levels of the resulting category.  By default, labels are constructed
    #           using '"(a,b]"' interval notation.  If 'labels = FALSE', simple integer codes are returned
    #           instead of a factor.
    # include.lowest: logical, indicating if an 'x[i]' equal to the lowest (or highest, for 'right = FALSE')
    #           'breaks' value should be included.
    #    right: logical, indicating if the intervals should be closed on the right (and open on the left) or vice versa.
    #  dig.lab: integer which is used when labels are not given.  It determines the number of digits
    #           used in formatting the break

# if you run this as the main, do debug only mode..so no h2o needed
if __name__ == '__main__':
    debugNoH2O = True
    debugPrintEnable = True
    debugRefPrintEnable = True


# http://eli.thegreenplace.net/2011/05/15/understanding-unboundlocalerror-in-python
# understanding this
#    c <<= a + b
# UnboundLocalError: local variable 'c' referenced before assignment

# section 6.2 "Assignment statements" in the Simple Statements chapter of the language reference:
# Assignment of an object to a single target is recursively defined as follows. If the target is an identifier (name):
# If the name does not occur in a global statement in the current code block: the name is bound to the object in the current local namespace.
# Otherwise: the name is bound to the object in the current global namespace.
#
# section 4.1 "Naming and binding" of the Execution model chapter:
# If a name is bound in a block, it is a local variable of that block.
# When a name is used in a code block, it is resolved using the nearest enclosing scope.
# # If the name refers to a local variable that has not been bound, a UnboundLocalError exception is raised.

# <<= (augmented expression)
# An augmented assignment evaluates the target (which, unlike normal assignment statements, cannot be an unpacking) and the expression list, performs the binary operation specific to the type of assignment on the two operands, and assigns the result to the original target. The target is only evaluated once.
#
# An augmented assignment expression like x += 1 can be rewritten as x = x + 1 to achieve a similar, but not exactly equal effect. In the augmented version, x is only evaluated once. Also, when possible, the actual operation is performed in-place, meaning that rather than creating a new object and assigning that to the target, the old object is modified instead.
#
# With the exception of assigning to tuples and multiple targets in a single statement, the assignment done by augmented assignment statements is handled the same way as normal assignments. Similarly, with the exception of the possible in-place behavior, the binary operation performed by augmented assignment is the same as the normal binary operations.
#
