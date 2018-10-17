package org.janelia.saalfeldlab.util;

import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;

public class IntervalUtils {

	public static <T> RandomAccessibleInterval<T> intersectIfNecessary(
			final RandomAccessibleInterval<T> target,
			final Interval interval)
	{
		return interval == null || Intervals.contains(interval, target)
				? target
				: Views.interval(target, Intervals.intersect(interval, target));
	}

}
