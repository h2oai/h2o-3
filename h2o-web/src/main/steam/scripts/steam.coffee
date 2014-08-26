# TODO
# link$ _.onRouteFailed, (address) -> console.error "Not found: #{address}"
unless exports?
  $ ->
    window.steam = steam = Steam.Application do Steam.ApplicationContext
    ko.applyBindings steam
    steam.context.ready()


