Steam.NotificationView = (_, _notification) ->

  title: _notification.message
  level: _notification.level
  data: _notification.data
  cause: _notification.cause
  timestamp: _notification.timestamp.toString()
  dispose: ->
  template: 'notification-view'

