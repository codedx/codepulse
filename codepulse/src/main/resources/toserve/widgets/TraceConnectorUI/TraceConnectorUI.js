$(document).ready(function(){
	var $uiContainer = $('.TraceConnectorUI'),
		$gearButton = $uiContainer.find('.gear-button'),
		$gearIcon = $gearButton.find('.gear i'),
		$settingsSlider = $uiContainer.find('.settings-slider')

	var isSettingsOpen = false

	function toggleSettingsOpen(open){
		if(arguments.length) isSettingsOpen = open
		else isSettingsOpen = !isSettingsOpen

		$gearButton.toggleClass('open', isSettingsOpen)
		$settingsSlider.toggleClass('open', isSettingsOpen)
	}

	function setUIState(newState /* one of <idle|connecting|running> */){
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
	}

	$('#test-trace-idle').click(function(){ setUIState('idle') })
	$('#test-trace-connecting').click(function(){ setUIState('connecting') })
	$('#test-trace-running').click(function(){ setUIState('running') })

	$gearButton.click(function(){
		console.log('click')
		toggleSettingsOpen()
	})
})