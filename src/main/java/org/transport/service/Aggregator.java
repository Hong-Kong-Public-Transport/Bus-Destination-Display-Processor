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
import org.transport.Application;
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
import java.util.stream.Collectors;

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
		final DimensionsCache dimensionsCache = displaysByDimensions.computeIfAbsent(display.width(), key -> new Int2ObjectOpenHashMap<>()).computeIfAbsent(display.height(), key -> new DimensionsCache(new Object2ObjectLinkedOpenHashMap<>(), new ObjectOpenHashSet<>()));
		final String key = display.frames().stream().map(frame -> String.format("%s_%s", frame.duration(), Base64.getEncoder().encodeToString(frame.pixelBytes()))).collect(Collectors.joining("_"));
		final Display existingDisplay = dimensionsCache.existingDisplaysByKey.get(key);

		if (existingDisplay == null) {
			// Save to cache
			dimensionsCache.existingDisplaysByKey.put(key, display);

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
		if (mergedDisplays > 0) {
			System.out.printf("Merged %s identical display(s)%n", mergedDisplays);
		}

		// Clean other directories
		iterateDirectoryAndDelete(outputDirectory, path -> !outputDirectories.contains(path.getFileName().toString()));

		displaysByDimensions.forEach((width, byteListForHeight) -> byteListForHeight.forEach((height, dimensionsCache) -> {
			final Path imageDirectory = createImageDirectory(width, height);
			final Path newOutputDirectory = createDirectory(width, height, null);
			final Path indexFile = newOutputDirectory.resolve(INDEX_FILE);
			final Path binaryFile = newOutputDirectory.resolve(BINARY_FILE);

			// Clean main directory
			iterateDirectoryAndDelete(newOutputDirectory, path -> !Files.isDirectory(path) && !path.equals(imageDirectory) && !path.equals(indexFile) && !path.equals(binaryFile));

			// Create index list
			final ObjectArrayList<Index> indexList = new ObjectArrayList<>();

			// Create binary file
			final int headerOffset = 2 * Application.BYTES_PER_INT;
			final ByteArrayWriter imageByteArrayWriter = new ByteArrayWriter(headerOffset); // include width and height

			for (final Display display : dimensionsCache.existingDisplaysByKey.values()) {
				// Append index
				indexList.add(new Index(new ObjectArraySet<>(display.groups()), display.fileName()));

				// Append binary file
				final ByteArrayWriter frameByteArrayWriter = new ByteArrayWriter(headerOffset + (dimensionsCache.existingDisplaysByKey.size() + 1) * Application.BYTES_PER_INT + imageByteArrayWriter.getRawOffset()); // include header, image count, and image offsets
				display.frames().forEach(frame -> frameByteArrayWriter.write(frame.duration(), frame.pixelBytes()));
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
			iterateDirectoryAndDelete(imageDirectory, path -> !dimensionsCache.displayFileNames.contains(path.getFileName().toString()));
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

	private record DimensionsCache(Object2ObjectLinkedOpenHashMap<String, Display> existingDisplaysByKey, ObjectOpenHashSet<String> displayFileNames) {
	}
}
