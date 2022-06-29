import sys
sys.path.insert(1,"../../")

from h2o.utils.threading import local_context, local_env

from tests import pyunit_utils as pu


def test_local_context():
    myenv_res = dict()
    my2env_res = dict()
    my3env_res = dict()
    
    def _collect_envs(where):
        myenv_res[where] = local_env('myenv')
        my2env_res[where] = local_env('my2env')
        my3env_res[where] = local_env('my3env')
        
    _collect_envs("before")
    with local_context(myenv='first_level', my2env="only_first"):
        _collect_envs("before_nested")
        with local_context(myenv='second_level', my3env="only_second"):
            _collect_envs("inside_nested")
        _collect_envs("after_nested")
    _collect_envs("after")

    assert myenv_res == dict(
        before=None,
        before_nested="first_level",
        inside_nested="second_level",
        after_nested="first_level",
        after=None
    )
    assert my2env_res == dict(
        before=None,
        before_nested="only_first",
        inside_nested="only_first",
        after_nested="only_first",
        after=None
    )
    assert my3env_res == dict(
        before=None,
        before_nested=None,
        inside_nested="only_second",
        after_nested=None,
        after=None
    )
 
    
tests = [test_local_context]


try:
    from tests.testdir_utils.optional_threading import test_local_context_in_async_loop
    if sys.version_info > (3, 7):  # we need asyncio.run
        tests.append(test_local_context_in_async_loop)
    
except SyntaxError:
    assert sys.version_info < (3,)  # no async, await primitives before that


pu.run_tests(tests)    
