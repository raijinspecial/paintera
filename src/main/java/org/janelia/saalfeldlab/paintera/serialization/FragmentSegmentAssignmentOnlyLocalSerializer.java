package org.janelia.saalfeldlab.paintera.serialization;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import javafx.util.Pair;
import org.janelia.saalfeldlab.paintera.control.assignment.FragmentSegmentAssignmentOnlyLocal;
import org.janelia.saalfeldlab.paintera.control.assignment.action.AssignmentAction;
import org.scijava.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Plugin(type = PainteraSerialization.PainteraSerializer.class)
public class FragmentSegmentAssignmentOnlyLocalSerializer implements PainteraSerialization.PainteraSerializer<FragmentSegmentAssignmentOnlyLocal>
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public static final String TYPE_KEY = "type";

	public static final String DATA_KEY = "data";

	public static final String ACTIONS_KEY = "actions";

	@Override
	public JsonElement serialize(final FragmentSegmentAssignmentOnlyLocal src, final Type typeOfSrc, final
	JsonSerializationContext context)
	{
		final List<AssignmentAction> actions = src.events().stream().filter(p -> p.getValue().get()).map(Pair::getKey).collect(Collectors.toList());
		LOG.debug("Serializing actions {}", actions);
		final List<JsonElement> serializedActions = new ArrayList<>();
		for (final AssignmentAction action : actions)
		{
			final JsonObject serializedAction = new JsonObject();
			serializedAction.add(TYPE_KEY, context.serialize(action.getType()));
			serializedAction.add(DATA_KEY, context.serialize(action));
			serializedActions.add(serializedAction);
		}
		LOG.debug("Serialized actions {}", serializedActions);
		final JsonObject map = new JsonObject();
		map.add(ACTIONS_KEY, context.serialize(serializedActions));
		return map;
	}

	@Override
	public Class<FragmentSegmentAssignmentOnlyLocal> getTargetClass() {
		return FragmentSegmentAssignmentOnlyLocal.class;
	}
}
