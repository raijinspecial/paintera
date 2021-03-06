package org.janelia.saalfeldlab.paintera.serialization.sourcestate;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Type;
import java.util.function.Predicate;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import gnu.trove.set.hash.TLongHashSet;
import net.imglib2.Interval;
import org.janelia.saalfeldlab.paintera.control.lock.LockedSegmentsOnlyLocal;
import org.janelia.saalfeldlab.paintera.control.selection.SelectedSegments;
import org.janelia.saalfeldlab.paintera.meshes.InterruptibleFunction;
import org.janelia.saalfeldlab.paintera.meshes.ManagedMeshSettings;
import org.janelia.saalfeldlab.paintera.serialization.PainteraSerialization;
import org.janelia.saalfeldlab.paintera.state.LabelSourceState;
import org.scijava.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Plugin(type = PainteraSerialization.PainteraSerializer.class)
public class LabelSourceStateSerializer
		extends SourceStateSerialization.SourceStateSerializerWithoutDependencies<LabelSourceState<?, ?>>
	implements PainteraSerialization.PainteraSerializer<LabelSourceState<?, ?>>
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public static final String SELECTED_IDS_KEY = "selectedIds";

	public static final String ASSIGNMENT_KEY = "assignment";

	public static final String MANAGED_MESH_SETTINGS_KEY = "meshSettings";

	public static final String LABEL_BLOCK_MAPPING_KEY = "labelBlockMapping";

	public static final String TYPE_KEY = "type";

	public static final String DATA_KEY = "data";

	@Override
	public JsonObject serialize(final LabelSourceState<?, ?> state, final Type type, final JsonSerializationContext
			context)
	{
		final JsonObject map = super.serialize(state, type, context);
		map.add(SELECTED_IDS_KEY, context.serialize(state.selectedIds(), state.selectedIds().getClass()));
		map.add(ASSIGNMENT_KEY, context.serialize(state.assignment()));
		map.add(
				LabelSourceStateDeserializer.LOCKED_SEGMENTS_KEY,
				context.serialize(((LockedSegmentsOnlyLocal) state.lockedSegments()).lockedSegmentsCopy())
		       );
		final ManagedMeshSettings managedMeshSettings = new ManagedMeshSettings(state.managedMeshSettings()
				.getGlobalSettings());
		managedMeshSettings.set(state.managedMeshSettings());
		final TLongHashSet activeSegments = new TLongHashSet(new SelectedSegments(
				state.selectedIds(),
				state.assignment()
		).getSelectedSegments());
		final Predicate<Long> isSelected  = activeSegments::contains;
		final Predicate<Long> isManaged   = id -> managedMeshSettings.isManagedProperty(id).get();
		managedMeshSettings.keepOnlyMatching(isSelected.and(isManaged.negate()));
		map.add(MANAGED_MESH_SETTINGS_KEY, context.serialize(managedMeshSettings));
		return map;
	}

	@Override
	public Class<LabelSourceState<?, ?>> getTargetClass() {
		return (Class<LabelSourceState<?, ?>>) (Class<?>) LabelSourceState.class;
	}
}
