package dnz.launcher;

import java.awt.Desktop;
import java.net.URI;
import javax.swing.JOptionPane;
import javax.swing.UIManager;

/**
 * Entry point of the standalone DNZ Launcher jar. Built for old Java too, so that someone with an older Java gets a
 * clear message (and a link to Java 25) instead of nothing happening when they double-click the jar.
 */
public final class Boot {
	private static final int NEEDED = 25;
	private static final String JAVA_PAGE = "https://adoptium.net/temurin/releases/?version=25";

	private Boot() {
	}

	public static void main(String[] args) throws Throwable {
		int version = javaVersion();
		if (version < NEEDED) {
			tooOld(version);
			return;
		}
		Class.forName("dnz.launcher.MainKt").getMethod("main", String[].class).invoke(null, (Object) args);
	}

	/** Major Java version: "1.8" = 8, "25" = 25. */
	private static int javaVersion() {
		String spec = System.getProperty("java.specification.version", "0");
		if (spec.startsWith("1.")) {
			spec = spec.substring(2);
		}
		try {
			return Integer.parseInt(spec);
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private static void tooOld(int version) {
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (Exception ignored) {
		}
		String message = "DNZ Launcher needs Java " + NEEDED + " or newer (this computer runs Java " + version + ").\n"
			+ "DNZ Launcher için Java " + NEEDED + " veya daha yenisi gerekiyor (bu bilgisayarda Java " + version + " var).\n\n"
			+ "Tip: the DNZ Launcher setup or the portable zip already has Java inside.\n"
			+ "İpucu: DNZ Launcher kurulumunda ve kurulumsuz (portable) zip'te Java zaten var.";
		Object[] buttons = {"Download Java 25 / Java 25 indir", "Close / Kapat"};
		int choice = JOptionPane.showOptionDialog(null, message, "DNZ Launcher", JOptionPane.DEFAULT_OPTION,
			JOptionPane.WARNING_MESSAGE, null, buttons, buttons[0]);
		if (choice == 0) {
			try {
				Desktop.getDesktop().browse(new URI(JAVA_PAGE));
			} catch (Exception ignored) {
			}
		}
	}
}
