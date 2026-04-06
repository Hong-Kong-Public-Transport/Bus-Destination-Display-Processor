package org.transport;

import org.bytedeco.javacpp.Loader;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.transport.service.Aggregator;
import org.transport.service.Parser;
import org.transport.service.StandardFileProcessor;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class Application {

	public static final String FILE_FORMAT = ".png";
	public static final int BYTES_PER_INT = 4;
	public static final int BITS_PER_BYTE = 8;

	public static void main(String[] args) {
		Loader.load(opencv_core.class);
		Loader.load(opencv_imgproc.class);
		Loader.load(opencv_imgcodecs.class);

		if (args.length < 2) {
			System.err.println("Invalid arguments!");
			return;
		}

		final URI baseUri = URI.create(args[0] + (args[0].endsWith("/") ? "" : "/"));
		final Path outputDirectory = Paths.get(args[1]);

		final Aggregator aggregator = new Aggregator(outputDirectory);
		new Parser(baseUri).parse((groups, fileName, rawImageBytes) -> new StandardFileProcessor(groups, fileName, rawImageBytes).process(aggregator::add));
		aggregator.aggregate();
	}
}
