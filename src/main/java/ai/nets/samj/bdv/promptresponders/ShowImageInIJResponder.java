package ai.nets.samj.bdv.promptresponders;

import bdv.interactive.prompts.planarshapes.PlanarPolygonIn3D;
import bdv.interactive.prompts.planarshapes.PlanarRectangleIn3D;
import bdv.interactive.prompts.BdvPrompts;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.RealType;

import java.util.Collections;
import java.util.List;

public class ShowImageInIJResponder <T extends RealType<T>> implements BdvPrompts.PromptsProcessor<T> {
	@Override
	public List<PlanarPolygonIn3D> process(PlanarRectangleIn3D<T> prompt, final boolean hasViewChangedSinceBefore) {
		//show the view image only if there is a new one
		if (hasViewChangedSinceBefore) ImageJFunctions.show(prompt.getViewImage2D());
		return Collections.emptyList();
	}
}
