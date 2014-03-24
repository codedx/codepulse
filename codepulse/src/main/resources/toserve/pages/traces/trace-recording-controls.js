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

	function OverlapsRecordingController(){
		var recording = this.recording = new Recording(),
			widget = this.widget = new RecordingWidget()

		recording
			.setLabel('multiple recordings')
			.setColor('purple')

		recording.color.onValue(widget.setColor)
		recording.label.onValue(widget.setLabel)


		widget.colorEdits.onValue(recording.setColor)
		widget.enableColorEditor()
		widget.setSelected(true)

		this.getUi = function(){ return widget.$ui }
	}

	function setupOverlapsRecording(recordingManager, $uiContainer){
		var controller = new OverlapsRecordingController()
		controller.getUi()
			.appendTo($uiContainer)
			.addClass('multiples-recording')
		
		var managedId = recordingManager.addRecording(controller.recording)

		// Show or Hide the widget depending on whether or not the recording manager
		// has multiple selections.
		recordingManager.activeRecordingsProp
			.map(function(activeById){ return d3.sum(d3.values(activeById)) > 1 })
			.onValue(function(hasMultipleSelection){
				controller.widget.setDisabled(!hasMultipleSelection)
				controller.widget.setInProgress(hasMultipleSelection)
				controller.widget.setSelected(hasMultipleSelection)
			})

		return managedId
	}

	function createTestRecording(recordingData){
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

	function createRecentRecording(){
		var recording = new Recording(),
			widget = new RecordingWidget()

		recording.setColor('steelblue')

		var timeWindows = [
			{label: 'all activity', dataKey: 'all'},
			{divider: true},
			{label: 'latest 10 seconds', dataKey: 'recent/10000'},
			{label: 'latest 60 seconds', dataKey: 'recent/60000'},
			{label: 'latest 2 minutes', dataKey: 'recent/120000'},
			{label: 'latest 5 minutes', dataKey: 'recent/300000'},
			{label: 'latest 10 minutes', dataKey: 'recent/600000'}
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
					.setLabel((index == 0 ? 'Show ' : 'Show the ') + label)
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

		return new RecordingController(recording, widget, generateMenu)
	}

	

	// ---------------------------------------------------------------
	// CustomRecordingAdder:
	//
	// This class manages a UI Button that will add new instances of
	// the CustomRecordingWidget to the DOM when clicked. It manages
	// the AJAX operation of POSTing a /recording/start request to the
	// server in order to obtain a recording id.
	// ----------------------------------------------------------------
	function CustomRecordingAdder(recorderId, recordingManager, $widgetContainer){
		
		var button = this.$element = customRecordingAdderTemplate.clone()
		
		button.click(function(){
			TraceAPI.requestNewRecording(function(recordingData, error){
				if(error) alert("failed to start a new recording because: " + error)
				else addNewRecording(recordingData, true)
			})
		})
		
		function addNewRecording(recordingData, animate){
			var testRecording = createTestRecording(recordingData)

			var toShow = testRecording.widget.$ui
				.hide()
				.appendTo($widgetContainer)

			toShow[animate? 'slideDown': 'show']()
				// .slideDown()

			var managedId = recordingManager.addRecording(testRecording.recording)

		}

		this.addNewRecording = addNewRecording
		
	}
	// End CustomRecordingAdder class definition

	function TraceInfo(){
		this.totalNumMethods = null
	}
	exports.TraceInfo = TraceInfo

	// -------------------------------------------------------------------
	// TraceRecordingControlsWidget
	//
	// Creates wiring that is responsible for starting/ending traces, and
	// sets up secondary controls that add recordings etc. It also creates
	// a recordings manager, so that for whichever recording is selected,
	// it will request the data for that recording and fire it as a 'data
	// update' event.
	// -------------------------------------------------------------------
	function TraceRecordingControlsWidget(traceInfo){
		var newTraceArea = traceSetupTemplate.clone().appendTo(controlsContainer),
			newTraceButton = newTraceArea.find('[data-role=new-trace]').hide(),
			connectionWaitingText = newTraceArea.find('[data-role=connection-waiting]').hide(),
			endTraceButton = newTraceArea.find('[data-role=end-trace]').hide(),
			recordingControls = $('#recording-controls'),
			recordingManager = new RecordingManager(traceInfo),
			self = this
			
		this.liveTraceData = new Bacon.Bus()
		var coverageRecordEvents = new Bacon.Bus()
		this.coverageRecords = coverageRecordEvents.toProperty({})

		var legendDataEvents = new Bacon.Bus(),
			legendMultiplesKey = null // to be assigned once the trace starts
		this.legendData = legendDataEvents.toProperty({}).map(function(data){
			var rep = {}
			for(var key in data){
				var v = data[key]
				var repKey = (key == legendMultiplesKey) ? 'multi' : key
				rep[repKey] = v
			}
			return rep
		})

		// Add a Recording/Widget for the "all activity"/"latest XXX"
		var timeWindowsController = createRecentRecording()
		recordingManager.addRecording(timeWindowsController.recording)
		timeWindowsController.widget.$ui.appendTo(controlsContainer)

		// var overlapsRecording = new OverlapsRecordingController()
		// overlapsRecording.getUi().appendTo(controlsContainer)
		legendMultiplesKey = setupOverlapsRecording(recordingManager, controlsContainer)

		// Create a button that will add new "custom" recordings
		var customRecordingAdder = new CustomRecordingAdder(self.recorderId, recordingManager, controlsContainer)
		customRecordingAdder.$element.appendTo(controlsContainer)

		TraceAPI.requestRecordings(function(recordings, error){
			if(error){ console.error('failed to load recordings') }
			else {
				recordings.forEach(function(rec){ customRecordingAdder.addNewRecording(rec, false) })
			}
		})
		
		// forward all coverageRecordEvents from the recordingManager to the `coverageRecordEvents` bus
		coverageRecordEvents.plug( recordingManager.coverageRecordEvents )

		// forward the LegendData from the recordingManager to the `legendDataEvents` bus
		recordingManager.legendDataProp.onValue(legendDataEvents.push)

		// forward all non-empty liveTraceData from the recordingManager to the `liveTraceData` bus
		self.liveTraceData.plug( recordingManager.liveTraceData.filter('.length') )

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