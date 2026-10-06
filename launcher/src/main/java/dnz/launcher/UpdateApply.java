package dnz.launcher;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Swaps in a downloaded launcher update (see Updater.kt) and starts the launcher again.
 * Runs from a temporary copy of the launcher jar with plain Java only, after the launcher has quit.
 *
 * Arguments: launcher process id, lib folder, update folder, "--", command that starts the launcher.
 */
public final class UpdateApply {
	private UpdateApply() {
	}

	public static void main(String[] args) throws Exception {
		long pid = Long.parseLong(args[0]);
		File lib = new File(args[1]);
		File stage = new File(args[2]);
		List<String> restart = new ArrayList<>(Arrays.asList(args).subList(Arrays.asList(args).indexOf("--") + 1, args.length));

		// Old copies of this program from earlier updates.
		File[] old = new File(System.getProperty("java.io.tmpdir")).listFiles((d, n) -> n.startsWith("dnz-update-") && n.endsWith(".jar"));
		if (old != null) {
			for (File f : old) {
				f.delete(); // the copy this program runs from stays (it is in use)
			}
		}

		// Wait (up to a minute) for the launcher to close, so its jars are no longer in use.
		Optional<ProcessHandle> launcher = ProcessHandle.of(pid);
		long until = System.currentTimeMillis() + 60_000;
		while (launcher.isPresent() && launcher.get().isAlive() && System.currentTimeMillis() < until) {
			Thread.sleep(200);
		}

		try {
			File[] files = stage.listFiles();
			if (files != null) {
				for (File f : files) {
					if (f.getName().endsWith(".jar")) {
						replace(f, new File(lib, f.getName()));
					}
				}
			}
			File remove = new File(stage, "remove.txt");
			if (remove.isFile()) {
				for (String name : Files.readAllLines(remove.toPath())) {
					if (!name.isBlank() && name.endsWith(".jar") && !name.contains("/") && !name.contains("\\")) {
						Files.deleteIfExists(new File(lib, name).toPath());
					}
				}
			}
		} finally {
			// Never retried: a failed swap must not end in a restart loop.
			deleteTree(stage);
		}
		new ProcessBuilder(restart).directory(lib.getParentFile()).start();
	}

	/** Copies with a few retries: antivirus programs sometimes hold a fresh file for a moment. */
	private static void replace(File from, File to) throws Exception {
		IOException last = null;
		for (int i = 0; i < 25; i++) {
			try {
				Files.copy(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING);
				return;
			} catch (IOException e) {
				last = e;
				Thread.sleep(400);
			}
		}
		throw last;
	}

	private static void deleteTree(File dir) {
		File[] files = dir.listFiles();
		if (files != null) {
			for (File f : files) {
				deleteTree(f);
			}
		}
		dir.delete();
	}
}
