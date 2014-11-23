
import h2o_exec as h2e
# from h2o_exec import xUnary, xBinary, xCall, xSequence, xColon, xAssign, xNum, xExec, xFrame, xVector

def xUnary(unary, rhs):
    return "%s %s" % (unary, rhs)

def xBinary(binary, operand1, operand2):
    return "%s %s %s" % (binary, operand1, operand2)

def xCall(function, *operands):
    return "(%s %s)" % (function, " ".join(map(str,operands)))

def xSequence(*operands):
    return "{%s}" % (function, ";".join(map(str,operands)))

def xColon(a, b):
    # we always add $ to a here?
    if a is None:
        a = '"null"'
    if b is None:
        b = '"null"'
    return '(: %s %s)' % (a, b) 

# row/col can be numbers, ranges, vectors. verify legal?
def xFrame(a, row, col):
    # we always add $ to a here?
    if row is None:
        row = '"null"'
    if col is None:
        col = '"null"'
    return '([ $%s %s %s)' % (a, row, col)

# row/col can be numbers, ranges, vectors. verify legal?
# should Frame call this for single arg case?
def xVector(a, row):
    # we always add $ to a here?
    if row is None:
        row = '"null"'
    return '([ $%s %s)' % (a, row)

# args should be individual strings, not lists
def xAssign(lhs, rhs):
    return "(= !%s %s)" % (lhs, rhs)

def xNum(number):
    return "#%s" % number

def xKey(key):
    return "$%s" % key

def xExec(execExpr, timeoutSecs=30):
    resultExec, result = h2e.exec_expr(execExpr=execExpr, timeoutSecs=30)
    return resultExec, result 


