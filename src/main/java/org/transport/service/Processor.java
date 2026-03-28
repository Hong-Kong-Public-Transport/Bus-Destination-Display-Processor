package org.transport.service;

import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.AllArgsConstructor;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.transport.Application;
import org.transport.entity.Display;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.function.Consumer;

@AllArgsConstructor
public final class Processor {

	private final ObjectArrayList<String> groups;
	private final String source;
	private final Path outputDirectory;

	private static final int MAX_SMOOTH_AMOUNT = 5;
	private static final String FILE_FORMAT = ".png";
	private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(10)).build();

	/**
	 * Using the raw image bytes of an image of an LED dot matrix, convert it to a black and white byte array representing which LEDs are on.
	 */
	public void process(Consumer<Display> callback) {
		getGoogleDriveImage(bytes -> {
			try {
				final Mat image = getImage(bytes);

				try {
					callback.accept(getResult(image, estimatePitch(image, false), estimatePitch(image, true)));
				} finally {
					image.release();
				}
			} catch (Exception e) {
				System.err.printf("Failed to process image [%s]: %s%n", getGroupName(), e.getMessage());
			}
		});
	}

	private void getGoogleDriveImage(Consumer<byte[]> callback) {
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

	/**
	 * @param image  the input image
	 * @param pitchX the estimated distance in pixels between LEDs on the X-axis
	 * @param pitchY the estimated distance in pixels between LEDs on the Y-axis
	 * @return a {@link Display} object with width, height, and image byte data (1 bit per pixel)
	 */
	private Display getResult(Mat image, int pitchX, int pitchY) {
		final Mat binary = new Mat();
		final int rawWidth = image.width();
		final int rawHeight = image.height();
		final int newPitchX = Math.max(1, pitchX);
		final int newPitchY = Math.max(1, pitchY);
		final int width = Math.round((float) rawWidth / newPitchX);
		final int height = Math.round((float) rawHeight / newPitchY);
		final Mat output = new Mat(height, width, CvType.CV_8U);
		final MatOfByte matOfByte = new MatOfByte();

		try {
			Imgproc.threshold(image, binary, 0, 255, Imgproc.THRESH_BINARY | Imgproc.THRESH_OTSU);
			final int[] pixels = new int[width * height];
			final IntOpenHashSet values = new IntOpenHashSet();

			for (int x = 0; x < width; x++) {
				for (int y = 0; y < height; y++) {
					final int x1 = x * newPitchX;
					final int x2 = Math.min((x + 1) * newPitchX, rawWidth);
					final int y1 = y * newPitchY;
					final int y2 = Math.min((y + 1) * newPitchY, rawHeight);
					final int value = getWhitePixelCount(binary, x1, y1, x2, y2);
					values.add(value);
					pixels[x + y * width] = value;
				}
			}

			final int threshold = getMedian(new IntArrayList(values));
			final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

			for (int i = 0; i < pixels.length; i += 8) {
				int data = 0;
				for (int j = 0; j < 8; j++) {
					if (i + j < pixels.length) {
						data |= (pixels[i + j] >= threshold ? 0x80 : 0) >> j;
						output.put((i + j) / width, (i + j) % width, pixels[i + j] >= threshold ? 0xFF : 0);
					}
				}
				byteArrayOutputStream.write(data);
			}

			final Path newOutputDirectory = Application.createDirectory(outputDirectory, width, height, "image");
			final String fileName = cleanString(String.format("%s_%s", getGroupName(), source.toLowerCase().replace("_", ""))) + FILE_FORMAT;
			final Path outputPath = newOutputDirectory.resolve(fileName);

			Imgcodecs.imencode(FILE_FORMAT, output, matOfByte);
			final byte[] bytes = matOfByte.toArray();

			if (!Files.exists(outputPath) || !Arrays.equals(bytes, Files.readAllBytes(outputPath))) {
				Files.write(outputPath, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
			}

			return new Display(groups, width, height, fileName, byteArrayOutputStream.toByteArray());
		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			binary.release();
			output.release();
			matOfByte.release();
		}
	}

	private String getGroupName() {
		final ObjectArrayList<String> text = new ObjectArrayList<>();

		for (int i = 0; i < Math.min(groups.size(), 2); i++) {
			text.add(groups.get(i).toUpperCase());
		}

		return String.join("_", text);
	}

	/**
	 * @param imageBytes the raw image bytes
	 * @return a grayscale {@link Mat} image
	 */
	private static Mat getImage(byte[] imageBytes) {
		final MatOfByte matOfByte = new MatOfByte(imageBytes);

		try {
			Mat imageBGR = Imgcodecs.imdecode(matOfByte, Imgcodecs.IMREAD_COLOR);
			final Mat imageHSV = new Mat();
			final Mat grayscaleImage = new Mat();

			try {
				Imgproc.cvtColor(imageBGR, imageHSV, Imgproc.COLOR_BGR2HSV);

				Core.extractChannel(imageHSV, grayscaleImage, 2);
				return new Mat(grayscaleImage, getCropRange(grayscaleImage, false), getCropRange(grayscaleImage, true)).clone();
			} finally {
				imageBGR.release();
				imageHSV.release();
				grayscaleImage.release();
			}
		} finally {
			matOfByte.release();
		}
	}

	/**
	 * @param image the input image
	 * @param axis  {@code false} for X-axis, {@code true} for Y-axis
	 * @return a range of pixels with the black border removed
	 */
	private static Range getCropRange(Mat image, boolean axis) {
		final int width = image.width();
		final int height = image.height();
		final Mat croppedImage = new Mat(
				image,
				axis ? new Range((int) Math.floor(height * 0.4), (int) Math.ceil(height * 0.6) + 1) : Range.all(),
				axis ? Range.all() : new Range((int) Math.floor(width * 0.4), (int) Math.ceil(width * 0.6) + 1)
		);

		try {
			final double[] projection = getProjection(croppedImage, !axis);

			int start = -1;
			int end = -1;

			for (int i = 0; i < projection.length; i++) {
				if (start < 0 && projection[i] > 0) {
					start = i;
				}

				if (end < 0 && projection[projection.length - i - 1] > 0) {
					end = projection.length - i;
				}
			}

			return end > start ? new Range(start, end) : Range.all();
		} finally {
			croppedImage.release();
		}
	}

	/**
	 * @param input the input image
	 * @param axis  {@code false} for X-axis, {@code true} for Y-axis
	 * @return an array of summed brightness values across an axis of the image
	 */
	private static double[] getProjection(Mat input, boolean axis) {
		final Mat sum = new Mat();

		try {
			Core.reduce(input, sum, axis ? 1 : 0, Core.REDUCE_SUM, CvType.CV_64F);
			final double[] output = new double[sum.rows() * sum.cols()];
			sum.get(0, 0, output);
			return output;
		} finally {
			sum.release();
		}
	}

	/**
	 * @param image the input image
	 * @param axis  {@code false} for X-axis, {@code true} for Y-axis
	 * @return the estimated pixels between LEDs on the image
	 */
	private static int estimatePitch(Mat image, boolean axis) {
		final double[] projection = getProjection(image, axis);
		final IntArrayList pitches = new IntArrayList();

		for (int smoothAmount = 0; smoothAmount < MAX_SMOOTH_AMOUNT; smoothAmount++) {
			double previousValue = getSmoothedValue(projection, 0, smoothAmount);
			boolean previousIncreasing = false;
			boolean previousDecreasing = false;
			int previousMaxIndex = -1;
			int previousMinIndex = -1;

			for (int i = 1; i < projection.length; i++) {
				final double currentValue = getSmoothedValue(projection, i, smoothAmount);
				final boolean currentIncreasing = currentValue > previousValue;
				final boolean currentDecreasing = currentValue < previousValue;

				if (previousIncreasing && !currentIncreasing) {
					if (previousMaxIndex >= 0) {
						pitches.add(i - previousMaxIndex);
					}
					previousMaxIndex = i;
				}

				if (previousDecreasing && !currentDecreasing) {
					if (previousMinIndex >= 0) {
						pitches.add(i - previousMinIndex);
					}
					previousMinIndex = i;
				}

				previousValue = currentValue;
				previousIncreasing = currentIncreasing;
				previousDecreasing = currentDecreasing;
			}
		}

		return getMode(pitches);
	}

	/**
	 * @param values       an input array of values
	 * @param index        the index to retrieve
	 * @param smoothAmount how many adjacent values to smooth from
	 * @return a smoothed value created by the mean of adjacent values
	 */
	private static double getSmoothedValue(double[] values, int index, int smoothAmount) {
		double sum = 0;

		for (int i = -smoothAmount; i <= smoothAmount; i++) {
			sum += values[Math.clamp(index + i, 0, values.length - 1)];
		}

		return sum / (smoothAmount * 2 + 1);
	}

	private static int getWhitePixelCount(Mat image, int x1, int y1, int x2, int y2) {
		int count = 0;
		for (int x = x1; x < x2; x++) {
			for (int y = y1; y < y2; y++) {
				if (image.get(y, x)[0] > 0) {
					count++;
				}
			}
		}
		return count;
	}

	private static int getMedian(IntArrayList values) {
		Collections.sort(values);
		return values.isEmpty() ? 0 : values.getInt(values.size() / 2);
	}

	private static int getMode(IntArrayList values) {
		if (values.isEmpty()) {
			return 0;
		}

		final Int2LongOpenHashMap frequencyMap = new Int2LongOpenHashMap();
		for (int i = 0; i < values.size(); i++) {
			frequencyMap.addTo(values.getInt(i), 1);
		}

		final long maxFrequency = Collections.max(frequencyMap.values());
		return frequencyMap.int2LongEntrySet()
				.stream()
				.filter(entry -> entry.getLongValue() == maxFrequency)
				.mapToInt(Int2LongMap.Entry::getIntKey)
				.findFirst()
				.orElse(0);
	}

	private static String cleanString(String text) {
		return text.trim().replaceAll("\\W+", "_").replaceAll("_+", "_");
	}
}
