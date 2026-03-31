package org.transport.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FileUtils;
import org.jspecify.annotations.Nullable;
import org.transport.entity.Display;
import org.transport.entity.Index;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Base64;
import java.util.function.Predicate;

@RequiredArgsConstructor
public final class Aggregator {

	private int mergedDisplays;

	private final String namespace;
	private final Path outputDirectory;

	private final Int2ObjectOpenHashMap<Int2ObjectOpenHashMap<DimensionsCache>> displaysByDimensions = new Int2ObjectOpenHashMap<>();
	private final ObjectOpenHashSet<String> outputDirectories = new ObjectOpenHashSet<>();

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
	private static final int BYTES_PER_INT = 4;
	private static final int BITS_PER_BYTE = 8;

	public void add(Display display) {
		final DimensionsCache dimensionsCache = displaysByDimensions.computeIfAbsent(display.width(), key -> new Int2ObjectOpenHashMap<>()).computeIfAbsent(display.height(), key -> new DimensionsCache(new Object2ObjectLinkedOpenHashMap<>(), new ObjectOpenHashSet<>()));
		final String base64 = Base64.getEncoder().encodeToString(display.pixelBytes());
		final Display existingDisplay = dimensionsCache.existingDisplaysByBase64.get(base64);

		if (existingDisplay == null) {
			// Save to cache
			dimensionsCache.existingDisplaysByBase64.put(base64, display);

			// Write image file
			final Path imagePath = createImageDirectory(display.width(), display.height()).resolve(display.fileName());
			dimensionsCache.displayFileNames.add(display.fileName());

			try {
				if (!Files.exists(imagePath) || !Arrays.equals(display.fileBytes(), Files.readAllBytes(imagePath))) {
					Files.write(imagePath, display.fileBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
				}
			} catch (IOException e) {
				System.err.println(e.getMessage());
			}
		} else {
			mergedDisplays++;
			existingDisplay.groups().addAll(display.groups());
		}
	}

	public void aggregate() {
		System.out.printf("Merged %s identical display(s)%n", mergedDisplays);

		// Clean other directories
		iterateDirectoryAndDelete(outputDirectory, path -> !outputDirectories.contains(path.getFileName().toString()));

		displaysByDimensions.forEach((width, byteListForHeight) -> byteListForHeight.forEach((height, dimensionsCache) -> {
			final Path imageDirectory = createImageDirectory(width, height);
			final Path newOutputDirectory = createDirectory(width, height, null);
			final Path indexFile = newOutputDirectory.resolve("index.json");
			final Path binaryFile = newOutputDirectory.resolve("displays.dat");

			// Clean main directory
			iterateDirectoryAndDelete(newOutputDirectory, path -> !Files.isDirectory(path) && !path.equals(imageDirectory) && !path.equals(indexFile) && !path.equals(binaryFile));

			// Create index list
			final ObjectArrayList<Index> indexList = new ObjectArrayList<>();

			// Create binary file
			final ByteArrayOutputStream offsetsStream = new ByteArrayOutputStream();
			final ByteArrayOutputStream imageBytesStream = new ByteArrayOutputStream();
			final int imageCount = dimensionsCache.existingDisplaysByBase64.size();
			int binaryOffset = (imageCount + 3) * BYTES_PER_INT; // include width, height, and image count

			for (final Display display : dimensionsCache.existingDisplaysByBase64.values()) {
				// Append index
				indexList.add(new Index(new ObjectArraySet<>(display.groups()), display.fileName()));

				// Append binary file
				write32(offsetsStream, binaryOffset);
				imageBytesStream.writeBytes(display.pixelBytes());
				binaryOffset += display.pixelBytes().length;
			}

			// Write index file
			try {
				OBJECT_MAPPER.writeValue(indexFile.toFile(), indexList);
			} catch (IOException e) {
				System.err.println(e.getMessage());
			}

			// Write binary file
			final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
			write32(outputStream, width);
			write32(outputStream, height);
			write32(outputStream, imageCount);

			try {
				offsetsStream.writeTo(outputStream);
				imageBytesStream.writeTo(outputStream);
				Files.write(binaryFile, outputStream.toByteArray(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
			} catch (IOException e) {
				System.err.println(e.getMessage());
			}

			// Delete extra files in image directory
			iterateDirectoryAndDelete(imageDirectory, path -> !dimensionsCache.displayFileNames.contains(path.getFileName().toString()));
		}));
	}

	private Path createImageDirectory(int width, int height) {
		return createDirectory(width, height, "image");
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

	private static void iterateDirectoryAndDelete(Path directory, Predicate<Path> deleteCondition) {
		try (final DirectoryStream<Path> directoryStream = Files.newDirectoryStream(directory)) {
			directoryStream.forEach(path -> {
				if (deleteCondition.test(path)) {
					System.out.printf("Deleting %s [%s]%n", Files.isDirectory(path) ? "directory" : "file", path.toAbsolutePath());
					try {
						FileUtils.forceDelete(path.toFile());
					} catch (IOException e) {
						System.err.println(e.getMessage());
					}
				}
			});
		} catch (IOException e) {
			System.err.println(e.getMessage());
		}
	}

	private static void write32(ByteArrayOutputStream byteArrayOutputStream, int data) {
		for (int i = 0; i < BYTES_PER_INT; i++) {
			byteArrayOutputStream.write((byte) ((data >> i * BITS_PER_BYTE) & 0xFF));
		}
	}

	private record DimensionsCache(Object2ObjectLinkedOpenHashMap<String, Display> existingDisplaysByBase64, ObjectOpenHashSet<String> displayFileNames) {
	}
}
