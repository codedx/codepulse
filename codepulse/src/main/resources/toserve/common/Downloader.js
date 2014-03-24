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

 ;(function(scope){

	function Downloader($elem){
		var url = $elem.data('downloader'),
			fileHint = $elem.data('filename')

		if(CodePulse.isEmbedded) {

			// NATIVE NODE-WEBKIT

			$elem.attr('href', 'javascript:void(0);')

			var filenamePicker = $('<input>')
				.attr('type', 'file')
				.attr('nwsaveas', fileHint)
				.css('display', 'none')
				.appendTo('body')

			function pickFilename(callback){ filenamePicker
				.val('')
				.one('change', function(){
					var path = filenamePicker.val()
					if(path) callback(path)
					filenamePicker.val('')
				})
				.click()
			}

			// clicking the download link triggers the filenamePicker's dialog
			$elem.click(function(e){
				pickFilename(function(path){
					console.log('picked filename: ', path)
					doDownload(url, path)
				})
			})

			this.destroy = function(){
				filenamePicker.remove()
			}

		} else {

			// BROWSER
			$elem
				.attr('href', url)
				.attr('download', fileHint || 'file')

			this.destroy = function(){}
		}

	}

	function doDownload(url, savePath){
		var xhr = new XMLHttpRequest()
		xhr.open('GET', url, true)
		xhr.responseType = 'arraybuffer'

		xhr.onload = function(e){
			var uint8array = new Uint8Array(this.response)
			var arrayBuffer = new Buffer(uint8array)

			console.log('loaded raw data from server; about to save it to ' + savePath, arrayBuffer)

			var fs = require('fs')

			fs.writeFile(savePath, arrayBuffer, function(err){
				if(err) {
					console.error('failed to save the array data:', err)
					alert('Failed to save ' + savePath + '\nError Message: ' + err.message)
				} else {
					console.log('saved the array file!')
				}
			})
		}

		xhr.send()
	}

	$.fn.traceDownloader = function(method){
		if(method == 'create'){
			var old = this.data('traceDownloader')
			if(old) old.destroy()

			var downloader = new Downloader(this)
			this.data('traceDownloader', downloader)
			return this
		}

		if(method == 'destroy'){
			var old = this.data('traceDownloader')
			if(old) old.destroy()
			this.data('traceDownloader', null)
			return this
		}

		var dl = this.data('traceDownloader')
		return dl
	}

	$(document).ready(function(){
		$('[data-downloader]').each(function(){
			// new Downloader($(this))
			$(this).traceDownloader('create')
		})
	})

})(this);