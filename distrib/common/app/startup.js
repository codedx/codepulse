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

var backend = process.mainModule.exports;
var log = backend.log;
var codepulse = backend.codepulse;

var gui = require('nw.gui');

function toggleLog() {
	var log = document.getElementById('log')
	var caret = document.getElementById('log-linkcaret');

	if (log.className == 'hidden') {
		log.className = '';
		caret.innerHTML = '&or;';
	} else {
		log.className = 'hidden';
		caret.innerHTML = '&and;';
	}
}

var logContents = '';

function updateLog(str) {
	logContents += str;

	var log = document.getElementById('log-content');

	log.innerText = logContents;
	log.scrollTop = log.scrollHeight;
}

function redirect(cpUrl) {
	log.write('Redirecting to Code Pulse instance at ' + cpUrl + '\n');
	log.removeListener('log', updateLog);
	window.location.href = cpUrl;
}

window.onload = function() {
	// get the log all wired up
	updateLog(log.getContents());
	log.on('log', updateLog);

	// listen in on code pulse events
	codepulse.once('started', redirect);

	// register the close hooks/etc for the window
	backend.registerMainWindow({ window: window, gui: gui.Window.get() });
}

if (gui.App.argv.indexOf('--log') >= 0) {
	window.open('log.html');
}