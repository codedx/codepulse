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
		templatesContainer,
		traceSetupTemplate,
		customRecordingAdderTemplate
	
	// once the DOM has loaded, initialize the template/container variables
	$(document).ready(function(){
		controlsContainer = $('#recording-controls')
		templatesContainer = controlsContainer.find('[data-role=templates-container]')
		traceSetupTemplate = templatesContainer.find('[data-role=trace-setup]')
		customRecordingAdderTemplate = templatesContainer.find('[data-role=custom-recording-adder]')
		recordingTemplate = templatesContainer.find('[data-role=recording-template]')
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
		recording.coverage.onValue(function(c){ widget.setCoverage(c[0], c[1]) })

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
			TraceAPI.updateRecordingLabel(recordingId, label)
		})

		recording.color.debounce(50).onValue(function(color){
			TraceAPI.updateRecordingColor(recordingId, color)
		})

		recording
			.setDataKey('recording/' + recordingId)
			.setColor(recordingData.color || nextTemplateColor())
			.setRunning(recordingData.running)

		function generateMenu(setMenu){

			function stopRecording(){
				TraceAPI.updateRecordingRunning(recordingId, false, function(ok, error){
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
				TraceAPI.deleteRecording(recordingId, function(ok, error){
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
	function CustomRecordingAdder(recorderId, $widgetContainer){
		
		var button = this.$element = customRecordingAdderTemplate.clone()
		
		button.click(function(){
			TraceAPI.requestNewRecording(function(recordingData, error){
				if(error) alert("failed to start a new recording because: " + error)
				else addNewRecording(recordingData, true)
			})
		})
		
		function addNewRecording(recordingData, animate){
			var testRecording = createUserRecording(recordingData)

			var toShow = testRecording.widget.$ui
				.hide()
				.appendTo($widgetContainer)

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
	// TraceRecordingControlsWidget
	//
	// Creates wiring that is responsible for starting/ending traces, and
	// sets up secondary controls that add recordings etc. It also creates
	// a recordings manager, so that for whichever recording is selected,
	// it will request the data for that recording and fire it as a 'data
	// update' event.
	// -------------------------------------------------------------------
	function TraceRecordingControlsWidget(){
		var newTraceArea = traceSetupTemplate.clone().appendTo(controlsContainer),
			newTraceButton = newTraceArea.find('[data-role=new-trace]').hide(),
			connectionWaitingText = newTraceArea.find('[data-role=connection-waiting]').hide(),
			endTraceButton = newTraceArea.find('[data-role=end-trace]').hide(),
			recordingControls = $('#recording-controls'),
			self = this

		
		Trace.allActivityRecordingId = createAllActivityRecording(controlsContainer)

		// Add a Recording/Widget for the "all activity"/"latest XXX"
		var timeWindowsController = createRecentRecording(controlsContainer)

		var legendMultiplesKey = setupOverlapsRecording(controlsContainer)

		Trace.getColorLegend = function(){
			var legend = {}
			Trace.forEachRecording(function(rec, id){
				if(id == legendMultiplesKey) id = 'multi'
				legend[id] = rec.getColor()
			})
			return legend
		}

		// Create a button that will add new "custom" recordings
		var customRecordingAdder = new CustomRecordingAdder(self.recorderId, controlsContainer)
		customRecordingAdder.$element.appendTo(controlsContainer)

		TraceAPI.requestRecordings(function(recordings, error){
			if(error){ console.error('failed to load recordings') }
			else {
				recordings.forEach(function(rec){ customRecordingAdder.addNewRecording(rec, false) })
			}
		})
		
		// forward all coverageRecordEvents from the recordingManager to the `coverageRecordEvents` bus
		// coverageRecordEvents.plug( Trace.coverageRecordEvents )

		var traceRunningBus = new Bacon.Bus(),
			traceRunningProp = traceRunningBus.toProperty().assign(controlsContainer, 'attr', 'trace-running')

		/*
		 * Load the current trace status and set the UI accordingly.
		 * (Don't use animations; just instantly show/hide things.)
		 */
		TraceAPI.requestStatus(function(status, error){
			if(error) console.error('failed to load tracer status from server')
			else switch(status){
			case 'idle':
				newTraceButton.show()
				connectionWaitingText.hide()
				endTraceButton.hide().overlay('ready')
				traceRunningBus.push(false)
				break
			case 'connecting':
				newTraceButton.hide()
				connectionWaitingText.show()
				endTraceButton.hide().overlay('ready')
				break
			case 'running':
				newTraceButton.hide()
				connectionWaitingText.hide()
				endTraceButton.show().overlay('ready')
				traceRunningBus.push(true)
				break
			case 'ending':
				newTraceButton.hide()
				connectionWaitingText.hide()
				endTraceButton.show().overlay('wait')
				break
			}
		})

		/*
		 * React to `tracer-state-change` events by showing and hiding
		 * the appropriate elements using nice animations.
		 */
		$(document).on('tracer-state-change', function(event, params){
			switch(params['state']){
			case 'connecting':
				newTraceButton.slideUp()
				connectionWaitingText.slideDown()
				endTraceButton.hide()
				break
			case 'connected':
				// ...nothing?
				break
			case 'started':
				newTraceButton.hide()
				connectionWaitingText.slideUp()
				endTraceButton.slideDown()
				traceRunningBus.push(true)
				break
			case 'finished':
				newTraceButton.slideDown()
				connectionWaitingText.hide()
				endTraceButton.slideUp()
				endTraceButton.overlay('ready')
				traceRunningBus.push(false)
				break
			case 'deleted':
				alert('This trace has been deleted. You will be redirected to the home screen')
				window.location.href = '/'
			}
		})

		/* Clicking the newTraceButton asks the server to look for
		 * a new tracer connection. This may be ignored if the trace
		 * is not in the 'idle' state.
		 */
		newTraceButton.click(function(){ TraceAPI.requestStart() })

		/* Clicking the endTraceButton asks the server to stop the
		 * current trace (if there is one). Open a waiting overlay
		 * that will be closed when the trace's state becomes 'finished'.
		 */
		endTraceButton.click(function(){ 
			endTraceButton.overlay('wait')
			TraceAPI.requestEnd() 
		})

	}
	exports.TraceRecordingControlsWidget = TraceRecordingControlsWidget
	// End TraceRecordingControlsWidget class definition
	

}(this));