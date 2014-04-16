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

	// wire up the new analysis form to submit to the trace creation url, with a mandatory name
	setupUploadForm($('#new-analysis-form'), '/trace-api/trace/create', false)

	// wire up the new import form to submit to the trace import url, with an optional name
	setupUploadForm($('#new-import-form'), '/trace-api/trace/import', true)

	function setupUploadForm($form, uploadUrl, nameOptional){
		/*
		 * Find all of the elements from the form so we can use them later.
		 */
		var fileInput = $form.find('[name=file-input]'),
			nameInput = $form.find('[name=name-input]'),
			fileChoiceLabel = $form.find('[name=file-input-choice-label]'),
			fileChoiceOriginalText = fileChoiceLabel.text(),
			fileDropzone = $form.find('.file-dropzone'),
			cancelButton = $form.find('[name=cancel-button]'),
			submitButton = $form.find('[name=submit-button]'),
			nameFeedbackArea = $form.find('[name=name-feedback]'),
			fileFeedbackArea = $form.find('[name=file-feedback]')

		/*
		 * Set the current state of the form (`name` and `fileData`) to null, initially.
		 */
		var currentName = null,
			currentFileData = null

		/*
		 * Set the `currentFeedback` to an empty object. As errors and warnings w.r.t.
		 * the form start appearing, they will be stored in this object, for display.
		 */
		var currentFeedback = {}

		/*
		 * Clear the form when the cancel button is clicked.
		 */
		cancelButton.click(clearForm)

		/*
		 * Submit the form when the submit button is clicked.
		 */
		submitButton.click(submitForm)

		/*
		 * Set up the fileInput using the jQuery file upload plugin.
		 * Don't submit files for upload immediately; submission is
		 * done when the user clicks OK and the form passes validation.
		 */
		fileInput.fileupload({
			url: uploadUrl,
			dropZone: fileDropzone,
			add: function(e, data){
				// use this `data` as the current file data.
				// this will be used once the form is submitted.
				currentFileData = data

				// get the file's name and put it in the fileChoiceLabel
				var filename = data.files[0].name
				fileChoiceLabel.text(filename)

				if(!currentName){
					currentName = filename
					nameInput.val(currentName).trigger('input')
				}
			},
			formData: function(){
				if(!currentName) return []
				else return [{name: 'name', value: currentName}]
			},
			done: function(e, data){
				onSubmitDone(data.result)
			},
			error: function(e, data){
				onSubmitError(e.responseText)
			}
		})

		/*
		 * Alternate code path to sending the data to the server, used
		 * when Code Pulse is being run as an embedded node-webkit app.
		 * It sends the `path` anc `currentName` to the server, assuming
		 * that the server is on the same machine, so it has read access
		 * to the file at that path.
		 */
		function doNativeUpload(path){
			console.log('using native upload behavior on ', path)
			$.ajax(uploadUrl, {
				data: {'path': path, name: currentName},
				type: 'POST',
				error: function(xhr, status){ onSubmitError(xhr.responseText) },
				success: function(data){ onSubmitDone(data) }
			})
		}

		/*
		 * Callback for when the file upload (both native and browser-based)
		 * encounters an error when trying to contact the server.
		 */
		function onSubmitError(err){
			alert('Error: ' + (err || '(unknown error)'))
		}

		/*
		 * Callback for when the file upload (both native and browser-based)
		 * finishes. The result is expected to contain a `href` field that
		 * tells the client the URL of the newly-created trace. The page will
		 * automatically be redirected to that location.
		 */
		function onSubmitDone(result){
			window.location.href = result.href
		}

		/*
		 * Set the `fileDropzone` so that it gets the `in` class while the
		 * user is dragging a file over the page, and the `hover` class when
		 * the drag goes over the drop zone itself.
		 */
		;(function setupDropZone(){
			var timeout = undefined

			$(document).bind('dragover', function(e){
				if(!isFileDrag(e)) return

				if(!timeout) fileDropzone.addClass('in')
				else clearTimeout(timeout)

				// figure out if we're dragging over the dropzone
				var found = false, node = e.target
				do {
					if(node === fileDropzone[0]){
						found = true
						break
					}
					node = node.parentNode
				} while(node != null)

				// set the 'hover' class depending on `found`
				fileDropzone.toggleClass('hover', found)

				timeout = setTimeout(function(){
					timeout = null
					fileDropzone.removeClass('in hover')
				}, 300)
			})
		})()

		/*
		 * Check if a drag event contains a file. We don't care about
		 * drag/drop events if they don't have files.
		 */
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
		 * Create an event stream that represents the value of the
		 * name input; it is updated 500ms after the latest change
		 * to the input, and yields the trimmed version.
		 */
		var nameInputValues = nameInput.asEventStream('input')
			.map(function(){ return nameInput.val().trim() })
		
		/*
		 * Assign the latest change from the nameInput to the currentName
		 */
		nameInputValues
			.onValue(function(name){
				currentName = name
			})

		/*
		 * As the name changes, ask the server if the latest name
		 * would conflict with the name of an existing trace. Set
		 * the 'name-conflict' feedback warning accordingly.
		 */
		nameInputValues
			.debounce(300)
			.flatMapLatest(function(name){
				return Bacon.fromNodeCallback(function(callback){
					checkNameConflict(name, callback)
				})
			})
			.onValue(function(hasNameConflict){
				if(hasNameConflict){
					setFeedback('name-conflict', 'Another trace has the same name', 'warning')
				} else {
					setFeedback('name-conflict', null)
				}
			})

		/*
		 * If the name is manditory, set the 'name-empty' feedback
		 * error if the name becomes blank after the user had already
		 * typed something (e.g. they deleted what they typed).
		 */
		if(!nameOptional) nameInputValues
			.skipWhile(function(name){ return !name.length })
			.map(function(name){ return !name.length })
			.onValue(function(isEmpty){
				if(isEmpty){
					setFeedback('name-empty', 'The name cannot be blank', 'error')
				} else {
					setFeedback('name-empty', null)
				}
			})

		/*
		 * Return whether or not the form can be submitted.
		 * There must be a file chosen, and there must be a name
		 * filled in unless the name is optional.
		 */
		function canSubmitForm(){
			var hasValidName = nameOptional || (currentName && currentName.trim())
			console.log(hasValidName, currentFileData)
			return hasValidName && currentFileData
		}

		/*
		 * Begin the upload process, using either 'native' or regular
		 * browser behavior depending on whether CodePulse is being run
		 * as an embedded node-webkit app or in a regular browser.
		 */
		function submitForm(){
			if(!canSubmitForm()){
				alert("You can't do that yet")
				return
			}

			var file = currentFileData.files[0],
				filepath = file.path

			if(CodePulse.isEmbedded && filepath){
				// native upload behavior
				doNativeUpload(filepath)
			} else {
				// browser upload behavior
				currentFileData.submit()
			}
		}

		/*
		 * Ask the server if there will be a name conflict with the given `name`.
		 * The `callback` is a `function(error, result)` in the style of node.js,
		 * because this function is intended to be used with Bacon.fromNodeCallback.
		 */
		function checkNameConflict(name, callback){
			$.ajax('/trace-api/check-name-conflict', {
				data: {'name': name},
				type: 'GET',
				success: function(data){
					callback(null, data == 'true')
				},
				error: function(xhr, status){
					callback(xhr.responseText, null)
				}
			})
		}

		/*
		 * Modify the `currentFeedback` object by adding or removing the
		 * entry associated with the given `category`. If `message` is defined,
		 * a feedback object will be created with the `message` and optional
		 * `type`. If not, the feedback object will be null. After modifying
		 * the `currentFeedback` object, a list of "feedbacks" is created and
		 * sent to the UI via updateFeedbackUI.
		 */
		function setFeedback(category, message, type){
			var feedbackObj = message ? {message: message, type: type} : null
			currentFeedback[category] = feedbackObj

			console.log(currentFeedback)

			var nameFeedbacks = [
				currentFeedback['name-conflict'],
				currentFeedback['name-empty']
			].filter(function(d){ return !!d })

			updateFeedbackUI(nameFeedbackArea, nameFeedbacks)

			// TODO: add file feedbacks
		}

		/*
		 * Set the feedback messages for the given $feedbackArea jQuery element.
		 * The `feedbackList` argument is expected to be an array of objects in the form of:
		 * `{message: <Any String>, type: ['error'|'warning'|undefined]}`
		 */
		function updateFeedbackUI($feedbackArea, feedbackList){
			var selection = d3.select($feedbackArea[0]).selectAll('.feedback').data(feedbackList)

			selection.exit().remove()
			selection.enter().append('div')

			selection
				.text(function(d){ return d.message })
				.attr('class', function(d){
					var t = d.type
					if(!t) return 'feedback'
					else return 'feedback ' + t
				})
		}

		/*
		 * Clears the form by setting the `currentX` variables to their initial states
		 * and resetting the input and feedback UI elements.
		 */
		function clearForm(){
			currentName = null
			currentFileData = null
			currentFeedback = {}

			nameInput.val('')
			fileChoiceLabel.text(fileChoiceOriginalText)
			updateFeedbackUI(nameFeedbackArea, [])
			updateFeedbackUI(fileFeedbackArea, [])
		}
	}

})