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

$(document).ready(function(){
	var $uiContainer = $('.TraceConnectorUI'),
		$gearButton = $uiContainer.find('.gear-button'),
		$gearIcon = $gearButton.find('.gear i'),
		$settingsSlider = $uiContainer.find('.settings-slider'),
		$messageSlider = $uiContainer.find('.message-slider'),
		$currentTraceMessage = $uiContainer.find('.current-trace-message')

	var isSettingsOpen = false

	function toggleSettingsOpen(open){
		if(arguments.length) isSettingsOpen = open
		else isSettingsOpen = !isSettingsOpen

		$gearButton.toggleClass('open', isSettingsOpen)
		$settingsSlider.toggleClass('open', isSettingsOpen)
	}

	// `stateObj` contains the following fields:
	// 'state': a String, either "idle", "connecting", or "running"
	// 'animateSlide': Boolean, defaults to false. Determines if the message
	//     slider should animate in/out.
	// 'tracedProject': an Integer, optional if the 'state' isn't "running"
	function setUIState(stateObj){

		var state = stateObj.state
		if(state != 'idle' && state != 'connecting' && state != 'running') return

		// flag for if the given 'tracedProject' is the same as the page we are looking at
		var tracedProjectIsCurrent = CodePulse.isOnProjectPage &&
			(CodePulse.projectPageId == stateObj.tracedProject)

		// set the 'trace-xxx' class on the container based on the state
		$uiContainer.removeClass('trace-idle trace-connecting trace-running')
		$uiContainer.addClass('trace-' + state)

		if(state == 'running'){
			console.log('running in state:', state)
			$currentTraceMessage.removeClass('this-project other-project')
			$currentTraceMessage.addClass(tracedProjectIsCurrent ? 'this-project' : 'other-project')
			$currentTraceMessage.find('a')
				.attr('href', CodePulse.projectPath(stateObj.tracedProject))
			$currentTraceMessage.slideDown(150)
		} else {
			console.log('hide current trace message')
			$currentTraceMessage.slideUp(150)
		}

		$gearIcon.toggleClass('fa-spin', state == 'running')

		$messageSlider.toggleClass('animated', stateObj.animateSlide)
		$messageSlider.toggleClass('open', state == 'connecting')

		ConnectionHelpForm.setDisabledState(state == 'running')
	}

	function rejectConnection(){
		$.ajax(CodePulse.apiPath('connection/reject'), {type: 'POST'})
	}

	function acceptConnection(projectId){
		$.ajax(CodePulse.apiPath('connection/accept/' + projectId), {type: 'POST'})
	}

	// Some testing buttons to manually set the UI state
	$('#test-trace-idle').click(function(){
		setUIState({
			state: 'idle',
			animateSlide: true
		})
	})
	$('#test-trace-connecting').click(function(){
		setUIState({
			state: 'connecting',
			animateSlide: true
		})
	})
	$('#test-trace-running-0').click(function(){
		setUIState({
			state: 'running',
			tracedProject: 0,
			animateSlide: true
		})
	})
	$('#test-trace-running-1').click(function(){
		setUIState({
			state: 'running',
			tracedProject: 1,
			animateSlide: true
		})
	})

	// The ConnectionLooperEvents comet component will trigger
	// 'connector-state-change' events when it changes state.
	$(document).on('connector-state-change', function(e,data){
		var state = {
			state: data.state,
			animateSlide: true
		}
		if(data.project) state.tracedProject = data.project
		setUIState(state)
	})

	// Clicking the gear button should toggle the settings popup
	$gearButton.click(toggleSettingsOpen)

	// Update the ui state based on the initial css class on the container.
	// The tracedProject may be specified as a data attribute.
	;(function(){
		var cls = $uiContainer.attr('class'),
			r = /trace-(\w+)/.exec(cls)
		r && r[1] && setUIState({
			state: r[1],
			animateSlide: false,
			tracedProject: $uiContainer.data('tracedProject')
		})
	})()

	// Show the appropriate message based on the current page
	CodePulse.isOnHomePage && $messageSlider.find('.main-page-message').show()
	CodePulse.isOnProjectPage && $messageSlider.find('.project-page-message').show()

	// Clicking the 'another project' links in the new connection message
	// should open the Project Switcher.
	$messageSlider.find('.message a[role=other]').click(ProjectSwitcher.open)

	// Clicking the 'drop' links in the message slider should reject the awaiting connection
	$messageSlider.find('.message a[role=drop]').click(rejectConnection)

	// Clicking the 'accept' links in the message slider should accept the trace with the current project
	$messageSlider.find('.message a[role=accept]').click(function(){
		acceptConnection(CodePulse.projectPageId)
	})
})