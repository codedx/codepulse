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
	
	var controlsContainer,
		templatesContainer
	
	// once the DOM has loaded, initialize the template/container variables
	$(document).ready(function(){
		controlsContainer = $('#recording-controls')
		templatesContainer = controlsContainer.find('[data-role=templates-container]')
	})

	var traceCoverageUpdateRequests = new Bacon.Bus()
	var traceRecordingColorUpdates = new Bacon.Bus()

	var recordingColorResets = new Bacon.Bus()

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

	nextTemplateColor.reset = function(){
		nextTemplateColor.nextId = 0
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

		recordingColorResets.onValue(function(){
			recording.setColor('purple')
		})

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

		var colorPicker = colorpickerTooltip($swatch, recording.getColor(), {
			progressCallback: function(current){ $swatch.css('background-color', current) },
			finishedCallback: function(choice){ choice && recording.setColor(choice) },
			onOpen: function(){ $swatchContainer.addClass('editor-open') },
			onClose: function(){ $swatchContainer.removeClass('editor-open') }
		})

		recording.color.onValue(function(color){
			colorPicker.reset(color)
			$swatch.css('background-color', color)
		})
	}

	function createActivityTickerRecording(){
		var recording = new Recording(),
			$container = $('#activity-ticker'),
			$swatch = $container.find('.swatch-container'),
			$menuItems = $container.find('.dropdown-menu li'),
			$label = $container.find('.legend-text'),
			defaultColor = 'steelblue'

		// set the initial and reset color
		recording.setColor(defaultColor)
		recordingColorResets.onValue(function(){
			recording.setColor(defaultColor)
		})

		var menuItems = []

		// fill in the menuItems array
		$menuItems.each(function(){
			var $li = $(this),
				$a = $li.find('a'),
				label = $a.text(),
				key = $a.data('tickerKey'),
				active = $li.hasClass('active')

			$a.attr('href', 'javascript:void(0)')

			menuItems.push({
				'$li': $li,
				'$a': $a,
				'key': key,
				'label': label
			})
		})

		function activateItem(menuItem){
			// set the `active` class
			$menuItems.removeClass('active')
			menuItem.$li.addClass('active')

			recording
				.setLabel(menuItem.label)
				.setDataKey(menuItem.key)

			$label.text(menuItem.label)
		}

		// activate the default item, now, and any item when clicked
		menuItems.forEach(function(item, index){
			if(index == 0) activateItem(item)
			item.$a.click(function(){
				activateItem(item)
			})
		})

		var managedId = Trace.addRecording(recording)

		// Watch the recording for coloring changes, sending the events
		// to the treemapColoringStateChanges stream.
		Trace.treemapColoringStateChanges.plug(
			recording.color.map(function(color){
				return 'Recording Update: color(ticker) -> ' + color
			})
		)

		// Watch the recording for scenarios where it should poll for
		// coverage updates, forwarding these events to the 
		// traceCoverageUpdateRequests stream.
		var watcher = watchForRecentDataKeyChanges(recording, managedId)
		Trace.traceCoverageUpdateRequests.plug(
			watcher
		)

		setupRecordingColorpicker(recording, $swatch)

		return managedId
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
				if(/*isSelected && */isRecentKey(dataKey)){
					return 'poll'
				} else /*if(isSelected)*/{
					return 'once'
				} /*else {
					return false
				}*/
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

		var isRunning = false,
			addedRecordings = d3.set()

		Trace.status.onValue(function(status){
			isRunning = (status == 'running')
			$adderButton.toggleClass('disabled', !isRunning)
		})

		$adderButton.click(function(){
			if(!isRunning) return

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
			addedRecordings.add(managedId)

			// Watch the recording for coloring changes, sending the events
			// to the treemapColoringStateChanges stream.
			Trace.treemapColoringStateChanges.plug(
				watchForTreemapColoringUpdates(testRecording.recording, managedId)
			)
		}

		// handle color resets
		recordingColorResets.onValue(function(){
			nextTemplateColor.reset()
			Trace.getManagedRecordingIds().forEach(function(id){
				if(addedRecordings.has(id)){
					var rec = Trace.getRecording(id)
					rec && rec.setColor(nextTemplateColor())
				}
			})
		})

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
		var controlsContainer = $('#recording-controls')
		
		// Set up the 'ticker' recording, which holds the 'all activity'
		// and the 'latest X seconds' recordings.
		Trace.allActivityRecordingId = createActivityTickerRecording()

		// set up the 'overlaps' recording, which represents overlaps in
		// coverage between multiple selected recordings
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

		$('#recording-color-resetter').click(function(){
			recordingColorResets.push(1)
		})

	})

}(this));