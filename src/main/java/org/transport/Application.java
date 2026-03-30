package org.transport;

import nu.pattern.OpenCV;
import org.transport.service.Aggregator;
import org.transport.service.Parser;
import org.transport.service.Processor;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class Application {

	public static void main(String[] args) {
		OpenCV.loadLocally();

		if (args.length < 3) {
			System.err.println("Invalid arguments!");
			return;
		}

		final URI baseUri = URI.create(args[0] + (args[0].endsWith("/") ? "" : "/"));
		final String namespace = args[1];
		final Path outputDirectory = Paths.get(args[2]);

		final Aggregator aggregator = new Aggregator(namespace, outputDirectory);
		new Parser(baseUri).parse((groups, source) -> new Processor(groups, source).process(aggregator::add));
		aggregator.aggregate();
	}
}
