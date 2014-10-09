Steam.AdminListView = (_) ->
  _items = do nodes$
  _cloudView = null
  _timelineView = null
  _perfbarView = null

  displayItem = (item) ->
    if item
      item.load()
    else
      _.displayEmpty()
    _.inspect null

  displayActiveItem = ->
    activeItem = find _items(), (item) -> item.isActive()
    if activeItem
      displayItem activeItem
    else
      activateAndDisplayItem head _items()

  activateAndDisplayItem = (item) ->
    for other in _items()
      if other is item
        other.isActive yes
      else
        other.isActive no

    displayItem item

  createItem = (title, caption, action) ->
    self =
      title: title
      caption: caption
      display: -> activateAndDisplayItem self
      load: action
      isActive: node$ no

  loadCloud = ->
    _cloudView = Steam.CloudView _ unless _cloudView
    _.displayView _cloudView

  loadTimeline = ->
    _timelineView = Steam.TimelineView _ unless _timelineView
    _.displayView _timelineView

  loadPerfbar = ->
    _perfbarView = Steam.PerfbarView _ unless _perfbarView
    _.displayView _perfbarView

  initialize = ->
    items = [
      createItem 'Cloud', 'Monitor status of each node.', loadCloud
      createItem 'Timeline', 'Monitor cluster activity.', loadTimeline
      createItem 'Water Meter (Perfbar)', 'Perfbar CPU activity meter.', loadPerfbar
    ]
    _items items

  do initialize

  link$ _.loadAdmin, ->
    displayActiveItem()

  items: _items
  template: 'admin-list-view'

