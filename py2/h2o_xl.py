import h2o_exec as h2e, h2o_print as h2p
import re
# from h2o_xl import Fcn, Seq, Cbind, Colon, Assign, Item, Exec, KeyIndexed, Cut

# local to this stuff. manually set the enable
debugPrintEnable = True
debugNoH2O = False
def debugprint(*args, **kwargs):
    if debugPrintEnable:
        # compatible with print definition
        for x in args: 
            print x,
        for x in kwargs:
            print x,
        print

class Xbase(object):
    # can set this in a test to disable the actual exec, just debugprint()
    lastExecResult = {}
    lastResult = None
    defaultAst = "Empty from Xbase init"
    keyWriteHistoryList = []

    def json(self): # returns a json string. debugprint(s it too.)
        import json
        s = vars(self)
        debugprint(json.dumps(s, indent=4, sort_keys=True))
        return s

    def __init__(self):
        # maybe we should track depth/complexity of everything below too, 
        # and force an eval at a certain complexity
        # track leaves and depth?
        self.assignDone = False
        self.depth = 0
        self.complexity = 0

        # this probably doesn't mean that a Key got created
        self.execDone = False
        self.execResult = None
        self.result = None
        self.execExpr = None
        self.funs = False
        # self.ast = self.defaultAst

        # should we init this to 0? or only after a KeyInit
        # I guess None here means it's not in the h2o k/v store
        # Maybe we'll say this state doesn't exit unless you create a Key at h2o
        # with a .do()
        # self.numRows = None
        # self.numCols = None

    def __str__(self):
        # this should always be overwritten by the other classes"
        return self.defaultAst

    __repr__ = __str__

    def __getitem__(self, items):
        debugprint("%s __getitem__ start" % type(self))
        # If self is anything other a Key, means it was from a pending eval with no Assign.
        # Assign's should always have been .do()'ed, so views of them become Keys?
        # Keys with indexed views, become KeyIndexed. 
        # Fcn operations can happen on KeyIndexed or Key.

        # a = b[0] is just a view. 
        # b[1] = c[3] is a transformation of a dataframe, since b[1:0] will be different view after.

        # Keys: If there's no name (None) , one will be created based on the instance id
        # KeyIndexeds are always already named.
        if isinstance(self, Key):
            myKeyIndexed = self.add_indexing(items)
        else:
            # h2o can support indexing on any Fcn or anything?
            # Assign.do() that anything so there is a temp key
            # Future? Maybe allow direct indexing of Fcn result, to avoid the temp?
            # h2o probably can't index an indexed thing. (KeyIndexed) or Seq?
            if self is None:
                raise Exception("Can't index something that doesn't exist yet,\
                     even on lhs. %s %s" % (type(self)))
            myAssign = Assign(None)
            result = myAssign.do()
            myKey = Key(myAssign.frame)
            myKeyIndexed = myKey.add_indexing(items)
        debugprint("%s __getitem__  end" % type(self))
        return myKeyIndexed

    # Only Keys are flexible enough to be index'ed
    # If you index a frame, assign it to temp (since it has a shape)
    # then reindex into that shape
    # Since it's a new temp don't re-use the name of the src key
    def __setitem__(self, items, rhs):
        # should we just assume this is a no-op? 
        # i.e. we will always have <<= when there is a lhs setitem??
        # I'm having problems with the redundant .do() for <<= and lhs []
        if 1==1:
            debugprint("%s __setitem__ start" % type(self))
            # .do() checks if already done, and noops if so
            # new!..may have already be done by ilshift? but that would turn it into a KeyIndexed

            # anything other than a Key...complete it
            # but for it to be here, it has to be  Key?
            # so this is not required?
            if isinstance(self, Key) and not self.assignDone:
                # this returns a KeyIndexed? (so we can go from Key (anything possible) to 
                # KeyIndexed (not anything possible)
                # HACK if <<= already did the assign, don't redo it because of indexing on the lhs
                # check if self and rhs are the same? it would be a noop
                debugprint("%s __setitem__ completing %s" % (type(self), self))
                fr = self.add_indexing(items)
                Assign(fr, rhs).do()
                fr.assignDone = True

            debugprint("%s __setitem__  end" % type(self))

    # A trick for getting an assign overload (<<=) with .do()
    # Don't want the .do() if you're in a function though?
    # Need to be able to do KeyIndexed and Key here?
    # hacky disable  to avoid the double writes for a[0] <<=
    # the "done" flag inhibits that?
    def __ilshift__(self, b):
        if not isinstance(self, (Key, KeyIndexed)):
            debugprint(('WARNING: lhs for <<= needs to be Key/KeyIndexed %s %s' % (type(self))))
            debugprint("coercing lhs to Key")
            lhs = Key() # anonymous
        else:
            lhs = self

        # type checking is done downstream
        # I suppose lhs needs to be able to take [] stuff also (for set)
        # Only gets the default timeoutSecs?
        # If we index the lhs, it has a row and col and will be KeyIndex
        # That means a [] indexing must have done it, and __setitem__ will be called
        # after this again by python. We should let it redo the framing and .do()
        debugprint("ilshift Assign start")
        # don't do it id you know a lhs __setitem__ is going to happen after the __ilshift__
        # because lhs is a KeyIndexed.
        if lhs.assignDone or isinstance(self, KeyIndexed):
            return lhs
        else:
            Assign(lhs, b).do()
            # can start fresh!
            lhs.assignDone = True
            newForOld = Key(lhs.frame)
            newForOld.assignDone = True
            debugprint("ilshift Assign done")
            return newForOld

    def _unary_common(self, funstr):
        # funstr is the h2o function string..this function is just fot standard binary ops?
        # FIX! add row/col len checks against Key objects
        if not isinstance(self, (Key, KeyIndexed, Fcn)):
            raise TypeError('h2o_xl unsupported operand type(s) for %s: %s' % (funstr, type(self)))
        else:
            raise TypeError('h2o_xl unsupported operand type(s) for %s: %s and %s' % \
                (funstr, type(self)))

    def _binary_common(self, funStr, right):
        # funstr is the h2o function string..this function is just fot standard binary ops?
        # FIX! add row/col len checks against Key objects
        if not isinstance(self, (Key, KeyIndexed, Fcn)):
            raise TypeError('h2o_xl unsupported operand type(s) for %s: %s and %s' % \
                (funstr, type(self), type(right)))
        elif isinstance(right, (int, list, tuple)):
            return Fcn(funStr, self, Item(right))
        elif isinstance(right, (Key, KeyIndexed, Fcn)):
            return Fcn(funStr, self, right)
        elif isinstance(right, (float)):
            raise TypeError('Rapids unsupported operand type(s) for %s: %s and %s' % \
                (funstr, type(self), type(right)))
        else:
            raise TypeError('h2o_xl unsupported operand type(s) for %s: %s and %s' % \
                (funstr, type(self), type(right)))

    def __add__(self, right):
        return self._binary_common('+', right)
    def __radd_(self, left):
        return self.__add__(left)  # or self + left

    def __sub__(self, right):
        return self._binary_common('+', right)
    def __rsub_(self, left):
        return self.__add__(left)

    def __mul__(self, right):
        return self._binary_common('+', right)
    def __rmul_(self, left):
        return self.__add__(left)

    def __div__(self, right):
        return self._binary_common('+', right)
    def __rdiv_(self, left):
        return self.__add__(left) 

    def __mod__(self, right):
        return self._binary_common('%', right)
    def __rmod_(self, left):
        return self.__add__(left)

    def __pow__(self, right):
        return self._binary_common('**', right)
    def __rpow_(self, left):
        return self.__add__(left)

    def __and__(self, right):
        return self._binary_common('&', right)
    def __rand_(self, left):
        return self.__add__(left)

    def __or__(self, right):
        return self._binary_common('|', right)
    def __ror_(self, left):
        return self.__add__(left)

    def __xor__(self, right):
        return self._binary_common('^', right)
    def __rxor_(self, left):
        return self.__add__(left)

    # don't use __cmp__ ?

    # http://www.python-course.eu/python3_magic_methods.php
    # none of the extended assigns (since we overload <<=)
    # // is __floordiv (does h2o do that)
    # << and >> are lshift and rshift
    
    # unary
    # -, +, abs, ~, int, float
    def __neg__(self):
        return _unary_common('_') # use special Rapids negation function
    # pos is a no-op? just return self?
    def __pos__(self):
        return _unary_common('_') # use special Rapids negation function
    def __abs__(self):
        return _unary_common('abs') # use special Rapids negation function
    def __int__(self):
        return _unary_common('trunc') # use special Rapids negation function

    # does h2o allow conversion to reals? ints to reals?  does it matter *because of compression*
    # (what if enums or strings)
    # FIX! for now, just leave it as is
    def __float__(self):
        print "WARNING: not converting your h2o data to float %s %s" % (type(self), self)
        return self

    # complex/long/oct/hex not supported

    def __lt__(self, right):
        return self._binary_common('<', right)
    def __rlt_(self, left):
        return self.__add__(left)

    def __le__(self, right):
        return self._binary_common('<=', right)
    def __rle_(self, left):
        return self.__add__(left)

    def __gt__(self, right):
        return self._binary_common('<', right)
    def __rgt_(self, left):
        return self.__add__(left)

    def __ge__(self, right):
        return self._binary_common('<=', right)
    def __rge_(self, left):
        return self.__add__(left)


    # currently not used..can't create ast at init time
    def check_do_against_ast(self):
        # We could bind to a string at init time, but we're still looking at the instances to decide type
        # so they better not change while we're waiting to eval them at h2o..i.e. no benefit to binding the
        # string early..it might fool us. If we're looking at instance, just look at instances
        # (till you need a string)
        # For debug, we could temporarily compare this, to a ast we generated at __init__ time for the instance?
        # they should be identical (we can remove the check once we think things are solid.
        if self.execExpr!=self.ast:
            raise Exception("late binding to string should get same results as that during __init__?? %s %s" % \
                (self.execExpr, self.ast))

    # not everything will "do" at h2o correctly? Should just be Assign/Expr/Def. maybe Key/KeyIndexed
    def do(self, timeoutSecs=30):
        debugprint("%s .do() start %s" % (type(self), self))
        if not isinstance(self, (Assign, Expr, Def, Key, KeyInit, KeyIndexed, Item, Fcn)):
            raise Exception(".do() Maybe you're trying to send a wrong instance to h2o? %s %s" % \
                (type(self)))

        # this can only happen if we already Exec'ed it? that's not legal. Just exception for now..means bug?
        if self.execExpr:
            raise Exception(".do() Appears we already Exec'ed this? %s %s %s" % (self.execExpr, type(self)))

        if self.funs:
            self.execExpr = "[%s]" % self
        else:
            self.execExpr = "%s" % self
        # self.check_do_against_ast()

        if self.execDone:
            debugprint("%s .do() already done:" (type (self, self.execExpr)))
            return

        self.execResult = None
        self.result = None

        if debugNoH2O: 
            h2p.green_print("%s .do() debug ast: %s" % (type(self), self.execExpr))
            self.execResult =  {'debug': True}
            self.result = None
        else:
            # functions can be multiple in Rapids, need []
            # FIX! is it all right we don't reduce everything to string until here (until the .do)
            # none of the instances will have changed?
            # FIX! in here..if it returns a scalar, should we create a key with the scalar result and point to it?

            # or do we propagate scalar results into expressions? Better?
            self.execResult, self.result = h2e.exec_expr(execExpr=self.execExpr,
                doFuns=self.funs, timeoutSecs=timeoutSecs)

            # look at our secret stash in the base class
            if self.execResult['key'] is not None:
                Xbase.keyWriteHistoryList.append(self.execExpr)

            # if we support indexing by col names
            # remember the num_rows/num_cols (maybe update the saved col names 
            # this could update to None for scalar/string 
            # (temporary till we assign to key next in those cases)
            self.numRows = self.execResult['num_rows']
            self.numCols = self.execResult['num_cols']

            # these two are class variables. shouldn't be racy with multiple instances .do-ing?
            # execResult should always be a dict?
            Xbase.lastExecResult = self.execResult.copy()
            # this is a scalar or string that's a key name or ??
            # FIX! assume it's copied, for now
            Xbase.lastResult = self.result

            # Deal with h2o weirdness. 
            # If it gave a scalar result and didn't create a key, put that scalar in the key
            # with another assign. Why should I deal with another type that "depends" if the key existed already?
            if self.execResult['key'] is None:
                scalar = self.execResult['scalar']
                debugprint("Hacking scalar result %s into a key wth name I told h2o! %s" % (scalar, self.frame))
                # doesn't like 0.0?
                debugprint("FIX! Hacking scalar to int because rapids doesn't like reals?")
                # execExpr = "(= !%s (c {#%s}))" % (self.frame, self.execResult['scalar'])
                
                # FIX! hack to int, because rapids doesn't take reals yet
                if scalar is not None:
                    scalar = int(scalar)
                    execExpr = "(= !%s (c {#%s}))" % (self.frame, scalar)
                else:
                    # rapids hack to get a zero row key
                    debugprint("WARNING: %.do() is creating a zero-row result key, from %s" % (type(self)))
                    execExpr = "(= !%s (is.na (c {#0})))" % self.frame

                # execExpr = "(= !%s (c {#%s}))" % (self.frame, 0.0)
                execResult, result = h2e.exec_expr(execExpr=execExpr)
                if execResult['key'] is not None:
                    Xbase.keyWriteHistoryList.append(self.execExpr)

                # leave execResult/result as-is, from prior exec. set rows/cols again though?
                self.numRows = self.execResult['num_rows']
                self.numCols = self.execResult['num_cols']

        # don't return the full json...can look that up if necessary
        # this this always nothing now, since we always init Key to a real h2o key?
        # hopefully it gets the key name that we can use in an inspect (or does exec_expr 
        # read the key and return value if rows=1 and cols=1? (using min)
        self.execDone = True
        return self.result

    # __call__ = __init__
    # __call__ = do

#********************************************************************************
def translateValue(item="F"):
    # translate any common text abbreviations to the required long form
    # T and F can be translated? we shouldn't get key names when this is used?
    translate = {
        'T': '$TRUE',
        'F': '$FALSE',
        'TRUE': '$TRUE',
        'FALSE': '$FALSE',
        'True': '$TRUE',
        'False': '$FALSE',
        'true': '$TRUE',
        'false': '$FALSE',
        '"NULL"': '"null"',
        'NULL': '"null"',
        'null': '"null"',
    }
    if item is None:
        return '"null"'
    elif item is True:
        return '$TRUE'
    elif item is False:
        return '$FALSE'
    elif isinstance(item, basestring) and  item in translate:
        return translate[item]
    else:
        return item

#********************************************************************************
class Item(Xbase):
    def __init__(self, item):
        self.item = item
        # self.funs is not resolved until a string resolution?
        # self.ast = str(self) # for debug/comparision

    def __str__(self):
        item = self.item
        # debugprint("Item:", item)
        # xItem can't be used for lhs
        # if list or tuple, exception
        if isinstance(item, (list, tuple, dict)):
            raise Exception("item doesn't take lists, tuples (or dicts) %s" % item)

        item = translateValue(item)

        # if string and has comma, -> exception
        # space can arise from prior expansion
        itemStr = str(item)
        if re.search(r"[,]", itemStr):
            raise Exception("item has comma. Bad. %s" % item)
        elif len(itemStr)==0:
            # Colon can return length 0 thing?..no longer
            # return itemStr
            raise Exception("item is len 0 %s" % item)

        # if string & starts with #, strip and check it's a number. Done if so. Else Exception
        start = itemStr[0]
        if start=="!":
            raise Exception("item starts with !. Only for lhs (Assign*). Bad. %s" % item)
        elif start=="#":
            if itemStr=="#":
                raise Exception("item is just #. Bad. %s" % item)
            # can be a number, or the start of a string with a number at the beginning
        # elif string & starts with $, Done. Else if next char is a-zA-Z, done. Else Exception
        elif start=="$":
            if itemStr=="$":
                raise Exception("item is just $. Bad. %s" % item)
            # can be a ref , or the start of a string with a ref at the beginning
        # elif number, add #
        else:
            # print "hello kevin %s %s" % (type(item), item)
            if isinstance(item, (int, float)):
                # number!
                itemStr = "#%s" % item # good number!
            else: # not number
                # if it's just [a-zA-Z0-9_], tack on the $ for probable initial key reference
                itemStr = "%s" % item 
                if re.match(r"[a-zA-Z0-9_]+$", itemStr):
                    itemStr = "$%s" % item

        return itemStr

    def __getitem__(self, items):
        raise Exception("trying to __getitem__ index an Item? doesn't make sense? %s %s" % (self, items))

    def __setitem__(self, items, rhs):
        raise Exception("trying to __setitem__ index an Item? doesn't make sense? %s %s" % (self, items))

    __repr__ = __str__


#********************************************************************************

xFcnXlate = { '>':'g', '>=':'G', '<':'l', '<=':'L', '==':'n', '!=':'N', '!':'_', '~':'_' }

# FIX! should we use weakref Dicts, to avoid inhibiting garbage collection by having it
# in a list here?
xFcnUser = set()

xFcnOpBinSet = set([
'&&', '||', '+', '-', '*', '/', '**', '%', '&', '|',
'not', 'plus', 'sub', 'mul', 'div', 'pow', 'pow2', 'mod',
'and', 'or', 'lt', 'le', 'gt', 'ge', 'eq', 'ne',
'la', 'lo', 'g', 'G', 'l', 'L', 'n', 'N',
])

xFcnOp1Set = set([
'c', '_',
'is.na', 'is.factor', 'any.factor', 'any.na',
'canbecoercedtological',
'nrow', 'ncol', 'length',
'abs', 'sign', 'sqrt', 'ceiling', 'floor', 'log', 'exp', 'scale', 'factor',
'cos', 'sin', 'tan', 'acos', 'asin', 'atan', 'cosh', 'sinh', 'tanh',
'min', 'max', 'sum', 'sd', 'mean', 'match', 'unique', 'xorsum',
'ls',
])
# 'rename',

xFcnOp2Set = set()

xFcnOp3Set = set([
'cut', 'ddply', 'round', 'signif', 'trun', 'cbind', 'qtile', 'ifelse', 'apply', 'sapply', 'runif',
'seq', 'seq_len', 'rep_len', 'reduce', 'table', 'var',
])

#********************************************************************************
# operands is a list of items or an item. Each item can be number, string, list or tuple
# there is only one level of unpacking lists or tuples
# returns operandString, operandList
# Note this can't unpack a dict
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
        if re.match('\$', frameStr):
            raise Exception("%s: frame shouldn't start with '$' %s" % (parent, frameStr))
        if re.match('c$', frameStr):
            raise Exception("%s: frame can't be 'c' %s" % (parent, frameStr))
        if not re.match('[\a-zA-Z0-9_]', frameStr):
            raise Exception("%s: Don't like the chars in your frame %s" % (parent, frameStr))
    debugprint("%s frame: %s" % (parent, frameStr))
    return True


#*******************************************************************************
# maybe do some reading here
# http://python-3-patterns-idioms-test.readthedocs.org/en/latest/Factory.html
# row/col can be numbers or strings or not specified

# FIX! get rid of this? or ?? is Key sufficient? why KeyIndexed (no slicing?)
# FIX! KeyIndexed doesn't create a key on h2o..only h2o does
# KeyIndexed is used after a Key was created. it'a lower level way to use a frame, then Key
# Key can do indexing/slicing. KeyIndexed is fixed at row/col
class KeyIndexed(Xbase):
    # row 0/col0 should always exist if we init keys to 0?
    def __init__(self, frame=None, row=0, col=0, dim=2):
        if frame is None:
            # no h2o name? give it one that's unique for the instance
            frame = "fnon_" + hex(id(self))
            debugprint("KeyIndexed creating h2o key name for the instance, none provided: %s" % frame)
        # shouldn't get a key here, just a string
        elif not isinstance(frame, basestring):
            raise Exception("KeyIndexed should have frame param = string (or initially none) %s %s" % (type(frame), frame))

        super(KeyIndexed, self).__init__()

        # can have row/col?
        legalKey(frame, "KeyIndexed")
        self.frame = frame
        # if it's not a string, turn the assumed number into a number string
        self.row = Item(row)
        self.col = Item(col)
        self.dim = dim # dimensions
        # self.ast = str(self) # for debug/comparision

        # how to decide whether to send 2d or 1d references to h2o
        # is there no such thing as a row vector, only a column vector (or data frame)

        # row and col can be Seq, Colon, Item (could return a Cbind?)
        # or should it pass the python construct to h2o?
        # it could put the list into a h2o key, and then do a[b] in hto?
        # row extracts problematic?

        # None translates to "null"
        # not key yet

    def __str__(self):
        frame = self.frame
        row = self.row
        col = self.col
        if row in [None, '"null"'] and col in [None, '"null"']:
            return '$%s' % frame

        if row is None:
            row = '"null"'
        if col is None:
            col = '"null"'

        # does it already start with '$' ?
        # we always add $ to a here?. Suppose could detect whether it's already there
        # does it already start with '$' ?
        if not re.match('\$', frame):
            frame = '$%s' % self.frame

        # is a 1 dimensional frame all rows (1 col?)
        if self.dim==1:
            return '([ %s %s %s)' % (frame, row, '#0')
        else:
            return '([ %s %s %s)' % (frame, row, col)

    __repr__ = __str__


#********************************************************************************
# operands is a list of items
class Seq(Xbase):
    def __init__(self, *operands):
        operandList = unpackOperands(operands, parent="Seq operands")
        self.operandList = operandList
        # FIX! should we do more type checking on operands?
        # self.ast = str(self) # for debug/comparision


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
        # self.ast = str(self) # for debug/comparision


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
        # self.ast = str(self) # for debug/comparision

    def __str__(self):
        # This should give zero row key result. Does that result in Scalar?
        return "(= !%s %s)" % (self.frame, '(is.na (c {#0}))' )

    # this shouldn't be used with any of the setiem/getitem type stuff..add stuff to make that illegal?
    # or any operators?

#********************************************************************************
# key is a string
# change to init from Xbase not KeyIndexed
# a Key is the nebulous thing, that can get locked down into a KeyIndexed by indexing..
# a KeyIndexed can't be re-indexed, or turned back into a Key, 
# until it's executed by rapids (so the Key can be used again)
class Key(KeyIndexed):
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

        super(Key, self).__init__(frame)

        # can have row/col?
        legalKey(frame, "Key")
        self.frame = frame


        # add to list of created h2o keys (for deletion later?)
        # FIX! should make this a weak dictionary reference? don't want to affect python GC?
        # xKeyIndexedList.append(frame)
        # self.ast = str(self) # for debug/comparision

        # make it appear in h2o as a real key..so when we op on it, we don't get scalars from rapids!
        # FIX! is there a better way to think of this
        # should assign be  method on key? but Assign figures out lhs/rhs views. so those are new instances?
        # just want to point to the same h2o key name
        # ..can't do Assign, it inherits from Key
        # it will get checked again in KeyIndexed if we index..redundant
        # KeyIndexed checks, since we init with KeyIndexed, we shouldn't have to check here
        # legalKey(self.frame, "Key")

        # FIX how to handle this? Look at a list of existing key names?
        # would have to manage it correctly with respect to h2o. but user can delete keys in browser too.
        # KeyInit(self.frame).do()

    def __str__(self):
        frame = self.frame
        if not re.match('\$', frame):
            frame = '$%s' % self.frame
        return '%s' % frame

    __repr__ = __str__

    # for debug/wip of slicing
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


# slicing/indexing magic methods
# http://www.siafoo.net/article/57

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
        # no $ prefix
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
            raise Exception("Fcn legalFunction not found: %s" % function)

        # FIX! currently not checking any.
        # only check 1 and 2. not sure of the 3 group. cbind is conditional..need to do that special
        if False and len(operandList)!=required and required<3 and function!='cbind':
            raise Exception("Fcn wrong # of operands: %s %s" % (required, len(operandList)))

        self.operandList = operandList
        self.function = function
        # can I do a str() here before everything has been initted?
        debugprint("Fcn:", str(self))
        # self.ast = str(self) # for debug/comparision

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
        self.funs = False

    def __str__(self):
        return "%s" % self.expr

    __repr__ = __str__

    def __getitem__(self, items):
        raise Exception("trying to __getitem__ index a Return? doesn't make sense? %s %s" % (self, items))
    def __setitem__(self, items, rhs):
        raise Exception("trying to __setitem__ index a Return? doesn't make sense? %s %s" % (self, items))



class Assign(Key):
    # let rhs be more than one now, to allow for (if..) (else..)
    # obj for disabling the .do and just returning thr object
    # rhs can be more than one for (if ..) (else ..)
    # maybe get rid of separate Else object and just have If and IfElse
    # then just one rhs, and can have obj param
    # but init can't selectively return the object vs the result of the .do()
    def __init__(self, lhs=None, rhs=None, timeoutSecs=30):
        debugprint("Assign enter. lhs %s %s" % (type(lhs), lhs))
        # base init for execResult etc results. Should only need for Assign, Expr, Def ?
        if lhs is None:
            lhs = Key()
        elif not isinstance(lhs, (Key, KeyIndexed, basestring)):
            raise Exception("Assign: lhs not Key/KeyIndexed/string or None %s %s" % (type(lhs), lhs))

        super(Assign, self).__init__(lhs)

        # to date, have been passing strings
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
        self.rhs = Item(rhs)
        self.funs = False
        # self.ast = str(self) # for debug/comparision

        debugprint("Assign lhs: %s" % self.lhs)
        debugprint("Assign rhs: %s" % self.rhs)

    # leading $ is illegal on lhs
    # could check that it's a legal key name
    # FIX! what about checking rhs references have $ for keys.
    def __str__(self):
        # if there is row/col for lhs, have to resolve here?
        # hack: change the rhs reference '$' to the lhs '!'
        # to be diligent, only replace the first character
        # can't assign to immutable string indices
        # this is all side-effect of having the lhs get indexing like the rhs, so treated equally
        # no...self.lhs is type KeyIndexed
        # the $ may be in a ([ ...) ..so screw it, just do a translate
        # only if first?
        lhsAssign = re.sub('^\$','',str(self.lhs))

        # only add the ! if Key..once indexed, you don't use !
        lhsprefix = '!' if isinstance(self.lhs, (Key, basestring)) else ""
        return "(= %s%s %s)" % (lhsprefix, lhsAssign, self.rhs)

# can only do one expression/statement per ast.
# might be used to cause Fcn's to execute (with side effects?)

# You can't Colon or Index an Expr. You can endlessly Expr an Expr
# Expr includes all Fcn. So you can endlesly Fcn a Fcn.
# So overloaders can keep creating Fcn's of Fcn's
# if it needs to be resolve, and Expr can be used, and then the temp key name passed
# (the Expr becomes a Key basically, so it can get Colon'ed or Indexed.
class Expr(Assign):
    def __init__(self, expr, obj=False, timeoutSecs=30):
        # just be like Assign with no lhs?
        # create an anonymous key name. Only eval it to a key if we have to?
        # i.e. if we can't build up a new expression due to limitations of h2o support
        # suppose we can wait to create that name until we have to create a key
        super(Expr, self).__init__(lhs=None, rhs=expr, obj=obj, timeoutSecs=timeoutSecs)
        # self.ast = str(self) # for debug/comparision

    def __getitem__(self, items):
        raise Exception("trying to __getitem__ index a Expr? doesn't make sense? %s %s" % (self, items))
    def __setitem__(self, items, rhs):
        raise Exception("trying to __setitem__ index a Expr? doesn't make sense? %s %s" % (self, items))


# args should be individual strings, not lists
# FIX! take any number of params
class Def(Xbase):
    def __init__(self, function, params, *exprs):
        super(Def, self).__init__()
        # might have to add $ things on params
        # how do you assign to function output?

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
        # self.ast = str(self) # for debug/comparision

        debugprint("xFcnUser", xFcnUser)

    def __str__(self):
        # could check that it's a legal key name
        # FIX! what about checking rhs references have $ for keys.
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
        # self.ast = str(self) # for debug/comparision

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
        # self.ast = str(self) # for debug/comparision

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
        vector=None, breaks='#2', labels='$FALSE', include_lowest='$FALSE', right='$FALSE', dig_lab='#0'):

        vector = Item(vector)
        breaks = Item(breaks) # can be a Seq or a number?
        labels = self.translateValue(labels) # string? "(a,b]" or FALSE
        include_lowest = self.translateValue(include_lowest) # boolean
        right = self.translateValue(right) # boolean
        lab = Item(dig_lab) # integer
        super(Cut, self).__init__("cut", vector, breaks, labels, include_lowest, right, dig_lab)
        # self.ast = str(self) # for debug/comparision

# cut(a, breaks = c(min(a), mean(a), max(a)), labels = c("a", "b"))
# (cut $a {4.3;5.84333333333333;7.9} {"a";"b"} $FALSE $TRUE #3))

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
