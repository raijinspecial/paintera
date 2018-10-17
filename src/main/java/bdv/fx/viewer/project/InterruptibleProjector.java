package bdv.fx.viewer.project;

import net.imglib2.Interval;

public interface InterruptibleProjector {
	boolean map(Interval renderInterval);

	void cancel();

	long getLastFrameRenderNanoTime();
}
