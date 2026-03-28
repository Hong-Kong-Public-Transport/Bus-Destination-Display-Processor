package org.transport;

import nu.pattern.OpenCV;
import org.jspecify.annotations.Nullable;
import org.transport.service.Aggregator;
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

		if (args.length < 3) {
			System.err.println("Invalid arguments!");
			return;
		}

		final URI baseUri = URI.create(args[0] + (args[0].endsWith("/") ? "" : "/"));
		final String namespace = args[1];
		final Path outputDirectory = Paths.get(args[2]);

		final Aggregator aggregator = new Aggregator(namespace, outputDirectory);
		new Parser(baseUri).parse((groups, source) -> new Processor(groups, source, outputDirectory).process(aggregator::add));
		aggregator.aggregate();
	}

	public static Path createDirectory(Path outputDirectory, int width, int height, @Nullable String name) {
		Path newOutputDirectory = outputDirectory.resolve(String.format("%s_%s", width, height));

		if (name != null) {
			newOutputDirectory = newOutputDirectory.resolve(name);
		}

		try {
			Files.createDirectories(newOutputDirectory);
		} catch (IOException e) {
			System.err.println(e.getMessage());
		}

		return newOutputDirectory;
	}
}
