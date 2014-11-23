
import h2o_exec as h2e
import re
# from h2o_exec import xUnary, xBinary, xCall, xSequence, xColon, xAssign, xNum, xExec, xFrame, xVector

def xNum(number):
    return "#%s" % number

def xKey(key):
    return "$%s" % key

def xUnary(unary='_', rhs='xTemp'):
    return "%s %s" % (unary, rhs)

def xBinary(binary='+', operand1='xTemp', operand2='xTemp'):
    return "%s %s %s" % (binary, operand1, operand2)

def xCall(function='sum', *operands):
    # no checking for correct number of params
    for k,v in operands.iteritems():
        newOperands[k] = xNumToString(v)

    if len(operands)!=0:
        return "(%s %s)" % (function, " ".join(newOperands))
    else:
        raise Exception("xexec xCall, no operands: %s" % operands)

def xNumToString(a):
    return xNum(a)

def xSequence(*operands):
    for k,v in operands.iteritems():
        newOperands[k] = xNumToString(v)
    
    if len(newOperands)!=0:
        return "{%s}" % (function, ";".join(newOperands))
    else:
        raise Exception("xexec xSequence, no operands: %s" % operands)

# a/b can be number or string
def xColon(a='#0', b='#0'):
    # if it's not a string, turn the assumed number into a number string
    # so we can pass numbers
    xNumToString(a)
    xNumToString(b)
    # we always add $ to a here?
    if a is None:
        a = '"null"'
    if b is None:
        b = '"null"'
    return '(: %s %s)' % (a, b) 

# row/col can be numbers, ranges, vectors. verify legal?
def xFrame(frame='xTemp', row=None, col=None):
    # if it's not a string, turn the assumed number into a number string
    # so we can pass numbers
    xNumToString(row)
    xNumToString(col)
    if re.match('\$', frame):
        raise Exception("xExec xFrame adds '$': frame ref shouldn't start with '$' %s" % frame)

    # we always add $ to a here?. Suppose could detect whether it's already there
    if row is None:
        row = '"null"'
    if col is None:
        col = '"null"'
    return '([ $%s %s %s)' % (frame, row, col)

# row/col can be numbers, ranges, vectors. verify legal?
# should Frame call this for single arg case?
def xVector(frame='xTemp', row=None):
    xNumToString(row)
    if re.match('\$', frame):
        raise Exception("xExec xFrame adds '$': frame ref shouldn't start with '$' %s" % frame)

    # we always add $ to a here?. Suppose could detect whether it's already there
    if row is None:
        row = '"null"'
    return '([ $%s %s)' % (frame, row)

# args should be individual strings, not lists
def xAssign(lhs='xResult', rhs='xTemp'):
    # leading $ is illegal on lhs
    if re.match('\$', lhs):
        raise Exception("xExec xAssign: lhs can't start with '$' %s" % frame)

    # could check that it's a legal key name
    return "(= !%s %s)" % (lhs, rhs)

def xExec(execExpr, timeoutSecs=30):
    resultExec, result = h2e.exec_expr(execExpr=execExpr, timeoutSecs=30)
    return resultExec, result 


