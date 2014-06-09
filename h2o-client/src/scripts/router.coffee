Steam.Router = (_, routes) ->

  #
  # Setup crossroads.js
  #

  # Signal dispatched every time that crossroads.parse find a route that matches the request.
  crossroads.routed.add (route, data) -> _.onRouteSucceeded route

  # Signal dispatched every time that crossroads.parse can't find a route that matches the request.
  crossroads.bypassed.add (route) -> _.onRouteFailed route

  # Add all routes to crosroads.
  for route, handler of routes
    crossroads.addRoute route, handler


  #
  # Setup hasher.js
  # 
   
  # See: Using Hasher together with Crossroads.js
  # https://github.com/millermedeiros/Hasher
  notifyCrossroads = (newHash, oldHash) -> crossroads.parse newHash
  hasher.initialized.add notifyCrossroads # parse initial hash
  hasher.changed.add notifyCrossroads  #parse hash changes

  #
  # Setup services 
  #
   
  link$ _.route, hasher.setHash

  link$ _.getRoute, hasher.getHash

  link$ _.setRoute, (address) ->
    # See: Setting hash value without dispatching changed signal
    # https://github.com/millermedeiros/Hasher
    hasher.changed.active = no
    hasher.setHash address
    hasher.changed.active = yes


  # Start listening for history change
  do hasher.init

  return
