/*
 * bytefrog: a tracing framework for the JVM. For more information
 * see http://code-pulse.com/bytefrog
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

package com.codedx.codepulse.hq.monitor

/** Describes the health state of a TraceComponent. Can be one of
  * `Healthy`, `Concerned`, `Unhealthy`, or `Unknown`, and will be
  * used as part of a [[TraceComponentHealth]] by the HealthMonitors.
  */
sealed trait TraceComponentStatus

object TraceComponentStatus {
	case object Healthy extends TraceComponentStatus
	case object Concerned extends TraceComponentStatus
	case object Unhealthy extends TraceComponentStatus
	case object Unknown extends TraceComponentStatus
}