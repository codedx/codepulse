/*
 * Code Pulse: A real-time code coverage testing tool. For more information
 * see http://code-pulse.com
 *
 * Copyright (C) 2014 Applied Visions - http://securedecisions.avi.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

;(function(exports){
	
	// define variables to represent
	var controlsContainer,
		templatesContainer
	
	// once the DOM has loaded, initialize the template/container variables
	$(document).ready(function(){
		controlsContainer = $('#recording-controls')
		templatesContainer = controlsContainer.find('[data-role=templates-container]')
	})

	var traceCoverageUpdateRequests = new Bacon.Bus()
	var traceRecordingColorUpdates = new Bacon.Bus()

	// ColorBrewer's 'Paired12' scheme, but re-ordered to avoid 
	// putting similar colors adjacent to each other.
	var colorScheme = [
		'#a6cee3', // light blue
		'#b2df8a', // light green
		'#fb9a99', // light red/pink
		'#fdbf6f', // light orange
		'#cab2d6', // light purple
		'#ffff99', // light tan/yellow
		'#1f78b4', // blue
		'#33a02c', // green
		'#e31a1c', // red
		'#ff7f00', // orange
		'#6a3d9a', // purple
		'#b15928', // brown
	]

	function nextTemplateColor(){
		var id = nextTemplateColor.nextId || 0,
			c = nextTemplateColor.palette || d3.scale.ordinal().range(colorScheme)
		nextTemplateColor.nextId = id + 1
		nextTemplateColor.palette = c
		return c(id)
	}

	RecordingController.nextId = 0

	function RecordingController(recording, widget, generateMenu){
		this.recording = recording
		this.widget = widget

		var id = this.id = RecordingController.nextId++
		recording.running.onValue(widget.setInProgress)
		recording.color.onValue(widget.setColor)
		recording.label.onValue(widget.setLabel)
		recording.menu.onValue(widget.setMenu)
		recording.selected.onValue(widget.setSelected)

		widget.labelEdits.onValue(recording.setLabel)
		widget.colorEdits.onValue(recording.setColor)
		widget.selectionClicks.onValue(function(){
			recording.setSelected(!recording.isSelected())
		})
		widget.enableColorEditor()

		generateMenu(recording.setMenu)
	}

	function setupOverlapsRecording(){
		var recording = new Recording()

		recording
			.setLabel('overlaps')
			.setColor('purple')
			.setDataKey('overlaps')

		var managedId = Trace.addRecording(recording)

		// Watch the recording for coloring changes, sending the events
		// to the treemapColoringStateChanges stream.
		recording.color.onValue(function(color){
			var msg = 'Recording Update: color(overlaps) -> ' + color
			Trace.treemapColoringStateChanges.push(msg)
		})

		setupRecordingColorpicker(recording, '#overlaps-color-swatch')

		return managedId
	}

	function createUserRecording(recordingData){
		var recording = new Recording(),
			widget = new RecordingWidget()

		var recordingId = recordingData.id

		if(recordingData.label) recording.setLabel(recordingData.label)

		recording.label.debounce(50).onValue(function(label){
			API.updateRecordingLabel(recordingId, label)
		})

		recording.color.debounce(50).onValue(function(color){
			API.updateRecordingColor(recordingId, color)
		})

		recording
			.setDataKey('recording/' + recordingId)
			.setColor(recordingData.color || nextTemplateColor())
			.setRunning(recordingData.running)

		function generateMenu(setMenu){

			function stopRecording(){
				API.updateRecordingRunning(recordingId, false, function(ok, error){
					if(error){
						console.error('failed to end recording ' + recordingId)
					} else {
						console.log('ended recording ' + recordingId)
						recording.setRunning(false)
						updateMenu()
					}
				})
			}

			function deleteRecording(){
				API.deleteRecording(recordingId, function(ok, error){
					if(error) alert('Failed to delete the recording')
					else {
						var ui = widget.$ui
						ui.slideUp(function(){ ui.remove() })
					}
				})
			}

			function createMenu(){
				var menu = []

				// Rename 
				menu.push({
					id: 'rename',
					icon: 'pencil',
					label: 'Rename...',
					onSelect: function(){ widget.openLabelEditor() }
				})

				// Stop
				if(recording.isRunning()) menu.push({
					id: 'stop',
					icon: 'stop',
					label: 'Stop Recording',
					onSelect: function(){ stopRecording() }
				})

				// Delete
				menu.push({
					id: 'delete',
					icon: 'trash',
					label: 'Delete',
					onSelect: deleteRecording
				})

				return menu
			}

			function updateMenu(){
				setMenu(createMenu())
			}

			updateMenu()

		}

		return new RecordingController(recording, widget, generateMenu)
	}

	function pollTimeUpdates(initial, interval){
		var first = Bacon.once(initial)
		var poll = Bacon.fromPoll(interval, function(){
			var msg = 'Time Update: tick'
			return new Bacon.Next(msg)
		})

		return first.concat(poll)
	}

	/**
	 * Convenience method for setting up a colorpickerTooltip on a recording color swatch.
	 * Note: 'custom' recordings already do this. This method is really just for use in the
	 * 'legend' recordings, i.e. 'all activity' and 'overlaps'.
	 *
	 * @param recording the Recording whose color will be changed when the tooltip is used
	 * @param swatchContainer a selector string describing the swatch widget's container element
	 */
	function setupRecordingColorpicker(recording, swatchContainer){
		var $swatchContainer = $(swatchContainer),
			$swatch = $swatchContainer.find('.swatch')

		colorpickerTooltip($swatch, recording.getColor(), {
			progressCallback: function(current){ $swatch.css('background-color', current) },
			finishedCallback: function(choice){ choice && recording.setColor(choice) },
			onOpen: function(){ $swatchContainer.addClass('editor-open') },
			onClose: function(){ $swatchContainer.removeClass('editor-open') }
		})
	}

	function createAllActivityRecording(){
		var recording = new Recording()

		recording
			.setLabel('all activity')
			.setDataKey('all')
			.setColor('steelblue')

		var managedId = Trace.addRecording(recording)

		// Watch the recording for coloring changes, sending the events
		// to the treemapColoringStateChanges stream.
		Trace.treemapColoringStateChanges.plug(
			recording.color.map(function(color){
				return 'Recording Update: color(all activity) -> ' + color
			})
		)

		setupRecordingColorpicker(recording, '#all-activity-color-swatch')

		return managedId
	}

	function createRecentRecording(controlsContainer){
		var recording = new Recording(),
			widget = new RecordingWidget()

		recording.setColor('darkgreen')

		widget.$ui.find('.recording-label').addClass('no-icon')

		var timeWindows = [
			{label: 'Latest 10 seconds', dataKey: 'recent/10000'},
			{label: 'Latest 60 seconds', dataKey: 'recent/60000'},
			{label: 'Latest 2 minutes', dataKey: 'recent/120000'},
			{label: 'Latest 5 minutes', dataKey: 'recent/300000'},
			{label: 'Latest 10 minutes', dataKey: 'recent/600000'}
		]

		function generateMenu(setMenu){

			var activeIndex = 0

			function activateIndex(index){
				activeIndex = index

				timeWindows.forEach(function(tw, i){
					tw.active = (i === index)
				})
				var label = timeWindows[index].label,
					dataKey = timeWindows[index].dataKey

				recording
					.setLabel(label)
					.setDataKey(dataKey)

				setMenu(createMenu())
			}

			function createMenu(){
				return timeWindows.map(function(tw, i){
					if(tw.divider) return {
						divider: true
					} 
					else return {
						icon: 'time',
						label: tw.label,
						onSelect: function(){ activateIndex(i) },
						selected: (i == activeIndex)
					}
				})
			}

			activateIndex(activeIndex)
		}

		// Register the recording with the Trace
		var managedId = Trace.addRecording(recording)

		// Add the recording UI to the container
		widget.$ui.appendTo(controlsContainer)

		// Watch the recording for coloring changes, sending the events
		// to the treemapColoringStateChanges stream.
		Trace.treemapColoringStateChanges.plug(
			watchForTreemapColoringUpdates(recording, managedId)
		)

		// Watch the recording for scenarios where it should poll for
		// coverage updates, forwarding these events to the 
		// traceCoverageUpdateRequests stream.
		Trace.traceCoverageUpdateRequests.plug(
			watchForRecentDataKeyChanges(recording, managedId)
		)

		return new RecordingController(recording, widget, generateMenu)
	}

	// In order to minimize the frequency that the browser needs to request trace
	// coverage data updates, we watch the 'recent' recording for state changes that
	// would require a coverage data update. This function creates an event stream of
	// those changes.
	//
	// argument: recording [the 'recent' recording]
	// argument: managedId [the ID that was generated when adding the recording to the Trace]
	function watchForRecentDataKeyChanges(recording, managedId){
		function isRecentKey(key){ return key.indexOf('recent/') === 0 }

		return Bacon
			// poll if the recording is selected and on a 'recent/*' dataKey
			.combineWith(function(isSelected, dataKey){
				if(isSelected && isRecentKey(dataKey)){
					return 'poll'
				} else if(isSelected){
					return 'once'
				} else {
					return false
				}
			}, recording.selected, recording.dataKey)
			// if the `combineWith` returns true, kick off a polling stream
			// (otherwise, do nothing)
			.flatMapLatest(function(shouldPoll){
				if(shouldPoll == 'poll') return pollTimeUpdates('Start polling!', 1500)
				else if(shouldPoll == 'once') return Bacon.once('just once')
				else return Bacon.never()
			})
			// only allow the polling to continue if the recording is managed
			.takeWhile(function(){
				return Trace.getRecording(managedId) == recording
			})
	}

	// In order to minimize the frequency that the UI has to recolor the treemap
	// visualization, we want to watch each recording for state changes that would
	// affect the treemap. This function creates an event stream of those changes.
	//
	// argument: recording [the recording to watch]
	// argument: managedId [the ID that was generated by calling Trace.addRecording(recording)]
	// argument: selectionOptional [optional flag; if true, it doesn't matter if the
	//    recording was selected when its color changed.]
	function watchForTreemapColoringUpdates(recording, managedId, selectionOptional){

		// If the recording's color is changed while it is selected
		var colorChange = recording.color.changes()
			.filter(function(){ return recording.isSelected() || selectionOptional })
			.map(function(color){
				return 'Recording Update: color(' + managedId + ') -> ' + color
			})

		// If the recording's selection state is changed
		var selectionChange = recording.selected.changes()
			.map(function(sel){
				return 'Recording Update: selected(' + managedId + ') -> ' + sel
			})

		// Watch both of these streams as long as the recording is being managed
		return colorChange.merge(selectionChange).takeWhile(function(){
			return Trace.getRecording(managedId) == recording
		})
	}

	// ---------------------------------------------------------------
	// CustomRecordingAdder:
	//
	// This class manages a UI Button that will add new instances of
	// the CustomRecordingWidget to the DOM when clicked. It manages
	// the AJAX operation of POSTing a /recording/start request to the
	// server in order to obtain a recording id.
	// ----------------------------------------------------------------
	function CustomRecordingAdder($adderButton, $recordingsList){
		
		$adderButton.click(function(){
			API.requestNewRecording(function(recordingData, error){
				if(error) alert("failed to start a new recording because: " + error)
				else addNewRecording(recordingData, true)
			})
		})
		
		function addNewRecording(recordingData, animate){
			var testRecording = createUserRecording(recordingData)

			var toShow = testRecording.widget.$ui
				.hide()
				.appendTo($recordingsList)

			toShow[animate? 'slideDown': 'show']()

			var managedId = Trace.addRecording(testRecording.recording)

			// Watch the recording for coloring changes, sending the events
			// to the treemapColoringStateChanges stream.
			Trace.treemapColoringStateChanges.plug(
				watchForTreemapColoringUpdates(testRecording.recording, managedId)
			)
		}

		this.addNewRecording = addNewRecording
		
	}
	// End CustomRecordingAdder class definition

	// -------------------------------------------------------------------
	// Create wiring for the page's sidebar.
	//
	// The sidebar includes a legend for the 'all activity' and 'overlaps'
	// colors, and the ability to change those colors; a timing window filter,
	// which lets users see trace data within a given time window; a list
	// of user-added recordings, and a button for adding new ones; and buttons
	// for starting and ending a trace.
	// -------------------------------------------------------------------

	$(document).ready(function(){
		var controlsContainer = $('#recording-controls'),
			newTraceArea = controlsContainer.find('.trace-setup-area'),
			newTraceButton = newTraceArea.find('[data-role=new-trace]').hide(),
			connectionWaitingText = newTraceArea.find('[data-role=connection-waiting]').hide(),
			endTraceButton = newTraceArea.find('[data-role=end-trace]').hide(),
			recordingControls = $('#recording-controls')
		
		Trace.allActivityRecordingId = createAllActivityRecording(controlsContainer)

		// Add a Recording/Widget for the "all activity"/"latest XXX"
		var timeWindowsController = createRecentRecording(controlsContainer.find('.timingFilterArea'))

		var legendMultiplesKey = setupOverlapsRecording(controlsContainer)

		Trace.getColorLegend = function(){
			var legend = {}
			Trace.forEachRecording(function(rec, id){
				if(id == legendMultiplesKey) id = 'multi'
				legend[id] = rec.getColor()
			})
			return legend
		}

		// Set up a button that will add new "custom" recordings
		var customRecordingAdder = new CustomRecordingAdder(
			controlsContainer.find('.recording-adder-button'), 
			controlsContainer.find('.recordingsList'))

		// Load any existing user-created recordings from the server, adding them to the UI.
		// Don't actually do it until the trace is 'ready' (i.e. the state isn't 'loading').
		Trace.ready(function(){
			API.requestRecordings(function(recordings, error){
				if(error){ console.error('failed to load recordings') }
				else {
					recordings.forEach(function(rec){ customRecordingAdder.addNewRecording(rec, false) })
				}
			})
		})

		// assign the 'trace-running' attribute to the controlsContainer, depending on the trace state
		Trace.running.assign(controlsContainer, 'attr', 'trace-running')

		// Update the state of the newTraceArea based on the Trace's state.
		;(function(){
			function togglerFunc($elem){
				return function(show, animate){
					if(animate) $elem[show? 'slideDown': 'slideUp']()
					else $elem[show? 'show': 'hide']()
				}
			}
			var toggleNewTraceButton = togglerFunc(newTraceButton),
				toggleConnectingText = togglerFunc(connectionWaitingText),
				toggleEndTraceButton = togglerFunc(endTraceButton)

			function toggleFinishing(finishing){ endTraceButton.overlay(finishing ? 'wait': 'ready') }

			function onStateChange(state, animate){
				toggleNewTraceButton(state == 'idle', animate)
				toggleConnectingText(state == 'connecting', animate)
				toggleEndTraceButton(state == 'running' || state == 'ending', animate)
				toggleFinishing(state == 'ending')
			}

			var gotFirstValue = false,
				valuesThatCount = d3.set(['idle', 'connecting', 'running', 'ending'])

			// When the state changes, show or hide the appropriate divs.
			// Depending on whether this is the first 'visible' state change, the show/hide 
			// effect will have an animation. (it animates after the initial change).
			Trace.status.onValue(function(state){
				var doAnimate = gotFirstValue

				gotFirstValue = gotFirstValue || valuesThatCount.has(state)

				onStateChange(state, doAnimate)
			})
		})()

		/* Clicking the newTraceButton asks the server to look for
		 * a new tracer connection. This may be ignored if the trace
		 * is not in the 'idle' state.
		 */
		newTraceButton.click(function(){ API.requestStart(exports.updateTraceAgentCommand) })

		/* Clicking the endTraceButton asks the server to stop the
		 * current trace (if there is one). Open a waiting overlay
		 * that will be closed when the trace's state becomes 'finished'.
		 */
		endTraceButton.click(function(){ 
			API.requestEnd() 
		})

		// Set up the connection help link
		var connectionHelpPopup = $('#connection-help-popup')
		$('#connection-help-link').click(function(){
			var isVisible = connectionHelpPopup.is(':visible')
			//toggle visibility
			connectionHelpPopup[isVisible ? 'hide' : 'show']()
		})
		connectionHelpPopup.find('.dismissal').click(function(){
			connectionHelpPopup.hide()
		})

		// setup the agent port number control
		;(function() {
			var $agentPort = $('#agent-port'), $agentLine = $agentPort.parent(), $agentError = $('#agent-port-error')

			$('#options-menu').click(function(e) { e.stopPropagation() })
			
			function errorOut() {
				$agentError.slideUp(100, function() { blockErrorOut = false })
			}
			function errorIn(error) {
				if (error) {
					$agentError.text(error)
					$agentError.slideDown(100, function() { blockErrorIn = false })
				} else {
					errorOut()
				}
			}

			function setPortSaving() {
				$agentPort.removeClass('invalid')
				errorOut()
			}
			function setPortSaved() {
				$agentPort.removeClass('invalid')
				errorOut()
			}
			function setPortInvalid(error) {
				$agentPort.addClass('invalid')
				errorIn(error)
			}

			$agentLine.overlay('wait')

			$.ajax('/api/agent-port', {
				type: 'GET',
				success: function(data) {
					$agentPort.val(data)
					setPortSaved()
					$agentLine.overlay('ready')
				},
				error: function(xhr, status) {
					setPortInvalid()
					console.error('failed to get agent port', xhr.responseText)
				}
			})

			$agentPort.on('input', function() {
				var val = $agentPort.val().trim()
				if (/^\d+$/.test(val)) {
					setPortSaving()

					$.ajax('/api/agent-port', {
						data: { 'port': val },
						type: 'PUT',
						success: function() {
							$agentPort.val(val)
							setPortSaved()
							updateTraceAgentCommand()
						},
						error: function(xhr, status) {
							setPortInvalid(xhr.responseText)
						}
					})
				} else {
					setPortInvalid()
				}
			})
		})()

	})
	
	exports.updateTraceAgentCommand = function() {
		var $agentString = $('#trace-agent-command')

		$agentString.overlay('wait')
		$.ajax('/api/agent-string', {
			type: 'GET',
			success: function(str) {
				$agentString.text(str)
				$agentString.overlay('ready')
			},
			error: function(xhr, status) {
				console.error('failed to get updated agent string', xhr.responseText)
			}
		})
	}

}(this));