package ai.nets.samj.bdv.prompts;

import ai.nets.samj.bdv.planarshapes.PlanarPolygonIn3D;
import ai.nets.samj.bdv.planarshapes.PlanarRectangleIn3D;
import bdv.interactive.prompts.BdvPrompts;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.RealType;

import java.util.Collections;
import java.util.List;

public class ShowImageInIJResponder <T extends RealType<T>> implements BdvPrompts.PromptsProcessor<T> {
	@Override
	public List<PlanarPolygonIn3D> process(PlanarRectangleIn3D<T> prompt) {
		ImageJFunctions.show(prompt.getViewImage2D());
		return Collections.emptyList();
	}
}
