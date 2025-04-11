package ai.nets.samj.bdv.promptresponders;

import bdv.interactive.prompts.planarshapes.PlanarPolygonIn3D;
import bdv.interactive.prompts.planarshapes.PlanarRectangleIn3D;
import bdv.interactive.prompts.BdvPrompts;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;

import java.util.Collections;
import java.util.List;

public class ReportImageOnConsoleResponder <T extends RealType<T> & NativeType<T>> implements BdvPrompts.PromptsProcessor<T> {
	@Override
	public List<PlanarPolygonIn3D> process(PlanarRectangleIn3D<T> prompt, boolean hasViewChangedSinceBefore) {
		System.out.println("Processing a new prompt... "+prompt.getBbox2D());
		if (hasViewChangedSinceBefore) {
			System.out.println("  ...on a new image: "+prompt.getViewImage2D());
		}
		return Collections.emptyList();
	}
}
