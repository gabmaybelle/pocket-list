package com.gabbymaybelle.pocketlist;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Writing a file so that a crash part-way through never leaves it half-written or empty. */
final class SafeFiles {
	/** What goes in the file. */
	interface Contents {
		void writeTo(Writer writer) throws IOException;
	}

	private SafeFiles() {
	}

	/**
	 * Writes to a temporary file beside {@code file}, then swaps it in with one move, so the old
	 * file stays whole until the new one is complete.
	 */
	static void write(Path file, Contents contents) throws IOException {
		Files.createDirectories(file.getParent());
		Path temp = file.resolveSibling(file.getFileName() + ".tmp");
		try (Writer writer = Files.newBufferedWriter(temp)) {
			contents.writeTo(writer);
		}
		try {
			Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
		}
	}
}
