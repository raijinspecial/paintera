package org.janelia.saalfeldlab.paintera.control.paint;

import java.lang.invoke.MethodHandles;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.janelia.saalfeldlab.fx.event.InstallAndRemove;
import org.janelia.saalfeldlab.paintera.data.DataSource;
import org.janelia.saalfeldlab.paintera.data.mask.MaskInUse;
import org.janelia.saalfeldlab.paintera.data.mask.MaskInfo;
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource;
import org.janelia.saalfeldlab.paintera.state.GlobalTransformManager;
import org.janelia.saalfeldlab.paintera.state.SourceInfo;
import org.janelia.saalfeldlab.paintera.state.SourceState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.fx.viewer.ViewerPanelFX;
import bdv.fx.viewer.ViewerState;
import bdv.viewer.Source;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.scene.Node;
import javafx.scene.input.MouseEvent;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.label.Label;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.util.Intervals;
import net.imglib2.util.LinAlgHelpers;
import net.imglib2.view.Views;

public class PaintActions2D
{

	private static Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	private static final class ForegroundCheck implements Predicate< UnsignedLongType >
	{

		@Override
		public boolean test( final UnsignedLongType t )
		{
			return t.getIntegerLong() > 0;
		}

	}

	private class PaintEventHandler
	{

		protected final AffineTransform3D labelToViewerTransform = new AffineTransform3D();

		protected final AffineTransform3D labelToGlobalTransform = new AffineTransform3D();

		protected final AffineTransform3D globalToViewerTransform = new AffineTransform3D();

		protected final SimpleObjectProperty< MaskedSource< ?, ? > > maskedSource = new SimpleObjectProperty<>();

		protected final SimpleObjectProperty< RandomAccessibleInterval< UnsignedLongType > > canvas = new SimpleObjectProperty<>();

		protected final SimpleObjectProperty< Interval > interval = new SimpleObjectProperty<>();

		private int fillLabel = 1;

		public void prepareForPainting( final Long id ) throws MaskInUse
		{
			if ( id == null )
			{
				LOG.debug( "Do not a valid id to paint: {} -- will not paint.", id );
				return;
			}
			final ViewerState state = viewer.getState();
			final Source< ? > viewerSource = sourceInfo.currentSourceProperty().get();
			final int currentSource = sourceInfo.currentSourceIndexInVisibleSources().get();
			this.canvas.set( null );
			this.maskedSource.set( null );
			this.interval.set( null );

			LOG.debug( "Prepare for painting with source {}", viewerSource );

			if ( viewerSource == null || !( viewerSource instanceof DataSource< ?, ? > ) || !sourceInfo.getState( viewerSource ).isVisibleProperty().get() ) { return; }

			final SourceState< ?, ? > currentSourceState = sourceInfo.getState( viewerSource );
			final DataSource< ?, ? > source = currentSourceState.getDataSource();

			if ( !( source instanceof MaskedSource< ?, ? > ) ) { return; }

			final MaskedSource< ?, ? > maskedSource = ( MaskedSource< ?, ? > ) source;

			final AffineTransform3D viewerTransform = new AffineTransform3D();
			state.getViewerTransform( viewerTransform );
			final AffineTransform3D screenScaleTransform = new AffineTransform3D();
			final int level = state.getBestMipMapLevel( screenScaleTransform, currentSource );
			maskedSource.getSourceTransform( 0, level, labelToGlobalTransform );
			this.labelToViewerTransform.set( viewerTransform.copy().concatenate( labelToGlobalTransform ) );
			this.globalToViewerTransform.set( viewerTransform );

			final UnsignedLongType value = new UnsignedLongType( id );

			final MaskInfo< UnsignedLongType > mask = new MaskInfo<>( 0, level, value );
			final RandomAccessibleInterval< UnsignedLongType > canvas = maskedSource.generateMask( mask, FOREGROUND_CHECK );
			// canvasSource.getDataSource( state.getCurrentTimepoint(), level );
			LOG.debug( "Setting canvas to {}", canvas );
			this.canvas.set( canvas );
			this.maskedSource.set( maskedSource );
			this.fillLabel = 1;
		}

		public void paint( final double viewerX, final double viewerY )
		{

//		LOG.warn( "At {} {}", viewerX, viewerY );

			final RandomAccessibleInterval< UnsignedLongType > labels = this.canvas.get();
			if ( labels == null ) { return; }
			final Interval trackedInterval = Paint2D.paint(
					Views.extendValue( labels, new UnsignedLongType( Label.INVALID ) ),
					fillLabel,
					viewerX,
					viewerY,
					brushRadius.get(),
					brushDepth.get(),
					labelToViewerTransform,
					globalToViewerTransform,
					labelToGlobalTransform );
			this.interval.set( Intervals.union( trackedInterval, Optional.ofNullable( this.interval.get() ).orElse( trackedInterval ) ) );
			++this.fillLabel;
			repaintRequest.run();

		}

		public void applyMask()
		{
			Optional.ofNullable( maskedSource.get() ).ifPresent( ms -> ms.applyMask( canvas.get(), interval.get(), FOREGROUND_CHECK ) );
		}

	}

	private static final ForegroundCheck FOREGROUND_CHECK = new ForegroundCheck();

	private final ViewerPanelFX viewer;

	private final SourceInfo sourceInfo;

	private final BrushOverlay brushOverlay;

	private final SimpleDoubleProperty brushRadius = new SimpleDoubleProperty( 5.0 );

	private final SimpleDoubleProperty brushRadiusIncrement = new SimpleDoubleProperty( 1.0 );

	private final SimpleDoubleProperty brushDepth = new SimpleDoubleProperty( 1.0 );

	private final Runnable repaintRequest;

	private final ExecutorService paintQueue;

	public PaintActions2D(
			final ViewerPanelFX viewer,
			final SourceInfo sourceInfo,
			final GlobalTransformManager manager,
			final Runnable repaintRequest,
			final ExecutorService paintQueue )
	{
		super();
		this.viewer = viewer;
		this.sourceInfo = sourceInfo;
		this.brushOverlay = new BrushOverlay( this.viewer, manager );
		this.brushOverlay.physicalRadiusProperty().bind( brushRadius );
		this.brushOverlay.brushDepthProperty().bind( brushDepth );
		this.repaintRequest = repaintRequest;
		this.paintQueue = paintQueue;
	}

	public void hideBrushOverlay()
	{
		setBrushOverlayVisible( false );
	}

	public void showBrushOverlay()
	{
		setBrushOverlayVisible( true );
	}

	public void setBrushOverlayVisible( final boolean visible )
	{
		this.brushOverlay.setVisible( visible );
		viewer.getDisplay().drawOverlays();
	}

	public void changeBrushRadius( final double sign )
	{
		if ( sign > 0 )
		{
			decreaseBrushRadius();
		}
		else if ( sign < 0 )
		{
			increaseBrushRadius();
		}
	}

	public void changeBrushDepth( final double sign )
	{
		final double newDepth = brushDepth.get() + ( sign > 0 ? -1 : 1 );
		this.brushDepth.set( Math.max( Math.min( newDepth, 2.0 ), 1.0 ) );
	}

	public void decreaseBrushRadius()
	{
		setBrushRadius( brushRadius.get() - brushRadiusIncrement.get() );
	}

	public void increaseBrushRadius()
	{
		setBrushRadius( brushRadius.get() + brushRadiusIncrement.get() );
	}

	public void setBrushRadius( final double radius )
	{
		if ( radius > 0 && radius < Math.min( viewer.getWidth(), viewer.getHeight() ) )
		{
			this.brushRadius.set( radius );
		}
	}

	public InstallAndRemove< Node > mouseHandler(
			final Supplier< Long > id,
			final Predicate< MouseEvent > filter )
	{

		final PaintMouseEventHandler handler = new PaintMouseEventHandler( id, filter );
		final InstallAndRemove< Node > iar = new InstallAndRemove< Node >()
		{

			@Override
			public void installInto( final Node t )
			{
				t.addEventHandler( MouseEvent.ANY, handler );
			}

			@Override
			public void removeFrom( final Node t )
			{
				t.removeEventHandler( MouseEvent.ANY, handler );
			}
		};
		return iar;
	}

	private class PaintMouseEventHandler implements EventHandler< MouseEvent >
	{

		private final Supplier< Long > id;

		private final Predicate< MouseEvent > filter;

		private double startX = 0;

		private double startY = 0;

		private boolean isDragging = false;

		private final PaintEventHandler handler = new PaintEventHandler();

		public PaintMouseEventHandler(
				final Supplier< Long > id,
				final Predicate< MouseEvent > filter )
		{
			super();
			this.id = id;
			this.filter = filter;
		}

		@Override
		public void handle( final MouseEvent event )
		{

			final EventType< ? extends MouseEvent > et = event.getEventType();
			final double x = event.getX();
			final double y = event.getY();

			if ( isDragging && MouseEvent.MOUSE_RELEASED.equals( et ) )
			{
				if ( isDragging )
				{
					paintQueue.submit( handler::applyMask );
				}
				isDragging = false;
				return;
			}

			else if ( !filter.test( event ) )
			{
				return;
			}
			else if ( MouseEvent.DRAG_DETECTED.equals( et ) )
			{
				event.consume();
				try
				{
					handler.prepareForPainting( id.get() );
				}
				catch ( final MaskInUse e )
				{
					LOG.info( "{} -- will not paint.", e.getMessage() );
					return;
				}
				isDragging = true;
				startX = event.getX();
				startY = event.getY();
			}
			else if ( MouseEvent.MOUSE_PRESSED.equals( et ) )
			{
				event.consume();
				try
				{
					handler.prepareForPainting( id.get() );
				}
				catch ( final MaskInUse e )
				{
					LOG.info( "{} -- will not paint.", e.getMessage() );
					return;
				}
				paintQueue.submit( () -> {
					handler.paint( x, y );
					handler.applyMask();
					return null;
				} );
			}
			else if ( ( MouseEvent.MOUSE_DRAGGED.equals( et ) || MouseEvent.MOUSE_MOVED.equals( et ) ) && isDragging )
			{
				if ( x != startX || y != startY )
				{
					final double[] p1 = new double[] { startX, startY };

					final double[] d = new double[] { x, y };

					LinAlgHelpers.subtract( d, p1, d );

					final double l = LinAlgHelpers.length( d );
					LinAlgHelpers.normalize( d );

					LOG.debug( "Number of paintings triggered {}", l + 1 );
					paintQueue.submit( () -> {

						final long t0 = System.currentTimeMillis();
						for ( int i = 0; i < l; ++i )
						{
							handler.paint( p1[ 0 ], p1[ 1 ] );
							LinAlgHelpers.add( p1, d, p1 );
						}
						handler.paint( x, y );
						final long t1 = System.currentTimeMillis();
						LOG.debug( "Painting {} times with radius {} took a total of {}ms", l + 1, brushRadius.get(), t1 - t0 );
					} );
				}
				startX = x;
				startY = y;
				event.consume();
			}
		}

	}

	public DoubleProperty brushRadiusProperty()
	{
		return this.brushRadius;
	}

	public DoubleProperty brushRadiusIncrementProperty()
	{
		return this.brushRadiusIncrement;
	}

	public DoubleProperty brushDepthProperty()
	{
		return this.brushDepth;
	}

}
