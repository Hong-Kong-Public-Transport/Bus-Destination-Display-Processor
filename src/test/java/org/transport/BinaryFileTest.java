package org.transport;

import nu.pattern.OpenCV;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
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
		OpenCV.loadLocally();
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
		final int bytesPerImage = bitsPerImage / Application.BITS_PER_BYTE;

		// Get offsets
		final int[] offsets = new int[expectedImageCount];
		for (int i = 0; i < expectedImageCount; i++) {
			offsets[i] = byteBuffer.getInt();
		}

		// Decode PackBits
		final int compressedOffset = offsets[new Random().nextInt(expectedImageCount)];
		final byte[] decoded = new byte[bytesPerImage];
		int decodedIndex = 0;
		int fileBytesIndex = compressedOffset;

		while (decodedIndex < bytesPerImage) {
			final int header = fileBytes[fileBytesIndex++];
			if (header >= 0) {
				// Literal run
				for (int i = 0; i < header + 1; i++) {
					decoded[decodedIndex++] = fileBytes[fileBytesIndex++];
				}
			} else {
				// Repeated bytes
				final byte value = fileBytes[fileBytesIndex++];
				for (int i = 0; i < 1 - header; i++) {
					decoded[decodedIndex++] = value;
				}
			}
		}

		Assertions.assertEquals(bytesPerImage, decodedIndex);

		// Create image
		final Mat mat = new Mat(expectedHeight, expectedWidth, CvType.CV_8UC1);

		try {
			for (int i = 0; i < bitsPerImage; i += Application.BITS_PER_BYTE) {
				for (int j = 0; j < Application.BITS_PER_BYTE; j++) {
					mat.put((i + j) / expectedWidth, (i + j) % expectedWidth, (decoded[i / Application.BITS_PER_BYTE] & (1 << j)) > 0 ? 0xFF : 0);
				}
			}
			Imgcodecs.imwrite(directory.resolve("test.png").toString(), mat);
		} finally {
			mat.release();
		}
	}

	private static int getFileCount(Path directory) throws IOException {
		final int[] count = {0};
		try (final DirectoryStream<Path> directoryStream = Files.newDirectoryStream(directory)) {
			directoryStream.forEach(path -> count[0]++);
		}
		return count[0];
	}
}
