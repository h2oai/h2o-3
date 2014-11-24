
import h2o_exec as h2e
import re
# from h2o_exec import xUnary, xBinary, xFcn, xSequence, xColon, xAssign, xNum, xExec, xFrame, xVector

#********************************************************************************
def xNum(number):
    if isinstance(number, (list, tuple)):
        raise Exception("xexec xNum doesn't take lists or tuples %s" % number)
    elif isinstance(number, basestring):
        if re.match("#", number):
            return number # already okay
    # do a try/except to decide if it's a number that needs to be a number-string representation
    try: 
        junk = 1.0 + number
        return "#%s" % number
    except:
        return number

#********************************************************************************
# operands is a list of items or an item. Each item can be number, string, list or tuple
# there is only one level of unpacking lists or tuples
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
        return "%s" % operandString
    else:
        raise Exception("xexec unpackOperands for %s, no operands: %s" % (parent, operands))

#********************************************************************************
# key is a string
def xKey(key):
    if isinstance(key, (list, tuple)):
        raise Exception("xexec xKey doesn't take lists or tuples %s" % key)
    if not isinstance(key, basestring):
        raise Exception("xexec xKey wants to be a string %s" % key)

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

# function is a string. operands is a list of items
def xFcn(function='sum', *operands):
    # no checking for correct number of params
    print "xFcn %s has %s operands" % (function, len(operands))

    oprStr = unpackOperands(operands, joinSep=' ', parent='xSequence')
    if len(oprStr)==0:
        raise Exception("xexec xFcn, no operands: %s" % operands)
    else:
        return "(%s %s)" % (function, oprStr)

# operands is a list of items
def xSeq(*operands):
    print "xexec xSeq operands: %s" % repr(operands)
    return "{%s}" % unpackOperands(operands, joinSep=";", parent='xSeq')

# operands is a list of items
# 'c' can only have one operand? And it has to be a string or number
# have this because it's so common as a function
def xC(operand):
    return xFcn("c", operand)

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
        raise Exception("xExec xFrame adds '$': frame ref shouldn't start with '$' %s" % frame)
    if row is None:
        row = '"null"'
    if col is None:
        col = '"null"'
    # if it's not a string, turn the assumed number into a number string
    a = xNum(row)
    b = xNum(col)
    # we always add $ to a here?. Suppose could detect whether it's already there
    return '([ $%s %s %s)' % (frame, row, col)

# row/col can be numbers or strings or not speciffied
# should Frame call this for single arg case?
def xVector(frame='xTemp', row=None):
    if re.match('\$', frame):
        raise Exception("xExec xFrame adds '$': frame ref shouldn't start with '$' %s" % frame)
    if row is None:
        row = '"null"'
    row = xNum(row)
    # we always add $ to a here?. Suppose could detect whether it's already there
    return '([ $%s %s)' % (frame, row)

# args should be individual strings, not lists
def xAssign(lhs='xResult', rhs='xTemp'):
    print "xexec xAssign: %s" % rhs
    rhs = xNum(rhs)
    print "xexec xAssign after xNum: %s" % rhs
    # leading $ is illegal on lhs
    if re.match('\$', lhs):
        raise Exception("xExec xAssign: lhs can't start with '$' %s" % frame)
    # could check that it's a legal key name
    return "(= !%s %s)" % (lhs, rhs)

def xExec(execExpr, timeoutSecs=30):
    resultExec, result = h2e.exec_expr(execExpr=execExpr, timeoutSecs=30)
    return resultExec, result 


