package org.transport.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import lombok.RequiredArgsConstructor;
import org.apache.commons.imaging.common.PackBits;
import org.jspecify.annotations.Nullable;
import org.transport.Application;
import org.transport.entity.Display;
import org.transport.entity.Index;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

@RequiredArgsConstructor
public final class Aggregator {

	private int mergedDisplays;

	private final Path outputDirectory;

	private final Int2ObjectOpenHashMap<Int2ObjectOpenHashMap<DimensionsCache>> displaysByDimensions = new Int2ObjectOpenHashMap<>();
	private final ObjectOpenHashSet<String> outputDirectories = new ObjectOpenHashSet<>();

	public static final String IMAGE_DIRECTORY = "image";
	public static final String INDEX_FILE = "index.json";
	public static final String BINARY_FILE = "displays.dat";
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	public void add(Display display) {
		final DimensionsCache dimensionsCache = displaysByDimensions.computeIfAbsent(display.width(), key -> new Int2ObjectOpenHashMap<>()).computeIfAbsent(display.height(), key -> new DimensionsCache(new ObjectLinkedOpenHashSet<>(), new FileWriter(createImageDirectory(display.width(), display.height()))));
		final Display existingDisplay = dimensionsCache.existingDisplays.get(display);

		if (existingDisplay == null) {
			// Save to cache
			dimensionsCache.existingDisplays.add(display);

			// Write image file
			dimensionsCache.fileWriter.writeFile(display);
		} else {
			mergedDisplays++;
			existingDisplay.groups().addAll(display.groups());
		}
	}

	public void aggregate() {
		if (mergedDisplays > 0) {
			System.out.printf("Merged %s identical display(s)%n", mergedDisplays);
		}

		// Clean other directories
		FileWriter.iterateDirectoryAndDelete(outputDirectory, path -> !outputDirectories.contains(path.getFileName().toString()));

		displaysByDimensions.forEach((width, byteListForHeight) -> byteListForHeight.forEach((height, dimensionsCache) -> {
			final Path imageDirectory = createImageDirectory(width, height);
			final Path newOutputDirectory = createDirectory(width, height, null);
			final Path indexFile = newOutputDirectory.resolve(INDEX_FILE);
			final Path binaryFile = newOutputDirectory.resolve(BINARY_FILE);

			// Clean main directory
			FileWriter.iterateDirectoryAndDelete(newOutputDirectory, path -> !Files.isDirectory(path) && !path.equals(imageDirectory) && !path.equals(indexFile) && !path.equals(binaryFile));

			// Create index list
			final ObjectArrayList<Index> indexList = new ObjectArrayList<>();

			// Create binary file
			final int headerOffset = 2 * Application.BYTES_PER_INT;
			final ByteArrayWriter imageByteArrayWriter = new ByteArrayWriter(headerOffset); // include width and height

			for (final Display display : dimensionsCache.existingDisplays) {
				// Append index
				indexList.add(new Index(new ObjectArraySet<>(display.groups()), display.fileName()));

				// Append binary file
				final ByteArrayWriter frameByteArrayWriter = new ByteArrayWriter(headerOffset + (dimensionsCache.existingDisplays.size() + 1) * Application.BYTES_PER_INT + imageByteArrayWriter.getRawOffset()); // include header, image count, and image offsets
				display.frames().forEach(frame -> frameByteArrayWriter.write(frame.delayMicros(), getImageBytes(frame.pixels())));
				imageByteArrayWriter.write(frameByteArrayWriter.getResult());
			}

			// Write index file
			try {
				OBJECT_MAPPER.writeValue(indexFile.toFile(), indexList);
			} catch (IOException e) {
				System.err.println(e.getMessage());
			}

			// Write binary file
			final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
			ByteArrayWriter.write32(outputStream, width);
			ByteArrayWriter.write32(outputStream, height);
			outputStream.writeBytes(imageByteArrayWriter.getResult());

			try {
				Files.write(binaryFile, outputStream.toByteArray(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
			} catch (IOException e) {
				System.err.println(e.getMessage());
			}

			// Delete extra files in image directory
			dimensionsCache.fileWriter.cleanDirectory();
		}));
	}

	private Path createImageDirectory(int width, int height) {
		return createDirectory(width, height, IMAGE_DIRECTORY);
	}

	private Path createDirectory(int width, int height, @Nullable String name) {
		Path newOutputDirectory = outputDirectory.resolve(String.format("%s_%s", width, height));
		outputDirectories.add(newOutputDirectory.getFileName().toString());

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

	private static byte[] getImageBytes(boolean[] pixels) {
		final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

		for (int i = 0; i < pixels.length; i += Application.BITS_PER_BYTE) {
			int data = 0;
			for (int j = 0; j < Application.BITS_PER_BYTE; j++) {
				if (i + j < pixels.length) {
					data |= (pixels[i + j] ? 1 : 0) << j;
				}
			}
			byteArrayOutputStream.write(data);
		}

		try {
			return PackBits.compress(byteArrayOutputStream.toByteArray());
		} catch (IOException e) {
			System.err.println(e.getMessage());
			return new byte[0];
		}
	}

	private record DimensionsCache(ObjectLinkedOpenHashSet<Display> existingDisplays, FileWriter fileWriter) {
	}
}
