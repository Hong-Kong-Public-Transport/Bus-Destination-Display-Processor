package org.transport.service;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.AllArgsConstructor;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.IntPointer;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.MatVector;
import org.transport.Application;
import org.transport.entity.Display;
import org.transport.tool.FileHelper;
import org.transport.tool.OpenCVHelper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

@AllArgsConstructor
public final class FileWriter {

	private final Path outputDirectory;
	private final ObjectArrayList<String> fileNames = new ObjectArrayList<>();

	public void writeFile(Display display) {
		final ObjectArrayList<Mat> outputFrames = new ObjectArrayList<>();

		try (final BytePointer outputBytePointer = new BytePointer()) {
			final int frameCount = display.getFrames().size();

			if (frameCount == 1) {
				outputFrames.add(OpenCVHelper.pixelsToMat(display.getWidth(), display.getHeight(), display.getFrames().getFirst().pixels()));
				opencv_imgcodecs.imencode(Application.FILE_FORMAT, outputFrames.getFirst(), outputBytePointer);
			} else {
				final int[] durations = new int[frameCount];

				for (int i = 0; i < frameCount; i++) {
					outputFrames.add(OpenCVHelper.pixelsToMat(display.getWidth(), display.getHeight(), display.getFrames().get(i).pixels()));
					durations[i] = display.getFrames().get(i == frameCount - 1 ? i : i + 1).delayMicros() / 1000;
				}

				final opencv_imgcodecs.Animation animation = new opencv_imgcodecs.Animation();
				animation.frames(new MatVector(outputFrames.toArray(Mat[]::new)));
				animation.durations(new IntPointer(durations));
				opencv_imgcodecs.imencodeanimation(Application.FILE_FORMAT, animation, outputBytePointer);
			}

			final byte[] outputBytes = new byte[(int) outputBytePointer.limit()];
			outputBytePointer.get(outputBytes);
			final Path outputPath = outputDirectory.resolve(display.getFileName());
			fileNames.add(display.getFileName());

			if (!Files.exists(outputPath) || !Arrays.equals(outputBytes, Files.readAllBytes(outputPath))) {
				Files.write(outputPath, outputBytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
			}
		} catch (IOException e) {
			System.err.printf("Failed to write file [%s]: %s%n", display.getFileName(), e.getMessage());
		} finally {
			outputFrames.forEach(Mat::release);
		}
	}

	public void cleanDirectory() {
		final int writeCount = fileNames.size();
		final int deleteCount = FileHelper.getDirectoryFileCount(outputDirectory) - writeCount;
		if (deleteCount < writeCount) {
			FileHelper.iterateDirectoryAndDelete(outputDirectory, path -> !fileNames.contains(path.getFileName().toString()));
		}
	}
}
