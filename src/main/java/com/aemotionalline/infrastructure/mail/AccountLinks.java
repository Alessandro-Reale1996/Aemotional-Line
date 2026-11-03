package com.aemotionalline.infrastructure.mail;

/**
 * The frontend's pages that use a one-time link (activation, new password), in one place: the emails and the
 * first-administrator command must point to the same pages. The base URL may end with a slash or not.
 */
public final class AccountLinks
{
	private AccountLinks()
	{
	}

	public static String activation(String baseUrl, String code)
	{
		return withoutTrailingSlash(baseUrl) + "/attiva?codice=" + code;
	}

	public static String passwordReset(String baseUrl, String code)
	{
		return withoutTrailingSlash(baseUrl) + "/reimposta-password?codice=" + code;
	}

	public static String home(String baseUrl)
	{
		return withoutTrailingSlash(baseUrl);
	}

	private static String withoutTrailingSlash(String baseUrl)
	{
		return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
	}
}
