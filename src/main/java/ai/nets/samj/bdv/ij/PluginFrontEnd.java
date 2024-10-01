package ai.nets.samj.bdv.ij;

import ai.nets.samj.bdv.promptresponders.FakeResponder;
import ai.nets.samj.bdv.promptresponders.SamjResponder;
import ai.nets.samj.bdv.promptresponders.ShowImageInIJResponder;
import ai.nets.samj.communication.model.EfficientSAM;
import ai.nets.samj.communication.model.SAM2Tiny;
import bdv.interactive.prompts.BdvPrompts;
import net.imagej.Dataset;
import net.imagej.ImgPlus;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.simplifiedio.SimplifiedIO;

import java.io.IOException;

@Plugin(type = Command.class, name = "SAMJ Annotator in BDV", menuPath = "Plugins>SAMJ>BDV demo")
public class PluginFrontEnd implements Command {
	@Parameter
	Dataset inputImage;

	@Parameter(label = "Select network to use: ",
			  choices = {"Efficient SAM", "SAM2 Tiny", "fake responses"})
			  //TODO use initializator to readout which networks are installed
	String selectedNetwork = "fake";

	@Parameter(label = "Show encoded images: ")
	boolean showImagesSubmittedToNetwork = false;

	@Override
	public void run() {
		Img<? extends RealType<?>> origImage = inputImage.getImgPlus().getImg();

		if (origImage.numDimensions() < 3)
			throw new IllegalArgumentException("Sorry, can't handle pure 2D images.");

		annotateWithBDV((Img)origImage);
	}

	public <T extends RealType<T>> void annotateWithBDV(final Img<T> img) {
		final BdvPrompts<T, FloatType> annotator = new BdvPrompts<>(img, new FloatType()).enableShowingPolygons();
		if (showImagesSubmittedToNetwork) annotator.addPromptsProcessor( new ShowImageInIJResponder<>() );

		try {
			if (selectedNetwork.startsWith("Efficient")) {
				System.out.println("...working with Efficient SAM");
				annotator.addPromptsProcessor( new SamjResponder<>( new EfficientSAM() ));
			} else if (selectedNetwork.startsWith("SAM2 Tiny")) {
				System.out.println("...working with SAM2 Tiny");
				annotator.addPromptsProcessor( new SamjResponder<>( new SAM2Tiny() ));
			} else {
				//in any other case, just add the fake responder...
				System.out.println("...working with fake responses");
				annotator.addPromptsProcessor( new FakeResponder<>() );
			}
		} catch (IOException|InterruptedException e) {
			System.out.println("Exception occurred during EfficientSAM initialization: "+e.getMessage());
		}
	}

	public static void main(String[] args) {
		ImgPlus image = SimplifiedIO.openImage("/home/ulman/devel/HackBrno23/HackBrno23_introIntoImglib2AndBDV__SOLUTION/src/main/resources/t1-head.tif");
		new PluginFrontEnd().annotateWithBDV(image.getImg());
	}
}
