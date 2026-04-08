package org.transport;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.apache.commons.io.FileUtils;
import org.bytedeco.javacpp.IntPointer;
import org.bytedeco.javacpp.Loader;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.MatVector;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.junit.jupiter.api.Assertions;
import org.transport.entity.DisplayType;
import org.transport.service.Aggregator;
import org.transport.service.FileProcessorBase;
import org.transport.tool.FileHelper;
import org.transport.tool.OpenCVHelper;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public abstract class BinaryFileTest {

	protected static final String DIRECTORY_PROPERTY = "directory";

	static {
		Loader.load(opencv_core.class);
		Loader.load(opencv_imgproc.class);
		Loader.load(opencv_imgcodecs.class);
	}

	protected abstract FileProcessorBase getFileProcessor(String fileName, byte[] rawImageBytes);

	protected final void setup() {
		final Path inputDirectory = Paths.get(System.getProperty(DIRECTORY_PROPERTY));
		final Path outputDirectory = inputDirectory.resolve("output");
		FileUtils.deleteQuietly(outputDirectory.toFile());
		final Aggregator aggregator = new Aggregator(outputDirectory);
		FileHelper.iterateDirectory(inputDirectory, path -> getFileProcessor(path.getFileName().toString(), Files.readAllBytes(path)).process(aggregator::add));
		aggregator.aggregate();

		FileHelper.iterateDirectory(outputDirectory, innerDirectory -> {
			final String[] directorySplit = innerDirectory.getFileName().toString().split("_");
			final int expectedWidth = Integer.parseInt(directorySplit[0]);
			final int expectedHeight = Integer.parseInt(directorySplit[1]);
			final int expectedImageCount = FileHelper.getDirectoryFileCount(innerDirectory.resolve(Aggregator.IMAGE_DIRECTORY));

			// Assert header
			final byte[] fileBytes = Files.readAllBytes(innerDirectory.resolve(Aggregator.BINARY_FILE));
			final ByteBuffer byteBuffer = ByteBuffer.wrap(fileBytes).order(ByteOrder.LITTLE_ENDIAN);
			Assertions.assertEquals(expectedWidth, byteBuffer.getInt());
			Assertions.assertEquals(expectedHeight, byteBuffer.getInt());
			Assertions.assertEquals(expectedImageCount, byteBuffer.getInt());

			// Get image offsets
			final int[] imageOffsets = new int[expectedImageCount];
			for (int i = 0; i < expectedImageCount; i++) {
				imageOffsets[i] = byteBuffer.getInt();
			}

			for (int i = 0; i < expectedImageCount; i++) {
				// Read frame from offset
				byteBuffer.position(imageOffsets[i]);
				final String outputFileName = outputDirectory.resolve(String.format("test_%s_%s", innerDirectory.getFileName().toString(), i)).toString();
				switch (DisplayType.values()[byteBuffer.get()]) {
					case IMAGE -> decodeGenericImageBinary(expectedWidth, expectedHeight, byteBuffer, outputFileName);
					case ANIMATED -> decodeGenericAnimatedBinary(expectedWidth, expectedHeight, byteBuffer, outputFileName);
					case STANDARD_SCROLL -> decodeStandardScrollBinary(expectedWidth, expectedHeight, byteBuffer, outputFileName);
				}
			}
		});
	}

	private static void decodeGenericImageBinary(int width, int height, ByteBuffer byteBuffer, String outputFileName) {
		final Mat output = decodePackBits(width, height, byteBuffer, width * height / Application.BITS_PER_BYTE);
		opencv_imgcodecs.imwrite(outputFileName + Application.FILE_FORMAT, output);
		output.release();
	}

	private static void decodeGenericAnimatedBinary(int width, int height, ByteBuffer byteBuffer, String outputFileName) {
		final int frameCount = byteBuffer.getInt();
		final ObjectArrayList<Mat> frames = new ObjectArrayList<>();
		final IntArrayList durations = new IntArrayList();

		// Get frame offsets
		final int[] frameOffsets = new int[frameCount];
		for (int i = 0; i < frameCount; i++) {
			frameOffsets[i] = byteBuffer.getInt();
		}

		// Rebuild frames
		for (final int frameOffset : frameOffsets) {
			byteBuffer.position(frameOffset);
			durations.add(byteBuffer.getInt() / 1000);
			frames.add(decodePackBits(width, height, byteBuffer, width * height / Application.BITS_PER_BYTE));
		}

		// Export result
		durations.removeInt(0);
		durations.add((int) durations.getLast());
		final opencv_imgcodecs.Animation animation = new opencv_imgcodecs.Animation();
		animation.frames(new MatVector(frames.toArray(Mat[]::new)));
		animation.durations(new IntPointer(durations.toIntArray()));
		opencv_imgcodecs.imwriteanimation(outputFileName + Application.FILE_FORMAT, animation);
		frames.forEach(Mat::release);
	}

	private static void decodeStandardScrollBinary(int width, int height, ByteBuffer byteBuffer, String outputFileName) {
		final int sameColumnCount = byteBuffer.getInt();
		final int animatedColumnCount = byteBuffer.getInt();
		final Mat combined = decodePackBits(sameColumnCount + animatedColumnCount, height, byteBuffer, (sameColumnCount + animatedColumnCount) * height / Application.BITS_PER_BYTE);
		opencv_imgcodecs.imwrite(outputFileName + "_combined" + Application.FILE_FORMAT, combined);
		final ObjectArrayList<Mat> frames = new ObjectArrayList<>();
		final IntArrayList durations = new IntArrayList();

		for (int i = 0; i < animatedColumnCount + width - sameColumnCount; i++) {
			final Mat frame = new Mat(height, width, opencv_core.CV_8UC1, Scalar.all(0));
			frames.add(frame);
			durations.add(33);

			for (int y = 0; y < height; y++) {
				for (int x = 0; x < sameColumnCount; x++) {
					OpenCVHelper.setPixel(frame, x, y, (byte) OpenCVHelper.getPixel(combined, x, y));
				}

				for (int x = sameColumnCount; x < width; x++) {
					final int newX = x - width + sameColumnCount + i;
					if (newX >= sameColumnCount && newX < sameColumnCount + animatedColumnCount) {
						OpenCVHelper.setPixel(frame, x, y, (byte) OpenCVHelper.getPixel(combined, newX, y));
					}
				}
			}
		}

		// Export result
		final opencv_imgcodecs.Animation animation = new opencv_imgcodecs.Animation();
		animation.frames(new MatVector(frames.toArray(Mat[]::new)));
		animation.durations(new IntPointer(durations.toIntArray()));
		opencv_imgcodecs.imwriteanimation(outputFileName + Application.FILE_FORMAT, animation);
		frames.forEach(Mat::release);
		combined.release();
	}

	private static Mat decodePackBits(int width, int height, ByteBuffer byteBuffer, int expectedCount) {
		final byte[] decoded = new byte[expectedCount];
		int decodedIndex = 0;

		while (decodedIndex < expectedCount) {
			final int header = byteBuffer.get();
			if (header >= 0) {
				// Literal run
				for (int i = 0; i < header + 1; i++) {
					decoded[decodedIndex++] = byteBuffer.get();
				}
			} else if (header > -128) {
				// Repeated bytes
				final byte value = byteBuffer.get();
				for (int i = 0; i < 1 - header; i++) {
					decoded[decodedIndex++] = value;
				}
			}
		}

		// Build image
		final Mat output = new Mat(height, width, opencv_core.CV_8UC1);

		for (int i = 0; i < width * height; i += Application.BITS_PER_BYTE) {
			for (int j = 0; j < Application.BITS_PER_BYTE; j++) {
				OpenCVHelper.setPixel(output, (i + j) % width, (i + j) / width, (decoded[i / Application.BITS_PER_BYTE] & (1 << j)) > 0 ? (byte) 0xFF : 0);
			}
		}

		return output;
	}
}
