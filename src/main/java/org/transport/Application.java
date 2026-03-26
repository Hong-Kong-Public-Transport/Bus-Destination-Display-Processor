package org.transport;

import nu.pattern.OpenCV;
import org.apache.commons.io.FileUtils;
import org.transport.service.Parser;
import org.transport.service.Processor;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class Application {

	public static void main(String[] args) throws IOException {
		OpenCV.loadLocally();

		if (args.length < 2) {
			System.err.println("Invalid arguments!");
			return;
		}

		final URI baseUri = URI.create(args[0] + (args[0].endsWith("/") ? "" : "/"));
		final Path outputDirectory = Paths.get(args[1]);
		FileUtils.deleteDirectory(outputDirectory.toFile());
		Files.createDirectories(outputDirectory);

		System.out.println("Fetching displays");
		new Parser(baseUri).parse((groups, imageBytes) -> new Processor(groups, imageBytes, outputDirectory).process());
	}
}
