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

// Recording Management:
// 
// This file adds methods to the 'Trace' global object, for 'managing' recordings.
// It also handles requests for live trace data accumulation and trace coverage
// records, exposing them as event streams and properties.
;(function(Trace){

	// ---------------------------------------------------------------
	// Recording Manager:
	//
	// Handles the various "recordings" which are buttons that can 
	// be clicked to select a particular data set in the treemap.
	// It manages a selection state, and will continuously request
	// data updates for the recorded methods of the selected recording.
	// ----------------------------------------------------------------
	var nextId = 0,
		recordings = {},
		activeRecordings = d3.set()
	
	var activeRecordingsChanges = new Bacon.Bus()
	Trace.activeRecordingsProp = activeRecordingsChanges.toProperty(activeRecordings).noLazy()

	// Add a Recording for management. Doing so associates the
	// given `recording` with an ID, which is returned.
	Trace.addRecording = function(recording){
		var id = nextId++

		recordings[id] = recording

		// listen for selection changes on the recording
		recording.selected
			.takeWhile(function(){ return recordings[id] == recording })
			.onValue(function(selected){
				activeRecordings[selected? 'add' : 'remove'](id)
				// activeRecordings[id] = selected
				activeRecordingsChanges.push(activeRecordings)
			})

		return id
	}

	// Look up a managed recording by its ID.
	Trace.getRecording = function(id){
		return recordings[id]
	}

	// Iterate through each managed recording with `f`.
	// argument: f = function(recording, id){...}
	Trace.forEachRecording = function(f){
		for(var id in recordings){
			var rec = recordings[id]
			f(rec, id)
		}
	}

	// Get an array of each managed recording's IDs.
	Trace.getManagedRecordingIds = function(){
		return Object.keys(recordings)
	}

	// Get an array of managed recordings that are current `selected`.
	Trace.getActiveRecordingIds = function(){
		var s = d3.set()
		for(var id in recordings){
			var rec = recordings[id]
			rec.isSelected() && s.add(id)
		}
		return s
	}
	
	// Remove a recording by its managed ID.
	Trace.removeRecording = function(recId){
		// delete activeRecordings[recId]
		activeRecordings.remove(recId)
		activeRecordingChanges.push(activeRecordings)
		delete recordings[recId]
	}

	// For use with the trace coverage request. This function generates
	// an object to represent the request parameters, e.g.
	// {'all': 0, 'recent/10000': 1, 'recording/5': 2}
	// The keys are recording `dataKey`s, and the values are the
	// managed IDs. When the trace coverage request is answered, the
	// values are used to represent their respective recordings.
	function getRecordsReqParams(){
		var p = {}
		for(var id in recordings){
			var rec = recordings[id]
			p[rec.getDataKey()] = id
		}
		return p
	}

	// Exposes the trace coverage (as detailed records) for each
	// of the currently-selected recordings, as an event stream
	// which updates roughly every 2 seconds.
	Trace.coverageRecords = Trace
		.requestResponseStream(
			Trace.traceCoverageUpdateRequests.debounce(100),
			500, // throttle time after requests finish
			function(request, callback){
				TraceAPI.requestTraceCoverageRecords(getRecordsReqParams(), callback)
			}
		)
		.toProperty().noLazy()

	// As the trace coverage is updated, calculate how many methods were covered
	// by each recording, then update their coverage counts accordingly.
	Trace.coverageRecords
		.map(function(records){
			var coverageCounts = {}
			for(var id in recordings){
				coverageCounts[id] = 0
			}
			for(var methodId in records){
				var coverage = records[methodId]
				coverage.forEach(function(id){
					coverageCounts[id]++
				})
			}
			return coverageCounts
		})
		.onValue(function(coverageCounts){
			for(var id in coverageCounts){
				recordings[id].setCoverage(coverageCounts[id], Trace.totalNumMethods)
			}
		})

	// Exposes the latest live trace data events. Each event is an
	// array of IDs of methods that were traced since the last request.
	// Events are requested roughly 3 times per second.
	Trace.liveTraceData = TraceAPI.streamTraceCoverageEvents(300).filter('.length')

	// Incoming live trace data means that the coverage may have changed, so a new
	// trace coverage request should be made.
	Trace.traceCoverageUpdateRequests.plug(
		Trace.liveTraceData
	)

})(this.Trace || (this.Trace = {}));