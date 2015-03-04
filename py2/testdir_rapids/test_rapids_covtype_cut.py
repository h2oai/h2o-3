import unittest, random, sys, time
sys.path.extend(['.','..','../..','py'])
import h2o2 as h2o
import h2o_exec as h2e, h2o_import as h2i, h2o_cmd
import h2o_print as h2p
from h2o_test import dump_json, verboseprint
import re

DO_ROLLUP = True
exprList = [
    '(= !b (c {#1;#2;#3}))',
    '(= !a (cbind %b %b %b %b %b %b %b %b %b %b %b %b %b %b %b %b %b %b %b))',
    '(= !e0 %a)',
    '(= !e1 %a)',
    '(= !e2 %a)',
    '(= !e3 %a)',
    '(= !e4 %a)',
    '(= !e5 %a)',
    '(= !e6 %a)',
    '(= !e7 %a)',
    '(= !e8 %a)',
    '(= !e9 %a)',
    '(= !e8 ([ %p (& (& (& (& (& (& (& (n #0 ([ %p "null" #0)) (n #3 ([ %p "null" #1))) (n #0 ([ %p "null" #2))) (n #0 ([ %p "null" #3))) (n #2 ([ %p "null" #4))) (n #3 ([ %p "null" #5))) (n #1 ([ %p "null" #6))) (n #1 ([ %p "null" #9))) "null"))',
    '(= !e0 ([ %p (& (& (& (& (n #1 ([ %p "null" #0)) (n #3 ([ %p "null" #5))) (n #2 ([ %p "null" #6))) (n #1 ([ %p "null" #8))) (n #1 ([ %p "null" #9))) "null"))',
    '(= !e4 ([ %p (& (& (& (& (& (n #0 ([ %p "null" #0)) (n #1 ([ %p "null" #2))) (n #2 ([ %p "null" #4))) (n #2 ([ %p "null" #6))) (n #0 ([ %p "null" #8))) (n #2 ([ %p "null" #9))) "null"))',
    '(= !e4 ([ %p (& (& (& (& (& (& (& (& (n #1 ([ %p "null" #0)) (n #0 ([ %p "null" #2))) (n #0 ([ %p "null" #3))) (n #0 ([ %p "null" #4))) (n #0 ([ %p "null" #5))) (n #1 ([ %p "null" #6))) (n #0 ([ %p "null" #7))) (n #1 ([ %p "null" #8))) (n #3 ([ %p "null" #9))) "null"))',
    '(= !e7 ([ %p (n #2 ([ %p "null" #6)) "null"))',
    '(= !e6 ([ %p (& (& (& (n #3 ([ %p "null" #1)) (n #2 ([ %p "null" #5))) (n #0 ([ %p "null" #7))) (n #3 ([ %p "null" #9))) "null"))',
    '(= !e2 ([ %p (& (& (& (& (& (& (& (& (n #1 ([ %p "null" #0)) (n #1 ([ %p "null" #1))) (n #1 ([ %p "null" #2))) (n #1 ([ %p "null" #3))) (n #2 ([ %p "null" #4))) (n #1 ([ %p "null" #6))) (n #0 ([ %p "null" #7))) (n #0 ([ %p "null" #8))) (n #3 ([ %p "null" #9))) "null"))',
    '(= !e4 ([ %p (& (& (& (& (& (& (& (& (n #0 ([ %p "null" #0)) (n #1 ([ %p "null" #2))) (n #1 ([ %p "null" #3))) (n #0 ([ %p "null" #4))) (n #3 ([ %p "null" #5))) (n #2 ([ %p "null" #6))) (n #0 ([ %p "null" #7))) (n #0 ([ %p "null" #8))) (n #2 ([ %p "null" #9))) "null"))',
    '(= !e7 ([ %p (& (& (n #0 ([ %p "null" #2)) (n #0 ([ %p "null" #3))) (n #2 ([ %p "null" #4))) "null"))',
    '(= !e1 ([ %p (n #2 ([ %p "null" #6)) "null"))',
    '(= !e3 ([ %p (n #1 ([ %p "null" #6)) "null"))',
    '(= !e3 ([ %p (& (& (& (& (& (n #1 ([ %p "null" #0)) (n #0 ([ %p "null" #2))) (n #0 ([ %p "null" #3))) (n #0 ([ %p "null" #4))) (n #3 ([ %p "null" #5))) (n #0 ([ %p "null" #6))) "null"))',
    '(= !e5 ([ %p (& (& (& (& (& (n #1 ([ %p "null" #0)) (n #3 ([ %p "null" #1))) (n #0 ([ %p "null" #2))) (n #1 ([ %p "null" #4))) (n #0 ([ %p "null" #7))) (n #2 ([ %p "null" #9))) "null"))',
    '(= !e4 ([ %p (& (& (& (& (& (& (& (n #0 ([ %p "null" #0)) (n #0 ([ %p "null" #1))) (n #0 ([ %p "null" #3))) (n #1 ([ %p "null" #4))) (n #0 ([ %p "null" #5))) (n #0 ([ %p "null" #6))) (n #0 ([ %p "null" #7))) (n #1 ([ %p "null" #8))) "null"))',
    '(= !e8 ([ %p (& (n #3 ([ %p "null" #1)) (n #3 ([ %p "null" #5))) "null"))',
    '(= !e7 ([ %p (& (& (n #0 ([ %p "null" #0)) (n #1 ([ %p "null" #1))) (n #3 ([ %p "null" #9))) "null"))',
    '(= !e4 ([ %p (& (& (& (n #1 ([ %p "null" #3)) (n #1 ([ %p "null" #4))) (n #0 ([ %p "null" #8))) (n #0 ([ %p "null" #9))) "null"))',
    '(= !e2 ([ %p (& (& (& (n #1 ([ %p "null" #3)) (n #1 ([ %p "null" #5))) (n #0 ([ %p "null" #8))) (n #3 ([ %p "null" #9))) "null"))',
    '(= !e7 ([ %p (& (& (& (n #0 ([ %p "null" #2)) (n #1 ([ %p "null" #3))) (n #0 ([ %p "null" #7))) (n #1 ([ %p "null" #8))) "null"))',
    '(= !e3 ([ %p (& (& (& (n #1 ([ %p "null" #0)) (n #2 ([ %p "null" #1))) (n #1 ([ %p "null" #2))) (n #0 ([ %p "null" #3))) "null"))',
    '(= !e3 ([ %p (& (& (& (n #3 ([ %p "null" #1)) (n #2 ([ %p "null" #5))) (n #0 ([ %p "null" #7))) (n #3 ([ %p "null" #9))) "null"))',
    '(= !e4 ([ %p (& (& (& (& (& (& (& (n #0 ([ %p "null" #0)) (n #0 ([ %p "null" #1))) (n #1 ([ %p "null" #3))) (n #1 ([ %p "null" #4))) (n #1 ([ %p "null" #6))) (n #0 ([ %p "null" #7))) (n #0 ([ %p "null" #8))) (n #2 ([ %p "null" #9))) "null"))',
    '(= !e1 ([ %p (& (& (& (n #0 ([ %p "null" #2)) (n #0 ([ %p "null" #4))) (n #1 ([ %p "null" #6))) (n #0 ([ %p "null" #7))) "null"))',
    '(= !e9 ([ %p (& (& (& (& (& (& (& (& (& (n #0 ([ %p "null" #0)) (n #2 ([ %p "null" #1))) (n #1 ([ %p "null" #2))) (n #0 ([ %p "null" #3))) (n #2 ([ %p "null" #4))) (n #0 ([ %p "null" #5))) (n #1 ([ %p "null" #6))) (n #0 ([ %p "null" #7))) (n #0 ([ %p "null" #8))) (n #2 ([ %p "null" #9))) "null"))',
    '(= !e1 ([ %p (& (& (& (& (n #1 ([ %p "null" #0)) (n #3 ([ %p "null" #5))) (n #2 ([ %p "null" #6))) (n #1 ([ %p "null" #8))) (n #1 ([ %p "null" #9))) "null"))',
    '(= !e4 ([ %p (& (& (& (& (& (& (& (n #1 ([ %p "null" #0)) (n #2 ([ %p "null" #1))) (n #0 ([ %p "null" #2))) (n #1 ([ %p "null" #3))) (n #2 ([ %p "null" #4))) (n #0 ([ %p "null" #7))) (n #1 ([ %p "null" #8))) (n #3 ([ %p "null" #9))) "null"))',
    '(= !e4 ([ %p (& (& (& (& (n #1 ([ %p "null" #0)) (n #3 ([ %p "null" #5))) (n #2 ([ %p "null" #6))) (n #1 ([ %p "null" #8))) (n #1 ([ %p "null" #9))) "null"))',
    '(= !e5 ([ %p (& (& (& (n #1 ([ %p "null" #0)) (n #1 ([ %p "null" #2))) (n #2 ([ %p "null" #4))) (n #2 ([ %p "null" #6))) "null"))',
    '(= !e2 ([ %p (& (& (& (n #1 ([ %p "null" #0)) (n #1 ([ %p "null" #2))) (n #2 ([ %p "null" #4))) (n #2 ([ %p "null" #6))) "null"))',
    '(= !e6 ([ %p (& (& (& (& (& (& (& (& (& (n #0 ([ %p "null" #0)) (n #1 ([ %p "null" #1))) (n #0 ([ %p "null" #2))) (n #0 ([ %p "null" #3))) (n #0 ([ %p "null" #4))) (n #1 ([ %p "null" #5))) (n #1 ([ %p "null" #6))) (n #0 ([ %p "null" #7))) (n #0 ([ %p "null" #8))) (n #3 ([ %p "null" #9))) "null"))',
    '(= !e1 ([ %p (n #3 ([ %p "null" #1)) "null"))',
    '(= !e6 ([ %p (& (& (& (& (& (& (& (& (n #1 ([ %p "null" #0)) (n #3 ([ %p "null" #1))) (n #1 ([ %p "null" #2))) (n #0 ([ %p "null" #3))) (n #3 ([ %p "null" #5))) (n #0 ([ %p "null" #6))) (n #0 ([ %p "null" #7))) (n #1 ([ %p "null" #8))) (n #0 ([ %p "null" #9))) "null"))',
    '(= !e4 ([ %p (& (& (& (& (& (& (& (n #0 ([ %p "null" #0)) (n #3 ([ %p "null" #1))) (n #0 ([ %p "null" #2))) (n #0 ([ %p "null" #3))) (n #2 ([ %p "null" #4))) (n #3 ([ %p "null" #5))) (n #1 ([ %p "null" #6))) (n #1 ([ %p "null" #9))) "null"))',
    '(= !e7 ([ %p (& (& (& (& (& (& (& (n #1 ([ %p "null" #0)) (n #3 ([ %p "null" #1))) (n #0 ([ %p "null" #2))) (n #0 ([ %p "null" #3))) (n #1 ([ %p "null" #6))) (n #0 ([ %p "null" #7))) (n #0 ([ %p "null" #8))) (n #3 ([ %p "null" #9))) "null"))',
    '(= !e7 ([ %p (n #1 ([ %p "null" #6)) "null"))',
    '(= !e8 ([ %p (& (& (& (n #1 ([ %p "null" #3)) (n #0 ([ %p "null" #4))) (n #1 ([ %p "null" #8))) (n #2 ([ %p "null" #9))) "null"))',
    '(= !e0 ([ %p (& (n #2 ([ %p "null" #1)) (n #2 ([ %p "null" #9))) "null"))',
    '(= !e8 ([ %p (& (& (& (& (& (& (& (& (n #1 ([ %p "null" #0)) (n #1 ([ %p "null" #1))) (n #1 ([ %p "null" #2))) (n #0 ([ %p "null" #3))) (n #1 ([ %p "null" #4))) (n #3 ([ %p "null" #5))) (n #0 ([ %p "null" #6))) (n #0 ([ %p "null" #7))) (n #0 ([ %p "null" #9))) "null"))',
    '(= !e8 ([ %p (& (& (& (& (& (& (& (& (n #0 ([ %p "null" #1)) (n #0 ([ %p "null" #2))) (n #0 ([ %p "null" #3))) (n #0 ([ %p "null" #4))) (n #2 ([ %p "null" #5))) (n #0 ([ %p "null" #6))) (n #0 ([ %p "null" #7))) (n #1 ([ %p "null" #8))) (n #2 ([ %p "null" #9))) "null"))',
    '(= !e8 ([ %p (& (& (& (& (& (& (& (n #1 ([ %p "null" #0)) (n #1 ([ %p "null" #1))) (n #1 ([ %p "null" #3))) (n #2 ([ %p "null" #4))) (n #2 ([ %p "null" #5))) (n #0 ([ %p "null" #6))) (n #0 ([ %p "null" #7))) (n #0 ([ %p "null" #8))) "null"))',
    '(= !e1 ([ %p (& (& (& (& (& (& (& (& (n #1 ([ %p "null" #0)) (n #1 ([ %p "null" #1))) (n #1 ([ %p "null" #2))) (n #0 ([ %p "null" #3))) (n #1 ([ %p "null" #4))) (n #0 ([ %p "null" #5))) (n #0 ([ %p "null" #7))) (n #1 ([ %p "null" #8))) (n #0 ([ %p "null" #9))) "null"))',
    '(= !e5 ([ %p (& (n #1 ([ %p "null" #8)) (n #1 ([ %p "null" #9))) "null"))',
    '(= !e5 ([ %p (& (& (& (& (& (& (& (n #2 ([ %p "null" #1)) (n #0 ([ %p "null" #2))) (n #0 ([ %p "null" #3))) (n #1 ([ %p "null" #4))) (n #0 ([ %p "null" #5))) (n #0 ([ %p "null" #7))) (n #0 ([ %p "null" #8))) (n #0 ([ %p "null" #9))) "null"))',
    '(= !e4 ([ %p (& (& (& (& (& (& (& (& (n #1 ([ %p "null" #0)) (n #1 ([ %p "null" #1))) (n #1 ([ %p "null" #2))) (n #0 ([ %p "null" #3))) (n #1 ([ %p "null" #4))) (n #0 ([ %p "null" #5))) (n #0 ([ %p "null" #7))) (n #1 ([ %p "null" #8))) (n #0 ([ %p "null" #9))) "null"))',
    '(= !e9 ([ %p (& (& (& (& (n #1 ([ %p "null" #3)) (n #2 ([ %p "null" #4))) (n #1 ([ %p "null" #5))) (n #2 ([ %p "null" #6))) (n #1 ([ %p "null" #8))) "null"))',
    '(= !e7 ([ %p (& (& (& (& (& (& (& (& (& (n #0 ([ %p "null" #0)) (n #2 ([ %p "null" #1))) (n #0 ([ %p "null" #2))) (n #1 ([ %p "null" #3))) (n #1 ([ %p "null" #4))) (n #2 ([ %p "null" #5))) (n #2 ([ %p "null" #6))) (n #0 ([ %p "null" #7))) (n #0 ([ %p "null" #8))) (n #2 ([ %p "null" #9))) "null"))',
    '(= !e9 ([ %p (n #1 ([ %p "null" #6)) "null"))',
    '(= !e6 ([ %p (& (& (& (& (& (& (& (& (& (n #1 ([ %p "null" #0)) (n #0 ([ %p "null" #1))) (n #0 ([ %p "null" #2))) (n #1 ([ %p "null" #3))) (n #0 ([ %p "null" #4))) (n #1 ([ %p "null" #5))) (n #1 ([ %p "null" #6))) (n #0 ([ %p "null" #7))) (n #0 ([ %p "null" #8))) (n #2 ([ %p "null" #9))) "null"))',
    '(= !e1 ([ %p (& (& (& (& (& (& (& (& (n #1 ([ %p "null" #0)) (n #1 ([ %p "null" #1))) (n #1 ([ %p "null" #2))) (n #1 ([ %p "null" #3))) (n #2 ([ %p "null" #4))) (n #1 ([ %p "null" #6))) (n #0 ([ %p "null" #7))) (n #0 ([ %p "null" #8))) (n #3 ([ %p "null" #9))) "null"))',
    '(= !e5 ([ %p (& (& (& (& (& (& (& (& (n #1 ([ %p "null" #0)) (n #1 ([ %p "null" #1))) (n #1 ([ %p "null" #2))) (n #0 ([ %p "null" #3))) (n #1 ([ %p "null" #4))) (n #0 ([ %p "null" #5))) (n #0 ([ %p "null" #7))) (n #1 ([ %p "null" #8))) (n #0 ([ %p "null" #9))) "null"))',
    '(= !e8 ([ %p (& (n #1 ([ %p "null" #3)) (n #1 ([ %p "null" #6))) "null"))',
    '(= !e3 ([ %p (& (& (& (& (& (& (& (n #1 ([ %p "null" #0)) (n #2 ([ %p "null" #1))) (n #0 ([ %p "null" #2))) (n #1 ([ %p "null" #3))) (n #2 ([ %p "null" #6))) (n #0 ([ %p "null" #7))) (n #1 ([ %p "null" #8))) (n #3 ([ %p "null" #9))) "null"))',
    '(= !e0 ([ %p (& (& (& (& (& (& (& (& (& (n #1 ([ %p "null" #0)) (n #2 ([ %p "null" #1))) (n #0 ([ %p "null" #2))) (n #1 ([ %p "null" #3))) (n #0 ([ %p "null" #4))) (n #0 ([ %p "null" #5))) (n #2 ([ %p "null" #6))) (n #0 ([ %p "null" #7))) (n #1 ([ %p "null" #8))) (n #0 ([ %p "null" #9))) "null"))',
    '(= !e4 ([ %p (& (& (n #0 ([ %p "null" #1)) (n #0 ([ %p "null" #4))) (n #3 ([ %p "null" #9))) "null"))',
    '(= !e5 ([ %p (& (& (& (& (n #2 ([ %p "null" #1)) (n #0 ([ %p "null" #2))) (n #1 ([ %p "null" #3))) (n #1 ([ %p "null" #4))) (n #1 ([ %p "null" #9))) "null"))',
    '(= !e9 ([ %p (& (& (& (& (n #1 ([ %p "null" #1)) (n #1 ([ %p "null" #3))) (n #0 ([ %p "null" #7))) (n #0 ([ %p "null" #8))) (n #2 ([ %p "null" #9))) "null"))',
    '(= !e5 ([ %p (& (& (& (n #0 ([ %p "null" #4)) (n #3 ([ %p "null" #5))) (n #1 ([ %p "null" #6))) (n #2 ([ %p "null" #9))) "null"))',
    '(= !e6 ([ %p (& (& (& (& (& (& (& (& (& (n #1 ([ %p "null" #0)) (n #2 ([ %p "null" #1))) (n #0 ([ %p "null" #2))) (n #1 ([ %p "null" #3))) (n #2 ([ %p "null" #4))) (n #0 ([ %p "null" #5))) (n #2 ([ %p "null" #6))) (n #0 ([ %p "null" #7))) (n #0 ([ %p "null" #8))) (n #2 ([ %p "null" #9))) "null"))',
    '(= !e7 ([ %p (n #3 ([ %p "null" #1)) "null"))',
    '(= !e5 ([ %p (& (& (& (& (& (& (& (& (& (n #1 ([ %p "null" #0)) (n #1 ([ %p "null" #1))) (n #1 ([ %p "null" #2))) (n #0 ([ %p "null" #3))) (n #1 ([ %p "null" #4))) (n #2 ([ %p "null" #5))) (n #0 ([ %p "null" #6))) (n #0 ([ %p "null" #7))) (n #1 ([ %p "null" #8))) (n #0 ([ %p "null" #9))) "null"))',
    '(= !e0 ([ %p (& (& (& (& (& (& (& (& (& (n #1 ([ %p "null" #0)) (n #2 ([ %p "null" #1))) (n #0 ([ %p "null" #2))) (n #1 ([ %p "null" #3))) (n #1 ([ %p "null" #4))) (n #2 ([ %p "null" #5))) (n #0 ([ %p "null" #6))) (n #0 ([ %p "null" #7))) (n #0 ([ %p "null" #8))) (n #3 ([ %p "null" #9))) "null"))',
    '(= !e6 ([ %p (n #0 ([ %p "null" #7)) "null"))',
    '(= !e1 ([ %p (& (& (& (& (& (n #1 ([ %p "null" #0)) (n #0 ([ %p "null" #2))) (n #0 ([ %p "null" #3))) (n #0 ([ %p "null" #4))) (n #3 ([ %p "null" #5))) (n #0 ([ %p "null" #6))) "null"))',
    '(= !e8 ([ %p (& (& (n #1 ([ %p "null" #1)) (n #0 ([ %p "null" #8))) (n #3 ([ %p "null" #9))) "null"))',
    '(= !e3 ([ %p (& (& (& (& (& (& (& (& (& (n #0 ([ %p "null" #0)) (n #2 ([ %p "null" #1))) (n #0 ([ %p "null" #2))) (n #1 ([ %p "null" #3))) (n #1 ([ %p "null" #4))) (n #2 ([ %p "null" #5))) (n #2 ([ %p "null" #6))) (n #0 ([ %p "null" #7))) (n #1 ([ %p "null" #8))) (n #2 ([ %p "null" #9))) "null"))',
    '(= !e4 ([ %p (n #1 ([ %p "null" #6)) "null"))',
    '(= !e8 ([ %p (n #1 ([ %p "null" #6)) "null"))',
    '(= !e1 ([ %p (& (& (& (& (& (n #1 ([ %p "null" #0)) (n #3 ([ %p "null" #1))) (n #0 ([ %p "null" #2))) (n #1 ([ %p "null" #4))) (n #0 ([ %p "null" #7))) (n #2 ([ %p "null" #9))) "null"))',
    '(= !e0 ([ %p (& (& (& (& (& (& (& (& (& (n #1 ([ %p "null" #0)) (n #0 ([ %p "null" #1))) (n #1 ([ %p "null" #2))) (n #1 ([ %p "null" #3))) (n #0 ([ %p "null" #4))) (n #0 ([ %p "null" #5))) (n #2 ([ %p "null" #6))) (n #0 ([ %p "null" #7))) (n #1 ([ %p "null" #8))) (n #3 ([ %p "null" #9))) "null"))',
    '(= !e5 ([ %p (& (& (& (& (& (& (& (& (& (n #0 ([ %p "null" #0)) (n #2 ([ %p "null" #1))) (n #1 ([ %p "null" #2))) (n #0 ([ %p "null" #3))) (n #2 ([ %p "null" #4))) (n #0 ([ %p "null" #5))) (n #1 ([ %p "null" #6))) (n #0 ([ %p "null" #7))) (n #0 ([ %p "null" #8))) (n #2 ([ %p "null" #9))) "null"))',
    '(= !e9 ([ %p (& (& (& (& (n #0 ([ %p "null" #0)) (n #3 ([ %p "null" #1))) (n #1 ([ %p "null" #3))) (n #2 ([ %p "null" #4))) (n #0 ([ %p "null" #6))) "null"))',
    '(= !e6 ([ %p (& (& (& (n #1 ([ %p "null" #3)) (n #1 ([ %p "null" #4))) (n #0 ([ %p "null" #8))) (n #0 ([ %p "null" #9))) "null"))',
    '(= !e3 ([ %p (n #1 ([ %p "null" #6)) "null"))',
    '(= !e5 ([ %p (& (& (& (& (& (& (& (n #1 ([ %p "null" #0)) (n #1 ([ %p "null" #2))) (n #0 ([ %p "null" #3))) (n #0 ([ %p "null" #4))) (n #2 ([ %p "null" #5))) (n #2 ([ %p "null" #6))) (n #1 ([ %p "null" #8))) (n #0 ([ %p "null" #9))) "null"))',
    '(= !e7 ([ %p (n #1 ([ %p "null" #8)) "null"))',
    '(= !e6 ([ %p (& (& (& (& (& (n #0 ([ %p "null" #0)) (n #1 ([ %p "null" #3))) (n #1 ([ %p "null" #5))) (n #1 ([ %p "null" #6))) (n #0 ([ %p "null" #7))) (n #0 ([ %p "null" #8))) "null"))',
    '(= !e8 ([ %p (& (& (& (& (& (& (& (n #0 ([ %p "null" #0)) (n #2 ([ %p "null" #1))) (n #0 ([ %p "null" #3))) (n #0 ([ %p "null" #4))) (n #2 ([ %p "null" #6))) (n #0 ([ %p "null" #7))) (n #0 ([ %p "null" #8))) (n #1 ([ %p "null" #9))) "null"))',
    '(= !e8 ([ %p (n #0 ([ %p "null" #7)) "null"))',
    '(= !e3 ([ %p (& (& (& (& (n #1 ([ %p "null" #0)) (n #3 ([ %p "null" #5))) (n #2 ([ %p "null" #6))) (n #1 ([ %p "null" #8))) (n #1 ([ %p "null" #9))) "null"))',
    '(= !e1 ([ %p (& (n #1 ([ %p "null" #6)) (n #0 ([ %p "null" #7))) "null"))',
    '(= !e1 ([ %p (& (& (& (& (& (& (& (& (& (n #0 ([ %p "null" #0)) (n #2 ([ %p "null" #1))) (n #1 ([ %p "null" #2))) (n #0 ([ %p "null" #3))) (n #2 ([ %p "null" #4))) (n #0 ([ %p "null" #5))) (n #1 ([ %p "null" #6))) (n #0 ([ %p "null" #7))) (n #0 ([ %p "null" #8))) (n #2 ([ %p "null" #9))) "null"))',
    '(= !e6 ([ %p (& (& (& (n #2 ([ %p "null" #1)) (n #1 ([ %p "null" #3))) (n #1 ([ %p "null" #4))) (n #0 ([ %p "null" #7))) "null"))',
    '(= !e7 ([ %p (& (& (n #0 ([ %p "null" #0)) (n #1 ([ %p "null" #1))) (n #3 ([ %p "null" #9))) "null"))',
    '(= !e4 ([ %p (& (n #0 ([ %p "null" #6)) (n #0 ([ %p "null" #7))) "null"))',
    '(= !e5 ([ %p (& (& (& (& (& (& (& (& (& (n #1 ([ %p "null" #0)) (n #2 ([ %p "null" #1))) (n #0 ([ %p "null" #2))) (n #1 ([ %p "null" #3))) (n #0 ([ %p "null" #4))) (n #3 ([ %p "null" #5))) (n #1 ([ %p "null" #6))) (n #0 ([ %p "null" #7))) (n #1 ([ %p "null" #8))) (n #0 ([ %p "null" #9))) "null"))',
    '(= !e6 ([ %p (n #1 ([ %p "null" #8)) "null"))',
    '(= !e3 ([ %p (& (& (& (& (& (& (n #1 ([ %p "null" #1)) (n #1 ([ %p "null" #3))) (n #1 ([ %p "null" #4))) (n #2 ([ %p "null" #5))) (n #1 ([ %p "null" #6))) (n #0 ([ %p "null" #7))) (n #3 ([ %p "null" #9))) "null"))',
    '(= !e9 ([ %p (& (& (& (& (& (& (n #0 ([ %p "null" #0)) (n #3 ([ %p "null" #1))) (n #1 ([ %p "null" #2))) (n #1 ([ %p "null" #6))) (n #0 ([ %p "null" #7))) (n #0 ([ %p "null" #8))) (n #3 ([ %p "null" #9))) "null"))',
    '(= !e7 ([ %p (& (& (& (& (& (& (& (& (& (n #0 ([ %p "null" #0)) (n #2 ([ %p "null" #1))) (n #0 ([ %p "null" #2))) (n #0 ([ %p "null" #3))) (n #2 ([ %p "null" #4))) (n #2 ([ %p "null" #5))) (n #1 ([ %p "null" #6))) (n #0 ([ %p "null" #7))) (n #0 ([ %p "null" #8))) (n #3 ([ %p "null" #9))) "null"))',
    '(= !e7 ([ %p (& (& (& (& (& (n #0 ([ %p "null" #1)) (n #1 ([ %p "null" #2))) (n #1 ([ %p "null" #4))) (n #2 ([ %p "null" #5))) (n #1 ([ %p "null" #6))) (n #0 ([ %p "null" #8))) "null"))',
    '(= !e3 ([ %p (& (& (& (& (& (n #2 ([ %p "null" #1)) (n #0 ([ %p "null" #2))) (n #0 ([ %p "null" #3))) (n #1 ([ %p "null" #4))) (n #0 ([ %p "null" #7))) (n #0 ([ %p "null" #9))) "null"))',
    '(= !e9 ([ %p (& (& (& (& (& (n #0 ([ %p "null" #0)) (n #0 ([ %p "null" #1))) (n #1 ([ %p "null" #2))) (n #1 ([ %p "null" #4))) (n #1 ([ %p "null" #6))) (n #0 ([ %p "null" #8))) "null"))',
    '(= !e8 ([ %p (& (& (n #1 ([ %p "null" #3)) (n #0 ([ %p "null" #6))) (n #0 ([ %p "null" #7))) "null"))',
    '(= !e5 ([ %p (& (& (& (& (& (& (& (& (& (n #1 ([ %p "null" #0)) (n #0 ([ %p "null" #1))) (n #0 ([ %p "null" #2))) (n #1 ([ %p "null" #3))) (n #0 ([ %p "null" #4))) (n #1 ([ %p "null" #5))) (n #1 ([ %p "null" #6))) (n #0 ([ %p "null" #7))) (n #0 ([ %p "null" #8))) (n #2 ([ %p "null" #9))) "null"))',
    '(= !e2 ([ %p (& (& (& (& (& (& (& (& (n #0 ([ %p "null" #0)) (n #1 ([ %p "null" #1))) (n #0 ([ %p "null" #2))) (n #0 ([ %p "null" #3))) (n #0 ([ %p "null" #4))) (n #1 ([ %p "null" #5))) (n #0 ([ %p "null" #7))) (n #0 ([ %p "null" #8))) (n #3 ([ %p "null" #9))) "null"))',
    '(= !e4 ([ %p (& (& (& (n #1 ([ %p "null" #2)) (n #1 ([ %p "null" #5))) (n #2 ([ %p "null" #6))) (n #0 ([ %p "null" #7))) "null"))',
    '(= !e0 ([ %p (& (n #1 ([ %p "null" #8)) (n #1 ([ %p "null" #9))) "null"))',
    '(= !e8 ([ %p (& (& (& (& (& (& (& (& (n #0 ([ %p "null" #0)) (n #1 ([ %p "null" #1))) (n #0 ([ %p "null" #2))) (n #0 ([ %p "null" #3))) (n #0 ([ %p "null" #4))) (n #1 ([ %p "null" #5))) (n #0 ([ %p "null" #7))) (n #0 ([ %p "null" #8))) (n #3 ([ %p "null" #9))) "null"))',
    '(= !e1 ([ %p (& (& (& (& (& (& (n #1 ([ %p "null" #0)) (n #0 ([ %p "null" #2))) (n #1 ([ %p "null" #3))) (n #1 ([ %p "null" #4))) (n #2 ([ %p "null" #5))) (n #2 ([ %p "null" #6))) (n #1 ([ %p "null" #8))) "null"))',
    '(= !e4 ([ %p (& (& (& (& (& (& (& (n #0 ([ %p "null" #0)) (n #2 ([ %p "null" #1))) (n #0 ([ %p "null" #3))) (n #1 ([ %p "null" #4))) (n #2 ([ %p "null" #5))) (n #1 ([ %p "null" #6))) (n #1 ([ %p "null" #8))) (n #1 ([ %p "null" #9))) "null"))',
    '(= !e8 ([ %p (& (& (& (n #2 ([ %p "null" #1)) (n #1 ([ %p "null" #3))) (n #1 ([ %p "null" #4))) (n #0 ([ %p "null" #7))) "null"))',
    '(= !e2 ([ %p (& (& (& (& (& (& (& (n #0 ([ %p "null" #0)) (n #0 ([ %p "null" #1))) (n #0 ([ %p "null" #3))) (n #1 ([ %p "null" #4))) (n #0 ([ %p "null" #5))) (n #0 ([ %p "null" #6))) (n #0 ([ %p "null" #7))) (n #1 ([ %p "null" #8))) "null"))',
    '(= !e0 ([ %p (& (& (& (& (& (n #2 ([ %p "null" #1)) (n #0 ([ %p "null" #2))) (n #0 ([ %p "null" #3))) (n #3 ([ %p "null" #5))) (n #2 ([ %p "null" #6))) (n #1 ([ %p "null" #9))) "null"))',
    '(= !e8 ([ %p (& (& (& (& (& (& (& (& (n #0 ([ %p "null" #0)) (n #0 ([ %p "null" #1))) (n #1 ([ %p "null" #2))) (n #1 ([ %p "null" #3))) (n #2 ([ %p "null" #4))) (n #2 ([ %p "null" #6))) (n #0 ([ %p "null" #7))) (n #1 ([ %p "null" #8))) (n #1 ([ %p "null" #9))) "null"))',
    '(= !e3 ([ %p (& (n #1 ([ %p "null" #1)) (n #1 ([ %p "null" #6))) "null"))',
    '(= !e5 ([ %p (& (n #0 ([ %p "null" #3)) (n #2 ([ %p "null" #9))) "null"))',
    '(= !e6 ([ %p (& (& (& (& (& (n #0 ([ %p "null" #0)) (n #1 ([ %p "null" #2))) (n #0 ([ %p "null" #5))) (n #1 ([ %p "null" #6))) (n #1 ([ %p "null" #8))) (n #0 ([ %p "null" #9))) "null"))',
    '(= !e0 ([ %p (& (& (& (& (& (n #0 ([ %p "null" #0)) (n #1 ([ %p "null" #3))) (n #2 ([ %p "null" #5))) (n #2 ([ %p "null" #6))) (n #0 ([ %p "null" #7))) (n #3 ([ %p "null" #9))) "null"))',
    '(= !e1 ([ %p (& (n #1 ([ %p "null" #0)) (n #1 ([ %p "null" #8))) "null"))',
    '(= !e8 ([ %p (& (& (& (n #2 ([ %p "null" #1)) (n #0 ([ %p "null" #5))) (n #1 ([ %p "null" #8))) (n #0 ([ %p "null" #9))) "null"))',
    '(= !e6 ([ %p (& (& (& (& (& (& (& (& (& (n #0 ([ %p "null" #0)) (n #2 ([ %p "null" #1))) (n #0 ([ %p "null" #2))) (n #1 ([ %p "null" #3))) (n #1 ([ %p "null" #4))) (n #2 ([ %p "null" #5))) (n #2 ([ %p "null" #6))) (n #0 ([ %p "null" #7))) (n #0 ([ %p "null" #8))) (n #2 ([ %p "null" #9))) "null"))',
    '(= !e1 ([ %p (& (& (& (& (& (n #3 ([ %p "null" #1)) (n #1 ([ %p "null" #2))) (n #1 ([ %p "null" #3))) (n #0 ([ %p "null" #6))) (n #0 ([ %p "null" #7))) (n #1 ([ %p "null" #9))) "null"))',
    '(= !e7 ([ %p (& (& (& (& (& (& (& (n #0 ([ %p "null" #0)) (n #2 ([ %p "null" #1))) (n #0 ([ %p "null" #3))) (n #0 ([ %p "null" #4))) (n #2 ([ %p "null" #6))) (n #0 ([ %p "null" #7))) (n #0 ([ %p "null" #8))) (n #1 ([ %p "null" #9))) "null"))',
    '(= !e3 ([ %p (& (& (& (& (& (& (& (& (& (n #0 ([ %p "null" #0)) (n #2 ([ %p "null" #1))) (n #1 ([ %p "null" #2))) (n #0 ([ %p "null" #3))) (n #1 ([ %p "null" #4))) (n #2 ([ %p "null" #5))) (n #0 ([ %p "null" #6))) (n #0 ([ %p "null" #7))) (n #0 ([ %p "null" #8))) (n #3 ([ %p "null" #9))) "null"))',
    '(= !e9 ([ %p (& (n #1 ([ %p "null" #2)) (n #0 ([ %p "null" #4))) "null"))',
    '(= !e2 ([ %p (& (& (n #0 ([ %p "null" #5)) (n #1 ([ %p "null" #8))) (n #1 ([ %p "null" #9))) "null"))',
    '(= !e4 ([ %p (& (& (& (n #1 ([ %p "null" #1)) (n #0 ([ %p "null" #4))) (n #1 ([ %p "null" #6))) (n #0 ([ %p "null" #7))) "null"))',
    '(= !e6 ([ %p (& (n #1 ([ %p "null" #2)) (n #0 ([ %p "null" #4))) "null"))',
    '(= !e8 ([ %p (& (& (& (& (& (& (& (& (& (n #0 ([ %p "null" #0)) (n #2 ([ %p "null" #1))) (n #1 ([ %p "null" #2))) (n #0 ([ %p "null" #3))) (n #1 ([ %p "null" #4))) (n #2 ([ %p "null" #5))) (n #0 ([ %p "null" #6))) (n #0 ([ %p "null" #7))) (n #0 ([ %p "null" #8))) (n #3 ([ %p "null" #9))) "null"))',
    '(= !e0 ([ %p (& (& (& (& (& (& (& (& (n #0 ([ %p "null" #0)) (n #0 ([ %p "null" #2))) (n #1 ([ %p "null" #3))) (n #2 ([ %p "null" #4))) (n #2 ([ %p "null" #5))) (n #2 ([ %p "null" #6))) (n #0 ([ %p "null" #7))) (n #0 ([ %p "null" #8))) (n #2 ([ %p "null" #9))) "null"))',
    '(= !e7 ([ %p (& (& (& (& (n #1 ([ %p "null" #0)) (n #1 ([ %p "null" #2))) (n #1 ([ %p "null" #5))) (n #0 ([ %p "null" #8))) (n #2 ([ %p "null" #9))) "null"))',
    '(= !e4 ([ %p (& (& (& (& (& (& (& (& (n #1 ([ %p "null" #0)) (n #2 ([ %p "null" #1))) (n #1 ([ %p "null" #2))) (n #0 ([ %p "null" #3))) (n #3 ([ %p "null" #5))) (n #0 ([ %p "null" #6))) (n #0 ([ %p "null" #7))) (n #1 ([ %p "null" #8))) (n #0 ([ %p "null" #9))) "null"))',
    '(= !e7 ([ %p (n #1 ([ %p "null" #0)) "null"))',
    '(= !e7 ([ %p (& (& (& (& (& (& (& (& (n #1 ([ %p "null" #0)) (n #0 ([ %p "null" #2))) (n #1 ([ %p "null" #3))) (n #0 ([ %p "null" #4))) (n #3 ([ %p "null" #5))) (n #0 ([ %p "null" #6))) (n #0 ([ %p "null" #7))) (n #0 ([ %p "null" #8))) (n #1 ([ %p "null" #9))) "null"))',
    '(= !e2 ([ %p (& (& (& (& (& (n #2 ([ %p "null" #1)) (n #0 ([ %p "null" #2))) (n #0 ([ %p "null" #3))) (n #1 ([ %p "null" #4))) (n #0 ([ %p "null" #7))) (n #0 ([ %p "null" #9))) "null"))',
    '(= !e2 ([ %p (& (& (& (& (& (n #1 ([ %p "null" #0)) (n #2 ([ %p "null" #1))) (n #1 ([ %p "null" #2))) (n #2 ([ %p "null" #5))) (n #1 ([ %p "null" #6))) (n #0 ([ %p "null" #9))) "null"))',
    '(= !e5 ([ %p (& (& (& (& (& (& (& (& (n #1 ([ %p "null" #0)) (n #0 ([ %p "null" #2))) (n #1 ([ %p "null" #3))) (n #0 ([ %p "null" #4))) (n #3 ([ %p "null" #5))) (n #0 ([ %p "null" #6))) (n #0 ([ %p "null" #7))) (n #0 ([ %p "null" #8))) (n #1 ([ %p "null" #9))) "null"))',
    '(= !e1 ([ %p (& (& (& (& (& (& (& (& (n #0 ([ %p "null" #0)) (n #2 ([ %p "null" #1))) (n #1 ([ %p "null" #2))) (n #0 ([ %p "null" #3))) (n #0 ([ %p "null" #5))) (n #1 ([ %p "null" #6))) (n #0 ([ %p "null" #7))) (n #1 ([ %p "null" #8))) (n #2 ([ %p "null" #9))) "null"))',
    '(= !e3 ([ %p (& (& (& (& (& (& (& (& (n #0 ([ %p "null" #0)) (n #3 ([ %p "null" #1))) (n #0 ([ %p "null" #2))) (n #0 ([ %p "null" #3))) (n #0 ([ %p "null" #4))) (n #1 ([ %p "null" #5))) (n #2 ([ %p "null" #6))) (n #0 ([ %p "null" #7))) (n #0 ([ %p "null" #9))) "null"))',
    '(= !e3 ([ %p (& (& (& (n #1 ([ %p "null" #0)) (n #2 ([ %p "null" #1))) (n #1 ([ %p "null" #2))) (n #0 ([ %p "null" #3))) "null"))',
    '(= !e3 ([ %p (& (& (& (& (& (& (& (& (n #0 ([ %p "null" #0)) (n #2 ([ %p "null" #1))) (n #1 ([ %p "null" #2))) (n #1 ([ %p "null" #3))) (n #1 ([ %p "null" #4))) (n #1 ([ %p "null" #5))) (n #2 ([ %p "null" #6))) (n #0 ([ %p "null" #7))) (n #1 ([ %p "null" #8))) "null"))',
    '(= !e8 ([ %p (& (& (& (& (n #2 ([ %p "null" #1)) (n #0 ([ %p "null" #3))) (n #0 ([ %p "null" #4))) (n #3 ([ %p "null" #5))) (n #0 ([ %p "null" #9))) "null"))',
    '(= !e2 ([ %p (& (& (& (& (& (& (& (& (& (n #1 ([ %p "null" #0)) (n #2 ([ %p "null" #1))) (n #1 ([ %p "null" #2))) (n #1 ([ %p "null" #3))) (n #0 ([ %p "null" #4))) (n #0 ([ %p "null" #5))) (n #1 ([ %p "null" #6))) (n #0 ([ %p "null" #7))) (n #0 ([ %p "null" #8))) (n #0 ([ %p "null" #9))) "null"))',
    '(= !e0 ([ %p (n #0 ([ %p "null" #7)) "null"))',
    '(= !e4 ([ %p (& (& (n #0 ([ %p "null" #1)) (n #1 ([ %p "null" #4))) (n #3 ([ %p "null" #5))) "null"))',
    '(= !e6 ([ %p (& (& (& (& (& (& (n #1 ([ %p "null" #0)) (n #0 ([ %p "null" #2))) (n #1 ([ %p "null" #3))) (n #1 ([ %p "null" #4))) (n #2 ([ %p "null" #5))) (n #2 ([ %p "null" #6))) (n #1 ([ %p "null" #8))) "null"))',
    '(= !e6 ([ %p (& (& (& (& (& (& (& (& (n #1 ([ %p "null" #0)) (n #1 ([ %p "null" #2))) (n #0 ([ %p "null" #3))) (n #0 ([ %p "null" #4))) (n #3 ([ %p "null" #5))) (n #2 ([ %p "null" #6))) (n #0 ([ %p "null" #7))) (n #0 ([ %p "null" #8))) (n #1 ([ %p "null" #9))) "null"))',
    '(= !e1 ([ %p (& (& (& (& (& (n #1 ([ %p "null" #0)) (n #0 ([ %p "null" #2))) (n #0 ([ %p "null" #3))) (n #0 ([ %p "null" #4))) (n #3 ([ %p "null" #5))) (n #0 ([ %p "null" #6))) "null"))',
    '(= !e4 ([ %p (& (& (n #0 ([ %p "null" #0)) (n #1 ([ %p "null" #1))) (n #3 ([ %p "null" #9))) "null"))',
    '(= !e0 ([ %p (n #1 ([ %p "null" #0)) "null"))',
    '(= !e2 ([ %p (& (& (& (& (& (& (& (& (n #0 ([ %p "null" #0)) (n #1 ([ %p "null" #1))) (n #0 ([ %p "null" #2))) (n #0 ([ %p "null" #3))) (n #0 ([ %p "null" #4))) (n #1 ([ %p "null" #5))) (n #0 ([ %p "null" #7))) (n #0 ([ %p "null" #8))) (n #3 ([ %p "null" #9))) "null"))',
    '(= !e5 ([ %p (& (& (& (& (& (n #0 ([ %p "null" #0)) (n #0 ([ %p "null" #1))) (n #1 ([ %p "null" #2))) (n #1 ([ %p "null" #4))) (n #1 ([ %p "null" #6))) (n #0 ([ %p "null" #8))) "null"))',
    '(= !e8 ([ %p (& (& (n #1 ([ %p "null" #2)) (n #0 ([ %p "null" #3))) (n #1 ([ %p "null" #6))) "null"))',
    '(= !e3 ([ %p (n #0 ([ %p "null" #8)) "null"))',
    '(= !e3 ([ %p (& (& (& (& (n #2 ([ %p "null" #1)) (n #2 ([ %p "null" #4))) (n #1 ([ %p "null" #5))) (n #0 ([ %p "null" #7))) (n #1 ([ %p "null" #8))) "null"))',
    '(= !e5 ([ %p (n #0 ([ %p "null" #7)) "null"))',
    '(= !e2 ([ %p (& (n #1 ([ %p "null" #4)) (n #1 ([ %p "null" #8))) "null"))',
    '(= !e9 ([ %p (& (& (n #0 ([ %p "null" #2)) (n #1 ([ %p "null" #3))) (n #0 ([ %p "null" #8))) "null"))',
    '(= !e3 ([ %p (& (& (& (& (& (& (& (& (& (n #0 ([ %p "null" #0)) (n #2 ([ %p "null" #1))) (n #1 ([ %p "null" #2))) (n #0 ([ %p "null" #3))) (n #1 ([ %p "null" #4))) (n #2 ([ %p "null" #5))) (n #0 ([ %p "null" #6))) (n #0 ([ %p "null" #7))) (n #0 ([ %p "null" #8))) (n #3 ([ %p "null" #9))) "null"))',
    '(= !e3 ([ %p (& (& (& (& (& (& (n #0 ([ %p "null" #1)) (n #1 ([ %p "null" #2))) (n #0 ([ %p "null" #4))) (n #3 ([ %p "null" #5))) (n #1 ([ %p "null" #6))) (n #0 ([ %p "null" #8))) (n #1 ([ %p "null" #9))) "null"))',
    '(= !e7 ([ %p (& (& (& (n #0 ([ %p "null" #0)) (n #0 ([ %p "null" #4))) (n #2 ([ %p "null" #5))) (n #1 ([ %p "null" #8))) "null"))',
    '(= !e4 ([ %p (n #1 ([ %p "null" #6)) "null"))',
    '(= !e9 ([ %p (& (& (& (& (& (& (& (& (n #1 ([ %p "null" #0)) (n #0 ([ %p "null" #1))) (n #1 ([ %p "null" #2))) (n #1 ([ %p "null" #3))) (n #2 ([ %p "null" #4))) (n #2 ([ %p "null" #5))) (n #0 ([ %p "null" #7))) (n #0 ([ %p "null" #8))) (n #1 ([ %p "null" #9))) "null"))',
    '(= !e1 ([ %p (& (& (& (n #1 ([ %p "null" #1)) (n #0 ([ %p "null" #2))) (n #1 ([ %p "null" #6))) (n #2 ([ %p "null" #9))) "null"))',
    '(= !e8 ([ %p (& (& (& (& (& (& (& (& (& (n #0 ([ %p "null" #0)) (n #2 ([ %p "null" #1))) (n #0 ([ %p "null" #2))) (n #0 ([ %p "null" #3))) (n #1 ([ %p "null" #4))) (n #0 ([ %p "null" #5))) (n #2 ([ %p "null" #6))) (n #0 ([ %p "null" #7))) (n #0 ([ %p "null" #8))) (n #1 ([ %p "null" #9))) "null"))',
    '(= !e6 ([ %p (& (n #1 ([ %p "null" #3)) (n #0 ([ %p "null" #7))) "null"))',
    '(= !e8 ([ %p (& (& (& (& (& (& (n #0 ([ %p "null" #0)) (n #0 ([ %p "null" #2))) (n #0 ([ %p "null" #3))) (n #2 ([ %p "null" #6))) (n #0 ([ %p "null" #7))) (n #0 ([ %p "null" #8))) (n #2 ([ %p "null" #9))) "null"))',
    '(= !e7 ([ %p (n #1 ([ %p "null" #0)) "null"))',
    '(= !e5 ([ %p (& (& (n #0 ([ %p "null" #1)) (n #1 ([ %p "null" #4))) (n #3 ([ %p "null" #5))) "null"))',
    '(= !e1 ([ %p (& (& (& (& (& (n #0 ([ %p "null" #0)) (n #0 ([ %p "null" #2))) (n #1 ([ %p "null" #3))) (n #1 ([ %p "null" #4))) (n #0 ([ %p "null" #6))) (n #0 ([ %p "null" #9))) "null"))',
    '(= !e3 ([ %p (& (& (& (& (& (n #0 ([ %p "null" #0)) (n #2 ([ %p "null" #1))) (n #0 ([ %p "null" #2))) (n #0 ([ %p "null" #5))) (n #2 ([ %p "null" #6))) (n #1 ([ %p "null" #8))) "null"))',
    '(= !e6 ([ %p (& (& (& (n #1 ([ %p "null" #2)) (n #0 ([ %p "null" #3))) (n #1 ([ %p "null" #6))) (n #0 ([ %p "null" #7))) "null"))',
    '(= !e1 ([ %p (& (& (& (& (& (& (& (n #0 ([ %p "null" #0)) (n #1 ([ %p "null" #1))) (n #1 ([ %p "null" #2))) (n #1 ([ %p "null" #3))) (n #0 ([ %p "null" #5))) (n #2 ([ %p "null" #6))) (n #0 ([ %p "null" #7))) (n #2 ([ %p "null" #9))) "null"))',
    '(= !e9 ([ %p (& (& (& (& (& (& (& (n #0 ([ %p "null" #0)) (n #0 ([ %p "null" #1))) (n #0 ([ %p "null" #2))) (n #2 ([ %p "null" #5))) (n #0 ([ %p "null" #6))) (n #0 ([ %p "null" #7))) (n #0 ([ %p "null" #8))) (n #2 ([ %p "null" #9))) "null"))',
    '(= !e3 ([ %p (& (& (& (n #3 ([ %p "null" #1)) (n #2 ([ %p "null" #5))) (n #0 ([ %p "null" #7))) (n #3 ([ %p "null" #9))) "null"))',
    '(= !e6 ([ %p (& (& (& (& (& (& (& (n #1 ([ %p "null" #0)) (n #2 ([ %p "null" #1))) (n #0 ([ %p "null" #2))) (n #1 ([ %p "null" #3))) (n #2 ([ %p "null" #4))) (n #0 ([ %p "null" #7))) (n #1 ([ %p "null" #8))) (n #3 ([ %p "null" #9))) "null"))',
    '(= !e6 ([ %p (& (& (& (& (& (& (n #0 ([ %p "null" #0)) (n #1 ([ %p "null" #1))) (n #1 ([ %p "null" #2))) (n #0 ([ %p "null" #3))) (n #0 ([ %p "null" #4))) (n #2 ([ %p "null" #5))) (n #0 ([ %p "null" #7))) "null"))',
    '(= !e7 ([ %p (& (& (& (& (& (& (& (& (n #0 ([ %p "null" #0)) (n #0 ([ %p "null" #1))) (n #1 ([ %p "null" #2))) (n #1 ([ %p "null" #3))) (n #2 ([ %p "null" #4))) (n #2 ([ %p "null" #6))) (n #0 ([ %p "null" #7))) (n #1 ([ %p "null" #8))) (n #1 ([ %p "null" #9))) "null"))',
    '(= !e1 ([ %p (& (& (& (n #1 ([ %p "null" #3)) (n #1 ([ %p "null" #4))) (n #0 ([ %p "null" #8))) (n #0 ([ %p "null" #9))) "null"))',
    '(= !e6 ([ %p (& (& (& (& (& (& (& (n #1 ([ %p "null" #0)) (n #1 ([ %p "null" #2))) (n #0 ([ %p "null" #3))) (n #1 ([ %p "null" #4))) (n #2 ([ %p "null" #6))) (n #0 ([ %p "null" #7))) (n #1 ([ %p "null" #8))) (n #0 ([ %p "null" #9))) "null"))',
    '(= !e2 ([ %p (& (& (& (& (& (& (& (n #1 ([ %p "null" #0)) (n #1 ([ %p "null" #1))) (n #1 ([ %p "null" #3))) (n #2 ([ %p "null" #4))) (n #2 ([ %p "null" #5))) (n #0 ([ %p "null" #6))) (n #0 ([ %p "null" #7))) (n #0 ([ %p "null" #8))) "null"))',
    '(= !e5 ([ %p (& (& (& (& (& (& (n #1 ([ %p "null" #0)) (n #0 ([ %p "null" #2))) (n #1 ([ %p "null" #3))) (n #2 ([ %p "null" #4))) (n #3 ([ %p "null" #5))) (n #0 ([ %p "null" #8))) (n #2 ([ %p "null" #9))) "null"))',
    '(= !e3 ([ %p (& (& (& (& (& (n #1 ([ %p "null" #0)) (n #3 ([ %p "null" #1))) (n #0 ([ %p "null" #2))) (n #1 ([ %p "null" #4))) (n #0 ([ %p "null" #7))) (n #2 ([ %p "null" #9))) "null"))',
    '(= !e7 ([ %p (& (& (& (& (& (n #0 ([ %p "null" #0)) (n #1 ([ %p "null" #2))) (n #2 ([ %p "null" #4))) (n #2 ([ %p "null" #6))) (n #0 ([ %p "null" #8))) (n #2 ([ %p "null" #9))) "null"))',
    '(= !e6 ([ %p (& (& (& (& (& (& (& (& (& (n #0 ([ %p "null" #0)) (n #0 ([ %p "null" #1))) (n #1 ([ %p "null" #2))) (n #0 ([ %p "null" #3))) (n #1 ([ %p "null" #4))) (n #3 ([ %p "null" #5))) (n #0 ([ %p "null" #6))) (n #0 ([ %p "null" #7))) (n #0 ([ %p "null" #8))) (n #0 ([ %p "null" #9))) "null"))',
    '(= !e1 ([ %p (& (& (& (& (& (n #2 ([ %p "null" #1)) (n #0 ([ %p "null" #2))) (n #0 ([ %p "null" #3))) (n #3 ([ %p "null" #5))) (n #2 ([ %p "null" #6))) (n #1 ([ %p "null" #9))) "null"))',
    '(= !e9 ([ %p (& (& (n #0 ([ %p "null" #1)) (n #1 ([ %p "null" #4))) (n #3 ([ %p "null" #5))) "null"))',
    '(= !e4 ([ %p (& (& (& (& (& (& (& (& (n #1 ([ %p "null" #0)) (n #0 ([ %p "null" #1))) (n #1 ([ %p "null" #2))) (n #1 ([ %p "null" #3))) (n #2 ([ %p "null" #4))) (n #2 ([ %p "null" #5))) (n #0 ([ %p "null" #7))) (n #0 ([ %p "null" #8))) (n #1 ([ %p "null" #9))) "null"))',
    '(= !e0 ([ %p (n #0 ([ %p "null" #7)) "null"))',
    '(= !e6 ([ %p (& (& (& (& (& (& (& (n #0 ([ %p "null" #0)) (n #0 ([ %p "null" #1))) (n #0 ([ %p "null" #2))) (n #2 ([ %p "null" #5))) (n #0 ([ %p "null" #6))) (n #0 ([ %p "null" #7))) (n #0 ([ %p "null" #8))) (n #2 ([ %p "null" #9))) "null"))',
    '(= !e5 ([ %p (& (& (& (& (& (n #0 ([ %p "null" #0)) (n #1 ([ %p "null" #2))) (n #1 ([ %p "null" #3))) (n #1 ([ %p "null" #4))) (n #2 ([ %p "null" #5))) (n #1 ([ %p "null" #9))) "null"))',
    '(= !e1 ([ %p (& (& (& (& (n #2 ([ %p "null" #1)) (n #0 ([ %p "null" #4))) (n #0 ([ %p "null" #7))) (n #0 ([ %p "null" #8))) (n #3 ([ %p "null" #9))) "null"))',
    '(= !e8 ([ %p (n #2 ([ %p "null" #1)) "null"))',
    '(= !e4 ([ %p (n #2 ([ %p "null" #6)) "null"))',
    '(= !e6 ([ %p (& (& (& (& (& (& (& (& (& (n #0 ([ %p "null" #0)) (n #1 ([ %p "null" #1))) (n #0 ([ %p "null" #2))) (n #0 ([ %p "null" #3))) (n #0 ([ %p "null" #4))) (n #1 ([ %p "null" #5))) (n #1 ([ %p "null" #6))) (n #0 ([ %p "null" #7))) (n #0 ([ %p "null" #8))) (n #3 ([ %p "null" #9))) "null"))',
    '(= !e0 ([ %p (& (& (& (& (& (n #0 ([ %p "null" #0)) (n #2 ([ %p "null" #1))) (n #0 ([ %p "null" #2))) (n #0 ([ %p "null" #5))) (n #2 ([ %p "null" #6))) (n #1 ([ %p "null" #8))) "null"))',
    '(= !e7 ([ %p (n #0 ([ %p "null" #7)) "null"))',
    '(= !e2 ([ %p (& (n #3 ([ %p "null" #1)) (n #0 ([ %p "null" #7))) "null"))',
    '(= !e8 ([ %p (n #1 ([ %p "null" #0)) "null"))',
    '(= !e6 ([ %p (& (& (& (& (& (& (& (& (& (n #1 ([ %p "null" #0)) (n #2 ([ %p "null" #1))) (n #0 ([ %p "null" #2))) (n #1 ([ %p "null" #3))) (n #1 ([ %p "null" #4))) (n #2 ([ %p "null" #5))) (n #0 ([ %p "null" #6))) (n #0 ([ %p "null" #7))) (n #0 ([ %p "null" #8))) (n #3 ([ %p "null" #9))) "null"))',
    '(= !e4 ([ %p (& (& (& (n #1 ([ %p "null" #2)) (n #1 ([ %p "null" #5))) (n #2 ([ %p "null" #6))) (n #0 ([ %p "null" #7))) "null"))',
    '(= !e0 ([ %p (& (& (& (& (& (& (n #1 ([ %p "null" #1)) (n #1 ([ %p "null" #3))) (n #1 ([ %p "null" #4))) (n #2 ([ %p "null" #5))) (n #1 ([ %p "null" #6))) (n #0 ([ %p "null" #7))) (n #3 ([ %p "null" #9))) "null"))',
    '(= !e5 ([ %p (& (& (& (& (& (& (n #0 ([ %p "null" #0)) (n #3 ([ %p "null" #1))) (n #1 ([ %p "null" #2))) (n #1 ([ %p "null" #6))) (n #0 ([ %p "null" #7))) (n #0 ([ %p "null" #8))) (n #3 ([ %p "null" #9))) "null"))',
    '(= !e6 ([ %p (& (& (& (& (& (n #0 ([ %p "null" #0)) (n #1 ([ %p "null" #1))) (n #0 ([ %p "null" #4))) (n #1 ([ %p "null" #5))) (n #2 ([ %p "null" #6))) (n #0 ([ %p "null" #8))) "null"))',
    '(= !e1 ([ %p (& (n #3 ([ %p "null" #1)) (n #0 ([ %p "null" #7))) "null"))',
    '(= !e4 ([ %p (& (& (& (& (& (& (& (& (& (n #0 ([ %p "null" #0)) (n #2 ([ %p "null" #1))) (n #0 ([ %p "null" #2))) (n #0 ([ %p "null" #3))) (n #2 ([ %p "null" #4))) (n #2 ([ %p "null" #5))) (n #1 ([ %p "null" #6))) (n #0 ([ %p "null" #7))) (n #0 ([ %p "null" #8))) (n #3 ([ %p "null" #9))) "null"))',
    '(= !e3 ([ %p (& (& (& (& (n #1 ([ %p "null" #1)) (n #0 ([ %p "null" #2))) (n #3 ([ %p "null" #5))) (n #1 ([ %p "null" #6))) (n #0 ([ %p "null" #7))) "null"))',
    '(= !e3 ([ %p (& (& (& (& (& (& (& (& (& (n #0 ([ %p "null" #0)) (n #2 ([ %p "null" #1))) (n #0 ([ %p "null" #2))) (n #1 ([ %p "null" #3))) (n #1 ([ %p "null" #4))) (n #2 ([ %p "null" #5))) (n #2 ([ %p "null" #6))) (n #0 ([ %p "null" #7))) (n #0 ([ %p "null" #8))) (n #2 ([ %p "null" #9))) "null"))',
    '(= !e7 ([ %p (& (& (& (& (& (& (n #0 ([ %p "null" #0)) (n #2 ([ %p "null" #1))) (n #1 ([ %p "null" #2))) (n #0 ([ %p "null" #3))) (n #2 ([ %p "null" #4))) (n #1 ([ %p "null" #6))) (n #0 ([ %p "null" #8))) "null"))',
    '(= !e1 ([ %p (& (& (& (n #1 ([ %p "null" #0)) (n #1 ([ %p "null" #3))) (n #0 ([ %p "null" #7))) (n #1 ([ %p "null" #8))) "null"))',
]


class Basic(unittest.TestCase):
    def tearDown(self):
        h2o.check_sandbox_for_errors()

    @classmethod
    def setUpClass(cls):
        global SEED
        SEED = h2o.setup_random_seed()
        h2o.init(1, base_port=54333)

    @classmethod
    def tearDownClass(cls):
        h2o.tear_down_cloud()

    def test_rapids_basic(self):
        bucket = 'home-0xdiag-datasets'
        csvPathname = 'standard/covtype.data'
        hexKey = 'p'
        parseResult = h2i.import_parse(bucket=bucket, path=csvPathname, schema='put', hex_key=hexKey)

        keys = []
        for execExpr in exprList:
            r = re.match ('\(= \!([a-zA-Z0-9_]+) ', execExpr)
            resultKey = r.group(1)
            execResult, result = h2e.exec_expr(h2o.nodes[0], execExpr, resultKey=resultKey, timeoutSecs=4)
            if DO_ROLLUP:
                h2o_cmd.runInspect(key=resultKey)
            # rows might be zero!
            if execResult['num_rows'] or execResult['num_cols']:
                keys.append(execExpr)
            else:
                h2p.yellow_print("\nNo key created?\n", dump_json(execResult))

        print "\nExpressions that created keys. Shouldn't all of these expressions create keys"

        for k in keys:
            print k

        h2o.check_sandbox_for_errors()

if __name__ == '__main__':
    h2o.unit_main()
