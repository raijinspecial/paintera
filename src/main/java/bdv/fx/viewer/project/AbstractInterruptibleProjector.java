package bdv.fx.viewer.project;

import net.imglib2.AbstractInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converter;

public abstract class AbstractInterruptibleProjector< A, B > extends AbstractInterval implements InterruptibleProjector
{
	/**
	 * A converter from the source pixel type to the target pixel type.
	 */
	final protected Converter< ? super A, B > converter;

	/**
	 * The target interval. Pixels of the target interval should be set by
	 * {@link InterruptibleProjector#map(net.imglib2.Interval)}
	 */
	final protected RandomAccessibleInterval< B > target;

	/**
	 * Create new projector with a number of source dimensions and a converter
	 * from source to target pixel type. The new projector's
	 * {@link #numDimensions()} will be equal the number of source dimensions,
	 * allowing it to act as an interval on the source.
	 *
	 * @param numSourceDimensions
	 *            number of dimensions of the source.
	 * @param converter
	 *            converts from the source pixel type to the target pixel type.
	 * @param target
	 *            the target interval that this projector maps to
	 */
	public AbstractInterruptibleProjector(
			final int numSourceDimensions,
			final Converter< ? super A, B > converter,
			final RandomAccessibleInterval< B > target)
	{
		super( numSourceDimensions );
		this.converter = converter;
		this.target = target;
	}
}

