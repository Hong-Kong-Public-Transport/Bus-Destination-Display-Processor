package org.transport;

import nu.pattern.OpenCV;
import org.transport.service.Aggregator;
import org.transport.service.Parser;
import org.transport.service.Processor;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class Application {

	public static final int BYTES_PER_INT = 4;
	public static final int BITS_PER_BYTE = 8;

	public static void main(String[] args) {
		OpenCV.loadLocally();

		if (args.length < 2) {
			System.err.println("Invalid arguments!");
			return;
		}

		final URI baseUri = URI.create(args[0] + (args[0].endsWith("/") ? "" : "/"));
		final Path outputDirectory = Paths.get(args[1]);

		final Aggregator aggregator = new Aggregator(outputDirectory);
		new Parser(baseUri).parse((groups, source) -> new Processor(groups, source).process(aggregator::add));
		aggregator.aggregate();
	}
}
