Steam.Cache = (_) ->
  _cache = {}

  link$ _.putIntoCache, (key, value) -> _cache[key] = value
  link$ _.getFromCache, (key) -> _cache[key]
  link$ _.removeFromCache, (key) -> delete _cache[key]
