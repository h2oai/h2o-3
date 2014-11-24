
import h2o_exec as h2e
import re
# from h2o_xexec import xFcn, xSeq, xC, xCbind, xColon, xAssign, xAssignE, xNum, xExec, xFrame, xVector

# maybe don't need these
# from h2o_xexec import xUnary, xBinary

from sets import Set

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
'var',
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
def xNum(item):
    if isinstance(item, (list, tuple)):
        raise Exception("h2o_exec xNum doesn't take lists or tuples %s" % item)
    elif isinstance(item, basestring):
        if re.match("#", item):
            return item # already okay
    # do a try/except to decide if it's a item that needs to be a item-string representation
    try: 
        junk = float(item)
        return "#%s" % item
    except:
        return item

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
                operandList.append(xNum(v))
            else:
                for v2 in v:
                    # collapse it into the one new list
                    operandList.append(xNum(v2))
    else:
        operandList.append(xNum(operands))
    
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
    rhs = xNum(rhs)
    return "%s %s" % (unary, rhs)

def xBinary(binary='+', operand1='xTemp', operand2='xTemp'):
    operand1 = xNum(operand1)
    operand2 = xNum(operand2)
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
    a = xNum(a)
    b = xNum(b)
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
    row = xNum(row)
    col = xNum(col)
    # we always add $ to a here?. Suppose could detect whether it's already there
    return '([ $%s %s %s)' % (frame, row, col)

# row/col can be numbers or strings or not speciffied
# should Frame call this for single arg case?
def xVector(frame='xTemp', row=None):
    if re.match('\$', frame):
        raise Exception("h2o_exec xFrame adds '$': frame ref shouldn't start with '$' %s" % frame)
    if row is None:
        row = '"null"'
    row = xNum(row)
    # we always add $ to a here?. Suppose could detect whether it's already there
    return '([ $%s %s)' % (frame, row)

# args should be individual strings, not lists
def xAssign(lhs='xResult', rhs='xTemp'):
    print "h2o_exec xAssign: %s" % rhs
    rhs = xNum(rhs)
    print "h2o_exec xAssign after xNum: %s" % rhs
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
