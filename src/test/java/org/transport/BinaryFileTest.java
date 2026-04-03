package org.transport;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.bytedeco.javacpp.Loader;
import org.bytedeco.opencv.opencv_java;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfInt;
import org.opencv.imgcodecs.Animation;
import org.opencv.imgcodecs.Imgcodecs;
import org.transport.service.Aggregator;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;

public final class BinaryFileTest {

	private static final String DIRECTORY_PROPERTY = "directory";

	static {
		Loader.load(opencv_java.class);
	}

	@Test
	@EnabledIfSystemProperty(named = DIRECTORY_PROPERTY, matches = "(\\w+\\/)*\\d+_\\d+")
	public void validateSerialization() throws IOException {
		final Path directory = Paths.get(System.getProperty(DIRECTORY_PROPERTY));
		final String[] directorySplit = directory.getFileName().toString().split("_");
		final int expectedWidth = Integer.parseInt(directorySplit[0]);
		final int expectedHeight = Integer.parseInt(directorySplit[1]);
		final int expectedImageCount = getFileCount(directory.resolve(Aggregator.IMAGE_DIRECTORY));

		// Assert header
		final byte[] fileBytes = Files.readAllBytes(directory.resolve(Aggregator.BINARY_FILE));
		final ByteBuffer byteBuffer = ByteBuffer.wrap(fileBytes).order(ByteOrder.LITTLE_ENDIAN);
		Assertions.assertEquals(expectedWidth, byteBuffer.getInt());
		Assertions.assertEquals(expectedHeight, byteBuffer.getInt());
		Assertions.assertEquals(expectedImageCount, byteBuffer.getInt());
		final int bitsPerImage = expectedWidth * expectedHeight;
		Assertions.assertEquals(0, bitsPerImage % Application.BITS_PER_BYTE);

		// Get image offsets
		final int[] imageOffsets = new int[expectedImageCount];
		for (int i = 0; i < expectedImageCount; i++) {
			imageOffsets[i] = byteBuffer.getInt();
		}

		// Read frame from offset
		final int offset = imageOffsets[new Random().nextInt(expectedImageCount)];
		byteBuffer.position(offset);
		final int frameCount = byteBuffer.getInt();
		final Animation animation = new Animation();
		final ObjectArrayList<Mat> frames = new ObjectArrayList<>();
		final IntArrayList durations = new IntArrayList();

		// Get frame offsets
		final int[] frameOffsets = new int[frameCount];
		for (int i = 0; i < frameCount; i++) {
			frameOffsets[i] = byteBuffer.getInt();
		}

		// Rebuild frames
		for (int i = 0; i < frameCount; i++) {
			byteBuffer.position(frameOffsets[i]);
			durations.add(byteBuffer.getInt());
			final byte[] decoded = decodePackBits(fileBytes, byteBuffer.position(), bitsPerImage / Application.BITS_PER_BYTE);
			final Mat frame = new Mat(expectedHeight, expectedWidth, CvType.CV_8UC1);

			for (int j = 0; j < bitsPerImage; j += Application.BITS_PER_BYTE) {
				for (int k = 0; k < Application.BITS_PER_BYTE; k++) {
					frame.put((j + k) / expectedWidth, (j + k) % expectedWidth, (decoded[j / Application.BITS_PER_BYTE] & (1 << k)) > 0 ? 0xFF : 0);
				}
			}

			if (frames.isEmpty()) {
				frames.add(frame);
			} else {
				final Mat previousFrame = frames.getLast();
				final Mat newFrame = new Mat();
				Core.bitwise_xor(previousFrame, frame, newFrame);
				frames.add(newFrame);
				frame.release();
			}
		}

		// Export result
		animation.set_frames(frames);
		animation.set_durations(new MatOfInt(durations.toIntArray()));
		Imgcodecs.imwriteanimation(directory.resolve("test.png").toString(), animation);
		frames.forEach(Mat::release);
	}

	private static int getFileCount(Path directory) throws IOException {
		final int[] count = {0};
		try (final DirectoryStream<Path> directoryStream = Files.newDirectoryStream(directory)) {
			directoryStream.forEach(path -> count[0]++);
		}
		return count[0];
	}

	private static byte[] decodePackBits(byte[] encoded, int offset, int expectedCount) {
		final byte[] decoded = new byte[expectedCount];
		int decodedIndex = 0;
		int fileBytesIndex = offset;

		while (decodedIndex < expectedCount) {
			final int header = encoded[fileBytesIndex++];
			if (header >= 0) {
				// Literal run
				for (int i = 0; i < header + 1; i++) {
					decoded[decodedIndex++] = encoded[fileBytesIndex++];
				}
			} else if (header > -128) {
				// Repeated bytes
				final byte value = encoded[fileBytesIndex++];
				for (int i = 0; i < 1 - header; i++) {
					decoded[decodedIndex++] = value;
				}
			}
		}

		return decoded;
	}
}
