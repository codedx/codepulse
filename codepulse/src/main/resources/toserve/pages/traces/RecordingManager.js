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

	// Bacon.js Busses and Properties are lazy, so if nothing is listening
	// to them, they won't do work. This leads to bugs where a property isn't
	// updated when its underlying Bus is updated. This function ensures that
	// a listener is added to the Bus or Property, to avoid these bugs.
	function listenToBacon(bacon){
		bacon.onValue(function(){})
	}

	Bacon.Observable.prototype.noLazy = function(){
		this.onValue(function(){})
		return this
	}

	// ---------------------------------------------------------------
	// Recording Manager:
	//
	// Handles the various "recordings" which are buttons that can 
	// be clicked to select a particular data set in the treemap.
	// It manages a selection state, and will continuously request
	// data updates for the recorded methods of the selected recording.
	// ----------------------------------------------------------------
	function RecordingManager(traceInfo) {
		var nextId = 0,
			recordings = {},
			activeRecordings = {},
			traceEnded = false,
			self = this
		
		var legendData = {},
			legendDataEvents = new Bacon.Bus()

		this.legendDataProp = legendDataEvents.toProperty(legendData).debounce(100).noLazy()

		var activeRecordingsChanges = new Bacon.Bus()
		this.activeRecordingsProp = activeRecordingsChanges.toProperty(activeRecordings).noLazy()

		this.addRecording = function(recording){
			var id = nextId++

			recordings[id] = recording

			// listen for selection changes on the recording
			recording.selected.onValue(function(selected){
				activeRecordings[id] = selected
				activeRecordingsChanges.push(activeRecordings)
			})

			// treat the label and color as legend data, sending it to the `legendDataEvents` bus
			recording.label
				.combine(recording.color, function(label, color){ 
					return {label: label || 'Untitled Recording', color: color} 
				})
				.onValue(function(o){ legendData[id] = o; legendDataEvents.push(legendData) })

			return id
		}

		this.traceEnded = function(){
			traceEnded = true
			// TODO: the initial intent for this was to run side-effects... so do that.
		}
		
		this.removeRecording = function(recId){
			delete activeRecordings[recId]
			delete recordings[recId]
		}

		this.toggleSelect = function(id){
			if(!recordings[id]) return
			
			var makeItActive = activeRecordings[id] = !activeRecordings[id]
			
			//set the new one to the opposite state
			recordings[id]['ui-activate'](makeItActive)
		}

		function getRecordsReqParams(){
			var p = {}
			for(var k in activeRecordings){
				if(activeRecordings[k]) {
					var rec = recordings[k]
					p[rec.getDataKey()] = k
				}
			}
			return p
		}

		function getCoverageReqParams(){
			var reqParams = {}
			for(var id in recordings){
				var rec = recordings[id]
				reqParams[rec.getDataKey()] = id
			}
			return reqParams
		}

		// Every 2 seconds, find the trace coverage counts for each recording,
		// and set their UI state according to their trace coverage percentage.
		TraceAPI.streamTraceCoverageCounts(getCoverageReqParams, 2000).onValue(function(json){
			for(id in json){
				var numCovered = json[id]
				recordings[id].setCoverage(numCovered, traceInfo.totalNumMethods)
			}
		})

		// Exposes the trace coverage (as detailed records) for each
		// of the currently-selected recordings, as an event stream
		// which updates roughly every 2 seconds.
		this.coverageRecordEvents = TraceAPI.streamTraceCoverageRecords(getRecordsReqParams, 2000)

		// Exposes the latest live trace data events. Each event is an
		// array of IDs of methods that were traced since the last request.
		// Events are requested roughly 3 times per second.
		this.liveTraceData = TraceAPI.streamTraceCoverageEvents(300)

	}
	// End RecordingManager class definition

	exports.RecordingManager = RecordingManager

})(this);