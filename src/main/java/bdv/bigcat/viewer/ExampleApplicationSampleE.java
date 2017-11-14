package bdv.bigcat.viewer;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;

import org.janelia.saalfeldlab.n5.N5;

import com.beust.jcommander.JCommander;
import com.sun.javafx.application.PlatformImpl;

import bdv.AbstractViewerSetupImgLoader;
import bdv.bigcat.viewer.atlas.Atlas;
import bdv.bigcat.viewer.atlas.data.DataSource;
import bdv.bigcat.viewer.atlas.data.HDF5LabelMultisetDataSource;
import bdv.bigcat.viewer.atlas.data.RandomAccessibleIntervalDataSource;
import bdv.img.cache.VolatileGlobalCellCache;
import bdv.util.volatiles.SharedQueue;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import javafx.application.Platform;
import javafx.stage.Stage;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.Volatile;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.display.AbstractLinearRange;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.interpolation.randomaccess.ClampingNLinearInterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.volatiles.VolatileARGBType;
import net.imglib2.type.volatiles.VolatileUnsignedByteType;
import net.imglib2.util.Intervals;
import net.imglib2.view.ExtendedRandomAccessibleInterval;
import net.imglib2.view.Views;

public class ExampleApplicationSampleE
{

	public static void main( final String[] args ) throws Exception
	{
		PlatformImpl.startup( () -> {} );

		final String USER_HOME = System.getProperty( "user.home" );

		String n5Path = "/nrs/saalfeld/sample_E/sample_E.n5";
		String rawGroup = "/volumes/raw";
		String labelsFile = "/groups/saalfeld/home/saalfelds/cremi/sample_B+_padded_20170424.aligned.hdf";
		String labelsDataset = "volumes/labels/neuron_ids";

		double[] resolution = { 4, 4, 40 };
		int[] rawCellSize = { 192, 96, 7 };
		int[] labelCellSize = { 79, 79, 4 };

		final Parameters params = getParameters( args );
		if ( params != null )
		{
			System.out.println( "parameters are not null" );
			n5Path = params.filePath;
			rawGroup = params.rawDatasetPath;
			labelsFile = n5Path;
			labelsDataset = params.labelDatasetPath;
			resolution = new double[] { params.resolution.get( 0 ), params.resolution.get( 1 ), params.resolution.get( 2 ) };
			rawCellSize = new int[] { params.rawCellSize.get( 0 ), params.rawCellSize.get( 1 ), params.rawCellSize.get( 2 ) };
			labelCellSize = new int[] { params.labelCellSize.get( 0 ), params.labelCellSize.get( 1 ), params.labelCellSize.get( 2 ) };
		}

		final int numPriorities = 20;
		final SharedQueue sharedQueue = new SharedQueue( 12, 20 );

		// TODO remove
		final VolatileGlobalCellCache cellCache = new VolatileGlobalCellCache( 1, 12 );

		final RandomAccessibleIntervalDataSource< UnsignedByteType, VolatileUnsignedByteType > rawSource =
				DataSource.createN5MipmapRawSource( "raw", N5.openFSReader( n5Path ), rawGroup, resolution, sharedQueue, UnsignedByteType::new, VolatileUnsignedByteType::new );

//		final HDF5UnsignedByteDataSource rawSource = new HDF5UnsignedByteDataSource( rawFile, rawDataset, rawCellSize, resolution, "raw", cellCache, 0 );

		final double[] min = Arrays.stream( Intervals.minAsLongArray( rawSource.getSource( 0, 0 ) ) ).mapToDouble( v -> v ).toArray();
		final double[] max = Arrays.stream( Intervals.maxAsLongArray( rawSource.getSource( 0, 0 ) ) ).mapToDouble( v -> v ).toArray();
		final AffineTransform3D affine = new AffineTransform3D();
		rawSource.getSourceTransform( 0, 0, affine );
		affine.apply( min, min );
		affine.apply( max, max );

		final Atlas viewer = new Atlas(
				new FinalInterval( Arrays.stream( min ).mapToLong( Math::round ).toArray(),
						Arrays.stream( max ).mapToLong( Math::round ).toArray() ),
				sharedQueue );

		final CountDownLatch latch = new CountDownLatch( 1 );
		Platform.runLater( () -> {
			final Stage stage = new Stage();
			try
			{
				viewer.start( stage );
			}
			catch ( final InterruptedException e )
			{
				e.printStackTrace();
			}

			stage.show();
			latch.countDown();
		} );

		latch.await();
		viewer.addRawSource( rawSource, 0., ( 1 << 8 ) - 1. );

		final HDF5LabelMultisetDataSource labelSpec2 = new HDF5LabelMultisetDataSource( labelsFile, labelsDataset, labelCellSize, "labels", cellCache, 1 );
		viewer.addLabelSource( labelSpec2 );

	}

	private static Parameters getParameters( final String[] args )
	{
		// get the parameters
		final Parameters params = new Parameters();
		JCommander.newBuilder()
				.addObject( params )
				.build()
				.parse( args );

		final boolean success = validateParameters( params );
		if ( !success )
			return null;

		return params;
	}

	private static boolean validateParameters( final Parameters params )
	{
		return params.filePath != "";
	}

	public static class VolatileRealARGBConverter< T extends RealType< T > > extends AbstractLinearRange implements Converter< Volatile< T >, VolatileARGBType >
	{

		public VolatileRealARGBConverter( final double min, final double max )
		{
			super( min, max );
		}

		@Override
		public void convert( final Volatile< T > input, final VolatileARGBType output )
		{
			final boolean isValid = input.isValid();
			output.setValid( isValid );
			if ( isValid )
			{
				final double a = input.get().getRealDouble();
				final int b = Math.min( 255, roundPositive( Math.max( 0, ( a - min ) / scale * 255.0 ) ) );
				final int argb = 0xff000000 | ( b << 8 | b ) << 8 | b;
				output.set( argb );
			}
		}

	}

	public static class ARGBConvertedSource< T > implements Source< VolatileARGBType >
	{
		final private AbstractViewerSetupImgLoader< T, ? extends Volatile< T > > loader;

		private final int setupId;

		private final Converter< Volatile< T >, VolatileARGBType > converter;

		final protected InterpolatorFactory< VolatileARGBType, RandomAccessible< VolatileARGBType > >[] interpolatorFactories;
		{
			interpolatorFactories = new InterpolatorFactory[] {
					new NearestNeighborInterpolatorFactory< VolatileARGBType >(),
					new ClampingNLinearInterpolatorFactory< VolatileARGBType >()
			};
		}

		public ARGBConvertedSource(
				final int setupId,
				final AbstractViewerSetupImgLoader< T, ? extends Volatile< T > > loader,
				final Converter< Volatile< T >, VolatileARGBType > converter )
		{
			this.setupId = setupId;
			this.loader = loader;
			this.converter = converter;
		}

		final public AbstractViewerSetupImgLoader< T, ? extends Volatile< T > > getLoader()
		{
			return loader;
		}

		@Override
		public RandomAccessibleInterval< VolatileARGBType > getSource( final int t, final int level )
		{
			return Converters.convert(
					loader.getVolatileImage( t, level ),
					converter,
					new VolatileARGBType() );
		}

		@Override
		public void getSourceTransform( final int t, final int level, final AffineTransform3D transform )
		{
			transform.set( loader.getMipmapTransforms()[ level ] );
		}

		/**
		 * TODO Store this in a field
		 */
		@Override
		public int getNumMipmapLevels()
		{
			return loader.getMipmapResolutions().length;
		}

		@Override
		public boolean isPresent( final int t )
		{
			return t == 0;
		}

		@Override
		public RealRandomAccessible< VolatileARGBType > getInterpolatedSource( final int t, final int level, final Interpolation method )
		{

			final ExtendedRandomAccessibleInterval< VolatileARGBType, RandomAccessibleInterval< VolatileARGBType > > extendedSource =
					Views.extendValue( getSource( t, level ), new VolatileARGBType( 0 ) );
			switch ( method )
			{
			case NLINEAR:
				return Views.interpolate( extendedSource, interpolatorFactories[ 1 ] );
			default:
				return Views.interpolate( extendedSource, interpolatorFactories[ 0 ] );
			}
		}

		@Override
		public VolatileARGBType getType()
		{
			return new VolatileARGBType();
		}

		@Override
		public String getName()
		{
			return "1 2 3";
		}

		@Override
		public VoxelDimensions getVoxelDimensions()
		{
			return null;
		}

		// TODO: make ARGBType version of this source
		public Source nonVolatile()
		{
			return this;
		}
	}

}
