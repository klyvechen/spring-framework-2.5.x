package org.springframework.web.servlet.handler;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.core.Ordered;
import org.springframework.web.context.support.WebApplicationObjectSupport;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Abstract base class for HandlerMapping implementations.
 * Supports ordering, a default handler, and handler interceptors.
 * @author Juergen Hoeller
 * @since 07.04.2003
 * @see #getHandlerInternal
 * @see org.springframework.web.servlet.HandlerInterceptor
 */
public abstract class AbstractHandlerMapping extends WebApplicationObjectSupport
    implements HandlerMapping, Ordered {

	protected final Log logger = LogFactory.getLog(getClass());

	private int order = Integer.MAX_VALUE;  // default: same as non-Ordered

	private Object defaultHandler = null;

	private HandlerInterceptor[] interceptors;


	public final void setOrder(int order) {
	  this.order = order;
	}

	public final int getOrder() {
	  return order;
	}

	/**
	 * Set the default handler for this handler mapping.
	 * @param defaultHandler default handler instance, or null if none
	 */
	public final void setDefaultHandler(Object defaultHandler) {
		this.defaultHandler = defaultHandler;
		logger.info("Default mapping to handler [" + this.defaultHandler + "]");
	}

	/**
	 * Return the default handler for this handler mapping.
	 * @return the default handler instance, or null if none
	 */
	protected final Object getDefaultHandler() {
		return defaultHandler;
	}

	/**
	 * Set the handler interceptors to apply for all handlers mapped by
	 * this handler mapping.
	 * @param interceptors array of handler interceptors, or null if none
	 */
	public final void setInterceptors(HandlerInterceptor[] interceptors) {
		this.interceptors = interceptors;
	}


	/**
	 * Look up a handler for the given request, falling back to the default
	 * handler if no specific one is found.
	 * @param request current HTTP request
	 * @return the looked up handler instance, or the default handler
	 * @see #getHandlerInternal
	 */
	public final HandlerExecutionChain getHandler(HttpServletRequest request) throws ServletException {
		Object handler = getHandlerInternal(request);
		if (handler == null) {
			handler = this.defaultHandler;
		}
		if (handler == null) {
			return null;
		}
		return new HandlerExecutionChain(handler, this.interceptors);
	}

	/**
	 * Lookup a handler for the given request, returning null if no specific
	 * one is found. This method is called by getHandler, a null return value
	 * will lead to the default handler, if one is set.
	 * @param request current HTTP request
	 * @return the looked up handler instance, or null
	 * @throws ServletException if there is an internal error
	 */
	protected abstract Object getHandlerInternal(HttpServletRequest request) throws ServletException;

}
