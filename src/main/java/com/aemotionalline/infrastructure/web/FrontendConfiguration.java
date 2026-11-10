package com.aemotionalline.infrastructure.web;

import java.io.IOException;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serves the React app from the jar (CLAUDE.md, frontend point 1): the files of classpath:/static/ as they are, and the
 * app's index.html for every other address outside /api, so that its pages (/attiva, /conversazioni/...) open from an
 * email's link or on a reload, and the app itself shows its "not found" page. A missing file (an address whose last part
 * has an extension) is a 404, and so is an unknown address under /api, a Problem like every API error. Only GET requests
 * get here (SecurityConfiguration).
 */
@Configuration(proxyBeanMethods = false)
public class FrontendConfiguration implements WebMvcConfigurer
{
	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry)
	{
		// No caching resolver in the chain: it would remember every address ever asked for.
		registry.addResourceHandler("/**").addResourceLocations("classpath:/static/").resourceChain(false).addResolver(new AppPages());
	}

	/** A file when it exists, otherwise the app's index.html for an address that names a page rather than a file. */
	static final class AppPages extends PathResourceResolver
	{
		@Override
		protected @Nullable Resource getResource(String path, Resource location) throws IOException
		{
			Resource file = super.getResource(path, location);

			if (file != null || !isPage(path))
			{
				return file;
			}

			return super.getResource("index.html", location);
		}

		/** The address of a page of the app: outside /api, and its last part has no extension. */
		static boolean isPage(String path)
		{
			String last = path.substring(path.lastIndexOf('/') + 1);

			return !path.equals("api") && !path.startsWith("api/") && !last.contains(".");
		}
	}
}
