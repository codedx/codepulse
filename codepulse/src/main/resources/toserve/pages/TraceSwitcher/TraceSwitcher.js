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

/*
TraceSwitcher.js

This script provides the `TraceSwitcher` object in global scope.

It also will automatically wire up a button with the "data-toggle"
attribute set to "TraceSwitcher"; the button will cause the
TraceSwitcher's UI to toggle between shown and hidden states (by
calling TraceSwitcher.open() and TraceSwitcher.close() accordingly).

This script will automatically be included with the "TraceSwitcher"
template html. 
*/
;(function(exports){

	// predefinition of jQuery vars that will be assigned when the document is ready
	var slideOutForm, slideDownSidebar, formOpener

	var feedbackZone

	// the main exported variable, defined later
	var TraceSwitcher

	$(document).ready(function(){
		// get references to the moving parts
		slideOutForm = $('.traceSwitcher .slideOutForm')
		slideDownSidebar = $('.traceSwitcher .slideDownSidebar')
		formOpener = slideDownSidebar.find('.addTraceButton')
		feedbackZone = $('.traceSwitcher .feedback')

		feedbackZone.click(function(){
			sendFeedback("you clicked the feedback zone")
		})

		// wire up a file upload process to the fileChooser
		setupFileUpload()

		setupDropZone()

		// clicking the "Add a new trace target" button opens and closes the form
		formOpener.click(function(){ showForm('toggle') })

		// auto-setup a master button control to toggle the TraceSwitcher view
		$('[data-toggle=TraceSwitcher]').each(function(){
			wireUpController($(this))
		})
	})

	// showSidebar('toggle'|true|false) to show/hide the slideDownSidebar
	function showSidebar(arg){
		if(!slideDownSidebar) return

		var isCollapsed = slideDownSidebar.hasClass('collapsed'),
			shouldBeCollapsed = (arg == 'toggle') ? !isCollapsed : !arg

		slideDownSidebar.toggleClass('collapsed', shouldBeCollapsed)

		return !shouldBeCollapsed
	}

	// showForm('toggle'|true|false) to show/hide the slideOutForm
	function showForm(arg){
		if(!slideOutForm) return

		var isCollapsed = slideOutForm.hasClass('collapsed'),
			shouldBeCollapsed = (arg == 'toggle') ? !isCollapsed : !arg

		slideOutForm.toggleClass('collapsed', shouldBeCollapsed)

		formOpener.text(shouldBeCollapsed ? '+ New Trace' : 'Cancel')

		return !shouldBeCollapsed
	}



	function wireUpController($button){
		var switcherOpen = false
		$button.click(function(){
			switcherOpen = !switcherOpen
			TraceSwitcher[switcherOpen ? 'open': 'close']()
			$button.toggleClass('active', switcherOpen)
		})
	}

	function sendFeedback(message, style){
		var msgDiv = $("<div class='message out'>").text(message).hide(),
			msgBox = $("<div class='message-box'>")

		style && msgBox.addClass(style)

		msgBox.append(msgDiv).prependTo(feedbackZone)

		msgDiv.slideDown(function(){
			msgDiv.removeClass('out')
		})
	}

	function setupFileUpload(){ 
		var fileChooser = slideOutForm.find('input[type=file]'),
			progressFill = slideOutForm.find('.uploadProgressBar .fill'),
			filenameSpan = slideOutForm.find('[name=filename]'),
			uploadUrl = '/trace-api/file-upload'

		fileChooser.fileupload({
			url: uploadUrl,
			dropZone: slideOutForm,
			add: function(e, data){

				var file = data.files[0],
					filename = file.name,
					filepath = file.path

				filenameSpan.text(filename)

				// Reset the progress bar
				updateProgress(0)

				if(CodePulse.isEmbedded && filepath) doNativeUpload(filepath)
				else {
					console.log('using browser upload behavior on ', filename)
					data.submit()
				}
			},
			done: function(e, data){
				onUploadDone(data.result)
			},
			error: function(e, data){
				onUploadError(e.responseText)
			},
			progress: function(e, data){
				// The actual upload will appear like 90% of the progress.
				// The remaining 10% is for the server to parse the file and respond.
				updateProgress(0.9 * +data.loaded / +data.total)
			}
		})

		function onUploadError(err){
			sendFeedback('Error: ' + (err || '(unknown error)'), 'error')
			updateProgress(0)
		}

		function onUploadDone(result){
			updateProgress(1)

			var there = result.href,
				here = window.location.pathname,
				href = window.location.href.replace(new RegExp(here + '$'), there)
			console.log('there: ' + there, 'here: ' + here, 'href: ' + href)
			window.location.href = href
		}

		function doNativeUpload(path){
			console.log('using native upload behavior on ', path)
			updateProgress(0.9)
			$.ajax(uploadUrl, {
				data: {'path': path},
				type: 'POST',
				error: function(xhr, status){ onUploadError(xhr.responseText) },
				success: function(data){ onUploadDone(data) }
			})
			// TraceAPI.uploadFilePath(path, function(data, err){
			// 	if(err) onUploadError(err)
			// 	else onUploadDone(data)
			// })
		}

		function updateProgress(ratio){
			if(!isNaN(ratio) && isFinite(ratio)){
				var fillWidth = parseInt(ratio * 100) + '%'
				progressFill.css('width', fillWidth)
			}
		}

	}

	function setupDropZone(){
		var $zone = slideOutForm.find('.dropzone'),
			timeout = undefined

		$(document).bind('dragover', function(e){
			if(!timeout) $zone.addClass('in')
			else clearTimeout(timeout)

			// figure out if we're dragging over the dropzone
			var found = false, node = e.target
			do {
				if(node === $zone[0]){
					found = true
					break
				}
				node = node.parentNode
			} while(node != null)

			// set the 'hover' class depending on `found`
			$zone.toggleClass('hover', found)

			timeout = setTimeout(function(){
				timeout = null
				$zone.removeClass('in hover')
			}, 100)
		})
	}

	/* Drag and Drop is useless if they don't have files */
	function isFileDrag(event) {
		if(event.originalEvent) return isFileDrag(event.originalEvent)
		
		var dt = event.dataTransfer
		
		// can't use forEach or $.inArray because IE10 uses a DomStringList
		var foundFiles = false, idx
		for(idx = 0; idx < dt.types.length; idx++){
			var s = dt.types[idx]
			if(s == 'Files') foundFiles = true
		}
		
		return dt && foundFiles 
	}

	TraceSwitcher = exports['TraceSwitcher'] = {
		'open': function(){ showSidebar(true) },
		'close': function(){
			showForm(false)
			showSidebar(false)
		}
	}

})(this);