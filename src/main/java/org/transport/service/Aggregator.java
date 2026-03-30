package org.transport.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import lombok.AllArgsConstructor;
import org.apache.commons.io.FileUtils;
import org.jspecify.annotations.Nullable;
import org.transport.entity.Display;
import org.transport.entity.Index;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

@AllArgsConstructor
public final class Aggregator {

	private final String namespace;
	private final Path outputDirectory;

	private final Int2ObjectOpenHashMap<Int2ObjectOpenHashMap<DimensionsCache>> displaysByDimensions = new Int2ObjectOpenHashMap<>();

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	public void add(Display display) {
		final DimensionsCache dimensionsCache = displaysByDimensions.computeIfAbsent(display.width(), key -> new Int2ObjectOpenHashMap<>()).computeIfAbsent(display.height(), key -> new DimensionsCache(new Object2ObjectOpenHashMap<>(), new ObjectOpenHashSet<>()));
		final Display existingDisplay = dimensionsCache.existingDisplaysByBase64.get(display.base64());

		if (existingDisplay == null) {
			// Save to cache
			dimensionsCache.existingDisplaysByBase64.put(display.base64(), display);

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
			System.out.printf("Merging identical display [%s]%n", display.fileName());
			existingDisplay.groups().addAll(display.groups());
		}
	}

	public void aggregate() {
		displaysByDimensions.forEach((width, byteListForHeight) -> byteListForHeight.forEach((height, dimensionsCache) -> {
			final Path newOutputDirectory = createDirectory(width, height, null);

			// Create index list
			final ObjectArrayList<Index> indexList = new ObjectArrayList<>();

			// Create C++ file
			final StringBuilder stringBuilder = new StringBuilder("#pragma once\n#include <Arduino.h>\n\n");
			stringBuilder.append("namespace ").append(namespace).append("_").append(width).append("_").append(height).append(" {\n");
			stringBuilder.append("\tconstexpr auto WIDTH = ").append(width).append(";\n");
			stringBuilder.append("\tconstexpr auto HEIGHT = ").append(height).append(";\n");
			final int displayCount = dimensionsCache.existingDisplaysByBase64.size();
			stringBuilder.append("\tconstexpr auto COUNT = ").append(displayCount).append(";\n");
			stringBuilder.append("\tconst uint8_t PROGMEM DISPLAYS[").append(displayCount).append("][").append(width * height / 8).append("] = {\n");

			dimensionsCache.existingDisplaysByBase64.values().forEach(display -> {
				// Append index
				indexList.add(new Index(new ObjectArraySet<>(display.groups()), display.fileName(), display.base64()));

				// Append C++ file
				final ObjectArrayList<String> bytesString = new ObjectArrayList<>();
				for (final byte pixelByte : display.pixelBytes()) {
					bytesString.add(String.format("0x%02x", pixelByte));
				}
				stringBuilder.append("\t\t{ ").append(String.join(", ", bytesString)).append(" },\n");
			});

			stringBuilder.append("\t};\n");
			stringBuilder.append("};\n");

			// Write index file
			try {
				OBJECT_MAPPER.writeValue(newOutputDirectory.resolve("index.json").toFile(), indexList);
			} catch (IOException e) {
				System.err.println(e.getMessage());
			}

			// Write C++ file
			try {
				Files.writeString(newOutputDirectory.resolve("displays.h"), stringBuilder, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
			} catch (IOException e) {
				System.err.println(e.getMessage());
			}

			// Delete extra files in image directory
			try (final DirectoryStream<Path> directoryStream = Files.newDirectoryStream(createImageDirectory(width, height))) {
				directoryStream.forEach(path -> {
					if (!dimensionsCache.displayFileNames.contains(path.getFileName().toString())) {
						System.out.printf("Deleting unknown file [%s]%n", path.getFileName());
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
		}));
	}

	private Path createImageDirectory(int width, int height) {
		return createDirectory(width, height, "image");
	}

	private Path createDirectory(int width, int height, @Nullable String name) {
		Path newOutputDirectory = outputDirectory.resolve(String.format("%s_%s", width, height));

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

	private record DimensionsCache(Object2ObjectOpenHashMap<String, Display> existingDisplaysByBase64, ObjectOpenHashSet<String> displayFileNames) {
	}
}
