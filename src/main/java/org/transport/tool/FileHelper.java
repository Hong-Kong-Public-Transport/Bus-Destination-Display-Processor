package org.transport.tool;

import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Predicate;

public final class FileHelper {

	public static void iterateDirectory(Path directory, PathConsumer pathConsumer) {
		try (final DirectoryStream<Path> directoryStream = Files.newDirectoryStream(directory)) {
			for (final Path path : directoryStream) {
				pathConsumer.accept(path);
			}
		} catch (IOException e) {
			System.err.println(e.getMessage());
		}
	}

	public static void iterateDirectoryAndDelete(Path directory, Predicate<Path> deleteCondition) {
		iterateDirectory(directory, path -> {
			if (deleteCondition.test(path)) {
				System.out.printf("Deleting %s [%s]%n", Files.isDirectory(path) ? "directory" : "file", path.toAbsolutePath());
				FileUtils.forceDelete(path.toFile());
			}
		});
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

	@FunctionalInterface
	public interface PathConsumer {
		void accept(Path path) throws IOException;
	}
}
