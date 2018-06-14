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

package com.secdec.codepulse.data.model

case class Encounter(recordingId: Option[Int], nodeId: Int, sourceLocationId: Option[Int])

/** Access trait for nodes encountered.
  *
  * @author robertf
  */
trait TraceEncounterDataAccess {
	def record(recordings: List[Int], encounteredNodes: List[(Int, Option[Int])]): Unit
	def getAllEncounters(): List[(Int, Option[Int])]
	def getAllNodeEncountersSet(): Set[Int]
	def getRecordingEncounters(recordingId: Int): List[(Int, Option[Int])]
	def getRecordingNodeEncountersSet(recordingId: Int): Set[Int]
	def getTracedSourceLocations(nodeId: Int): List[SourceLocation]
}