package org.transport.service;

import de.siegmar.fastcsv.reader.CsvReader;
import de.siegmar.fastcsv.reader.NamedCsvRecord;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.AllArgsConstructor;
import org.transport.Application;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.Consumer;

/**
 * Parses a website and extracts bus destination display images as raw file bytes.
 */
@AllArgsConstructor
public final class Parser {

	private final URI csvUri;

	private static final String ROUTE_HEADER = "路線";
	private static final String DESTINATION_HEADER = "目的地";
	private static final String OLD_HEADER = "舊版";
	private static final String FILE_ID_HEADER = "圖片ID";
	private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(10)).build();

	public void parse(RawImageCallback callback) {
		try (final InputStream inputStream = HTTP_CLIENT.send(
			HttpRequest.newBuilder().uri(csvUri).GET().build(),
			HttpResponse.BodyHandlers.ofInputStream()
		).body(); final CsvReader<NamedCsvRecord> csvReader = CsvReader.builder().ofNamedCsvRecord(inputStream)) {
			csvReader.forEach(namedCsvRecord -> {
				final String route = namedCsvRecord.getField(ROUTE_HEADER);
				final String destination = namedCsvRecord.getField(DESTINATION_HEADER).replace("<br>", " ");
				final boolean isCurrent = namedCsvRecord.getField(OLD_HEADER).equals("0");
				final String fileId = namedCsvRecord.getField(FILE_ID_HEADER);
				final String fileName = cleanString(String.format("%s_%s", route, fileId.toLowerCase().replace("_", ""))) + Application.FILE_FORMAT;
				getGoogleDriveImage(fileId, rawImageBytes -> callback.accept(ObjectArrayList.of(route, destination), isCurrent, fileName, rawImageBytes));
			});
		} catch (IOException | InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	private static String cleanString(String text) {
		return text.trim().replaceAll("\\W+", "_").replaceAll("_+", "_");
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
		void accept(ObjectArrayList<String> groups, boolean isCurrent, String fileName, byte[] rawImageBytes);
	}
}
