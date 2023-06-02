from h2o.utils.threading import local_context, local_env


def test_local_context_in_async_loop():
    import asyncio
    
    async def fun(sleep_sec):
        myenv_res = dict()
        
        def _collect_envs(where):
            myenv_res[where] = local_env('myenv')
        
        _collect_envs("before_context")
        with local_context(myenv=sleep_sec):
            _collect_envs("before_sleep")
            await asyncio.sleep(sleep_sec)
            _collect_envs("after_sleep")
        _collect_envs("after_context")
        return myenv_res
    
    async def main():
        results = await asyncio.gather(
            fun(1),
            fun(2),
            fun(1),
            fun(2),
            fun(1),
            fun(2),
        )
        print(results)
        assert results == [
            dict(
                before_context=None,
                before_sleep=1,
                after_sleep=1,
                after_context=None
            ),
            dict(
                before_context=None,
                before_sleep=2,
                after_sleep=2,
                after_context=None
            ),
            dict(
                before_context=None,
                before_sleep=1,
                after_sleep=1,
                after_context=None
            ),
            dict(
                before_context=None,
                before_sleep=2,
                after_sleep=2,
                after_context=None
            ),
            dict(
                before_context=None,
                before_sleep=1,
                after_sleep=1,
                after_context=None
            ),
            dict(
                before_context=None,
                before_sleep=2,
                after_sleep=2,
                after_context=None
            ),
        ]
        
    asyncio.run(main())
        
