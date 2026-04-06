package org.transport.service;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.AllArgsConstructor;
import org.apache.commons.io.FileUtils;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.IntPointer;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.MatVector;
import org.transport.Application;
import org.transport.entity.Display;
import org.transport.tool.OpenCVHelper;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.function.Predicate;

@AllArgsConstructor
public final class FileWriter {

	private final Path outputDirectory;
	private final ObjectArrayList<String> fileNames = new ObjectArrayList<>();

	public void writeFile(Display display) {
		final ObjectArrayList<Mat> outputFrames = new ObjectArrayList<>();

		try (final BytePointer outputBytePointer = new BytePointer()) {
			final int frameCount = display.frames().size();

			if (frameCount == 1) {
				outputFrames.add(pixelsToMat(display.width(), display.height(), display.frames().getFirst().pixels()));
				opencv_imgcodecs.imencode(Application.FILE_FORMAT, outputFrames.getFirst(), outputBytePointer);
			} else {
				final int[] durations = new int[frameCount];

				for (int i = 0; i < frameCount; i++) {
					outputFrames.add(pixelsToMat(display.width(), display.height(), display.frames().get(i).pixels()));
					durations[i] = display.frames().get(i == frameCount - 1 ? i : i + 1).delayMicros() / 1000;
				}

				final opencv_imgcodecs.Animation animation = new opencv_imgcodecs.Animation();
				animation.frames(new MatVector(outputFrames.toArray(Mat[]::new)));
				animation.durations(new IntPointer(durations));
				opencv_imgcodecs.imencodeanimation(Application.FILE_FORMAT, animation, outputBytePointer);
			}

			final byte[] outputBytes = new byte[(int) outputBytePointer.limit()];
			outputBytePointer.get(outputBytes);
			final Path outputPath = outputDirectory.resolve(display.fileName());
			fileNames.add(display.fileName());

			if (!Files.exists(outputPath) || !Arrays.equals(outputBytes, Files.readAllBytes(outputPath))) {
				Files.write(outputPath, outputBytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
			}
		} catch (IOException e) {
			System.err.printf("Failed to write file [%s]: %s%n", display.fileName(), e.getMessage());
		} finally {
			outputFrames.forEach(Mat::release);
		}
	}

	public void cleanDirectory() {
		final int writeCount = fileNames.size();
		final int deleteCount = getDirectoryFileCount(outputDirectory) - writeCount;
		if (deleteCount < writeCount) {
			iterateDirectoryAndDelete(outputDirectory, path -> !fileNames.contains(path.getFileName().toString()));
		}
	}

	public static void iterateDirectoryAndDelete(Path directory, Predicate<Path> deleteCondition) {
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

	public static int getDirectoryFileCount(Path directory) {
		final int[] count = {0};
		try (final DirectoryStream<Path> directoryStream = Files.newDirectoryStream(directory)) {
			directoryStream.forEach(path -> count[0]++);
		} catch (IOException e) {
			System.err.println(e.getMessage());
		}
		return count[0];
	}

	private static Mat pixelsToMat(int width, int height, boolean[] pixels) {
		final Mat output = new Mat(height, width, opencv_core.CV_8UC1);
		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				OpenCVHelper.setPixel(output, x, y, pixels[x + y * width] ? (byte) 0xFF : 0);
			}
		}
		return output;
	}
}
