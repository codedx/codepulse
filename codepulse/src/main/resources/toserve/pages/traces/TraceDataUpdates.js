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

;(function(Trace){

	// A stream of events that are fired when recordings
	// that are managed by the Trace change their state in
	// a way that requires the treemap to recolor itself.
	// 
	// Generally, this means that the recording's selection
	// state changes, or the recording's color changes while
	// it is also selected.
	Trace.treemapColoringStateChanges = new Bacon.Bus()


	// A stream of events that are fired when something
	// happens that invalidates the latest trace coverage
	// record data; When this happens, the browser should
	// make a request through the TraceAPI for the latest
	// coverage data. When these requests are fulfilled,
	// the response data is fired through [TODO: variable name here!]
	Trace.traceCoverageUpdateRequests = new Bacon.Bus()


	Trace.treemapColoringStateChanges.log('color change:')
	Trace.traceCoverageUpdateRequests.log('polling change:')

	// Creates an event stream that represents the responses for requests in
	// the given `requestStream`. It has special behavior to ignore requests
	// that are made while waiting for a request, but still allow the latest
	// one through after the request generates a response. The `throttleTime`
	// argument determines a 'wait time' after a response arrives before new
	// requests are processed.
	//
	// argument: requestStream [Bacon.Observable, stream of request events]
	// argument: throttleTime [Int, number of milliseconds to wait after a request]
	// argument: makeRequest(request, function(result, error){...})
	Trace.requestResponseStream = function(requestStream, throttleTime, makeRequest){
		var responses = new Bacon.Bus()

		var latestRequest = undefined
		var requestInProgress = undefined

		requestStream.onValue(function(r){

			// If there was a request waiting, replace it with the new one
			latestRequest = {request: r}

			// If there was no request already running, run this request now.
			if(!requestInProgress){
				startNewRequest()
			}
		})

		function startNewRequest(){
			// Take the 'latest' request and mark it as 'in progress'
			requestInProgress = latestRequest
			latestRequest = undefined

			makeRequest(requestInProgress.request, function(result, error){
				if(error) responses.error(error)
				else responses.push(result)

				// wait before potentially kicking off the next request.
				// this is the throttle time to avoid making requests too often.
				setTimeout(function(){
					requestInProgress = undefined

					// if there was a request waiting to run, do it now
					if(latestRequest) {
						startNewRequest()
					}
				}, throttleTime)
			})
		}

		return responses
	}


})(this.Trace || (this.Trace = {}))