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

	// Event Stream for the trace status ('idle' | 'connecting' | 'running' | 'ending')
	var statusBus = new Bacon.Bus()

	// Property for the status
	var statusProp = statusBus.toProperty()

	// Property that shows if the Trace is currently running, based on the status
	var runningProp = statusProp.map(function(status){
		switch(status){
			case 'running':
			case 'ending':
				return true
			default:
				return false
		}
	})

	Trace.isLoadingProp = statusProp.scan(true, function(previous, nextState){
		// prop remains true while the state is 'loading'.
		// once the state enters the first non-loading state, this prop becomes false
		return previous && nextState == 'loading'
	}).skipDuplicates()

	/*
	 * Load the current trace status.
	 */
	API.requestStatus(function(status, error){
		console.log('initial status:', status, error)
		if(error) statusBus.error(error)
		else statusBus.push(status)
	})

	/*
	 * Watch for `tracer-state-change` events to update the trace status
	 */
	$(document).on('tracer-state-change', function(event, state){ statusBus.push(state) })

	Trace.status = statusProp.noLazy().log('Trace State:')
	Trace.running = runningProp.noLazy()

	Trace.ready = function(f){
		$(document).ready(function(){
			Trace.status
				.takeWhile(function(status){ return status == 'loading' })
				.onEnd(f)
		})
	}

})(this.Trace || (this.Trace = {}))