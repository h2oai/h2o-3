Steam.TimelineView = (_) ->
  _header = [
    'HH:MM:SS:MS'
    'nanosec'
    'Who'
    'I/O Type'
    'Event'
    'Bytes'
  ]
  _timeline = node$ null
  _timestamp = node$ Date.now()

  createEvent = (event) ->
    switch event.type
      when 'io'
        [
          event.date
          event.nanos
          event.node
          event.ioFlavor or '-'
          'I/O'
          event.data
        ]

      when 'heartbeat'
        [
          event.date
          event.nanos
          'many &#8594;  many'
          'UDP'
          'heartbeat'
          "#{event.sends} sent #{event.recvs} received"
        ]

      when 'network_msg'
        [
          event.date
          event.nanos
          "#{event.from} &#8594; #{event.to}"
          event.protocol
          event.msgType
          event.data
        ]

  createTimeline = (result) ->
    map result.events, createEvent

  refresh = ->
    _.requestTimeline (error, result) ->
      if error
        #TODO
      else
        _timeline createTimeline result
        _timestamp Date.now()

  refresh()

  header: _header
  timeline: _timeline
  timestamp: _timestamp
  template: 'timeline-view'

