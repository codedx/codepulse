$(document).ready(function(){
	var $uiContainer = $('.TraceConnectorUI'),
		$gearButton = $uiContainer.find('.gear-button'),
		$gearIcon = $gearButton.find('.gear i'),
		$settingsSlider = $uiContainer.find('.settings-slider'),
		$messageSlider = $uiContainer.find('.message-slider')

	var isSettingsOpen = false

	function toggleSettingsOpen(open){
		if(arguments.length) isSettingsOpen = open
		else isSettingsOpen = !isSettingsOpen

		$gearButton.toggleClass('open', isSettingsOpen)
		$settingsSlider.toggleClass('open', isSettingsOpen)
	}

	function setUIState(newState /* one of <idle|connecting|running> */, animated){
		var state, spinning;
		if(newState == 'idle'){
			state = 'idle'
			spinning = false
		} else if(newState == 'connecting'){
			state = 'connecting'
			spinning = false
		} else if(newState == 'running'){
			state = 'running'
			spinning = true
		} else {
			console.error('invalid state:', newState)
			return
		}

		$uiContainer.removeClass('trace-idle trace-connecting trace-running')
		$uiContainer.addClass('trace-' + state)

		$gearIcon.toggleClass('fa-spin', spinning)

		$messageSlider.toggleClass('animated', animated)
		$messageSlider.toggleClass('open', state == 'connecting')
	}

	// Some testing buttons to manually set the UI state
	$('#test-trace-idle').click(function(){ setUIState('idle', true) })
	$('#test-trace-connecting').click(function(){ setUIState('connecting', true) })
	$('#test-trace-running').click(function(){ setUIState('running', true) })

	// Clicking the gear button should toggle the settings popup
	$gearButton.click(toggleSettingsOpen)

	// Update the ui state based on the initial css class on the container
	;(function(){
		var cls = $uiContainer.attr('class'),
			r = /trace-(\w+)/.exec(cls)
		r && r[1] && setUIState(r[1], false)
	})()

	// Show the appropriate message based on the current page
	CodePulse.isOnHomePage && $messageSlider.find('.main-page-message').show()
	CodePulse.isOnProjectPage && $messageSlider.find('.project-page-message').show()

	// Clicking the 'another project' links in the new connection message
	// should open the Project Switcher.
	$messageSlider.find('.message a[role=other]').click(ProjectSwitcher.open)
})