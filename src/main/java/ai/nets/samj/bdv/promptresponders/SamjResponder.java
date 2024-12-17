package ai.nets.samj.bdv.promptresponders;

import ai.nets.samj.annotation.Mask;
import ai.nets.samj.communication.model.SAMModel;
import ai.nets.samj.ui.SAMJLogger;
import bdv.interactive.prompts.BdvPrompts;
import bdv.interactive.prompts.planarshapes.PlanarPolygonIn3D;
import bdv.interactive.prompts.planarshapes.PlanarRectangleIn3D;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;

import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class SamjResponder <T extends RealType<T> & NativeType<T>> implements BdvPrompts.PromptsProcessor<T> {
	public SamjResponder(final SAMModel network) {
		this(network, LOCAL_CONSOLE_LOGGER);
	}

	public SamjResponder(final SAMModel network, final SAMJLogger log) {
/*
		//TODO why is this not working as expected?
		if (!network.isInstalled()) {
			log.error("SamjResponder: The provided network "+network.getName()+" is not installed.");
			throw new IllegalArgumentException("SamjResponder: The provided network "+network.getName()+" is not installed.");
		}
*/

		this.network = network;
		this.networkName = network.getName();
		this.isNetworkReady = false;
		this.log = log;
	}

	final SAMModel network;
	boolean isNetworkReady;
	final SAMJLogger log;

	@Override
	public List<PlanarPolygonIn3D> process(PlanarRectangleIn3D<T> prompt, boolean hasViewChangedSinceBefore) {
		try {
			if (hasViewChangedSinceBefore || !isNetworkReady) {
				//re-encode on the current image
				network.setImage(prompt.getViewImage2D(), log);
				isNetworkReady = true;
			}

			final List<Polygon> awtPolys = new ArrayList<>(
					network.fetch2dSegmentation(prompt.getBbox2D())
							.stream()
							.map(m -> m.getContour())
							.collect(Collectors.toList())
				);
			if (returnLargestRoi) {
				int maxPerimeter = 0;
				Polygon longestPolygon = null;
				for (Polygon p : awtPolys) {
					if (p.npoints > maxPerimeter) {
						maxPerimeter = p.npoints;
						longestPolygon = p;
					}
				}
				awtPolys.clear();
				awtPolys.add(longestPolygon);
			}

			final AffineTransform3D sharedTo3dTransform = prompt.getTransformTo3d();
			final List<PlanarPolygonIn3D> planarPolys = new ArrayList<>(awtPolys.size());
			for (Polygon p : awtPolys) {
				PlanarPolygonIn3D pp = new PlanarPolygonIn3D(p.npoints, sharedTo3dTransform);
				for (int i = 0; i < p.npoints; ++i)
					pp.addPoint(p.xpoints[i], p.ypoints[i]);
				planarPolys.add(pp);
			}
			return planarPolys;
		} catch (IOException|InterruptedException e) {
			log.error("SamjResponder: Exception occurred during processing the prompt: "+e.getMessage());
			return Collections.emptyList();
		}
	}

	public boolean returnLargestRoi = true;
	public final String networkName;

	public static final SAMJLogger LOCAL_CONSOLE_LOGGER = new SAMJLogger() {
		@Override
		public void info(String text) {System.out.println(text);}
		@Override
		public void warn(String text) {System.out.println(text);}
		@Override
		public void error(String text) {System.out.println(text);}
	};
}
