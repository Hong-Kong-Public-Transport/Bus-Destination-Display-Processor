package org.transport.service;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import lombok.AllArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.jspecify.annotations.Nullable;
import org.transport.Application;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Parses a website and extracts bus destination display images as raw file bytes.
 */
@AllArgsConstructor
public final class Parser {

	private final URI baseUri;

	private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(10)).build();
	private static final String MATCHING_CLASS_NAME = "XqQF9c";
	private static final ObjectImmutableList<Pattern> GOOGLE_DRIVE_URL_PATTERNS = ObjectImmutableList.of(
			Pattern.compile("^https://drive\\.google\\.com/file/d/([a-zA-Z0-9_-]+)/view"),
			Pattern.compile("[?&]id=([a-zA-Z0-9_-]+)(&|$)")
	);

	public void parse(RawImageCallback callback) {
		parseWebsite(baseUri, document -> {
			// Find the div elements containing the table (inside data-code)
			final List<Element> elements = document.select("div[data-code*='table']");

			if (elements.isEmpty()) {
				System.err.printf("No table found for [%s]%n", document.title());
			} else {
				elements.forEach(divElement -> Jsoup.parse(divElement.attr("data-code")).select("tbody").forEach(tableElement -> {
					final Elements rowElements = tableElement.children();
					final String[] previousGroups = {"", ""};

					// Iterate each row
					rowElements.forEach(rowElement -> {
						final Elements columnElements = rowElement.children();
						final ObjectArrayList<String> groups = new ObjectArrayList<>();
						final ObjectArrayList<String> sources = new ObjectArrayList<>();

						// Iterate each column of each row
						for (int i = 0; i < columnElements.size(); i++) {
							final Element columnElement = columnElements.get(i);
							final Elements linkElements = columnElement.select("a[href]");

							if (linkElements.isEmpty()) {
								// If the column has no links, add to the groups
								final String group = columnElement.text();
								if (i < previousGroups.length) {
									if (!group.isEmpty()) {
										previousGroups[i] = group;
									}
									if (!previousGroups[i].isEmpty()) {
										groups.add(previousGroups[i]);
									}
								} else {
									if (!group.isEmpty()) {
										groups.add(group);
									}
								}
							} else {
								// If the column has links, add to the sources
								linkElements.forEach(linkElement -> {
									final String href = linkElement.attr("href");
									final String source = getGoogleDriveSource(href);
									if (source == null) {
										System.err.printf("Unknown image source [%s] for %s%n", href, groups);
									} else {
										sources.add(source);
									}
								});
							}
						}

						// Create display objects
						sources.forEach(source -> {
							final String fileName = cleanString(String.format("%s_%s", getGroupName(groups), source.toLowerCase().replace("_", ""))) + Application.FILE_FORMAT;
							getGoogleDriveImage(source, rawImageBytes -> callback.accept(groups, fileName, rawImageBytes));
						});
					});
				}));
			}
		});
	}

	private void parseWebsite(URI uri, Consumer<Document> consumer) {
		try {
			final Document document = Jsoup.parse(uri.toURL(), 20000);
			final ObjectArrayList<URI> links = parseLinks(document);

			if (links.isEmpty()) {
				System.out.printf("Parsing page [%s]%n", document.title());
				consumer.accept(document);
			} else {
				links.forEach(innerUri -> parseWebsite(innerUri, consumer));
			}
		} catch (IOException e) {
			System.err.println(e.getMessage());
		}
	}

	private ObjectArrayList<URI> parseLinks(Document document) {
		// Collect all elements with href attribute by class
		return document.select(String.format("a.%s[href]", MATCHING_CLASS_NAME)).stream().map(element -> {
			final String href = element.attr("href");
			return href.startsWith("/") ? baseUri.resolve(href) : URI.create(href);
		}).collect(Collectors.toCollection(ObjectArrayList::new));
	}

	private static String getGroupName(ObjectArrayList<String> groups) {
		final ObjectArrayList<String> text = new ObjectArrayList<>();

		for (int i = 0; i < Math.min(groups.size(), 2); i++) {
			text.add(groups.get(i).toUpperCase());
		}

		return String.join("_", text);
	}

	private static String cleanString(String text) {
		return text.trim().replaceAll("\\W+", "_").replaceAll("_+", "_");
	}

	/**
	 * Returns the Google Drive file ID from a URL.
	 *
	 * @param url the Google Drive URL
	 * @return the file ID
	 */
	@Nullable
	private static String getGoogleDriveSource(String url) {
		for (final Pattern pattern : GOOGLE_DRIVE_URL_PATTERNS) {
			final Matcher matcher = pattern.matcher(url);
			if (matcher.find()) {
				return matcher.group(1);
			}
		}

		return null;
	}

	private static void getGoogleDriveImage(String source, Consumer<byte[]> callback) {
		try {
			final HttpResponse<byte[]> httpResponse = HTTP_CLIENT.send(
					HttpRequest.newBuilder().uri(URI.create(String.format("https://lh3.googleusercontent.com/d/%s", source))).timeout(Duration.ofSeconds(20)).GET().build(),
					HttpResponse.BodyHandlers.ofByteArray()
			);

			if (httpResponse.statusCode() == 200) {
				callback.accept(httpResponse.body());
			} else {
				System.err.printf("HTTP %d for [%s]%n", httpResponse.statusCode(), source);
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			System.err.println(e.getMessage());
		} catch (IOException e) {
			System.err.println(e.getMessage());
		}
	}

	@FunctionalInterface
	public interface RawImageCallback {
		void accept(ObjectArrayList<String> groups, String fileName, byte[] rawImageBytes);
	}
}
