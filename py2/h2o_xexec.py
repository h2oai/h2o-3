
    
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
            print "execExpr:", self.execExpr
            self.execResult =  {'debugOnly': True}
            self.result = 555
        else:
            # functions can be multiple in Rapids, need []
            execExpr = "[%s]" % self.execExpr if self.funs else self.execExpr
            self.execResult, self.result = h2e.exec_expr(execExpr=execExpr, doFuns=self.funs, timeoutSecs=timeoutSecs)

        # don't return the full json...can look that up if necessary
        return self.result

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
'# is.na',
'is.factor',
'any.factor',
'any.na',
'canbecoercedtological',
'nrow',
'ncol',
'length',
'abs',
'sgn',
'sqrt',
'ceil',
'flr',
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
def unpackOperands(operands, item=True):
    def uu(v):
        if item:
            operandList.append(Item(v))
        else:
            operandList.append(v)

    if operands is None:
        raise Exception("unpackOperands no operands: %s" % operands)
    operandList = []
    if isinstance(operands, (list, tuple)):
        for v in operands:
            # can we handle any operand being a list here too? might be compact
            # just one level of extra unpacking
            if not isinstance(v, (list,tuple)): 
                uu(v)
            # collapse it into the one new list
            else: 
                for v2 in v: 
                    uu(v2)
    else: 
        uu(operands)

    return operandList

#********************************************************************************
# key is a string
class Key(Xbase):
    def __init__(self, key):
        self.key = key

    def __str__(self):
        key = self.key
        if isinstance(key, (list, tuple)):
            raise Exception("Key doesn't take lists or tuples %s" % key)
        if not isinstance(key, basestring):
            raise Exception("Key wants to be a string %s" % key)

        # does it already start with '$' ?
        if re.match('\$', key):
            return key
        else:
            return "$%s" % key

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
        operandList = unpackOperands(operands)
        if operandList is None:
            raise Exception("Seq operandList is None %s" % operandList)
        print "Fcn operands: %s" % operandList
        self.operandList = operandList

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
        operandList = unpackOperands(operands)
        if operandList is None:
            raise Exception("Seq operandList is None %s" % operandList)
        print "Seq operands: %s" % map(str,operandList)
        self.operandList = operandList

    def __str__(self):
        oprString = ";".join(map(str, self.operandList))
        return "{%s}" % oprString

    __repr__ = __str__

# a/b can be number or string
class Colon(Xbase):
    def __init__(self, a='#0', b='#0'):
        # we always add $ to a here?
        if a is None:
            a = '"null"'
        if b is None:
            b = '"null"'
        # if it's not a string, turn the assumed number into a number string
        self.a = Item(a)
        self.b = Item(b)

    def __str__(self):
        return '(: %s %s)' % (self.a, self.b) 

# row/col can be numbers or strings or not specified

class Frame(Xbase):
    def __init__(self, frame='xTemp', row=None, col=None):
        super(Frame, self).__init__()
        if re.match('\$', frame):
            raise Exception("Frame adds '$': frame ref shouldn't start with '$' %s" % frame)
        self.frame = frame

        if row is None:
            row = '"null"'
        if col is None:
            col = '"null"'
        # if it's not a string, turn the assumed number into a number string
        self.row = Item(row)
        self.col = Item(col)

    def __str__(self):
        # we always add $ to a here?. Suppose could detect whether it's already there
        return '([ $%s %s %s)' % (self.frame, self.row, self.col)

# args should be individual strings, not lists
class Expr(Xbase):
    # can be an Item, Function, Col, Frame
    def __init__(self, expr):
        # base init for execResult etc results. Should only need for Assign, Expr, Def, Frame?
        super(Assign, self).__init__()

        self.expr = Item(expr)
        self.funs = False
        # verify the whole thing can be a rapids string at init time
        print "Expr: %s" % expr

    def __str__(self):
        return str(self.expr)

    __repr__ = __str__
    ast = __str__

#       if hasattr(self.ps.cmdline, '__call__'):
#                pcmdline = self.ps.cmdline()


class Assign(Xbase):
    def __init__(self, lhs='xTemp', rhs='xTemp'):
        # base init for execResult etc results. Should only need for Assign, Expr, Def ?
        super(Assign, self).__init__()

        self.lhs = lhs
        self.rhs = Item(rhs)
        self.funs = False
        # verify the whole thing can be a rapids string at init time
        if re.match('\$', lhs):
            raise Exception("Assign: lhs shouldn't start with '$' %s" % lhs)
        if re.match('c$', lhs):
            raise Exception("Assign: lhs can't be 'c'" % lhs)
        if not re.match('[a-zA-Z0-9_]', lhs):
            raise Exception("Assign: Don't like the chars in your lhs %s" % lhs)
        print "Assign lhs: %s" % self.lhs
        print "Assign rhs: %s" % self.rhs

    # leading $ is illegal on lhs
    # could check that it's a legal key name
    # FIX! what about checking rhs references have $ for keys.
    def __str__(self):
        return "(= !%s %s)" % (self.lhs, self.rhs)

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
        paramList = unpackOperands(params, item=False)
        if len(paramList)==0:
            raise Exception("Def, no exprs: %s" % exprs)
        print "Def params: %s" % paramList

        # check that all the parms are legal strings (variable names
        for p in paramList:
            if not re.match(r"[a-zA-Z0-9]+$", str(p)):
                raise Exception("Def, bad name for parameter: %s" % p)
        
        exprList = unpackOperands(exprs)
        if len(exprList)==0:
            raise Exception("Def, no exprs: %s" % exprs)
        print "Def exprs: %s" % exprList

        # legal function name (I overconstrain compared to what Rapids allows)
        if not re.match(r"[a-zA-Z0-9]+$", function):
            raise Exception("Def, bad name for function: %s" % function)

        self.funs = True
        self.function = function
        self.paramList = paramList
        self.exprList = exprList

        # add to the list of legal user functions
        xFcnUser.add(function)
        print "xFcnUser", xFcnUser

    def __str__(self):
        # could check that it's a legal key name
        # FIX! what about checking rhs references have $ for keys.
        paramStr = " ".join(map(str, self.paramList))
        exprStr = ";;".join(map(str, self.exprList))
        return "(def %s {%s} %s;;;)" % (self.function, paramStr, exprStr)

    __repr__ = __str__
    ast = __str__

# operands is a list of items
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
