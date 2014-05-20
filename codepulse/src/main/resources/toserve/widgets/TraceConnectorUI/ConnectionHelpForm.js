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

	// Sets up a form that lets users change the agent connection port
	// and shows them the resulting agent connection string.
	function ConnectionHelpForm($formRoot){
		var $portInput = $formRoot.find('input[name=agent-port]'),
			$portInputParent = $portInput.parent(),
			$formResult = $formRoot.find('.form-result'),
			$errorMessage = $formResult.find('.error-message'),
			$agentString = $formResult.find('.trace-agent-command')

		// Set the error/success state of the form result.
		// If `error` is specified, it sets the error state;
		// otherwise, it sets the success state.
		function setFormStatus(error){
			if(error){
				$formResult.removeClass('success').addClass('error')
				$errorMessage.text(error)
			} else {
				$formResult.removeClass('error').addClass('success')
				$errorMessage.text('')
			}
		}

		// Sets the error/success state of the port number input.
		// If `error` is specified, it sets the 'invalid' state with
		// the given error message; otherwise, it sets a 'success' state.
		function setPortInputState(error){
			if(error){
				$portInput.addClass('invalid')
				setFormStatus(error)
			} else {
				$portInput.removeClass('invalid')
				setFormStatus(null)
			}
		}

		// Requests the most up-to-date agent string from the server,
		// applying it to the UI once it's done.
		function updateTraceAgentCommand(){
			$agentString.overlay('wait')

			$.ajax('/api/agent-string', {
				type: 'GET',
				success: function(str) {
					$agentString.text(str)
				},
				error: function(xhr, status) {
					console.error('failed to get updated agent string', xhr.responseText)
				},
				complete: function(){
					$agentString.overlay('ready')
				}
			})
		}

		// Based on the current value in the $portInput, this function
		// sends the new port number to the backend, then updates the
		// agent string. If errors happen along the way, the appropriate
		// error state will be set.
		function updateFormResult(){
			var portValue = $portInput.val().trim()
			if (/^\d+$/.test(portValue)) {
				setPortInputState(null)

				$.ajax('/api/agent-port', {
					data: { 'port': portValue },
					type: 'PUT',
					success: function() {
						$portInput.val(portValue)
						setPortInputState(null)
						updateTraceAgentCommand()
					},
					error: function(xhr, status) {
						setPortInputState(xhr.responseText)
					}
				})
			} else {
				setPortInputState('Port numbers must be numeric.')
			}
		}

		// Loads the current agent connection port number setting from the server.
		// This should be called during initialization, and probably never again.
		function loadAgentPort(){
			$portInputParent.overlay('wait')

			$.ajax('/api/agent-port', {
				type: 'GET',
				success: function(port) {
					$portInput.val(port)
					setPortInputState(null)
				},
				error: function(xhr, status) {
					setPortInputState('please enter a port number')
					console.error('failed to get agent port', xhr.responseText)
				},
				complete: function(){
					$portInputParent.overlay('ready')
				}
			})
		}

		function setDisabledState(disabled){
			$portInput.attr('disabled', disabled ? true : null)
		}

		// Initialize the form
		loadAgentPort()
		$portInput.on('input', updateFormResult)
		this.setDisabledState = setDisabledState
	}

	var rememberedForms = []

	// When the UI is ready, set up the ConnectionHelpForm widgetry
	// on all elements with the 'connection-help-form' css class.
	$(document).ready(function(){
		$('.connection-help-form').each(function(){
			var $this = $(this),
				formControl = new ConnectionHelpForm($this)
			$this.data('formControl', formControl)
			rememberedForms.push(formControl)
		})
	})

	// Expose a single object that controls each form that was created
	// when the page loaded.
	exports.ConnectionHelpForm = {
		setDisabledState: function(disabled){
			rememberedForms.forEach(function(form){
				form.setDisabledState(disabled)
			})
		}
		// add more API methods here as needed
	}
})(this)
