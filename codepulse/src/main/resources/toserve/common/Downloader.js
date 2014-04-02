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

			function getAbsoluteUrl(relative) {
				var resolver = document.createElement('a');
				resolver.href = relative;
				return resolver.href;
			}

			function pickFilename(callback){ filenamePicker
				.val('')
				.one('change', function(){
					var path = filenamePicker.val()
					if(path) callback(path)
					filenamePicker.val('')
				})
				.click()
			}

			function doDownload(url){
				var http = require('http'), fs = require('fs');

				var request = http.get(getAbsoluteUrl(url), function(response) {
					if (response.statusCode == '200') {
						pickFilename(function(path) {
							var file = fs.createWriteStream(path);
							response.pipe(file);
							file.on('finish', function() { file.close(); });
						});
					} else
						alert('Error exporting: statusCode = ' + response.statusCode);
				}).on('error', function(e) { alert('Error exporting: ' + e); });
			}

			// clicking the download link triggers the filenamePicker's dialog
			$elem.click(function(e){
				doDownload(url)
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