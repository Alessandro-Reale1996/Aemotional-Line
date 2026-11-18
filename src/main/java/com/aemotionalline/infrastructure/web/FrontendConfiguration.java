package com.aemotionalline.infrastructure.web;

import java.io.IOException;
import java.time.Duration;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serves the React app from the jar (CLAUDE.md, frontend point 1): the files of classpath:/static/ as they are, and the
 * app's index.html for every other address outside /api, so that its pages (/attiva, /conversazioni/...) open from an
 * email's link or on a reload, and the app itself shows its "not found" page. A missing file (an address whose last part
 * has an extension) is a 404, and so is an unknown address under /api, a Problem like every API error. Only GET requests
 * get here (SecurityConfiguration).
 * <p>
 * Caching (CLAUDE.md, F11 decision 1): the files under /assets/ carry a hash of their content in their name (Vite's
 * build), so a browser may keep them for a year and never ask again; a new version has new names. Everything else, the
 * pages and every API answer included, keeps Spring Security's {@code no-store}: the pages are tiny and a new version
 * reaches the browser at once, and the API's answers hold therapy content that must not land in a shared device's disk
 * cache. A missing file under /assets/ is still a 404, with {@code no-store}.
 */
@Configuration(proxyBeanMethods = false)
public class FrontendConfiguration implements WebMvcConfigurer
{
	/** The files of the build whose names carry a hash of their content. */
	public static final String ASSETS = "/assets/**";

	/** A year, unchanged, for anyone: what a file named by its content can be kept for. */
	public static final CacheControl ASSETS_CACHE = CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable();

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry)
	{
		// No caching resolver in the chains: it would remember every address ever asked for.
		registry.addResourceHandler(ASSETS).addResourceLocations("classpath:/static/assets/").setCacheControl(ASSETS_CACHE).resourceChain(false);
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
