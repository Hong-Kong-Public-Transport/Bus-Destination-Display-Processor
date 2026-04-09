package org.transport;

import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacpp.Loader;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.transport.service.Aggregator;
import org.transport.service.Parser;
import org.transport.service.StandardFileProcessor;
import picocli.CommandLine;

import java.net.URI;
import java.nio.file.Path;

@CommandLine.Command(name = "Bus Destination Display Processor", mixinStandardHelpOptions = true)
public final class Application implements Runnable {

	@CommandLine.Parameters(description = "URL address(es) to parse")
	String[] rawUrls;
	@CommandLine.Option(names = {"-o", "--output"}, required = true, description = "Output directory")
	Path outputDirectory;
	@CommandLine.Option(names = {"-c", "--clean"}, defaultValue = "true", description = "Clean output by deleting extra files")
	boolean clean;

	public static final String FILE_FORMAT = ".png";
	public static final int BYTES_PER_INT = 4;
	public static final int BITS_PER_BYTE = 8;

	@Override
	public void run() {
		Loader.load(opencv_core.class);
		Loader.load(opencv_imgproc.class);
		Loader.load(opencv_imgcodecs.class);
		avutil.av_log_set_level(avutil.AV_LOG_WARNING);
		final Aggregator aggregator = new Aggregator(outputDirectory);

		for (final String rawUrl : rawUrls) {
			final URI baseUri = URI.create(rawUrl + (rawUrl.endsWith("/") ? "" : "/"));
			new Parser(baseUri).parse((groups, fileName, rawImageBytes) -> new StandardFileProcessor(groups, fileName, rawImageBytes).process(aggregator::add));
		}

		aggregator.aggregate(clean);
	}

	public static void main(String[] args) {
		System.exit(new CommandLine(new Application()).execute(args));
	}
}
