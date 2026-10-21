package dnz.launcher;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.RoundRectangle2D;
import javax.swing.JComponent;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * The small "Updating your launcher" window with a turning circle, shown while a launcher update is
 * downloaded at start and while UpdateApply swaps the files.
 */
public final class UpdateSplash {
	private static final Color BACKGROUND = new Color(0x15, 0x15, 0x1A);
	private static final Color BORDER = new Color(0x2A, 0x2A, 0x33);
	private static final Color ACCENT = new Color(0xFF, 0x8A, 0x3D);
	private static final Color TRACK = new Color(0xFF, 0x8A, 0x3D, 50);
	private static final Color TEXT = new Color(0xF2, 0xF2, 0xF5);

	private JWindow window;
	private Timer timer;

	private UpdateSplash() {
	}

	/** Opens the window (centered on the screen); null when there is no screen. */
	public static UpdateSplash show(String text) {
		UpdateSplash splash = new UpdateSplash();
		try {
			SwingUtilities.invokeAndWait(() -> splash.open(text));
			return splash;
		} catch (Throwable e) {
			return null;
		}
	}

	private void open(String text) {
		long start = System.nanoTime();
		JComponent view = new JComponent() {
			@Override
			protected void paintComponent(Graphics g0) {
				Graphics2D g = (Graphics2D) g0.create();
				g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
				int w = getWidth(), h = getHeight();
				g.setColor(BACKGROUND);
				g.fill(new RoundRectangle2D.Float(0, 0, w - 1, h - 1, 18, 18));
				g.setColor(BORDER);
				g.draw(new RoundRectangle2D.Float(0, 0, w - 1, h - 1, 18, 18));
				// the turning circle: a faint ring with a bright arc going round
				int size = 26, x = 28, y = (h - size) / 2;
				g.setStroke(new BasicStroke(3.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				g.setColor(TRACK);
				g.drawOval(x, y, size, size);
				double t = (System.nanoTime() - start) / 1e9;
				double angle = -(t * 360 * 1.15) % 360;
				double sweep = 70 + 50 * Math.sin(t * 3.2);
				g.setColor(ACCENT);
				g.draw(new Arc2D.Double(x, y, size, size, angle, sweep, Arc2D.OPEN));
				g.setColor(TEXT);
				g.setFont(new Font("Segoe UI", Font.BOLD, 15));
				g.drawString(text, x + size + 18, h / 2 + 5);
				g.dispose();
			}
		};
		view.setPreferredSize(new Dimension(330, 76));
		window = new JWindow();
		window.setBackground(new Color(0, 0, 0, 0));
		window.setContentPane(view);
		window.pack();
		window.setLocationRelativeTo(null);
		window.setAlwaysOnTop(true);
		window.setVisible(true);
		timer = new Timer(16, e -> view.repaint());
		timer.start();
	}

	public void close() {
		SwingUtilities.invokeLater(() -> {
			if (timer != null) {
				timer.stop();
			}
			if (window != null) {
				window.dispose();
			}
		});
	}
}
