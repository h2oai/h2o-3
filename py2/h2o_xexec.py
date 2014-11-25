
import h2o_exec as h2e
import re
# from h2o_xexec import xFcn, xSeq, xC, xCbind, xColon, xAssign, xAssignE, xItem, xExec, xFrame, xVector

# maybe don't need these
# from h2o_xexec import xUnary, xBinary
from sets import Set

#********************************************************************************
def xItem(item):
    print "xItem:", item
    # xItem can't be used for lhs
    # if list or tuple, exception
    if item is None:
        raise Exception("h2o_xexec xItem is None %s" % item)
    elif isinstance(item, (list, tuple, dict)):
        raise Exception("h2o_xexec xItem doesn't take lists, tuples (or dicts) %s" % item)

    # if string and has comma, -> exception
    # space can arise from prior expansion
    itemStr = str(item)
    if re.search(r"[,]", itemStr):
        raise Exception("h2o_xexec xItem has comma. Bad. %s" % item)
    elif len(itemStr)==0:
        raise Exception("h2o_xexec xItem is len 0 %s" % item)

    # elif string & starts with #, strip and check it's a number. Done if so. Else Exception
    start = itemStr[0]
    if start=="!":
        raise Exception("h2o_xexec xItem starts with !. Only for lhs (xAssign*). Bad. %s" % item)
    elif start=="#":
        if itemStr=="#":
            raise Exception("h2o_xexec xItem is just #. Bad. %s" % item)
        # can be a number, or the start of a string with a number at the beginning
        return item
    # elif string & starts with $, Done. Else if next char is a-zA-Z, done. Else Exception
    elif start=="$":
        if itemStr=="$":
            raise Exception("h2o_xexec xItem is just $. Bad. %s" % item)
        # can be a ref , or the start of a string with a ref at the beginning
        return item
    # elif number, add #
    else:
        try: 
            junk = float(item)
            # number!
            return "#%s" % item # good number!
        except: # not number
            # if it's just [a-zA-Z0-9], tack on the $ for probable initial key reference
            if re.match(r"[a-zA-Z0-9]+$", item):
                return "$%s" % item
            else:
                return item

xFcnXlate = {
'>':  'g',
'>=': 'G',
'<':  'l',
'<=': 'L',
'==': 'n',
'!=': 'N',
'!':  '_',
}

xFcnOpBinSet = Set ([
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

xFcnOp1Set = Set ([
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
'cosh',
'sinh',
'tanh',
'min',
'max',
'sum',
'sdev',
'mean',
'match',
'rename',
'unique',
'xorsum',
'cut',
'ls',
])

xFcnOp2Set = Set ([
])

xFcnOp3Set = Set ([
'var',
'table',
'reduce',
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
])
        


#********************************************************************************
# operands is a list of items or an item. Each item can be number, string, list or tuple
# there is only one level of unpacking lists or tuples
# returns operandString, operandList
def unpackOperands(operands, joinSep=',', parent="none"):
    operandList = []
    if isinstance(operands, (list, tuple)):
        for v in operands:
            # can we handle any operand being a list here too? might be compact
            # just one level of extra unpacking
            if not isinstance(v, (list,tuple)):
                operandList.append(xItem(v))
            else:
                for v2 in v:
                    # collapse it into the one new list
                    operandList.append(xItem(v2))
    else:
        operandList.append(xItem(operands))
    
    if len(operandList)!=0:
        operandString = joinSep.join(map(str, operandList))
        # operandList may not be strings!
        return (operandString, operandList)
    else:
        raise Exception("h2o_exec unpackOperands for %s, no operands: %s" % (parent, operands))

#********************************************************************************
# key is a string
def xKey(key):
    if isinstance(key, (list, tuple)):
        raise Exception("h2o_exec xKey doesn't take lists or tuples %s" % key)
    if not isinstance(key, basestring):
        raise Exception("h2o_exec xKey wants to be a string %s" % key)

    # does it already start with '$' ?
    if re.match('\$', frame):
        return key
    else:
        return "$%s" % key

#********************************************************************************
def xUnary(unary='_', rhs='xTemp'):
    rhs = xItem(rhs)
    return "%s %s" % (unary, rhs)

def xBinary(binary='+', operand1='xTemp', operand2='xTemp'):
    operand1 = xItem(operand1)
    operand2 = xItem(operand2)
    return "%s %s %s" % (binary, operand1, operand2)

def legalFunction(function):
    # return required operands
    if function in xFcnOp1Set: return 1
    if function in xFcnOp2Set: return 2
    if function in xFcnOp3Set: return 3
    if function in xFcnOpBinSet: return 2
    else: return 0

# function is a string. operands is a list of items
def xFcn(function='sum', *operands):
    # no checking for correct number of params
    print "xFcn %s has %s operands" % (function, len(operands))
    # see if we should translate the function name
    if function in xFcnXlate:
        function = xFcnXlate[function] 
    required = legalFunction(function)
    if required==0:
        raise Exception("h2o_xexec xFcn legalFunction not found: %s" % function)

    oprStr, oprList = unpackOperands(operands, joinSep=' ', parent='xSequence')
    if len(oprStr)==0:
        raise Exception("h2o_exec xFcn, no operands: %s" % operands)

    # only check 1 and 2. not sure of the 3 group. cbind is conditional..need to do that special
    if len(oprList)!=required and required<3 and function!='cbind':
        raise Exception("h2o_xexec xFcn wrong # of operands: %s %s" % (required, len(operands)))

    else:
        return "(%s %s)" % (function, oprStr)

# operands is a list of items
def xSeq(*operands):
    print "h2o_exec xSeq operands: %s" % repr(operands)
    operandString, operandList = unpackOperands(operands, joinSep=";", parent='xSeq')
    return "{%s}" % operandString

# operands is a list of items
# 'c' can only have one operand? And it has to be a string or number
# have this because it's so common as a function
def xC(operand):
    return xFcn("c", operand)

# FIX! key references need $ prefix
def xCbind(*operands):
    return xFcn("cbind", *operands)

# a/b can be number or string
def xColon(a='#0', b='#0'):
    # we always add $ to a here?
    if a is None:
        a = '"null"'
    if b is None:
        b = '"null"'
    # if it's not a string, turn the assumed number into a number string
    a = xItem(a)
    b = xItem(b)
    return '(: %s %s)' % (a, b) 

# row/col can be numbers or strings or not specified
def xFrame(frame='xTemp', row=None, col=None):
    if re.match('\$', frame):
        raise Exception("h2o_exec xFrame adds '$': frame ref shouldn't start with '$' %s" % frame)
    if row is None:
        row = '"null"'
    if col is None:
        col = '"null"'
    # if it's not a string, turn the assumed number into a number string
    row = xItem(row)
    col = xItem(col)
    # we always add $ to a here?. Suppose could detect whether it's already there
    return '([ $%s %s %s)' % (frame, row, col)

# row/col can be numbers or strings or not speciffied
# should Frame call this for single arg case?
def xVector(frame='xTemp', row=None):
    if re.match('\$', frame):
        raise Exception("h2o_exec xFrame adds '$': frame ref shouldn't start with '$' %s" % frame)
    if row is None:
        row = '"null"'
    row = xItem(row)
    # we always add $ to a here?. Suppose could detect whether it's already there
    return '([ $%s %s)' % (frame, row)

# args should be individual strings, not lists
def xAssign(lhs='xResult', rhs='xTemp'):
    print "h2o_exec xAssign: %s" % rhs
    rhs = xItem(rhs)
    print "h2o_exec xAssign after xItem: %s" % rhs
    # leading $ is illegal on lhs
    if re.match('\$', lhs):
        raise Exception("h2o_exec xAssign: lhs can't start with '$' %s" % frame)
    # could check that it's a legal key name
    # FIX! what about checking rhs references have $ for keys.
    return "(= !%s %s)" % (lhs, rhs)

# FIX! update option for funs= string
def xExec(execExpr, timeoutSecs=30, justPrint=False):
    # for debugging things. Don't do the Rapids
    if justPrint:
        print "justPrint: ast=", execExpr
        result = 0.0
        resultExec = {
              "ast": "",
              "col_names": [
                "C1", 
                "C2", 
                "C3"
              ], 
              "exception": None,
              "funs": None,
              "funstr": None,
              "key": {
                "name": "e5"
              }, 
              "num_cols": 3, 
              "num_rows": 0, 
              "result": "", 
              "scalar": 0.0, 
              "schema_name": "RapidsV1", 
              "schema_type": "Rapids", 
              "schema_version": 1, 
              "string": None
            }

    else:
        resultExec, result = h2e.exec_expr(execExpr=execExpr, timeoutSecs=30)

    return resultExec, result 

# xAssign, with a xExec wrapper
def xAssignE(lhs='xResult', rhs='xTemp', timeoutSecs=30, justPrint=False):
    execExpr = xAssign(lhs, rhs)
    return xExec(execExpr, timeoutSecs, justPrint)
