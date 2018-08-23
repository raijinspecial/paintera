package org.janelia.saalfeldlab.paintera.ui.source;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javafx.beans.InvalidationListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import net.imglib2.FinalRealInterval;
import net.imglib2.RealInterval;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.realtransform.AffineTransform3D;
import org.janelia.saalfeldlab.fx.Labels;
import org.janelia.saalfeldlab.fx.TitledPanes;
import org.janelia.saalfeldlab.fx.util.InvokeOnJavaFXApplicationThread;
import org.janelia.saalfeldlab.paintera.Paintera;
import org.janelia.saalfeldlab.paintera.data.mask.CannotClearCanvas;
import org.janelia.saalfeldlab.paintera.data.mask.MaskedSource;
import org.janelia.saalfeldlab.paintera.ui.BindUnbindAndNodeSupplier;

public class MaskedSourcePane implements BindUnbindAndNodeSupplier
{

	private final MaskedSource<?, ?> maskedSource;

	private final Consumer<RealInterval> centerAt;

	public MaskedSourcePane(MaskedSource<?, ?> maskedSource, Consumer<RealInterval> centerAt)
	{
		this.maskedSource = maskedSource;
		this.centerAt = centerAt;
	}

	@Override
	public Node get()
	{
		final CheckBox showCanvasCheckBox = new CheckBox("Show Canvas");
		final Button forgetButton = Buttons.withTooltip("Clear Canvas", e -> showForgetAlert());
		showCanvasCheckBox.selectedProperty().bindBidirectional(maskedSource.showCanvasOverBackgroundProperty());
		VBox contents = new VBox(
				showCanvasCheckBox,
				blockListNode(maskedSource, centerAt)
		);
		return TitledPanes.createCollapsed("Canvas", contents);
	}

	private void showForgetAlert()
	{
		final Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
		alert.setResizable(true);
		alert.setTitle(Paintera.NAME);
		alert.setHeaderText("Clear Canvas");
		final TextArea dialogText = new TextArea("Clearing canvas will remove all painted data that have not been committed yet. Proceed?");
		dialogText.setEditable(false);
		dialogText.setWrapText(true);
		alert.getDialogPane().setContent(dialogText);
		((Button)alert.getDialogPane().lookupButton(ButtonType.OK)).setText("Yes");
		((Button)alert.getDialogPane().lookupButton(ButtonType.CANCEL)).setText("No");
		if (alert.showAndWait().filter(bt -> ButtonType.OK.equals(bt)).isPresent()) {
			try {
				maskedSource.forgetCanvases();
			} catch (CannotClearCanvas e) {
				Exceptions.exceptionAlert(Paintera.NAME, "Unable to clear canvas.", e);
			}
		}
	}


	@Override
	public void bind()
	{

	}

	@Override
	public void unbind()
	{

	}

	private static Node blockListNode(
			MaskedSource<?, ?> maskedSource,
			Consumer<RealInterval> centerAt)
	{
		final int levels = maskedSource.getNumMipmapLevels();

		List<ObservableList<RealInterval>> intervals          = IntStream.range(0, levels).mapToObj(level -> FXCollections.<RealInterval>observableArrayList()).collect(Collectors.toList());
		TitledPane[]                   affectedBlockPanes = IntStream.range(0, levels).mapToObj(level -> TitledPanes.createCollapsed("" + level, intervalsNode(intervals.get(level), centerAt))).toArray(TitledPane[]::new);

		maskedSource.addOnCanvasClearedListener(() -> intervals.forEach(List::clear));
		maskedSource.addOnMaskAppliedListener(() -> updateLists(maskedSource, intervals));

		updateLists(maskedSource, intervals);


		return new VBox(affectedBlockPanes);
	}

	private static void updateLists(
			MaskedSource<?, ?> maskedSource,
			List<ObservableList<RealInterval>> intervals
	                               )
	{
		final int levels = maskedSource.getNumMipmapLevels();
		assert levels == intervals.size();

		long[] min = new long[3];

		double[] rmin = new double[3];
		double[] rmax = new double[3];

		// TODO need to figure out how to get blocks for other levels
		for ( int level = 0; level < 1; ++level )
		{
			long[] affectedBlocks = maskedSource.getAffectedBlocks();
			final ObservableList<RealInterval> ival = intervals.get(level);
			final AffineTransform3D tf = new AffineTransform3D();
			maskedSource.getSourceTransform(0, level, tf);
			final CellGrid grid = maskedSource.getCellGrid(0, level);
			List<RealInterval> localList = new ArrayList<>();
			for (long b : affectedBlocks)
			{
				grid.getCellGridPositionFlat(b, min);
				Arrays.setAll(rmin, d -> grid.getCellMin(d, min[d]));
				Arrays.setAll(rmax, d -> min[d] + grid.cellDimension(d) - 1);
				tf.apply(rmin, rmin);
				tf.apply(rmax, rmax);
				localList.add(new FinalRealInterval(rmin, rmax));
			}
			ival.setAll(localList);
		}

	}

	private static Node intervalsNode(ObservableList<RealInterval> intervals, Consumer<RealInterval> centerAt)
	{
		VBox contents = new VBox();
		intervals.addListener((InvalidationListener) obs -> InvokeOnJavaFXApplicationThread.invoke( () -> contents
				.getChildren()
				.setAll(new ArrayList<>(intervals).stream().map(interval -> MaskedSourcePane.intervalNode(interval, centerAt)).collect(Collectors.toList()))));
		return contents;
	}

	private static Node intervalNode(RealInterval interval, Consumer<RealInterval> centerAt)
	{
		final double minMaxWidth = 200;
		final double buttonWidth = 100;
		double[] min = new double[interval.numDimensions()];
		double[] max = new double[interval.numDimensions()];
		interval.realMin(min);
		interval.realMax(max);
		final long[] minRounded = Arrays.stream(min).mapToLong(Math::round).toArray();
		final long[] maxRounded = Arrays.stream(max).mapToLong(Math::round).toArray();

		final Label minLabel = Labels.withTooltip(Arrays.toString(minRounded), Arrays.toString(min));
		final Label maxLabel = Labels.withTooltip(Arrays.toString(maxRounded), Arrays.toString(max));

		final Button showButton = new Button("Center");
		showButton.setTooltip(new Tooltip("Axis aligns and centers orthogonal views at selected interval"));
		showButton.setOnAction(e -> centerAt.accept(interval));

		final Region filler = new Region();
		HBox.setHgrow(filler, Priority.ALWAYS);

		minLabel.setPrefWidth(minMaxWidth);
		maxLabel.setPrefWidth(minMaxWidth);
		showButton.setPrefWidth(buttonWidth);

		return new HBox(minLabel, maxLabel, filler, showButton);

	}
}
