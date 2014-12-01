
    
import h2o_exec as h2e
import re
# from h2o_xexec import xFcn, xSeq, xC, xCbind, xColon, xAssign, xAssignE, xItem, xExec, xFrame, xVector, xCut

# maybe don't need these
# from h2o_xexec import xUnary, xBinary

#********************************************************************************
class Xbase(object):
    # can set this in a test to disable the actual exec, just print
    debugOnly = False
    def json(self): # returns a json string. prints it too.
        import json
        s = vars(self)
        print json.dumps(s, indent=4, sort_keys=True)
        return s

    def __init__(self, thing='nothingFromXbase'):
        self.execResult = None
        self.result = None
        self.execExpr = None
        self.funs = False
        self.thing = thing

    def __str__(self):
        return str(self.thing)

    __repr__ = __str__
    # __call__ = __init__

    # not everything will "do" correctly
    def do(self, timeoutSecs=30):
        # keep these around so we can look at the h2o results?
        self.execResult = None
        self.result = None
        self.execExpr = str(self)
        if self.debugOnly:
            print "ast:", self.execExpr
            self.execResult =  {'debugOnly': True}
            self.result = 555
        else:
            # functions can be multiple in Rapids, need []
            execExpr = "[%s]" % self.execExpr if self.funs else self.execExpr
            self.execResult, self.result = h2e.exec_expr(execExpr=execExpr, doFuns=self.funs, timeoutSecs=timeoutSecs)

        # don't return the full json...can look that up if necessary
        return self.result

    # if we use the name of the vlass like a function call, or an instance...use .do()
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

    def __str__(self):
        item = self.item
        # print "Item:", item
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
            try: 
                junk = float(item)
                # number!
                itemStr = "#%s" % item # good number!
            except: # not number
                # if it's just [a-zA-Z0-9], tack on the $ for probable initial key reference
                if re.match(r"[a-zA-Z0-9]+$", itemStr):
                    itemStr = "$%s" % item 

        return itemStr

    __repr__ = __str__


#********************************************************************************

xFcnXlate = {
'>':  'g',
'>=': 'G',
'<':  'l',
'<=': 'L',
'==': 'n',
'!=': 'N',
'!':  '_',
}

xFcnUser = set()

xFcnOpBinSet = set([
'&&',
'||',
'not',
'plus',
'+',
'sub',
'-',
'mul',
'*',
'div',
'/',
'pow',
'*',
'pow2',
'**',
'mod',
'%',
'and',
'&',
'or',
'|',
'lt',
'le',
'gt',
'ge',
'eq',
'ne',
'la',
'lo',
'g',
'G',
'l',
'L',
'n',
'N',
])

xFcnOp1Set = set([
'c',
'_',
'is.na',
'is.factor',
'any.factor',
'any.na',
'canbecoercedtological',
'nrow',
'ncol',
'length',
'abs',
'sign',
'sqrt',
'ceiling',
'floor',
'log',
'exp',
'scale',
'factor',
'cos',
'sin',
'tan',
'acos',
'asin',
'atan',
# 'cosh',
'sinh',
'tanh',
'min',
'max',
'sum',
'sd',
'mean',
'match',
'unique',
'xorsum',
'ls',
])
# 'rename',

xFcnOp2Set = set()

xFcnOp3Set = set([
'cut',
'ddply',
'round',
'signif',
'trun',
'cbind',
'qtile',
'ifelse',
'apply',
'sapply',
'runif',
'seq',
'seq_len',
'rep_len',
'reduce',
'table',
'var',
])

#********************************************************************************
# operands is a list of items or an item. Each item can be number, string, list or tuple
# there is only one level of unpacking lists or tuples
# returns operandString, operandList
# Note this can't unpack a dict
def unpackOperands(operands, parent=None, item=True, lastOpr=None):
    lastOpr = None

    def addItem(opr):
        global lastOpr
        if isinstance(opr, Else) and not (lastOpr and (isinstance(lastOpr, If))):
            raise Exception("%s unpackOperands, Else without open If, %s", (parent, opr))
        if item:
            operandList.append(Item(opr))
        else:
            operandList.append(opr)
        lastOpr = opr

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
        print "%s: %s" % (parent, map(str,operandList))

    # always returns a list, even if one thing
    return operandList

#*******************************************************************************
# maybe do some reading here
# http://python-3-patterns-idioms-test.readthedocs.org/en/latest/Factory.html
# row/col can be numbers or strings or not specified
class Frame(Xbase):
    def __init__(self, frame='xTemp', row=None, col=None, dim=2):
        super(Frame, self).__init__()
        if re.match('\$', frame):
            raise Exception("Frame adds '$': frame ref shouldn't start with '$' %s" % frame)
        if isinstance(frame, (list, tuple)):
            raise Exception("frame doesn't take lists or tuples %s" % frame)
        if not isinstance(frame, basestring):
            raise Exception("frame wants to be a string %s" % frame)

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

        # None translates to "null"
        # not key yet

    def __str__(self):
        frame = self.frame
        row = self.row
        col = self.col
        if row in [None, '"null"']  and col in [None, '"null"']:
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
xKeyList = []

# key is a string
class Key(Frame):
    def __str__(self):
        frame = self.frame
        if not re.match('\$', frame):
            frame = '$%s' % self.frame
        return '%s' % frame

    __repr__ = __str__

    def __init__(self, key=None, dim=2):
        # HACK!
        Xbase.debugOnly = True
        if key is None:
            # no h2o name? give it one that's unique for the instance
            key = "anon_" + hex(id(self))
            print "Key creating h2o key name for the instance, none provided: %s" % key
        # Frame init?
        super(Key, self).__init__(key, None, None, dim)
        # add to list of created h2o keys (for deletion later?)
        xKeyList.append(key)

    # try a trick for getting an assign overload (<<=
    def __ilshift__(self, b):
        print "hello Key __ilshift__"
        Assign(self.frame, b).do()
        return self

    # for debug/wip of slicing
    def __getitem__(self, items):

        def slicer(item):
            print 'Key item %-15s  %s' % (type(item), item)

            if type(item) is int:
                print "Key item int", item
                return Item(item)

            elif isinstance( item, slice):
                # print "Key item start", str(item.start)
                # print "Key item stop", str(item.stop)
                # print "Key item step", str(item.step)
                # assume step is always None..
                assert item.step == None, "Key assuming step should be None %s" % item.step
                return Colon(item.start, item.stop)
                
            else:
                raise TypeError("Key.__getitem__ item(%s) must be int/slice") % item

        if isinstance(items, (list,tuple)):
            itemsList = list(items)
            # if there's a list, it better be just one or two dimensions
            # if length 0, ignore
            # one is row, two is row/col
            if len(itemsList)==0:
                print "Key ignoring length 0 items list/tuple) %s" % itemsList

            elif len(itemsList)==1:
                return(Frame(
                    frame=self.frame,
                    row=slicer(itemsList[0])
                ))

            elif len(itemsList)==2:
                return(Frame(
                    frame=self.frame,
                    row=slicer(itemsList[0]),
                    col=slicer(itemsList[1])
                ))

            else: 
                raise Exception("Key itemsList is >2 %s" % itemsList)
        else:
            return(Frame(
                frame=self.frame,
                row=slicer(items),
                dim=1, # one dimensional if using the single style?
            ))

        # FIX! should return an instance of the key with the updated row/col values
        return self

    # __call__ = __str__
            

# http://www.siafoo.net/article/57
# These methods are called when bracket notation is used.
# Python will behave differently depending on the type of value inside of the brackets:
# x[key], where key is a single value
#     Calls x.__*item__(key)
# x[start:end] where x.__*slice__ exists
#     Calls x.__*slice__(cooked_start, cooked_end) where start and end are 'cooked' as described below in 'Old-Style Slices'
# x[start:end] where x.__*slice__ does not exist, or x[extended_slice], where extended slice is any slice more complex than start:end
#     Calls x.__*item__ with slice object, Ellipsis, or list of these.
# 
# In general, if key is of an inappropriate type, TypeError should be raised. If it is outside the sequence of keys in instance, IndexError should be raised. If instance is a mapping object and key cannot be found, KeyError should be raised. (What if neither of these is true? I dont know.)
# 
# __getitem__(self, key)
# x.__getitem__(key) <==> x[key]
# Should return item(s) referenced by key.
# Not called if __setslice__ exists and simple start:end slicing is used.
# If not present, items cannot be evaluated using bracket notation, and an AttributeError is raised.
# 
# __setitem__(self, key, value)
# x.__setitem__(key, value) <==> x[key] = value
# Should set or replace item(s) referenced by key. value can be a single value or a sequence.
# Not called if __setslice__ exists and simple start:end slicing is used. Usage not dependent on presence of __getitem__.
# If not present, items cannot be assigned using bracket notation, and an AttributeError is raised.
# 
# __delitem__(self, key)
# x.__delitem__(key) <==> del x[key]
# Should delete item(s) represented by key. Not dependent on presence of __getitem__.
# Not called if __delslice__ exists and simple start:end slicing is used. Usage not dependent on presence of __getitem__.
# If not present, items cannot be deleted using bracket notation, and an AttributeError is raised.


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
        print "Fcn %s has %s operands" % (function, len(operands))
        # see if we should translate the function name
        if function in xFcnXlate:
            function = xFcnXlate[function] 

        required = legalFunction(function)
        if required==0:
            raise Exception("Fcn legalFunction not found: %s" % function)

        # FIX!
        # only check 1 and 2. not sure of the 3 group. cbind is conditional..need to do that special
        if False and len(operandList)!=required and required<3 and function!='cbind':
            raise Exception("Fcn wrong # of operands: %s %s" % (required, len(operandList)))

        self.operandList = operandList
        self.function = function

    def __str__(self):
        return "(%s %s)" % (self.function, " ".join(map(str, self.operandList)))

    ast = __str__


# operands is a list of items
class Seq(Xbase):
    def __init__(self, *operands):
        operandList = unpackOperands(operands, parent="Seq operands")
        self.operandList = operandList

    def __str__(self):
        oprString = ";".join(map(str, self.operandList))
        return "{%s}" % oprString

    __repr__ = __str__

# a/b can be number or string
class Colon(Xbase):
    def __init__(self, a='#0', b='#0'):
        # if it's not a string, turn the assumed number into a number string
        self.a = Item(a)
        self.b = Item(b)

    def __str__(self):
        # no colon if both None
        if str(self.a) in  ['"null"', None] and str(self.b) in ['"null"', None]:
            return '"null"'
        else:
            return '(: %s %s)' % (self.a, self.b) 


# higher level things might skip using Expr and do this stuff themselves
class Expr(Xbase):
    def __init__(self, *exprs):
        super(Expr, self).__init__()
        # this converts them to Items
        exprList = unpackOperands(exprs, parent="Expr exprs")
        self.exprList = exprList
        self.funs = False

    def __str__(self):
        exprString = " ".join(map(str, self.exprList))
        return "%s" % exprString

    __repr__ = __str__
    ast = __str__

class Return(Xbase):
    # return only has one expression?
    def __init__(self, expr):
        super(Return, self).__init__()
        self.expr = Item(expr)
        self.funs = False

    def __str__(self):
        return "%s" % self.expr

    __repr__ = __str__
    ast = __str__

#       if hasattr(self.ps.cmdline, '__call__'):
#                pcmdline = self.ps.cmdline()

class Assign(Xbase):
    # let rhs be more than one now, to allow for (if..) (else..)
    def __init__(self, lhs='xTemp', *rhs):
        # base init for execResult etc results. Should only need for Assign, Expr, Def ?
        super(Assign, self).__init__()

        self.lhs = lhs
        rhsList = unpackOperands(rhs, parent="Assign rhs")
        self.rhsList = rhsList
        self.funs = False
        # verify the whole thing can be a rapids string at init time

        if re.match('\$', lhs):
            raise Exception("Assign: lhs shouldn't start with '$' %s" % lhs)
        if re.match('c$', lhs):
            raise Exception("Assign: lhs can't be 'c' %s" % lhs)
        if not re.match('[a-zA-Z0-9_]', lhs):
            raise Exception("Assign: Don't like the chars in your lhs %s" % lhs)

        print "Assign lhs: %s" % self.lhs
        print "Assign rhs: %s" % self.rhsList

    # leading $ is illegal on lhs
    # could check that it's a legal key name
    # FIX! what about checking rhs references have $ for keys.
    def __str__(self):
        # should only have >1 for (if ..) (else ..) ???
        rhsStr = " ".join(map(str, self.rhsList))
        return "(= !%s %s)" % (self.lhs, rhsStr)

    ast = __str__


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

        paramList = unpackOperands(params, parent="Def params", item=False)

        # check that all the parms are legal strings (variable names
        for p in paramList:
            if not re.match(r"[a-zA-Z0-9]+$", str(p)):
                raise Exception("Def, bad name for parameter: %s" % p)
        
        exprList = unpackOperands(exprs, parent="Def exprs")

        # legal function name (I overconstrain compared to what Rapids allows)
        if not re.match(r"[a-zA-Z0-9]+$", function):
            raise Exception("Def, bad name for function: %s" % function)

        self.funs = True
        self.function = function
        self.paramList = paramList
        self.exprList = exprList

        print "xFcnUser", xFcnUser

    def __str__(self):
        # could check that it's a legal key name
        # FIX! what about checking rhs references have $ for keys.
        paramStr = " ".join(map(str, self.paramList))
        exprStr = ";;".join(map(str, self.exprList))
        return "(def %s {%s} %s;;;)" % (self.function, paramStr, exprStr)

    __repr__ = __str__
    ast = __str__

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
    ast = __str__

# in a series of expressions...Else can only follow If
class Else(Xbase):
    def __init__(self, *exprs):
        super(Else, self).__init__()
        exprList = unpackOperands(exprs, parent="Else exprs")
        self.exprList = exprList

    def __str__(self):
        exprStr = ";;".join(map(str, self.exprList))
        if len(self.exprList)>1:
            exprStr += ";;;"
        return "(else %s)" % exprStr

    __repr__ = __str__
    ast = __str__

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
    ast = __str__

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
