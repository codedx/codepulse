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

/**
 UpdateController.js
  - requires jQuery, Bacon.js
*/

;(function(exports) {

	var ReleasesUrl = 'https://api.github.com/repos/secdec/codepulse/releases'
	var CheckInterval = 6 * 60 * 60 * 1000 // check for updates every 6 hours

	function UpdateController() {
		var self = this

		var releaseBus = new Bacon.Bus
		this.release = releaseBus.toProperty().noLazy()

		function getVersion(callback) {
			$.getJSON(CodePulse.apiPath('version'), function(versionInfo) {
				callback(versionInfo.appVersion)
			})
		}

		function getLatestRelease(callback) {
			function getReleasePage(page, callback) {
				$.getJSON(ReleasesUrl, { page: page }, callback)
			}

			function recurse(page) {
				getReleasePage(page, function(releases) {
					if (releases.length > 0) {
						var filtered = releases.filter(function(release) { return !release.prerelease })
						var rel = filtered[0]

						if (rel)
							callback(rel)
						else
							recurse(page + 1)
					}
				})
			}

			recurse(1)
		}

		/** returns true if `releaseVersion` is newer than `ourVersion` */
		function compareVersions(ourVersion, releaseVersion) {
			var ourParts = ourVersion.split('.'), currentParts = releaseVersion.split('.')
			for (var i = 0, l = Math.max(ourParts.length, currentParts.length); i < l; i++) {
				var our = ourParts[i], current = currentParts[i]
				if (!our || our < current) return true
			}
			return false
		}

		/** returns true if the user has ignored this version */
		function isUpdateIgnored(version) {
			return localStorage.getItem('updater.ignoredVersion') == version
		}

		this.ignoreCurrentRelease = function() { localStorage.setItem('updater.ignoredVersion', self.releaseVersion) }

		/** checks `release`, firing off the appropriate events as necessary */
		function checkRelease(release) {
			getVersion(function (ourVersion) { 
				var releaseVersion = release.tag_name.replace(/^v/, '')
				self.releaseVersion = releaseVersion

				if (compareVersions(ourVersion, releaseVersion) && !isUpdateIgnored(releaseVersion))
					releaseBus.push({
						version: releaseVersion,
						url: release.html_url
					})
			})
		}

		function runUpdateCheck() {
			console.log('Fetching latest Code Pulse release on GitHub...')
			getLatestRelease(function(latestRelease) {
				checkRelease(latestRelease)

				localStorage.setItem('updater.latestReleaseCache', JSON.stringify(latestRelease))
				localStorage.setItem('updater.lastCheck', Date.now())
				setTimeout(runUpdateCheck, CheckInterval)
			})
		}

		/** schedule an update check (or run one immediately), based on the last check time */
		function scheduleUpdateCheck() {
			var lastCheck = parseInt(localStorage.getItem('updater.lastCheck'))
			var curTime = Date.now()

			if (!lastCheck || curTime > (lastCheck + CheckInterval))
				runUpdateCheck()
			else {
				// run full update check later
				setTimeout(runUpdateCheck, lastCheck + CheckInterval - curTime)

				// for now, check what's cached, if we can
				var cachedRelease = JSON.parse(localStorage.getItem('updater.latestReleaseCache'))
				if (cachedRelease)
					checkRelease(cachedRelease)
			}
		}

		scheduleUpdateCheck()
	}

	exports.updateController = new UpdateController

})(this)