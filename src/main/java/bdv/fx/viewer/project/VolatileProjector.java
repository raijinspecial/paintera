package bdv.fx.viewer.project;

import net.imglib2.Interval;

public interface VolatileProjector extends InterruptibleProjector {
	boolean map(Interval renderInterval, boolean clearUntouchedTargetPixels);

	boolean isValid();
}
