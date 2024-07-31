package ai.nets.samj.bdv.ij;

import ai.nets.samj.bdv.SAMJ_BDV_Annotator;
import net.imagej.Dataset;
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

	@Override
	public void run() {
		SAMJ_BDV_Annotator annotator = new SAMJ_BDV_Annotator();
		Img<? extends RealType<?>> origImage = inputImage.getImgPlus().getImg();
		//TODO: make sure it is a 3D image

		annotator.startBdvAnnotation((Img)origImage, inputImage.getName());
		annotator.setShowSubmittedImagesToSAM( showImagesSubmittedToSAM );
		annotator.setReturnFakeSAMResults( useFakePromptResults );
	}
}
