package ai.nets.samj.bdv.ij;

import ai.nets.samj.bdv.SAMJ_BDV_Annotator;
import net.imagej.Dataset;
import net.imglib2.RandomAccess;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.RealType;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

@Plugin(type = Command.class, name = "SAMJ Annotator in BDV", menuPath = "Plugins>SAMJ>BDV demo")
public class PluginFrontEnd implements Command {
	@Parameter
	Dataset inputImage;

	@Parameter
	boolean showImagesSubmittedToSAM = false;

	@Parameter
	boolean useFakePromptResults = false;

	@Parameter
	boolean drawSmallXYcrossesIntoPolygonsCentres = false;

	@Override
	public void run() {
		SAMJ_BDV_Annotator annotator = new SAMJ_BDV_Annotator();
		Img<? extends RealType<?>> origImage = inputImage.getImgPlus().getImg();
		//TODO: make sure it is a 3D image

		annotator.startBdvAnnotation((Img)origImage, inputImage.getName());
		annotator.setShowSubmittedImagesToSAM( showImagesSubmittedToSAM );
		annotator.setReturnFakeSAMResults( useFakePromptResults );

		if (drawSmallXYcrossesIntoPolygonsCentres) {
			annotator.registerPolygonsConsumer(p -> {
				double[] centre = new double[3];
				for (int i = 0; i < p.size(); ++i) {
					double[] c = p.coordinate3D(i);
					centre[0] += c[0];
					centre[1] += c[1];
					centre[2] += c[2];
				}
				if (p.size() > 0) {
					System.out.println("Hey, gonna draw a black xy-cross into polygon's centre in the original image.");
					centre[0] /= p.size();
					centre[1] /= p.size();
					centre[2] /= p.size();

					RandomAccess<? extends RealType<?>> ra = origImage.randomAccess();
					ra.setPosition(Math.round(centre[0]), 0);
					ra.setPosition(Math.round(centre[1]), 1);
					ra.setPosition(Math.round(centre[2]), 2);

					ra.get().setZero();

					ra.fwd(0);
					ra.get().setZero();
					ra.bck(0);
					ra.bck(0);
					ra.get().setZero();
					ra.fwd(0);

					ra.fwd(1);
					ra.get().setZero();
					ra.bck(1);
					ra.bck(1);
					ra.get().setZero();
					//ra.fwd(1);

					inputImage.update();
				}
			});
		}
	}
}
