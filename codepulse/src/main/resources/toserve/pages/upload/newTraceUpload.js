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
	var rootPathRegex = /(.*)\/newtrace\/\d+/,
		rootPath = (rootPathRegex.exec(document.location.pathname) || [])[1] || "",
		batchId = $('#upload-info').data('batchId'),
		uploadPath = rootPath + '/upload/file/' + batchId,
		$dropzone = $('.file-drop-zone'),
		$uploadList = $('.upload-list')
	
	/* Drag and Drop is useless if they don't have files (IE9 and down)*/
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

	/*
	 * Add the 'dragging' class to the $dropzone element
	 * whenever the user drags some files onto the page.
	 */
	var dropEffectTimeout = undefined
	$(document).bind('dragover', function (e) {
		// ignore drags with no files
		if(!isFileDrag(e)) return
		
		// clear the timeout that would remove the 'dragging' class
		if(dropEffectTimeout) clearTimeout(dropEffectTimeout)
		
		// add the 'dragging' class
		$dropzone.addClass('dragging')

		// 100ms from now, remove the 'dragging' class unless
		// we get another 'dragover' event
		dropEffectTimeout = setTimeout(function(){
			dropEffectTimeout = undefined
			$dropzone.removeClass('dragging')
		}, 100)
	});
	
	/* Prevent the browser from trying to open drag/dropped files */
	$(document).bind('drop dragover', function(e){ e.preventDefault() })

	/*
	 * Wire up the file upload button.
	 */
	$('#filechooser').fileupload({
		url: uploadPath,
		singleFileUploads: true,
		dataType: 'json',
		maxFileSize: maxUploadSize,

		done: function(e, data){ data.context.onDone(data) },
		
		error: function(e, data){
			(data.files || []).forEach(function(f){
				alert('Error while trying to upload ' + f.name)
			})
		},
		
		add: function(e, data){
			var file = (data.files || [])[0]
			if(file && file.size > maxUploadSize){
				alert("Not uploading " + file.name + " because it exceeds the " +
					" server's configured maximum file upload size of " +
					formatFilesize(maxUploadSize)
				)
				return false
			}
			
			var uploader = new Uploader(uploadPath, data)
			$uploadList.append(uploader.ui.$li)
			data.context = uploader
			var xhr = data.submit()
				.error(function(uploadData){
					uploader.onError(uploadData)
				})
		},
		
		progress: function(e, data) { data.context.onProgress(data) }
	})
	
	/* Set up the UI for file upload verifications. The server will send a comet
	 * message that triggers a 'verifications-updated' event whenever the upload
	 * state gets changed. React to each event by updating the UI.
	 */
	var verificationsUI = new VerificationMessages('.upload-verifications')
	
	$(document).on('verifications-updated', function(){
		var verifications = Array.prototype.slice.call(arguments, 1)
		verificationsUI.setMessages(verifications)
		var hasError = verificationsUI.hasError()
		$('.upload-verifications')
			.toggleClass('error', hasError)
			.toggleClass('success', !hasError)
	})
	
	var spinner = new Spinner({lines: 7, length: 5, radius: 2, width: 3, color: '#FFF'}),
		starterButton = $('#start-analysis-button'),
		starterIcon = starterButton.find('.icon'),
		pulseOverlay = $('<div>').addClass('pulse-overlay'),
		pulseContainer = $('.upload-list').closest('section-body').addClass('overlay-container')
		
	function activateInvokerSpinner(on){
		if(on){
			spinner.spin(starterIcon[0])
			starterIcon.removeClass('icon-play')
		} else {
			spinner.stop()
			starterIcon.addClass('icon-play')
		}
	}
	
	var $pulseOverlay = $('<div>').addClass('pulse-overlay'),
		$pulseOverlayContainer = $('#uploads-container')
	
	function activateOverlay(arg) {
		if(arg) {
			$pulseOverlay.appendTo($pulseOverlayContainer)
		} else {
			$pulseOverlay.remove()
		}
		
		$pulseOverlay.toggleClass('done', arg === 'done')
	}
	
	/*
	 * Do-Once gate for the start analysis button.
	 * When clicked, this flag is set to true. If clicked
	 * while the flag is true, do nothing. After clicking
	 * and the invokeAnalysis fails in a recoverable way,
	 * reset the flag to false so the user can click again.
	 */
	var clickedStartAnalysis = false
	
	// When the user clicks the 'start analysis' button,
	// call `invokeAnalysis` to start the analysis
	$("#start-analysis-button").click(function(){
		if(clickedStartAnalysis) return
		clickedStartAnalysis = true
		
		activateInvokerSpinner(true)
		activateOverlay(true)
		invokeAnalysis() // function provided in the page source by the server
	})
	
	// Handler for when the analysis failed to start.
	$(document).on('invoke-analysis-failed', function(e, d){ 
		if(d.failure){
			if(d.recoverable){
				activateInvokerSpinner(false)
				activateOverlay(false)
				clickedStartAnalysis = false
			} else {
				$('.upload-verifications').hide()
				activateOverlay('done')
			}
			alert(d.failure)
		}
	})
	
	// Handler for progress events during the analysis
	$(document).on('analysis-progress', function(e, d){ 
		$('.upload-verifications').hide()

		// Analysis was queued (waiting to start)
		if(d === 'queued') $('.upload-state').attr('state', 'queued')
		
		// Analysis was started, so start the timer
		if(d === 'started') {
			$('.upload-state').attr('state', 'started')
			$('.upload-state .state-info .timer').timer('start')
		}
		
		// Analysis completed; stop the timer; finish the pulse overlay; fill in the link
		if(d['completed']) {
			$('.upload-state').attr('state', 'completed')
			$('.upload-state .state-info .timer').timer('stop')
			activateOverlay('done')
			
			var analysisHref = rootPath + '/run/' + d['completed']
			$('#analysis-link').attr('href', analysisHref)
		}
		
		// Analysis had an error; stop the timer; finish the pulse overlay
		if(d === 'failed') {
			$('.upload-state').attr('state', 'error')
			$('.upload-state .state-info .timer').timer('stop')
			activateOverlay('done')
		}
	})
	
	/*
	 * Set up a handler for analysis issues that come in through global events.
	 * Each issue should be added to an AnalysisIssues UI element.
	 */
	
	var $issuesContainer = $("#analysis-issues-holder").hide(),
		issuesUI = new AnalysisIssues()

	issuesUI.$elem.appendTo($issuesContainer.find('.section-body'))
	
	// Handler for when an issue occurs during the analysis
	$(document).on('analysis-issue', function(event, issue){
		$issuesContainer.show()
		issuesUI.addIssue(issue)
	})
	
});