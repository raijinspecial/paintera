package bdv.fx.viewer;

import net.imglib2.Interval;

public interface RequestRepaint {
	void requestRepaint();

	void requestRepaint(final Interval interval);
}
