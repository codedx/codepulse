/* Code Pulse: a real-time code coverage tool, for more information, see <http://code-pulse.com/>
 *
 * Copyright (C) 2014-2017 Code Dx, Inc. <https://codedx.com/>
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

package com.codedx.codepulse.agent.trace;

import java.io.IOException;

import javax.servlet.*;
import javax.servlet.http.*;

import com.codedx.bytefrog.thirdparty.minlog.Log;

/** Servlet filter injected dynamically at runtime (if the container is supported).
  *
  * DO NOT INSTANTIATE OR REFERENCE DIRECTLY! the system classloader probably can't load it
  * because of the javax.servlet API references we're using (that are in the provided scope,
  * meaning we're counting on a different classloader being able to load them for us later,
  * to prevent clashing). `defineFilterClass(...)` takes care of injecting this where it needs
  * to be when the time is right.
  */
public class TraceFilter implements Filter {
	public void init(FilterConfig fc) {
		Log.debug("trace filter", "filter init");
	}

	public void destroy() {
		Log.debug("trace filter", "filter destroy");
	}

	public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain) throws IOException, ServletException {
		chain.doFilter(req, resp);
	}
}