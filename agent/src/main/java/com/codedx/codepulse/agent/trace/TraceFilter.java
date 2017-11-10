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