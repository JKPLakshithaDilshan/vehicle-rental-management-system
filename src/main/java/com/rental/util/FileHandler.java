package com.rental.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class FileHandler {

	public void ensureFileExists(Path filePath) {
		try {
			Path parent = filePath.getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}
			if (!Files.exists(filePath)) {
				Files.createFile(filePath);
			}
		} catch (IOException exception) {
			throw new IllegalStateException("Unable to initialize file: " + filePath, exception);
		}
	}

	public List<String> readLines(Path filePath) {
		ensureFileExists(filePath);
		try {
			return new ArrayList<>(Files.readAllLines(filePath, StandardCharsets.UTF_8));
		} catch (IOException exception) {
			throw new IllegalStateException("Unable to read file: " + filePath, exception);
		}
	}

	public void writeLines(Path filePath, List<String> lines) {
		ensureFileExists(filePath);
		try {
			Files.write(filePath, lines, StandardCharsets.UTF_8,
					StandardOpenOption.TRUNCATE_EXISTING,
					StandardOpenOption.WRITE);
		} catch (IOException exception) {
			throw new IllegalStateException("Unable to write file: " + filePath, exception);
		}
	}
}
