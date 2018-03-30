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

var events = require('events'), http = require('http'), byline = require('./byline');
var fs = require('fs');

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
var stopPort = false, stopKey = false;

function getJava() {
	var platform = require('os').platform;

	switch (platform()) {
		case 'darwin':
			return 'jre/Contents/Home/bin/java'

		case 'linux':
			return 'jre/bin/java'

		case 'win32':
			return 'jre/bin/java.exe';

		default:
			throw 'unknown platform ' + platform();
	}
}

function ensureReadAndExecute(path) {
    try {
        writeLog('Testing user\'s read & execute permission for ' + path + '...\n')
		var unusedAlways = fs.accessSync(path, fs.constants.R_OK | fs.constants.X_OK);
	} catch (e) {
		writeLog('User does not have read & execute permissions for ' + path + ': ' + e + '...\n');

		var newPermission = 0755;
		writeLog('Attempting to change permission for ' + path + ' to ' + newPermission.toString(8) + '...\n');
		fs.chmodSync(path, newPermission);
	}
}

var started = false;

function startCodePulse() {
	if (started) return;
	started = true;


	var spawn = require('child_process').spawn;

	try {
		var java = getJava();

        ensureReadAndExecute(java)

        const symbolServicePath = 'dotnet-symbol-service/SymbolService'
        var symbolServicePathExists = fs.existsSync(symbolServicePath)

        writeLog(symbolServicePath + ' exists: ' + symbolServicePathExists + '\n')
        if (symbolServicePathExists) {
            ensureReadAndExecute(symbolServicePath)
        }

		var args = [ '-DSTOP.PORT=0', '-Drun.mode=production', '-jar', 'start.jar', 'jetty.host=localhost', 'jetty.port=0' ];

		writeLog('Starting Code Pulse...\n');
		writeLog('Using Java: ' + java + '\n');
		writeLog('Starting with args: ' + args + '\n');
		writeLog('\n');

		childProcess = spawn('../' + java, args, { cwd: './backend' });

		childProcess.stdout.setEncoding('utf-8');
		childProcess.stderr.setEncoding('utf-8');

		var dataChecks = [
			function(data) {
				var m = data.match(/STOP\.PORT=(\d+)/)
				if (m) {
					stopPort = m[1];
					writeLog('Stop port is ' + stopPort + '\n');
					return true;
				}

				return false;
			},

			function(data) {
				var m = data.match(/STOP\.KEY=(.+)/)
				if (m) {
					stopKey = m[1];
					writeLog('Stop key is ' + stopKey + '\n');
					return true;
				}

				return false;
			},

			function(data) {
				var m = data.match(/Started ServerConnector@\w+\{[^\}]+}\{([^\}]+)\}/)
				if (m) {
					writeLog('Code Pulse running on ' + m[1] + '\n');
					cpUrl = 'http://' + m[1] + '/';
					cpEvent.emit('started', cpUrl);
					return true;
				}

				return false;
			}
		];

		function doDataChecks(data) {
			for (var i = 0; i < dataChecks.length; i++) {
				if (dataChecks[i](data)) {
					dataChecks.splice(i, 1);
					i--;
				}
			}
		}

        var childStdOut = byline.createStream(childProcess.stdout)
        var childStdErr = byline.createStream(childProcess.stderr)

        childStdOut.on('data', doDataChecks);
        childStdOut.on('data', (data) => { writeLog(data + '\n') });

        childStdErr.on('data', doDataChecks);
        childStdErr.on('data', (data) => { writeLog(data + '\n') });

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
	if (stopPort && stopKey) {
		var spawn = require('child_process').spawn;

		// make an effort to stop using stop.port/stop.key
		try {
			var args = [ '-DSTOP.PORT=' + stopPort, '-DSTOP.KEY=' + stopKey, '-jar', 'start.jar', '--stop' ];
			writeLog('Stopping Code Pulse (with args: ' + args + ')\n');
			childProcess = spawn('../' + getJava(), args, { cwd: './backend' });
			return true;
		} catch (err) {
			writeLog('Error stopping Code Pulse: ' + err + '\n');
		}
	}

	if (childProcess) {
		// fall back on sending SIGINT, which may not work on Windows
		writeLog('Stopping Code Pulse using SIGINT\n');
		childProcess.kill(signal || 'SIGINT');
		return true;
	}

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
	start: startCodePulse,
	kill: killCodePulse,
	addListener: cpEvent.addListener,
	on: function(event, listener) { checkNewCPEvent(event, listener); cpEvent.on(event, listener); return exports.codepulse; },
	once: function(event, listener) { if (checkNewCPEvent(event, listener)) cpEvent.once(event, listener); return exports.codepulse; },
	removeListener: function(event, listener) { cpEvent.removeListener(event, listener); return exports.codepulse; }
};

function checkWindowClose(window, cb) {
	if (cpUrl) {
		// make sure we don't wait any longer than 10 seconds
		var cwcTimeout = setTimeout(function() {
			cb();
		}, 10000);

		var request = http.get(cpUrl + '/api/projects', function(res) {
			if (res.statusCode == 200) {
				var body = '';
				res.setEncoding('utf8');
				res.on('data', function(chunk) {
					body += chunk;
				});
				res.on('error', function() {
					clearTimeout(cwcTimeout);
					cb();
				});
				res.on('end', function() {
					clearTimeout(cwcTimeout);
					var projects = JSON.parse(body);
					var idle = true;
					projects.forEach(function(project) {
						if (project.state == 'loading' || project.state == 'running')
							idle = false;
					});

					if (idle || window.confirm('Exiting Code Pulse while a project is being loaded or traced may cause data loss. Would you like to exit anyway?'))
						cb();
				})
			} else cb();
		})
	} else cb();
}

var mainWindow = false;

exports.registerMainWindow = function(window) {
	if (!mainWindow) {
		writeLog('Registering main window...\n');
		mainWindow = window;

		initStorage();

		window.gui.on('close', function() {
			checkWindowClose(window.window, function() {
				writeLog('Window closed, stopping Code Pulse...\n');

				window.gui.hide();
				if (childProcess)
                    killCodePulse();
				else
					window.gui.close(true);
			});
		});

		cpEvent.on('stopped', function() {
			writeLog('Code Pulse stopped; closing main window.\n');
			window.gui.close(true);
		});
	} else {
		writeLog('Attempt to re-register main window ignored...\n');
	}
}

// an alternate implementation of localStorage for the embedded version
// (since port number changes, so we're on a different origin every time, and therefore receive a
// different localStorage)
function initStorage() {
	var path = require('path'), fs = require('fs');

	var storage = {},
		storeFile = path.join(nw.App.dataPath, 'storage.json');

	function loadStorage() {
		try {
			if (fs.existsSync(storeFile))
				storage = JSON.parse(fs.readFileSync(storeFile, 'utf8'));
		} catch (e) {
			writeLog('Error loading storage.json: ' + e + '\n');
		}
	}

	function writeStorage() {
		fs.writeFileSync(storeFile, JSON.stringify(storage));
	}

	function getItem(key) {
		return storage[key];
	}

	function setItem(key, value) {
		if (typeof value !== 'string')
			value = value.toString();

		storage[key] = value;
		writeStorage();
	}

	function removeItem(key) {
		delete storage[key];
		writeStorage();
	}

	function clear() {
		storage = {};
		writeStorage();
	}

	loadStorage();

	exports.storage = {
		getItem: function(key) { return getItem(key); },
		setItem: function(key, value) { setItem(key, value); },
		removeItem: function(key) { removeItem(key); },
		clear: function() { clear(); }
	};
}
