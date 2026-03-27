package org.transport;

import nu.pattern.OpenCV;
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

		if (args.length < 5) {
			System.err.println("Invalid arguments!");
			return;
		}

		final URI baseUri = URI.create(args[0] + (args[0].endsWith("/") ? "" : "/"));
		final String namespace = args[1];
		final Path displayOutputDirectory = Paths.get(args[2]);
		final Path indexOutputDirectory = Paths.get(args[3]);
		final Path cppOutputDirectory = Paths.get(args[4]);

		Files.createDirectories(displayOutputDirectory);
		Files.createDirectories(indexOutputDirectory);
		Files.createDirectories(cppOutputDirectory);

		final Aggregator aggregator = new Aggregator(namespace, indexOutputDirectory, cppOutputDirectory);
		new Parser(baseUri).parse((groups, source) -> new Processor(groups, source, displayOutputDirectory).process(aggregator::add));
		aggregator.aggregate();
	}
}
