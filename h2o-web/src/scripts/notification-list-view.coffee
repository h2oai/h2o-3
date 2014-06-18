Steam.NotificationListView = (_) ->
  _items = do nodes$
  _hasItems = lift$ _items, (items) -> items.length > 0

  displayItem = (item) ->
    if item
      _.displayNotification item.data
    else
      _.displayEmpty()

  displayActiveItem = ->
    displayItem find _items(), (item) -> item.isActive()

  activateAndDisplayItem = (item) ->
    for other in _items()
      if other is item
        other.isActive yes
      else
        other.isActive no

    displayItem item

  createItem = (notification) ->
    self =
      data: notification
      title: notification.level
      caption: notification.message
      cutline: notification.timestamp.toString()
      display: -> activateAndDisplayItem self
      isActive: node$ no

  link$ _.notify, (notification) ->
    _items.unshift createItem notification

  #TODO predicate
  link$ _.loadNotifications, ->
    displayActiveItem()

  items: _items
  hasItems: _hasItems
  template: 'notification-list-view'
