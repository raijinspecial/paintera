package org.janelia.saalfeldlab.paintera.data.n5;

import net.imglib2.converter.Converter;
import net.imglib2.type.label.FromIntegerTypeConverter;
import net.imglib2.type.label.Label;
import net.imglib2.type.label.LabelMultisetType;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.util.Pair;

import java.util.Iterator;

/**
 * @author Philipp Hanslovsky
 *
 * Create an {@link Iterable} over painted pixels from an {@link Iterable} over pairs of background
 * ({@link LabelMultisetType}) and canvas ({@link UnsignedLongType}). If a canvas value is {@link Label#INVALID},
 * return background, otherwise return canvas converted to {@link LabelMultisetType}.
 *
 */
public class BackgroundCanvasIterable implements Iterable<LabelMultisetType> {

	private final Iterable<? extends Pair<LabelMultisetType, UnsignedLongType>> backgroundAndCanvas;

	/**
	 *
	 * @param backgroundAndCanvas {@link Iterable} over pairs of background ({@link LabelMultisetType})
	 *                                               and canvas ({@link UnsignedLongType}).
	 */
	public BackgroundCanvasIterable(Iterable<? extends Pair<LabelMultisetType, UnsignedLongType>> backgroundAndCanvas) {
		this.backgroundAndCanvas = backgroundAndCanvas;
	}

	public Iterator<LabelMultisetType> iterator() {
		return new Iterator<LabelMultisetType>() {

			final Iterator<? extends Pair<LabelMultisetType, UnsignedLongType>> iterator = backgroundAndCanvas.iterator();

			final Converter<UnsignedLongType, LabelMultisetType> conv = new FromIntegerTypeConverter<>();

			final LabelMultisetType type = FromIntegerTypeConverter.geAppropriateType();

			@Override
			public boolean hasNext() {
				return iterator.hasNext();
			}

			@Override
			public LabelMultisetType next() {
				final Pair<LabelMultisetType, UnsignedLongType> p = iterator.next();
				final UnsignedLongType b = p.getB();
				if (b.getIntegerLong() == Label.INVALID) {
					return p.getA();
				} else {
					conv.convert(b, type);
					return type;
				}
			}
		};
	}
}
