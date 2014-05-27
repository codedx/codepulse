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
editable.js

Adds the `editable` function to jQuery objects.
This script isn't made to be extermely general purpose;
it's coupled to the internal contents of the editable
element, expecting the editor elements to already be in
place.
*/
(function(exports, $){

	function Editable($elem){
		var $input = $elem.find('.editor input[type=text]'),
			$save = $elem.find('.editor .editor-save'),
			$content = $elem.find('.edit-content')

		function setEditing(isEditing){
			$elem.toggleClass('editing', isEditing)
			if(isEditing){
				$input
					.val($content.text().trim())
					.focus()
					.select()
			}
		}

		function saveEditor(){
			var txt = $input.val().trim()
			if(!txt){
				closeEditor()
			} else {
				$content.text(txt)
				setEditing(false)
				$elem.trigger('editable.save', txt)
			}
		}

		function closeEditor(){
			setEditing(false)
			$elem.trigger('editable.cancel')
		}

		$input.keydown(function(e){
			// Escape Key Pressed:
			if(e.which == 27) closeEditor()

			// Enter Key Pressed:
			if(e.which == 13) saveEditor()
		})

		// save when the 'save' button is clicked or the user leaves the input box
		$save.click(saveEditor)
		$input.focusout(saveEditor)

		this.open = function(){ setEditing(true) }

		this.getText = function(){ return $content.text().trim() }

		//$elem.on('click', ':not(.editing)', function(){ setEditing(true) })
		$content.on('click', function(){
			setEditing(true) 
		})
	}

	function getEditable($elem){
		var e = $elem.data('editable')
		if(!e){
			e = new Editable($elem)
			$elem.data('editable', e)
		}
		return e
	}

	$.fn.editable = function(action){
		var e = getEditable(this)

		if(action == 'open'){
			e.open()
		}

		if(action == 'getText'){
			return e.getText()
		}

		return this
	}

})(this, jQuery)