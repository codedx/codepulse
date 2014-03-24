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

//TODO handle uncaught node.js errors

var events = require('events');

var logContents = '', logEvent = new events.EventEmitter();

function writeLog(str) {
	logContents += str;
	logEvent.emit('log', str);
}

exports.log = {
	write: writeLog,
	getContents: function() { return logContents; },
	on: function(event, listener) { logEvent.on(event, listener); return exports.log; },
	removeListener: function(event, listener) { logEvent.removeListener(event, listener); return exports.log; }
};

var childProcess = false, cpUrl = false, cpEvent = new events.EventEmitter();

function startCodePulse() {
	var platform = require('os').platform,
	    spawn = require('child_process').spawn;
	    chmodSync = require('fs').chmodSync;

	try {
		var java = false;

		switch (platform()) {
			case 'darwin':
				java = 'jre/Contents/Home/bin/java'
				break;

			case 'linux':
				java = 'jre/bin/java'
				break;

			case 'win32':
				java = 'jre/bin/java.exe';
				break;

			default:
				throw 'unknown platform ' + platform();
		}

		// make sure java is executable...
		chmodSync(java, 0755);

		var args = [ '-XX:MaxPermSize=128M', '-Xmx1024M', '-jar', 'start.jar', 'jetty.host=localhost', 'jetty.port=0' ];

		writeLog('Starting Code Pulse...\n');
		writeLog('Using Java: ' + java + '\n');
		writeLog('Starting with args: ' + args + '\n');
		writeLog('\n');

		childProcess = spawn('../' + java, args, { cwd: './backend' });

		childProcess.stdout.setEncoding('utf-8');
		childProcess.stderr.setEncoding('utf-8');

		childProcess.stderr.on('data', function(data) {
			var m = data.match(/Started ServerConnector@\w+\{HTTP\/1.1\}\{([^\}]+)\}/)
			if (m) {
				writeLog('Code Pulse running on ' + m[1] + '\n');
				cpUrl = 'http://' + m[1] + '/';
				cpEvent.emit('started', cpUrl);
			}
		});

		childProcess.stdout.on('data', writeLog);
		childProcess.stderr.on('data', writeLog);

		childProcess.on('error', function(err) {
			writeLog('Error running Code Pulse: ' + err + '\n');
		});

		childProcess.on('exit', function(code) {
			writeLog('Code Pulse exited with code: ' + code + '\n');
			childProcess = false;
			cpEvent.emit('stopped', code);
		});
	} catch (err) {
		writeLog('Error spawning Code Pulse: ' + err + '\n');
	}
}

function killCodePulse(signal) {
	if (childProcess) {
		childProcess.kill(signal || 'SIGINT');
		return true;
	} else
		return false;
}

function checkNewCPEvent(event, listener) {
	if (event == 'started' && cpUrl) {
		listener(cpUrl);
		return false;
	} else
		return true;
}

exports.codepulse = {
	kill: killCodePulse,
	addListener: cpEvent.addListener,
	on: function(event, listener) { checkNewCPEvent(event, listener); cpEvent.on(event, listener); return exports.codepulse; },
	once: function(event, listener) { if (checkNewCPEvent(event, listener)) cpEvent.once(event, listener); return exports.codepulse; },
	removeListener: function(event, listener) { cpEvent.removeListener(event, listener); return exports.codepulse; }
};

startCodePulse();

var gotMainWindow = false;

exports.registerMainWindow = function(window) {
	if (!gotMainWindow) {
		writeLog('Registering main window...\n');

		window.on('close', function() {
			writeLog('Window closed, sending SIGINT to Code Pulse...\n');
			
			this.hide();
			if (childProcess)
				killCodePulse();
			else
				this.close(true);
		});

		cpEvent.on('stopped', function() {
			writeLog('Code Pulse stopped; closing main window.\n');
			window.close(true);
		});
	} else {
		writeLog('Attempt to re-register main window ignored...\n');
	}
}