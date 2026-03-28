package org.transport.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.AllArgsConstructor;
import org.transport.Application;
import org.transport.entity.Display;
import org.transport.entity.Index;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

@AllArgsConstructor
public final class Aggregator {

	private final String namespace;
	private final Path outputDirectory;

	private final Int2ObjectOpenHashMap<Int2ObjectOpenHashMap<ObjectArrayList<Display>>> displaysByDimensions = new Int2ObjectOpenHashMap<>();

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	public void add(Display display) {
		displaysByDimensions.computeIfAbsent(display.width(), key -> new Int2ObjectOpenHashMap<>()).computeIfAbsent(display.height(), key -> new ObjectArrayList<>()).add(display);
	}

	public void aggregate() {
		displaysByDimensions.forEach((width, byteListForHeight) -> byteListForHeight.forEach((height, displays) -> {
			final int displayCount = displays.size();
			final ObjectArrayList<Index> indexList = new ObjectArrayList<>();

			for (int i = 0; i < displayCount; i++) {
				final Display display = displays.get(i);
				indexList.add(new Index(display.groups(), display.fileName(), i));
			}

			final Path newOutputDirectory = Application.createDirectory(outputDirectory, width, height, null);

			try {
				OBJECT_MAPPER.writeValue(newOutputDirectory.resolve("index.json").toFile(), indexList);
			} catch (IOException e) {
				System.err.println(e.getMessage());
			}

			final StringBuilder stringBuilder = new StringBuilder("#pragma once\n#include <Arduino.h>\n\n");
			stringBuilder.append("namespace ").append(namespace).append("_").append(width).append("_").append(height).append(" {\n");
			stringBuilder.append("\tconstexpr auto WIDTH = ").append(width).append(";\n");
			stringBuilder.append("\tconstexpr auto HEIGHT = ").append(height).append(";\n\n");
			stringBuilder.append("\tconst uint8_t PROGMEM DISPLAYS[").append(displayCount).append("][").append(width * height / 8).append("] = {\n");

			displays.forEach(display -> {
				final ObjectArrayList<String> bytesString = new ObjectArrayList<>();
				for (final byte imageByte : display.imageBytes()) {
					bytesString.add(String.format("0x%02x", imageByte));
				}
				stringBuilder.append("\t\t{ ").append(String.join(", ", bytesString)).append(" },\n");
			});

			stringBuilder.append("\t};\n");
			stringBuilder.append("};\n");

			try {
				Files.writeString(newOutputDirectory.resolve("displays.h"), stringBuilder, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
			} catch (IOException e) {
				System.err.println(e.getMessage());
			}
		}));
	}
}
